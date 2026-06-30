package nextflow.slack

import groovy.transform.CompileStatic
import nextflow.processor.TaskHandler
import nextflow.trace.TraceRecord
import nextflow.util.Duration
import java.util.regex.Pattern

@CompileStatic
class OnTaskCompleteConfig {
    final boolean enabled
    final boolean onFirstFailure
    final String throttleInterval
    final List<TaskSelectorRule> selectors

    OnTaskCompleteConfig(Map config) {
        config = config ?: [:]
        this.enabled = config.enabled != null ? config.enabled as boolean : false
        this.onFirstFailure = config.onFirstFailure != null ? config.onFirstFailure as boolean : false
        this.throttleInterval = config.throttleInterval as String ?: config.interval as String ?: '30s'
        def rules = []
        config.each { key, value ->
            if (key instanceof String && value instanceof Map && (key.startsWith('withName:') || key.startsWith('withLabel:'))) {
                rules << TaskSelectorRule.fromSelectorKey(key, value as Map)
            }
        }
        this.selectors = rules
    }

    boolean isActive() { enabled && (selectors || onFirstFailure) }
    long getThrottleIntervalMillis() { parseDurationMillis(throttleInterval) }

    static long parseDurationMillis(String value) {
        if (!value) return 30_000L
        def matcher = (value =~ /^(\d+)([smh])$/)
        if (matcher.matches()) {
            def amount = Long.parseLong(matcher.group(1))
            switch (matcher.group(2)) {
                case 's': return amount * 1000L
                case 'm': return amount * 60_000L
                case 'h': return amount * 3_600_000L
            }
        }
        try { return Duration.of(value).toMillis() } catch (Exception ignored) {
            try { return Long.parseLong(value) } catch (NumberFormatException e) { return 30_000L }
        }
    }
}

@CompileStatic
class TaskSelectorRule {
    enum SelectorType { WITH_NAME, WITH_LABEL }
    final SelectorType type
    final String pattern
    final boolean enabled
    final String minDuration

    TaskSelectorRule(SelectorType type, String pattern, boolean enabled, String minDuration) {
        this.type = type; this.pattern = pattern; this.enabled = enabled; this.minDuration = minDuration
    }

    static TaskSelectorRule fromSelectorKey(String key, Map settings) {
        final prefix = key.startsWith('withName:') ? 'withName:' : 'withLabel:'
        final type = key.startsWith('withName:') ? SelectorType.WITH_NAME : SelectorType.WITH_LABEL
        return new TaskSelectorRule(type, key.substring(prefix.length()).trim(),
            settings?.enabled != null ? settings.enabled as boolean : true, settings?.minDuration as String)
    }

    long getMinDurationMillis() { OnTaskCompleteConfig.parseDurationMillis(minDuration) }
}

@CompileStatic
class TaskSelectorMatcher {
    static boolean matchesName(String name, String pattern) {
        if (!name || !pattern) return false
        final isNegated = pattern.startsWith('!')
        if (isNegated) pattern = pattern.substring(1).trim()
        return Pattern.compile(pattern).matcher(name).matches() ^ isNegated
    }

    static boolean matchesLabels(List<String> labels, String pattern) {
        if (!pattern) return false
        final isNegated = pattern.startsWith('!')
        if (isNegated) pattern = pattern.substring(1).trim()
        final regex = Pattern.compile(pattern)
        for (String label : labels ?: Collections.<String>emptyList()) {
            if (regex.matcher(label).matches()) return !isNegated
        }
        return isNegated
    }
}

@CompileStatic
class TaskNotificationMatcher {
    static boolean isTaskFailure(TraceRecord trace) {
        if (!trace) return false
        def exit = trace.get('exit'); if (exit == null) return false
        def v = exit.toString().trim()
        return v && v != '0' && v != '-'
    }

    static long getTaskDurationMillis(TraceRecord trace) {
        if (!trace) return 0L
        def duration = trace.get('realtime') ?: trace.get('duration')
        if (duration instanceof Duration) return (duration as Duration).toMillis()
        if (duration) { try { return Duration.of(duration.toString()).toMillis() } catch (Exception ignored) {} }
        return 0L
    }

    static List<String> getProcessLabels(TaskHandler handler) {
        def labels = handler?.task?.processor?.config?.labels
        return labels instanceof List ? labels as List<String> : Collections.<String>emptyList()
    }

    static TaskSelectorRule findMatchingRule(OnTaskCompleteConfig config, TaskHandler handler, TraceRecord trace) {
        if (!config?.selectors) return null
        def processName = trace?.get('process')?.toString()
        def labels = getProcessLabels(handler)
        for (TaskSelectorRule rule : config.selectors) {
            if (!rule.enabled) continue
            if (rule.minDurationMillis > 0L && getTaskDurationMillis(trace) < rule.minDurationMillis) continue
            boolean matches = false
            if (rule.type == TaskSelectorRule.SelectorType.WITH_NAME && processName) {
                matches = TaskSelectorMatcher.matchesName(processName, rule.pattern)
            } else if (rule.type == TaskSelectorRule.SelectorType.WITH_LABEL) {
                matches = TaskSelectorMatcher.matchesLabels(labels, rule.pattern)
            }
            if (matches) return rule
        }
        return null
    }

    static boolean shouldNotify(OnTaskCompleteConfig config, TaskHandler handler, TraceRecord trace, boolean firstFailureAlreadySent) {
        if (!config?.isActive() || !trace) return false
        if (config.onFirstFailure && isTaskFailure(trace) && !firstFailureAlreadySent) return true
        return findMatchingRule(config, handler, trace) != null
    }
}

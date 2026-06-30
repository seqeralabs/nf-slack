/*
 * Copyright 2025, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nextflow.slack

import groovy.json.JsonBuilder
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.trace.TraceRecord
import nextflow.util.Duration

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Builds Slack messages using Block Kit format for workflow events.
 *
 * Supports creating formatted messages for:
 * - Workflow started
 * - Workflow completed successfully
 * - Workflow failed
 * - Custom messages
 *
 * @author Adam Talbot <adam.talbot@seqera.io>
 */
@Slf4j
@CompileStatic
class SlackMessageBuilder {

    private static final String NEXTFLOW_ICON = 'https://www.nextflow.io/docs/latest/_static/favicon.ico'

    private final SlackConfig config
    private final Session session

    SlackMessageBuilder(SlackConfig config, Session session) {
        this.config = config
        this.session = session
    }

    /**
     * Create a markdown field block
     */
    private static Map createMarkdownField(String title, String value) {
        return [
            type: 'mrkdwn',
            text: "*${title}*\n${value}"
        ]
    }

    /**
     * Create a header section block
     */
    private static Map createHeaderSection(String text) {
        return [
            type: 'section',
            text: [
                type: 'mrkdwn',
                text: text
            ]
        ]
    }

    /**
     * Create a section block with fields
     */
    private static Map createFieldsSection(List<Map> fields) {
        return [
            type: 'section',
            fields: fields
        ]
    }

    /**
     * Create a divider block
     */
    private static Map createDivider() {
        return [type: 'divider']
    }

    /**
     * Check if a field should be included based on config-level includeFields.
     * When the list is null or empty, all fields are shown (default behavior).
     * When set, only listed fields are shown.
     */
    private static boolean shouldIncludeField(List<String> includeFields, String fieldName) {
        return !includeFields || includeFields.contains(fieldName)
    }

    /**
     * Create a context footer block with timestamp
     */
    private static Map createContextFooter(String status, String timestamp, String workflowName) {
        return [
            type: 'context',
            elements: [
                [
                    type: 'mrkdwn',
                    text: formatTimestamp(timestamp)
                ]
            ]
        ]
    }

    /**
     * Create a Seqera Platform button action block
     */
    private static Map createSeqeraPlatformButton(String url) {
        return [
            type: 'actions',
            elements: [
                [
                    type: 'button',
                    text: [
                        type: 'plain_text',
                        text: '🔗 View in Seqera Platform',
                        emoji: true
                    ],
                    url: url,
                    style: 'primary'
                ]
            ]
        ]
    }

    /**
     * Create a command line section block
     */
    private static Map createCommandLineSection(String commandLine) {
        return [
            type: 'section',
            text: [
                type: 'mrkdwn',
                text: "*Command Line*\n```${commandLine}```"
            ]
        ]
    }

    /**
     * Get Seqera Platform watch URL from TowerClient if deep links are enabled.
     * Returns null if unavailable — callers should skip the button.
     *
     * The URL is read directly from TowerClient's watchUrl field, which is the
     * fully-resolved URL returned by the Seqera Platform API.
     */
    private String getSeqeraPlatformUrl() {
        if (!config.seqeraPlatform?.enabled) return null

        def watchUrl = getTowerClientWatchUrl()
        log.debug "Seqera Platform: watchUrl=${watchUrl}"
        return watchUrl
    }

    /**
     * Retrieve the watch URL from TowerClient via reflection.
     *
     * TowerClient is in a separate plugin (nf-tower) so we cannot reference it
     * directly. We find it by class name in the session's observer list, then
     * read its private watchUrl field.
     */
    // Package-private for testability (Spock Spy cannot stub private methods)
    String getTowerClientWatchUrl() {
        try {
            def observersField = session.class.getDeclaredField('observersV2')
            observersField.accessible = true
            def observers = observersField.get(session) as List

            def towerClient = observers?.find {
                it.class.name == 'io.seqera.tower.plugin.TowerClient'
            }
            if (!towerClient) return null

            def watchUrlField = towerClient.class.getDeclaredField('watchUrl')
            watchUrlField.accessible = true
            return watchUrlField.get(towerClient) as String
        }
        catch (Exception e) {
            log.debug "Could not retrieve Seqera Platform watch URL: ${e.message}"
            return null
        }
    }

    /**
     * Get resource usage statistics as a formatted string
     */
    private String getResourceUsageStats() {
        def stats = session.workflowMetadata?.stats
        if (!stats) return null

        def resourceInfo = []
        if (stats.cachedCount) resourceInfo << "Cached: ${stats.cachedCount}"
        if (stats.succeedCount) resourceInfo << "Completed: ${stats.succeedCount}"
        if (stats.failedCount) resourceInfo << "Failed: ${stats.failedCount}"

        return resourceInfo ? resourceInfo.join(', ') : null
    }

    /**
     * Build the content blocks for a workflow start message (without footer).
     * Used by both buildWorkflowStartMessage and buildProgressUpdateMessage.
     */
    private List buildStartContentBlocks() {
        def blocks = []
        def includeFields = config.onStart.includeFields

        def runName = session.runName ?: 'Unknown run'

        // Header section
        def messageText = config.onStart.message instanceof String ? config.onStart.message : '🚀 *Pipeline started*'
        blocks << createHeaderSection(messageText)

        // Build fields
        List<Map> fields = []
        if (shouldIncludeField(includeFields, 'runName')) {
            fields << createMarkdownField('Run Name', runName)
        }

        if (fields) {
            blocks << createDivider()
            blocks << createFieldsSection(fields)
        }

        // Add work directory in a separate section if present
        if (shouldIncludeField(includeFields, 'workDir') && session.workDir) {
            blocks << [
                type: 'section',
                text: [
                    type: 'mrkdwn',
                    text: "*Work Directory*\n`${session.workDir}`"
                ]
            ]
        }

        // Add command line in a separate section if configured
        if (shouldIncludeField(includeFields, 'commandLine') && config.onStart.includeCommandLine && session.commandLine) {
            blocks << createCommandLineSection(session.commandLine)
        }

        return blocks
    }

    /**
     * Build message for workflow started event
     */
    String buildWorkflowStartMessage(String threadTs = null, String channelOverride = null) {
        def workflowName = session.workflowMetadata?.scriptName ?: 'Unknown workflow'
        def timestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        // Check if using custom message configuration
        if (config.onStart.message instanceof Map) {
            return buildCustomMessage(config.onStart.message as Map, workflowName, timestamp, 'started', null, threadTs, channelOverride)
        }

        def blocks = buildStartContentBlocks()

        // Footer
        if (config.onStart.showFooter) {
            blocks << createDivider()
            blocks << createContextFooter('started', timestamp, workflowName)
        }

        // Seqera Platform deep link button
        def seqeraUrl = getSeqeraPlatformUrl()
        if (seqeraUrl) {
            blocks << createSeqeraPlatformButton(seqeraUrl)
        }

        return createMessagePayload(blocks, threadTs, channelOverride)
    }

    /**
     * Build message for workflow completed successfully
     */
    String buildWorkflowCompleteMessage(String threadTs = null, String channelOverride = null) {
        def workflowName = session.workflowMetadata?.scriptName ?: 'Unknown workflow'
        def runName = session.runName ?: 'Unknown run'
        def duration = session.workflowMetadata?.duration ?: Duration.of(0)
        def timestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        // Check if using custom message configuration
        if (config.onComplete.message instanceof Map) {
            return buildCustomMessage(config.onComplete.message as Map, workflowName, timestamp, 'completed', null, threadTs, channelOverride)
        }

        def blocks = []
        def includeFields = config.onComplete.includeFields

        // Header section
        def messageText = config.onComplete.message instanceof String ? config.onComplete.message : '✅ *Pipeline completed successfully*'
        blocks << createHeaderSection(messageText)

        // Build fields
        List<Map> fields = []
        if (shouldIncludeField(includeFields, 'runName')) {
            fields << createMarkdownField('Run Name', runName)
        }
        if (shouldIncludeField(includeFields, 'duration')) {
            fields << createMarkdownField('Duration', duration.toString())
        }
        if (shouldIncludeField(includeFields, 'status')) {
            fields << createMarkdownField('Status', '✅ Success')
        }

        // Add resource usage if configured (via includeResourceUsage or includeFields)
        if (shouldIncludeField(includeFields, 'tasks') && (config.onComplete.includeResourceUsage || includeFields)) {
            def resourceStats = getResourceUsageStats()
            if (resourceStats) {
                fields << createMarkdownField('Tasks', resourceStats)
            }
        }

        if (fields) {
            blocks << createDivider()
            blocks << createFieldsSection(fields)
        }

        // Footer
        if (config.onComplete.showFooter) {
            blocks << createDivider()
            blocks << createContextFooter('completed', timestamp, workflowName)
        }

        // Seqera Platform deep link button
        def seqeraUrl = getSeqeraPlatformUrl()
        if (seqeraUrl) {
            blocks << createSeqeraPlatformButton(seqeraUrl)
        }

        return createMessagePayload(blocks, threadTs, channelOverride)
    }

    /**
     * Build message for workflow error
     */
    String buildWorkflowErrorMessage(TraceRecord errorRecord, String threadTs = null, String channelOverride = null) {
        def workflowName = session.workflowMetadata?.scriptName ?: 'Unknown workflow'
        def runName = session.runName ?: 'Unknown run'
        def duration = session.workflowMetadata?.duration ?: Duration.of(0)
        def timestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        def errorMessage = session.workflowMetadata?.errorMessage ?: 'Unknown error'

        // Check if using custom message configuration
        if (config.onError.message instanceof Map) {
            return buildCustomMessage(config.onError.message as Map, workflowName, timestamp, 'failed', errorRecord, threadTs, channelOverride)
        }

        def blocks = []
        def includeFields = config.onError.includeFields

        // Header section
        def messageText = config.onError.message instanceof String ? config.onError.message : '❌ *Pipeline failed*'
        blocks << createHeaderSection(messageText)

        // Build fields
        List<Map> fields = []
        if (shouldIncludeField(includeFields, 'runName')) {
            fields << createMarkdownField('Run Name', runName)
        }
        if (shouldIncludeField(includeFields, 'duration')) {
            fields << createMarkdownField('Duration', duration.toString())
        }
        if (shouldIncludeField(includeFields, 'status')) {
            fields << createMarkdownField('Status', '❌ Failed')
        }

        if (shouldIncludeField(includeFields, 'failedProcess') && errorRecord) {
            def processName = errorRecord.get('process')
            if (processName) {
                fields << createMarkdownField('Failed Process', "`${processName}`")
            }
        }

        if (fields) {
            blocks << createDivider()
            blocks << createFieldsSection(fields)
        }

        if (shouldIncludeField(includeFields, 'errorMessage')) {
            blocks << createDivider()
            blocks << [
                type: 'section',
                text: [
                    type: 'mrkdwn',
                    text: "*Error Message*\n```${errorMessage.take(2000)}${errorMessage.length() > 2000 ? '...' : ''}```"
                ]
            ]
        }

        if (shouldIncludeField(includeFields, 'commandLine') && config.onError.includeCommandLine && session.commandLine) {
            blocks << createCommandLineSection(session.commandLine)
        }

        // Footer
        if (config.onError.showFooter) {
            blocks << createDivider()
            blocks << createContextFooter('failed', timestamp, workflowName)
        }

        // Seqera Platform deep link button
        def seqeraUrl = getSeqeraPlatformUrl()
        if (seqeraUrl) {
            blocks << createSeqeraPlatformButton(seqeraUrl)
        }

        return createMessagePayload(blocks, threadTs, channelOverride)
    }

    /**
     * Build a simple text message
     */
    String buildSimpleMessage(String text, String threadTs = null) {
        def blocks = [
            [
                type: 'section',
                text: [
                    type: 'mrkdwn',
                    text: text
                ]
            ]
        ]
        return createMessagePayload(blocks, threadTs)
    }

    /**
     * Build a rich message with custom formatting
     *
     * @param options Map with keys: message (required), fields (list of maps with title/value/short)
     */
    String buildRichMessage(Map options, String threadTs = null) {
        if (!options.message) {
            throw new IllegalArgumentException("Message text is required")
        }

        def blocks = []
        blocks << createHeaderSection(options.message as String)

        def fieldsList = options.fields as List ?: []
        if (fieldsList) {
            def fields = fieldsList.collect { field ->
                def f = field as Map
                createMarkdownField(f.title as String, f.value as String)
            }
            blocks << createDivider()
            blocks << createFieldsSection(fields)
        }

        return createMessagePayload(blocks, threadTs)
    }

    /**
     * Build a custom message using map configuration
     */
    private String buildCustomMessage(Map customConfig, String workflowName, String timestamp, String status, TraceRecord errorRecord = null, String threadTs = null, String channelOverride = null) {
        def runName = session.runName ?: 'Unknown run'
        def duration = session.workflowMetadata?.duration ?: Duration.of(0)
        def errorMessage = session.workflowMetadata?.errorMessage ?: 'Unknown error'

        def blocks = []

        // Get message text
        def messageText = customConfig.text as String ?: getDefaultMessageText(status)
        blocks << createHeaderSection(messageText)

        // Build fields
        List<Map> fields = []

        // Add default fields if specified
        def includeFields = customConfig.includeFields as List ?: []
        if (includeFields) {
            if (includeFields.contains('runName')) {
                fields << createMarkdownField('Run Name', runName)
            }
            if (includeFields.contains('duration') && status != 'started') {
                fields << createMarkdownField('Duration', duration.toString())
            }
            if (includeFields.contains('status')) {
                fields << createMarkdownField('Status', getStatusEmoji(status))
            }
            if (includeFields.contains('failedProcess') && errorRecord) {
                def processName = errorRecord.get('process')
                if (processName) {
                    fields << createMarkdownField('Failed Process', "`${processName}`")
                }
            }
            if (includeFields.contains('tasks') && status == 'completed') {
                def resourceStats = getResourceUsageStats()
                if (resourceStats) {
                    fields << createMarkdownField('Tasks', resourceStats)
                }
            }
        }

        // Add custom fields
        def customFields = customConfig.customFields as List ?: []
        customFields.each { field ->
            def f = field as Map
            fields << createMarkdownField(f.title as String, f.value as String)
        }

        if (fields) {
            blocks << createDivider()
            blocks << createFieldsSection(fields)
        }

        // Handle long fields that should be their own sections
        if (includeFields.contains('commandLine') && session.commandLine) {
            blocks << createCommandLineSection(session.commandLine)
        }
        if (includeFields.contains('workDir') && session.workDir && status == 'started') {
            blocks << [
                type: 'section',
                text: [
                    type: 'mrkdwn',
                    text: "*Work Directory*\n`${session.workDir}`"
                ]
            ]
        }
        if (includeFields.contains('errorMessage') && status == 'failed') {
            blocks << [
                type: 'section',
                text: [
                    type: 'mrkdwn',
                    text: "*Error Message*\n```${errorMessage.take(2000)}${errorMessage.length() > 2000 ? '...' : ''}```"
                ]
            ]
        }

        // Footer
        def shouldShowFooter = status == 'started' ? config.onStart.showFooter :
                              (status == 'completed' ? config.onComplete.showFooter : config.onError.showFooter)
        if (shouldShowFooter) {
            blocks << createDivider()
            blocks << createContextFooter(status, timestamp, workflowName)
        }

        return createMessagePayload(blocks, threadTs, channelOverride)
    }

    /**
     * Get default message text for a status
     */
    private static String getDefaultMessageText(String status) {
        switch (status) {
            case 'started':
                return '*Pipeline started*'
            case 'completed':
                return '*Pipeline completed successfully*'
            case 'failed':
                return '*Pipeline failed*'
            default:
                return '*Pipeline event*'
        }
    }

    /**
     * Get status emoji
     */
    private static String getStatusEmoji(String status) {
        switch (status) {
            case 'started':
                return '🚀 Running'
            case 'completed':
                return '✅ Success'
            case 'failed':
                return '❌ Failed'
            default:
                return 'Unknown'
        }
    }

    /**
     * Format timestamp for display
     */
    private static String formatTimestamp(String isoTimestamp) {
        try {
            def dateTime = OffsetDateTime.parse(isoTimestamp)
            return dateTime.format(DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a"))
        } catch (Exception e) {
            return isoTimestamp
        }
    }

    /**
     * Create final message payload with channel and optional thread_ts
     */
    private String createMessagePayload(List blocks, String threadTs = null, String channelOverride = null) {
        def message = [blocks: blocks] as Map
        def channel = channelOverride ?: config.botChannel
        if (channel) {
            message.channel = channel
        }
        if (threadTs) {
            message.thread_ts = threadTs
        }
        return new JsonBuilder(message).toPrettyString()
    }

    /**
     * Build a progress update message showing workflow execution status.
     * Includes the original start message content with progress stats appended below.
     */
    String buildProgressUpdateMessage(int submitted, int completed, int cached, int failed, long elapsedMs, String threadTs = null) {
        // Start with the original start message content blocks
        def blocks = buildStartContentBlocks()

        // Append progress section
        blocks.add(createDivider())
        blocks.add(createHeaderSection('📊 *Progress*'))

        // Progress stats
        def elapsed = formatDuration(elapsedMs)
        List<Map> fields = []
        fields.add(createMarkdownField('Tasks Submitted', "${submitted}"))
        fields.add(createMarkdownField('Tasks Completed', "${completed}"))
        fields.add(createMarkdownField('Tasks Cached', "${cached}"))
        if (failed > 0) {
            fields.add(createMarkdownField('Tasks Failed', "${failed}"))
        }
        fields.add(createMarkdownField('Elapsed', elapsed))
        blocks.add(createFieldsSection(fields))

        return createMessagePayload(blocks, threadTs)
    }

    /**
     * Format a duration in milliseconds to a human-readable string.
     */
    private static String formatDuration(long millis) {
        long seconds = (long)(millis / 1000)
        long minutes = (long)(seconds / 60)
        long hours = (long)(minutes / 60)
        seconds = seconds % 60
        minutes = minutes % 60

        if (hours > 0) {
            return "${hours}h ${minutes}m ${seconds}s"
        } else if (minutes > 0) {
            return "${minutes}m ${seconds}s"
        } else {
            return "${seconds}s"
        }
    }
}

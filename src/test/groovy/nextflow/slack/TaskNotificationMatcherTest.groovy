package nextflow.slack

import nextflow.processor.TaskHandler
import nextflow.script.ProcessConfig
import nextflow.trace.TraceRecord
import nextflow.util.Duration
import spock.lang.Specification

class TaskNotificationMatcherTest extends Specification {

    private TraceRecord trace(String process, String exit = '0', Duration duration = Duration.of('45m')) {
        def record = Mock(TraceRecord)
        record.get('process') >> process
        record.get('exit') >> exit
        record.get('realtime') >> duration
        record.get('duration') >> duration
        return record
    }

    def 'should match withName selectors'() {
        given:
        def config = new OnTaskCompleteConfig([
            enabled: true,
            'withName:STAR_ALIGN': [enabled: true],
            'withName:GATK_.*': [enabled: true]
        ])

        expect:
        TaskNotificationMatcher.findMatchingRule(config, null, trace(process))?.pattern == expected

        where:
        process                | expected
        'STAR_ALIGN'           | 'STAR_ALIGN'
        'GATK_HaplotypeCaller' | 'GATK_.*'
        'FASTQC'               | null
    }

    def 'should notify on first failure without selectors'() {
        given:
        def config = new OnTaskCompleteConfig([enabled: true, onFirstFailure: true])

        expect:
        TaskNotificationMatcher.shouldNotify(config, null, trace('ANY', '1'), false)
        !TaskNotificationMatcher.shouldNotify(config, null, trace('ANY', '1'), true)
    }
}

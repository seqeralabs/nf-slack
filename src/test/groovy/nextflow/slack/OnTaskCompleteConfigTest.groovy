package nextflow.slack

import spock.lang.Specification

class OnTaskCompleteConfigTest extends Specification {

    def 'should have safe defaults'() {
        when:
        def config = new OnTaskCompleteConfig([:])

        then:
        !config.enabled
        !config.onFirstFailure
        config.throttleInterval == '30s'
        config.selectors.isEmpty()
        !config.isActive()
    }

    def 'should parse selectors and options'() {
        when:
        def config = new OnTaskCompleteConfig([
            enabled: true,
            onFirstFailure: true,
            throttleInterval: '1m',
            'withName:STAR_ALIGN': [enabled: true],
            'withName:GATK_.*': [enabled: true, minDuration: '30m']
        ])

        then:
        config.enabled
        config.selectors.size() == 2
        config.getThrottleIntervalMillis() == 60_000L
    }
}

package nextflow.slack

import spock.lang.Specification

class TaskSelectorMatcherTest extends Specification {

    def 'should match process names using Nextflow selector semantics'() {
        expect:
        TaskSelectorMatcher.matchesName(name, pattern) == expected

        where:
        name                   | pattern      | expected
        'STAR_ALIGN'           | 'STAR_ALIGN' | true
        'GATK_HaplotypeCaller' | 'GATK_.*'    | true
        'FASTQC'               | 'GATK_.*'    | false
        'FASTQC'               | '!FASTQC'    | false
        'STAR_ALIGN'           | '!FASTQC'    | true
    }

    def 'should match process labels'() {
        expect:
        TaskSelectorMatcher.matchesLabels(labels, pattern) == expected

        where:
        labels              | pattern         | expected
        ['long_running']    | 'long_running'  | true
        ['short']           | 'long_running'  | false
        ['short', 'batch']  | 'batch'         | true
        ['short']           | '!batch'        | true
    }
}

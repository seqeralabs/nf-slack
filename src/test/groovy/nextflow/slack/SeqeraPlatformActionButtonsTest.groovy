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

import nextflow.Session
import nextflow.script.WorkflowMetadata
import spock.lang.Specification
import spock.lang.Unroll

class SeqeraPlatformActionButtonsTest extends Specification {

    private static final String WATCH_URL = 'https://cloud.seqera.io/orgs/myorg/workspaces/myws/watch/abc123'

    private SlackMessageBuilder createBuilder(Map seqeraConfig = [:]) {
        def platformConfig = new SlackConfig([
            webhook: 'https://hooks.slack.com/test',
            seqeraPlatform: [enabled: true] + seqeraConfig
        ])
        def mockMetadata = Mock(WorkflowMetadata)
        mockMetadata.scriptName >> 'test-workflow.nf'
        def towerSession = Mock(Session)
        towerSession.config >> [:]
        towerSession.workflowMetadata >> mockMetadata
        towerSession.runName >> 'crazy_einstein'
        towerSession.uniqueId >> UUID.fromString('00000000-0000-0000-0000-000000000000')
        def builder = Spy(new SlackMessageBuilder(platformConfig, towerSession))
        builder.getTowerClientWatchUrl() >> WATCH_URL
        return builder
    }

    def 'extractWorkflowRunId parses watch URL'() {
        expect:
        SeqeraPlatformUrlBuilder.extractWorkflowRunId(WATCH_URL) == 'abc123'
        SeqeraPlatformUrlBuilder.extractWorkflowRunId('https://example.com/watch/run-id?tab=logs') == 'run-id'
        SeqeraPlatformUrlBuilder.extractWorkflowRunId(null) == null
        SeqeraPlatformUrlBuilder.extractWorkflowRunId('https://example.com/runs') == null
    }

    @Unroll
    def 'link mode buttons for phase #phase'() {
        given:
        def builder = createBuilder()
        def context = SeqeraWatchContext.fromWatchUrl(WATCH_URL)

        when:
        def actions = builder.createSeqeraPlatformActions(context, phase)

        then:
        actions.type == 'actions'
        actions.elements*.text.text == labels
        actions.elements.every { it.url == WATCH_URL }

        where:
        phase                        | labels
        SeqeraMessagePhase.RUNNING   | ['🔗 View in Seqera Platform', '⏹ Cancel']
        SeqeraMessagePhase.FAILED    | ['🔗 View in Seqera Platform', '▶️ Resume', '🔄 Relaunch']
        SeqeraMessagePhase.COMPLETED | ['🔗 View in Seqera Platform', '🔄 Relaunch']
    }

    def 'interactive mode emits action_id buttons'() {
        given:
        def builder = createBuilder(actionButtons: [mode: 'interactive'])
        def context = SeqeraWatchContext.fromWatchUrl(WATCH_URL)

        when:
        def actions = builder.createSeqeraPlatformActions(context, SeqeraMessagePhase.FAILED)

        then:
        actions.elements*.action_id == [
            'seqera_platform_view',
            'seqera_platform_resume',
            'seqera_platform_relaunch'
        ]
        actions.elements*.value == ['abc123', 'abc123', 'abc123']
        actions.elements.every { !it.url }
    }

    def 'disabled action buttons are omitted'() {
        given:
        def builder = createBuilder(actionButtons: [cancel: false, resume: false, relaunch: false])
        def context = SeqeraWatchContext.fromWatchUrl(WATCH_URL)

        when:
        def running = builder.createSeqeraPlatformActions(context, SeqeraMessagePhase.RUNNING)
        def failed = builder.createSeqeraPlatformActions(context, SeqeraMessagePhase.FAILED)
        def completed = builder.createSeqeraPlatformActions(context, SeqeraMessagePhase.COMPLETED)

        then:
        running.elements*.text.text == ['🔗 View in Seqera Platform']
        failed.elements*.text.text == ['🔗 View in Seqera Platform']
        completed.elements*.text.text == ['🔗 View in Seqera Platform']
    }

    def 'actionButtons config defaults'() {
        when:
        def config = new SeqeraPlatformActionButtonsConfig([:])

        then:
        config.mode == 'link'
        config.cancel
        config.resume
        config.relaunch
        config.isLinkMode()
        !config.isInteractiveMode()
    }
}

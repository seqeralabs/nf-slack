/*
 * Copyright 2025-2026, Seqera Labs
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
    private static final String API_ENDPOINT = 'https://api.cloud.seqera.io'

    private SlackMessageBuilder createBuilder(Map seqeraConfig = [:]) {
        def platformConfig = new SlackConfig([
            webhook: 'https://hooks.slack.com/test',
            seqeraPlatform: [enabled: true] + seqeraConfig
        ])
        def mockMetadata = Mock(WorkflowMetadata)
        mockMetadata.scriptName >> 'test-workflow.nf'
        def towerSession = Mock(Session)
        towerSession.config >> [tower: [endpoint: API_ENDPOINT]]
        towerSession.workflowMetadata >> mockMetadata
        towerSession.runName >> 'crazy_einstein'
        towerSession.uniqueId >> UUID.fromString('00000000-0000-0000-0000-000000000000')
        def builder = Spy(new SlackMessageBuilder(platformConfig, towerSession))
        builder.getTowerClientWatchUrl() >> WATCH_URL
        return builder
    }

    private static SeqeraWatchContext watchContext() {
        return SeqeraWatchContext.fromWatchUrl(WATCH_URL, API_ENDPOINT)
    }

    def 'extractWorkflowRunId parses watch URL'() {
        expect:
        SeqeraPlatformUrlBuilder.extractWorkflowRunId(WATCH_URL) == 'abc123'
        SeqeraPlatformUrlBuilder.extractWorkflowRunId('https://example.com/watch/run-id?tab=logs') == 'run-id'
        SeqeraPlatformUrlBuilder.extractWorkflowRunId(null) == null
        SeqeraPlatformUrlBuilder.extractWorkflowRunId('https://example.com/runs') == null
    }

    def 'cancelWorkflowUrl builds Platform API path'() {
        expect:
        SeqeraPlatformUrlBuilder.cancelWorkflowUrl(API_ENDPOINT, 'abc123') ==
            'https://api.cloud.seqera.io/workflow/abc123/cancel'
        SeqeraPlatformUrlBuilder.cancelWorkflowUrl('https://api.cloud.seqera.io/', 'abc123') ==
            'https://api.cloud.seqera.io/workflow/abc123/cancel'
        SeqeraPlatformUrlBuilder.cancelWorkflowUrl(API_ENDPOINT, null) == null
    }

    @Unroll
    def 'action buttons for phase #phase'() {
        given:
        def builder = createBuilder()
        def context = watchContext()

        when:
        def actions = builder.createSeqeraPlatformActions(context, phase)

        then:
        actions.type == 'actions'
        actions.elements*.text.text == labels
        actions.elements*.url == urls

        where:
        phase                        | labels                                              | urls
        SeqeraMessagePhase.RUNNING   | ['🔗 View in Seqera Platform', '⏹ Cancel']          | [WATCH_URL, 'https://api.cloud.seqera.io/workflow/abc123/cancel']
        SeqeraMessagePhase.FAILED    | ['🔗 View in Seqera Platform']                      | [WATCH_URL]
        SeqeraMessagePhase.COMPLETED | ['🔗 View in Seqera Platform']                      | [WATCH_URL]
    }

    def 'cancel button omitted when disabled'() {
        given:
        def builder = createBuilder(actionButtons: [cancel: false])
        def context = watchContext()

        when:
        def actions = builder.createSeqeraPlatformActions(context, SeqeraMessagePhase.RUNNING)

        then:
        actions.elements*.text.text == ['🔗 View in Seqera Platform']
        actions.elements*.url == [WATCH_URL]
    }

    def 'actionButtons config defaults'() {
        when:
        def config = new SeqeraPlatformActionButtonsConfig([:])

        then:
        config.cancel
        config.resume
        config.relaunch
    }
}

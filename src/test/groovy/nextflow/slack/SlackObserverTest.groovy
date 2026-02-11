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
import nextflow.trace.TraceRecord
import spock.lang.Specification

/**
 * Tests for SlackObserver and SlackFactory
 *
 * Note: Tests that attempt to send messages use invalid webhook URLs and will throw exceptions.
 * This reflects the actual behavior of the code when webhooks fail.
 */
class SlackObserverTest extends Specification {

    def 'should create the observer instance'() {
        given:
        def factory = new SlackFactory()

        when:
        def result = factory.create(Mock(Session))

        then:
        result.size() == 1
        result.first() instanceof SlackObserver
        SlackFactory.observerInstance instanceof SlackObserver
    }

    def 'should initialize when configuration is valid'() {
        given:
        def session = Mock(Session)
        session.config >> [
            slack: [
                webhook: [
                    url: 'https://hooks.slack.com/services/TEST/TEST/TEST'
                ],
                onStart: [
                    enabled: false
                ]
            ]
        ]
        def metadata = Mock(WorkflowMetadata)
        metadata.scriptName >> 'test.nf'
        session.workflowMetadata >> metadata
        session.runName >> 'test-run'

        def observer = new SlackObserver()

        when:
        observer.onFlowCreate(session)

        then:
        observer.config != null
        observer.sender != null
        observer.messageBuilder != null
        noExceptionThrown()
    }

    def 'should handle missing configuration gracefully'() {
        given:
        def session = Mock(Session)
        session.config >> [:]
        session.params >> [:]

        def observer = new SlackObserver()

        when:
        observer.onFlowCreate(session)

        then:
        observer.config == null
        observer.sender == null
        observer.messageBuilder == null
        noExceptionThrown()
    }


    def 'should handle onFlowComplete when not configured'() {
        given:
        def observer = new SlackObserver()

        when:
        observer.onFlowComplete()

        then:
        noExceptionThrown()
    }

    def 'should handle onFlowError when not configured'() {
        given:
        def observer = new SlackObserver()
        def errorRecord = Mock(TraceRecord)

        when:
        observer.onFlowError(null, errorRecord)

        then:
        noExceptionThrown()
    }

    def 'should send notification on flow complete when configured'() {
        given:
        def mockSender = Mock(SlackSender)

        def session = Mock(Session)
        session.config >> [
            slack: [
                webhook: [
                    url: 'https://hooks.slack.com/services/TEST/TEST/TEST'
                ],
                onStart: [
                    enabled: false
                ],
                onComplete: [
                    enabled: true
                ]
            ]
        ]
        def metadata = Mock(WorkflowMetadata)
        metadata.scriptName >> 'test.nf'
        session.workflowMetadata >> metadata
        session.runName >> 'test-run'

        def observer = new SlackObserver()
        observer.onFlowCreate(session)
        observer.setSender(mockSender)  // Inject mocked sender

        when:
        observer.onFlowComplete()

        then:
        noExceptionThrown()
    }

    def 'should send notification on flow error when configured'() {
        given:
        def mockSender = Mock(SlackSender)

        def session = Mock(Session)
        session.config >> [
            slack: [
                webhook: [
                    url: 'https://hooks.slack.com/services/TEST/TEST/TEST'
                ],
                onStart: [
                    enabled: false
                ],
                onError: [
                    enabled: true
                ]
            ]
        ]
        def metadata = Mock(WorkflowMetadata)
        metadata.scriptName >> 'test.nf'
        metadata.errorMessage >> 'Test error'
        session.workflowMetadata >> metadata
        session.runName >> 'test-run'

        def errorRecord = Mock(TraceRecord)
        errorRecord.get('process') >> 'FAILED_PROCESS'

        def observer = new SlackObserver()
        observer.onFlowCreate(session)
        observer.setSender(mockSender)  // Inject mocked sender

        when:
        observer.onFlowError(null, errorRecord)

        then:
        noExceptionThrown()
    }

    def 'should use thread timestamp when useThreads enabled and BotSlackSender'() {
        given:
        def mockBotSender = Mock(BotSlackSender)
        mockBotSender.getThreadTs() >> '1234567890.123456'

        def session = Mock(Session)
        session.config >> [
            slack: [
                bot: [
                    token: 'xoxb-token',
                    channel: 'C123456',
                    useThreads: true
                ],
                onComplete: [
                    enabled: true
                ]
            ]
        ]
        def metadata = Mock(WorkflowMetadata)
        metadata.scriptName >> 'test.nf'
        session.workflowMetadata >> metadata
        session.runName >> 'test-run'

        def observer = new SlackObserver()
        observer.onFlowCreate(session)
        observer.setSender(mockBotSender)

        when:
        observer.onFlowComplete()

        then:
        // Verify that getThreadTs was called to retrieve the thread timestamp
        1 * mockBotSender.getThreadTs()
    }

    def 'should not use thread timestamp when useThreads disabled'() {
        given:
        def mockBotSender = Mock(BotSlackSender)

        def session = Mock(Session)
        session.config >> [
            slack: [
                bot: [
                    token: 'xoxb-token',
                    channel: 'C123456',
                    useThreads: false
                ],
                onComplete: [
                    enabled: true
                ]
            ]
        ]
        def metadata = Mock(WorkflowMetadata)
        metadata.scriptName >> 'test.nf'
        session.workflowMetadata >> metadata
        session.runName >> 'test-run'

        def observer = new SlackObserver()
        observer.onFlowCreate(session)
        observer.setSender(mockBotSender)

        when:
        observer.onFlowComplete()

        then:
        // Verify that getThreadTs was NOT called since threading is disabled
        0 * mockBotSender.getThreadTs()
    }

    def 'should not use thread timestamp with WebhookSlackSender'() {
        given:
        def mockWebhookSender = Mock(WebhookSlackSender)

        def session = Mock(Session)
        session.config >> [
            slack: [
                webhook: [
                    url: 'https://hooks.slack.com/services/TEST/TEST/TEST'
                ],
                onComplete: [
                    enabled: true
                ]
            ]
        ]
        def metadata = Mock(WorkflowMetadata)
        metadata.scriptName >> 'test.nf'
        session.workflowMetadata >> metadata
        session.runName >> 'test-run'

        def observer = new SlackObserver()
        observer.onFlowCreate(session)
        observer.setSender(mockWebhookSender)

        when:
        observer.onFlowComplete()

        then:
        // Threading is not supported with webhook sender
        noExceptionThrown()
    }

     def 'should use thread timestamp for error messages when enabled'() {
         given:
         def mockBotSender = Mock(BotSlackSender)
         mockBotSender.getThreadTs() >> '1234567890.123456'

         def session = Mock(Session)
         session.config >> [
             slack: [
                 bot: [
                     token: 'xoxb-token',
                     channel: 'C123456',
                     useThreads: true
                 ],
                 onError: [
                     enabled: true
                 ]
             ]
         ]
         def metadata = Mock(WorkflowMetadata)
         metadata.scriptName >> 'test.nf'
         metadata.errorMessage >> 'Test error'
         session.workflowMetadata >> metadata
         session.runName >> 'test-run'

         def errorRecord = Mock(TraceRecord)
         errorRecord.get('process') >> 'FAILED_PROCESS'

         def observer = new SlackObserver()
         observer.onFlowCreate(session)
         observer.setSender(mockBotSender)

         when:
         observer.onFlowError(null, errorRecord)

         then:
         // Verify that getThreadTs was called for error messages too
         1 * mockBotSender.getThreadTs()
     }

     def 'should upload configured files on flow complete'() {
         given:
         def mockSender = Mock(SlackSender)

         def session = Mock(Session)
         session.config >> [
             slack: [
                 webhook: [
                     url: 'https://hooks.slack.com/services/TEST/TEST/TEST'
                 ],
                 onStart: [
                     enabled: false
                 ],
                 onComplete: [
                     enabled: true,
                     files: ['results/report.html', 'results/plot.png']
                 ]
             ]
         ]
         def metadata = Mock(WorkflowMetadata)
         metadata.scriptName >> 'test.nf'
         session.workflowMetadata >> metadata
         session.runName >> 'test-run'

         def observer = new SlackObserver()
         observer.onFlowCreate(session)
         observer.setSender(mockSender)

         when:
         observer.onFlowComplete()

         then:
         1 * mockSender.sendMessage(_)
         1 * mockSender.uploadFile({ it.toString().endsWith('report.html') }, _)
         1 * mockSender.uploadFile({ it.toString().endsWith('plot.png') }, _)
     }

     def 'should upload configured files on flow error'() {
         given:
         def mockSender = Mock(SlackSender)

         def session = Mock(Session)
         session.config >> [
             slack: [
                 webhook: [
                     url: 'https://hooks.slack.com/services/TEST/TEST/TEST'
                 ],
                 onStart: [
                     enabled: false
                 ],
                 onError: [
                     enabled: true,
                     files: ['results/pipeline_report.html']
                 ]
             ]
         ]
         def metadata = Mock(WorkflowMetadata)
         metadata.scriptName >> 'test.nf'
         metadata.errorMessage >> 'Test error'
         session.workflowMetadata >> metadata
         session.runName >> 'test-run'

         def errorRecord = Mock(TraceRecord)
         errorRecord.get('process') >> 'FAILED_PROCESS'

         def observer = new SlackObserver()
         observer.onFlowCreate(session)
         observer.setSender(mockSender)

         when:
         observer.onFlowError(null, errorRecord)

         then:
         1 * mockSender.sendMessage(_)
         1 * mockSender.uploadFile({ it.toString().endsWith('pipeline_report.html') }, _)
     }
}

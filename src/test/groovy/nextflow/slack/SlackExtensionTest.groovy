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

import java.nio.file.Path
import groovy.json.JsonSlurper
import nextflow.Session
import nextflow.script.WorkflowMetadata
import spock.lang.Specification

/**
 * Tests for SlackExtension
 */
class SlackExtensionTest extends Specification {

    def 'should call buildSimpleMessage and sendMessage when observer is configured'() {
        given:
        def mockBotSender = Mock(BotSlackSender)
        def mockObserver = Mock(SlackObserver)
        def mockConfig = Mock(SlackConfig)
        def mockMessageBuilder = Mock(SlackMessageBuilder)

        mockObserver.sender >> mockBotSender
        mockObserver.messageBuilder >> mockMessageBuilder
        mockObserver.config >> mockConfig
        mockConfig.useThreads >> false

        SlackFactory.observerInstance = mockObserver

        def extension = new SlackExtension()

        when:
        extension.slackMessage('Test message')

        then:
        1 * mockMessageBuilder.buildSimpleMessage('Test message', _) >> '{"text":"Test message"}'
        1 * mockBotSender.sendMessage(_)
    }

    def 'should call buildRichMessage and sendMessage when observer is configured'() {
        given:
        def mockBotSender = Mock(BotSlackSender)
        def mockObserver = Mock(SlackObserver)
        def mockConfig = Mock(SlackConfig)
        def mockMessageBuilder = Mock(SlackMessageBuilder)
        def options = [message: 'Rich message', fields: [[title: 'Field', value: 'Value', short: true]]]

        mockObserver.sender >> mockBotSender
        mockObserver.messageBuilder >> mockMessageBuilder
        mockObserver.config >> mockConfig
        mockConfig.useThreads >> false

        SlackFactory.observerInstance = mockObserver

        def extension = new SlackExtension()

        when:
        extension.slackMessage(options)

        then:
        1 * mockMessageBuilder.buildRichMessage(options, _) >> '{"blocks":[]}'
        1 * mockBotSender.sendMessage(_)
    }

    def 'should handle missing observer gracefully'() {
        given:
        SlackFactory.observerInstance = null
        def extension = new SlackExtension()

        when:
        extension.slackMessage('Test message')

        then:
        noExceptionThrown()
    }

    def 'should handle missing sender gracefully'() {
        given:
        def mockObserver = Mock(SlackObserver)
        mockObserver.sender >> null
        mockObserver.messageBuilder >> null

        SlackFactory.observerInstance = mockObserver

        def extension = new SlackExtension()

        when:
        extension.slackMessage('Test message')

        then:
        noExceptionThrown()
    }

    def 'should handle missing message parameter in rich message'() {
        given:
        def mockBotSender = Mock(BotSlackSender)
        def mockObserver = Mock(SlackObserver)
        def mockConfig = Mock(SlackConfig)
        def mockMessageBuilder = Mock(SlackMessageBuilder)

        mockObserver.sender >> mockBotSender
        mockObserver.messageBuilder >> mockMessageBuilder
        mockObserver.config >> mockConfig

        SlackFactory.observerInstance = mockObserver

        def extension = new SlackExtension()

        when:
        extension.slackMessage([:])  // Empty map, missing 'message' key

        then:
        noExceptionThrown()
        0 * mockBotSender.sendMessage(_)
    }

    def 'should call uploadFile with string path'() {
        given:
        def mockBotSender = Mock(BotSlackSender)
        def mockObserver = Mock(SlackObserver)
        def mockConfig = Mock(SlackConfig)

        mockObserver.sender >> mockBotSender
        mockObserver.config >> mockConfig
        mockConfig.useThreads >> false

        SlackFactory.observerInstance = mockObserver

        def extension = new SlackExtension()
        def tempFile = File.createTempFile('test-upload', '.txt')
        tempFile.text = 'test content'

        when:
        extension.slackFileUpload(tempFile.absolutePath)

        then:
        1 * mockBotSender.uploadFile(_, _)

        cleanup:
        tempFile.delete()
    }

    def 'should call uploadFile with map options'() {
        given:
        def mockBotSender = Mock(BotSlackSender)
        def mockObserver = Mock(SlackObserver)
        def mockConfig = Mock(SlackConfig)

        mockObserver.sender >> mockBotSender
        mockObserver.config >> mockConfig
        mockConfig.useThreads >> false

        SlackFactory.observerInstance = mockObserver

        def extension = new SlackExtension()
        def tempFile = File.createTempFile('test-upload-map', '.png')
        tempFile.text = 'fake image data'

        when:
        extension.slackFileUpload([
            file: tempFile.absolutePath,
            title: 'My Plot',
            comment: 'Results attached',
            filename: 'custom.png'
        ])

        then:
        1 * mockBotSender.uploadFile(_, { Map opts ->
            opts.title == 'My Plot' &&
            opts.comment == 'Results attached' &&
            opts.filename == 'custom.png'
        })

        cleanup:
        tempFile.delete()
    }

    def 'should handle file upload with missing observer gracefully'() {
        given:
        SlackFactory.observerInstance = null
        def extension = new SlackExtension()

        when:
        extension.slackFileUpload('/some/file.txt')

        then:
        noExceptionThrown()
    }

    def 'should handle file upload with missing sender gracefully'() {
        given:
        def mockObserver = Mock(SlackObserver)
        mockObserver.sender >> null

        SlackFactory.observerInstance = mockObserver

        def extension = new SlackExtension()

        when:
        extension.slackFileUpload('/some/file.txt')

        then:
        noExceptionThrown()
    }

    def 'should handle file upload with missing file parameter in map'() {
        given:
        def mockBotSender = Mock(BotSlackSender)
        def mockObserver = Mock(SlackObserver)
        def mockConfig = Mock(SlackConfig)

        mockObserver.sender >> mockBotSender
        mockObserver.config >> mockConfig

        SlackFactory.observerInstance = mockObserver

        def extension = new SlackExtension()

        when:
        extension.slackFileUpload([:])  // Missing 'file' key

        then:
        noExceptionThrown()
        0 * mockBotSender.uploadFile(_, _)
    }

    def 'should pass threadTs from bot sender when threading enabled'() {
        given:
        Map capturedOptions = null
        java.nio.file.Path capturedPath = null
        def mockBotSender = new BotSlackSender('xoxb-test', 'C123') {
            @Override
            String getThreadTs() { return '1234567890.123456' }
            @Override
            void sendMessage(String message) { /* no-op */ }
            @Override
            void uploadFile(java.nio.file.Path filePath, Map options) {
                capturedPath = filePath
                capturedOptions = options
            }
        }

        // Create a real SlackConfig with useThreads=true (Spock stubs can't override final field access)
        def mockSession = Mock(Session)
        mockSession.config >> [slack: [bot: [token: 'xoxb-test', channel: 'C123', useThreads: true]]]
        def config = SlackConfig.from(mockSession)

        def mockObserver = Stub(SlackObserver) {
            getSender() >> mockBotSender
            getConfig() >> config
        }

        SlackFactory.observerInstance = mockObserver

        def extension = new SlackExtension()
        def tempFile = File.createTempFile('test-thread', '.txt')
        tempFile.text = 'test content'

        when:
        extension.slackFileUpload(tempFile.absolutePath)

        then:
        capturedPath != null
        capturedOptions != null
        capturedOptions.threadTs == '1234567890.123456'

        cleanup:
        tempFile.delete()
    }
}

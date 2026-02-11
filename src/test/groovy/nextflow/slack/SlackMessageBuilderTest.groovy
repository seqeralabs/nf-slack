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

import groovy.json.JsonSlurper
import nextflow.Session
import nextflow.script.WorkflowMetadata
import nextflow.util.Duration
import spock.lang.Specification

/**
 * Tests for SlackMessageBuilder
 */
class SlackMessageBuilderTest extends Specification {

    def config
    def session
    def messageBuilder

    def setup() {
        config = new SlackConfig([
            enabled: true,
            webhook: 'https://hooks.slack.com/services/TEST/TEST/TEST',
            onStart: [
                enabled: true,
                includeCommandLine: true
            ],
            onComplete: [
                enabled: true,
                includeCommandLine: true,
                includeResourceUsage: true
            ],
            onError: [
                enabled: true,
                includeCommandLine: true
            ]
        ])

        session = Mock(Session)
        def metadata = Mock(WorkflowMetadata)
        metadata.scriptName >> 'test-workflow.nf'
        session.workflowMetadata >> metadata
        session.runName >> 'test-run'
        session.uniqueId >> UUID.fromString('00000000-0000-0000-0000-000000000000')
        session.commandLine >> 'nextflow run test.nf'
        session.workDir >> java.nio.file.Paths.get('/work/dir')

        messageBuilder = new SlackMessageBuilder(config, session)
    }

    def 'should build workflow start message'() {
        when:
        def message = messageBuilder.buildWorkflowStartMessage()
        def json = new JsonSlurper().parseText(message)

        then:
        json.blocks.size() >= 2
        def headerBlock = json.blocks.find { it.type == 'section' && it.text?.text?.contains('Pipeline started') }
        headerBlock != null

        // Check fields
        def fieldsBlock = json.blocks.find { it.type == 'section' && it.fields }
        def fields = fieldsBlock.fields
        fields.find { it.text.contains('Run Name') && it.text.contains('test-run') }

        // Command line should be in a separate section or fields
        def cmdLineBlock = json.blocks.find { it.type == 'section' && it.text?.text?.contains('Command Line') }
        cmdLineBlock.text.text.contains('nextflow run test.nf')
    }

    def 'should build workflow complete message'() {
        given:
        def metadata = Mock(WorkflowMetadata)
        metadata.scriptName >> 'test-workflow.nf'
        metadata.duration >> Duration.of('1h 30m')
        // Don't mock stats to avoid complexity - test basic message structure
        metadata.stats >> null
        session.workflowMetadata >> metadata

        when:
        def message = messageBuilder.buildWorkflowCompleteMessage()
        def json = new JsonSlurper().parseText(message)

        then:
        json.blocks.size() >= 2
        def headerBlock = json.blocks.find { it.type == 'section' && it.text?.text?.contains('Pipeline completed successfully') }
        headerBlock != null

        // Check basic fields
        def fieldsBlock = json.blocks.find { it.type == 'section' && it.fields }
        def fields = fieldsBlock.fields
        fields.find { it.text.contains('Status') && it.text.contains('Success') }
        fields.find { it.text.contains('Run Name') && it.text.contains('test-run') }
        fields.find { it.text.contains('Duration') }
    }

    def 'should build workflow error message'() {
        given:
        // Create a new session for this test with error metadata
        def errorSession = Mock(Session)
        def metadata = Mock(WorkflowMetadata)
        metadata.scriptName >> 'test-workflow.nf'
        metadata.duration >> Duration.of('30m')
        metadata.errorMessage >> 'Process failed with exit code 1'
        errorSession.workflowMetadata >> metadata
        errorSession.runName >> 'test-run'
        errorSession.commandLine >> 'nextflow run test.nf'

        def builder = new SlackMessageBuilder(config, errorSession)

        def errorRecord = Mock(nextflow.trace.TraceRecord)
        errorRecord.get('process') >> 'FAILED_PROCESS'

        when:
        def message = builder.buildWorkflowErrorMessage(errorRecord)
        def json = new JsonSlurper().parseText(message)

        then:
        json.blocks.size() >= 2
        def headerBlock = json.blocks.find { it.type == 'section' && it.text?.text?.contains('Pipeline failed') }
        headerBlock != null

        // Check fields
        def fieldsBlock = json.blocks.find { it.type == 'section' && it.fields }
        def fields = fieldsBlock.fields
        fields.find { it.text.contains('Status') && it.text.contains('Failed') }
        fields.find { it.text.contains('Failed Process') && it.text.contains('FAILED_PROCESS') }

        // Check error message block
        def errorBlock = json.blocks.find { it.type == 'section' && it.text?.text?.contains('Error Message') }
        errorBlock.text.text.contains('Process failed')
    }

    def 'should build simple text message'() {
        when:
        def message = messageBuilder.buildSimpleMessage('Hello from workflow!')
        def json = new JsonSlurper().parseText(message)

        then:
        json.blocks[0].text.text == 'Hello from workflow!'
    }

    def 'should build rich message with custom fields'() {
        given:
        def options = [
            message: 'Analysis complete',
            fields: [
                [title: 'Sample', value: 'sample123', short: true],
                [title: 'Status', value: 'Success', short: true]
            ]
        ]

        when:
        def message = messageBuilder.buildRichMessage(options)
        def json = new JsonSlurper().parseText(message)

        then:
        json.blocks.size() >= 2
        json.blocks[0].text.text == 'Analysis complete'

        def fieldsBlock = json.blocks.find { it.type == 'section' && it.fields }
        fieldsBlock.fields.size() == 2
        fieldsBlock.fields[0].text.contains('Sample')
        fieldsBlock.fields[0].text.contains('sample123')
    }

    def 'should throw exception for rich message without message text'() {
        given:
        def options = [
            color: '#2EB887'
        ]

        when:
        messageBuilder.buildRichMessage(options)

        then:
        thrown(IllegalArgumentException)
    }

    def 'should truncate long error messages'() {
        given:
        def errorSession = Mock(Session)
        def metadata = Mock(WorkflowMetadata)
        metadata.scriptName >> 'test-workflow.nf'
        metadata.duration >> Duration.of('1m')
        metadata.errorMessage >> ('x' * 2500) // Long error message
        errorSession.workflowMetadata >> metadata
        errorSession.runName >> 'test-run'

        def builder = new SlackMessageBuilder(config, errorSession)

        when:
        def message = builder.buildWorkflowErrorMessage(null)
        def json = new JsonSlurper().parseText(message)

        then:
        def errorBlock = json.blocks.find { it.type == 'section' && it.text?.text?.contains('Error Message') }
        errorBlock.text.text.length() < 2500
        errorBlock.text.text.contains('...')
    }

    def 'should not include command line when disabled'() {
        given:
        config = new SlackConfig([
            enabled: true,
            webhook: 'https://hooks.slack.com/services/TEST/TEST/TEST',
            onStart: [
                enabled: true,
                includeCommandLine: false
            ]
        ])
        messageBuilder = new SlackMessageBuilder(config, session)

        when:
        def message = messageBuilder.buildWorkflowStartMessage()
        def json = new JsonSlurper().parseText(message)

        then:
        !json.blocks.any { it.type == 'section' && it.text?.text?.contains('Command Line') }
    }

    def 'should use custom start message template'() {
        given:
        config = new SlackConfig([
            enabled: true,
            webhook: 'https://hooks.slack.com/services/TEST/TEST/TEST',
            onStart: [
                enabled: true,
                message: '🎬 *Custom workflow is starting!*'
            ]
        ])
        messageBuilder = new SlackMessageBuilder(config, session)

        when:
        def message = messageBuilder.buildWorkflowStartMessage()
        def json = new JsonSlurper().parseText(message)

        then:
        json.blocks[0].text.text == '🎬 *Custom workflow is starting!*'
    }

    def 'should use custom complete message template'() {
        given:
        config = new SlackConfig([
            enabled: true,
            webhook: 'https://hooks.slack.com/services/TEST/TEST/TEST',
            onComplete: [
                enabled: true,
                message: '🎉 *Analysis finished successfully!*'
            ]
        ])
        def metadata = Mock(WorkflowMetadata)
        metadata.scriptName >> 'test-workflow.nf'
        metadata.duration >> Duration.of('1h')
        session.workflowMetadata >> metadata
        messageBuilder = new SlackMessageBuilder(config, session)

        when:
        def message = messageBuilder.buildWorkflowCompleteMessage()
        def json = new JsonSlurper().parseText(message)

        then:
        json.blocks[0].text.text == '🎉 *Analysis finished successfully!*'
    }

    def 'should use custom error message template'() {
        given:
        config = new SlackConfig([
            enabled: true,
            webhook: 'https://hooks.slack.com/services/TEST/TEST/TEST',
            onError: [
                enabled: true,
                message: '💥 *Workflow encountered an error!*'
            ]
        ])
        def errorSession = Mock(Session)
        def metadata = Mock(WorkflowMetadata)
        metadata.scriptName >> 'test-workflow.nf'
        metadata.duration >> Duration.of('30m')
        metadata.errorMessage >> 'Process failed'
        errorSession.workflowMetadata >> metadata
        errorSession.runName >> 'test-run'
        messageBuilder = new SlackMessageBuilder(config, errorSession)

        when:
        def message = messageBuilder.buildWorkflowErrorMessage(null)
        def json = new JsonSlurper().parseText(message)

        then:
        json.blocks[0].text.text == '💥 *Workflow encountered an error!*'
    }

    def 'should use default messages when custom templates not provided'() {
        given:
        config = new SlackConfig([
            enabled: true,
            webhook: 'https://hooks.slack.com/services/TEST/TEST/TEST'
        ])
        messageBuilder = new SlackMessageBuilder(config, session)

        when:
        def startMessage = messageBuilder.buildWorkflowStartMessage()
        def startJson = new JsonSlurper().parseText(startMessage)

        then:
        startJson.blocks[0].text.text == '🚀 *Pipeline started*'
    }

    def 'should use map-based custom start message with custom fields'() {
        given:
        config = new SlackConfig([
            enabled: true,
            webhook: 'https://hooks.slack.com/services/TEST/TEST/TEST',
            onStart: [
                enabled: true,
                message: [
                    text: '🎬 *Custom pipeline starting*',
                    includeFields: ['runName', 'status'],
                    customFields: [
                        [title: 'Environment', value: 'Production', short: true]
                    ]
                ]
            ]
        ])
        messageBuilder = new SlackMessageBuilder(config, session)

        when:
        def message = messageBuilder.buildWorkflowStartMessage()
        def json = new JsonSlurper().parseText(message)

        then:
        json.blocks[0].text.text == '🎬 *Custom pipeline starting*'

        def fieldsBlock = json.blocks.find { it.type == 'section' && it.fields }
        def fields = fieldsBlock.fields
        fields.find { it.text.contains('Run Name') && it.text.contains('test-run') }
        fields.find { it.text.contains('Status') && it.text.contains('Running') }
        fields.find { it.text.contains('Environment') && it.text.contains('Production') }
    }

    def 'should use map-based custom complete message with selective fields'() {
        given:
        config = new SlackConfig([
            enabled: true,
            webhook: 'https://hooks.slack.com/services/TEST/TEST/TEST',
            onComplete: [
                enabled: true,
                message: [
                    text: '🎉 *Analysis finished!*',
                    includeFields: ['runName', 'duration', 'status'],
                    customFields: [
                        [title: 'Output Location', value: 's3://bucket/results', short: false]
                    ]
                ]
            ]
        ])
        def metadata = Mock(WorkflowMetadata)
        metadata.scriptName >> 'test-workflow.nf'
        metadata.duration >> Duration.of('2h')
        session.workflowMetadata >> metadata
        messageBuilder = new SlackMessageBuilder(config, session)

        when:
        def message = messageBuilder.buildWorkflowCompleteMessage()
        def json = new JsonSlurper().parseText(message)

        then:
        json.blocks[0].text.text == '🎉 *Analysis finished!*'

        def fieldsBlock = json.blocks.find { it.type == 'section' && it.fields }
        def fields = fieldsBlock.fields
        fields.find { it.text.contains('Run Name') }
        fields.find { it.text.contains('Duration') }
        fields.find { it.text.contains('Status') && it.text.contains('Success') }
        fields.find { it.text.contains('Output Location') && it.text.contains('s3://bucket/results') }
    }

    def 'should use map-based custom error message with error fields'() {
        given:
        config = new SlackConfig([
            enabled: true,
            webhook: 'https://hooks.slack.com/services/TEST/TEST/TEST',
            onError: [
                enabled: true,
                message: [
                    text: '💥 *Pipeline crashed!*',
                    includeFields: ['runName', 'duration', 'errorMessage', 'failedProcess'],
                    customFields: [
                        [title: 'Support', value: 'contact@example.com', short: true]
                    ]
                ]
            ]
        ])
        def errorSession = Mock(Session)
        def metadata = Mock(WorkflowMetadata)
        metadata.scriptName >> 'test-workflow.nf'
        metadata.duration >> Duration.of('30m')
        metadata.errorMessage >> 'Out of memory error'
        errorSession.workflowMetadata >> metadata
        errorSession.runName >> 'test-run'
        messageBuilder = new SlackMessageBuilder(config, errorSession)

        def errorRecord = Mock(nextflow.trace.TraceRecord)
        errorRecord.get('process') >> 'FAILED_PROCESS'

        when:
        def message = messageBuilder.buildWorkflowErrorMessage(errorRecord)
        def json = new JsonSlurper().parseText(message)

        then:
        json.blocks[0].text.text == '💥 *Pipeline crashed!*'

        def fieldsBlock = json.blocks.find { it.type == 'section' && it.fields }
        def fields = fieldsBlock.fields
        fields.find { it.text.contains('Run Name') }
        fields.find { it.text.contains('Duration') }
        fields.find { it.text.contains('Failed Process') && it.text.contains('FAILED_PROCESS') }
        fields.find { it.text.contains('Support') && it.text.contains('contact@example.com') }

        // Error message is in a separate block
        def errorBlock = json.blocks.find { it.type == 'section' && it.text?.text?.contains('Error Message') }
        errorBlock.text.text.contains('Out of memory')
    }

    def 'should use default values when map config has minimal settings'() {
        given:
        config = new SlackConfig([
            enabled: true,
            webhook: 'https://hooks.slack.com/services/TEST/TEST/TEST',
            onStart: [
                enabled: true,
                message: [
                    text: 'Starting...'
                ]
            ]
        ])
        messageBuilder = new SlackMessageBuilder(config, session)

        when:
        def message = messageBuilder.buildWorkflowStartMessage()
        def json = new JsonSlurper().parseText(message)

        then:
        json.blocks[0].text.text == 'Starting...'
        !json.blocks.any { it.type == 'section' && it.fields } // no fields since includeFields not specified
    }

    def 'should include thread_ts in workflow start message when provided'() {
        when:
        def message = messageBuilder.buildWorkflowStartMessage('1234567890.123456')
        def json = new JsonSlurper().parseText(message)

        then:
        json.thread_ts == '1234567890.123456'
    }

    def 'should omit thread_ts from workflow start message when null'() {
        when:
        def message = messageBuilder.buildWorkflowStartMessage(null)
        def json = new JsonSlurper().parseText(message)

        then:
        !json.containsKey('thread_ts')
    }

    def 'should include thread_ts in workflow complete message when provided'() {
        given:
        def metadata = Mock(WorkflowMetadata)
        metadata.scriptName >> 'test-workflow.nf'
        metadata.duration >> Duration.of('1h')
        session.workflowMetadata >> metadata

        when:
        def message = messageBuilder.buildWorkflowCompleteMessage('1234567890.123456')
        def json = new JsonSlurper().parseText(message)

        then:
        json.thread_ts == '1234567890.123456'
    }

    def 'should omit thread_ts from workflow complete message when null'() {
        given:
        def metadata = Mock(WorkflowMetadata)
        metadata.scriptName >> 'test-workflow.nf'
        metadata.duration >> Duration.of('1h')
        session.workflowMetadata >> metadata

        when:
        def message = messageBuilder.buildWorkflowCompleteMessage(null)
        def json = new JsonSlurper().parseText(message)

        then:
        !json.containsKey('thread_ts')
    }

    def 'should include thread_ts in workflow error message when provided'() {
        given:
        def errorSession = Mock(Session)
        def metadata = Mock(WorkflowMetadata)
        metadata.scriptName >> 'test-workflow.nf'
        metadata.duration >> Duration.of('30m')
        metadata.errorMessage >> 'Test error'
        errorSession.workflowMetadata >> metadata
        errorSession.runName >> 'test-run'

        def builder = new SlackMessageBuilder(config, errorSession)

        when:
        def message = builder.buildWorkflowErrorMessage(null, '1234567890.123456')
        def json = new JsonSlurper().parseText(message)

        then:
        json.thread_ts == '1234567890.123456'
    }

    def 'should omit thread_ts from workflow error message when null'() {
        given:
        def errorSession = Mock(Session)
        def metadata = Mock(WorkflowMetadata)
        metadata.scriptName >> 'test-workflow.nf'
        metadata.duration >> Duration.of('30m')
        metadata.errorMessage >> 'Test error'
        errorSession.workflowMetadata >> metadata
        errorSession.runName >> 'test-run'

        def builder = new SlackMessageBuilder(config, errorSession)

        when:
        def message = builder.buildWorkflowErrorMessage(null, null)
        def json = new JsonSlurper().parseText(message)

        then:
        !json.containsKey('thread_ts')
    }

    def 'should include thread_ts in simple message when provided'() {
        when:
        def message = messageBuilder.buildSimpleMessage('Test message', '1234567890.123456')
        def json = new JsonSlurper().parseText(message)

        then:
        json.thread_ts == '1234567890.123456'
    }

    def 'should omit thread_ts from simple message when null'() {
        when:
        def message = messageBuilder.buildSimpleMessage('Test message', null)
        def json = new JsonSlurper().parseText(message)

        then:
        !json.containsKey('thread_ts')
    }

    def 'should include thread_ts in rich message when provided'() {
        given:
        def options = [
            message: 'Rich message',
            fields: [
                [title: 'Field', value: 'Value', short: true]
            ]
        ]

        when:
        def message = messageBuilder.buildRichMessage(options, '1234567890.123456')
        def json = new JsonSlurper().parseText(message)

        then:
        json.thread_ts == '1234567890.123456'
    }

    def 'should omit thread_ts from rich message when null'() {
        given:
        def options = [
            message: 'Rich message',
            fields: [
                [title: 'Field', value: 'Value', short: true]
            ]
        ]

        when:
        def message = messageBuilder.buildRichMessage(options, null)
        def json = new JsonSlurper().parseText(message)

        then:
        !json.containsKey('thread_ts')
    }

    def 'should build progress update message'() {
        when:
        def message = messageBuilder.buildProgressUpdateMessage(10, 3, 1, 300000L, '1234567890.123456')
        def json = new JsonSlurper().parseText(message)

        then:
        json.blocks != null
        json.blocks.size() > 0
        json.thread_ts == '1234567890.123456'
        json.blocks[0].type == 'section'
    }

    def 'should format elapsed time in progress message'() {
        when:
        def message = messageBuilder.buildProgressUpdateMessage(5, 2, 0, 3661000L, null)
        def json = new JsonSlurper().parseText(message)

        then:
        json.blocks != null
        def text = json.blocks.collect { it.toString() }.join(' ')
        text.contains('5')
    }
}

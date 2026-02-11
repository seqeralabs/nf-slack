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

import spock.lang.Specification

/**
 * Tests for WebhookSlackSender
 *
 * These tests verify basic sender functionality.
 * Network calls are tested in integration tests.
 */
class SlackClientTest extends Specification {

    def 'should create sender with webhook URL'() {
        when:
        def sender = new WebhookSlackSender('https://hooks.slack.com/services/TEST/TEST/TEST')

        then:
        sender != null
    }

    def 'should handle null webhook URL gracefully'() {
        when:
        def sender = new WebhookSlackSender(null)
        sender.sendMessage('{"text":"test"}')

        then:
        noExceptionThrown()
    }

    def 'should handle invalid JSON gracefully'() {
        when:
        def sender = new WebhookSlackSender('https://hooks.slack.com/services/TEST/TEST/TEST')
        sender.sendMessage('not valid json')

        then:
        noExceptionThrown()
    }

    def 'should handle file upload gracefully with warning'() {
        given:
        def sender = new WebhookSlackSender('https://hooks.slack.com/services/TEST/TEST/TEST')
        def tempFile = File.createTempFile('test', '.txt')
        tempFile.text = 'Hello World'

        when:
        sender.uploadFile(tempFile.toPath(), [:])

        then:
        noExceptionThrown()

        cleanup:
        tempFile.delete()
    }
}

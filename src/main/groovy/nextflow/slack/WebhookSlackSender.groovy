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
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Slack sender implementation using Incoming Webhooks.
 *
 * Features:
 * - Synchronous message sending
 * - Graceful error handling (never fails workflow)
 *
 * @author Adam Talbot <adam.talbot@seqera.io>
 */
@Slf4j
@CompileStatic
class WebhookSlackSender implements SlackSender {

    private final String webhookUrl
    private final Set<String> loggedErrors = Collections.synchronizedSet(new HashSet<String>())

    /**
     * Create a new WebhookSlackSender with the given webhook URL
     */
    WebhookSlackSender(String webhookUrl) {
        this.webhookUrl = webhookUrl
    }

    /**
     * Send a message to Slack webhook
     *
     * @param message JSON message payload
     */
    @Override
    void sendMessage(String message) {
        try {
            def url = new URL(webhookUrl)
            def connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = 'POST'
            connection.doOutput = true
            connection.setRequestProperty('Content-type', 'application/json')

            // Send message
            connection.outputStream.write(message.bytes)
            connection.outputStream.close()

            // Check response
            def responseCode = connection.responseCode
            if (responseCode != 200) {
                def errorBody = connection.errorStream?.text ?: ""
                def errorMsg = "Slack webhook HTTP ${responseCode}: ${errorBody}".toString()
                if (loggedErrors.add(errorMsg)) {
                    log.error errorMsg
                }
            }

            connection.disconnect()
        } catch (Exception e) {
            def errorMsg = "Slack plugin: Error sending message: ${e.message}".toString()
            if (loggedErrors.add(errorMsg)) {
                log.error errorMsg
            }
        }
    }

    /**
     * File upload is not supported via webhooks.
     * Logs a warning and returns without uploading.
     *
     * @param filePath Path to the file
     * @param options Upload options (ignored)
     */
    @Override
    boolean validate() {
        log.info "Slack plugin: Webhook connections have limited validation - token and channel checks are not available"
        return true
    }

    @Override
    void uploadFile(Path filePath, Map options) {
        log.warn "Slack plugin: File upload is not supported with webhooks. Please configure a bot token to upload files."
    }
}

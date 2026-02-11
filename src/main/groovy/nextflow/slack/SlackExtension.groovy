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
import nextflow.Session
import nextflow.file.FileHelper
import nextflow.plugin.extension.Function
import nextflow.plugin.extension.PluginExtensionPoint

/**
 * Implements custom Slack functions which can be called from
 * Nextflow scripts.
 *
 * Available functions:
 * - slackMessage(String): Send a simple text message
 * - slackMessage(Map): Send a rich formatted message
 * - slackFileUpload(String): Upload a file to Slack
 * - slackFileUpload(Map): Upload a file with options (title, comment, etc.)
 *
 * @author Adam Talbot <adam.talbot@seqera.io>
 */
@Slf4j
@CompileStatic
class SlackExtension extends PluginExtensionPoint {

    @Override
    protected void init(Session session) {
        // No initialization needed
    }

    /**
     * Send a simple text message to Slack
     *
     * Example:
     * slackMessage("Analysis starting for sample ${sample_id}")
     *
     * @param text The message text to send
     */
    @Function
    void slackMessage(String text) {
        try {
            // Get the observer instance from factory
            def observer = SlackFactory.observerInstance

            if (!observer) {
                log.debug "Slack plugin: Observer not initialized, skipping message"
                return
            }

            if (!observer.sender || !observer.messageBuilder) {
                log.debug "Slack plugin: Not configured, skipping message"
                return
            }

            // Get thread timestamp if threading is enabled
            def threadTs = null
            if (observer.config?.useThreads && observer.sender instanceof BotSlackSender) {
                threadTs = (observer.sender as BotSlackSender).getThreadTs()
            }

            // Build and send simple message
            def message = observer.messageBuilder.buildSimpleMessage(text, threadTs)
            observer.sender.sendMessage(message)

            log.debug "Slack plugin: Sent custom text message"

        } catch (Exception e) {
            log.error "Slack plugin: Error sending message: ${e.message}", e
            // Don't propagate exception - never fail the workflow
        }
    }

    /**
     * Send a rich formatted message to Slack
     *
     * Example:
     * slackMessage([
     *     message: "Analysis complete",
     *     fields: [
     *         [title: "Sample", value: sample_id, short: true],
     *         [title: "Status", value: "Success", short: true]
     *     ]
     * ])
     *
     * @param options Map with keys:
     *   - message (required): The main message text
     *   - fields (optional): List of field maps with title, value, and short (boolean)
     */
    @Function
    void slackMessage(Map options) {
        try {
            // Validate required parameters
            if (!options.message) {
                log.error "Slack plugin: 'message' parameter is required for rich messages"
                return
            }

            // Get the observer instance from factory
            def observer = SlackFactory.observerInstance

            if (!observer) {
                log.debug "Slack plugin: Observer not initialized, skipping message"
                return
            }

            if (!observer.sender || !observer.messageBuilder) {
                log.debug "Slack plugin: Not configured, skipping message"
                return
            }

            // Get thread timestamp if threading is enabled
            def threadTs = null
            if (observer.config?.useThreads && observer.sender instanceof BotSlackSender) {
                threadTs = (observer.sender as BotSlackSender).getThreadTs()
            }

            // Build and send rich message
            def message = observer.messageBuilder.buildRichMessage(options, threadTs)
            observer.sender.sendMessage(message)

            log.debug "Slack plugin: Sent custom rich message"

        } catch (Exception e) {
            log.error "Slack plugin: Error sending rich message: ${e.message}", e
            // Don't propagate exception - never fail the workflow
        }
    }

    /**
     * Upload a file to Slack
     *
     * Example:
     * slackFileUpload("results/report.html")
     *
     * @param filePath Path to the file to upload (String or Path)
     */
    @Function
    void slackFileUpload(String filePath) {
        slackFileUpload([file: filePath])
    }

    /**
     * Upload a file to Slack with options
     *
     * Example:
     * slackFileUpload([
     *     file: "results/report.html",
     *     title: "Analysis Report",
     *     comment: "Here is the final report for sample ${sample_id}",
     *     filename: "report.html"
     * ])
     *
     * @param options Map with keys:
     *   - file (required): Path to the file to upload (String or Path)
     *   - title (optional): Title of the file in Slack
     *   - comment (optional): Initial comment to add with the file
     *   - filename (optional): Override the filename shown in Slack
     */
    @Function
    void slackFileUpload(Map options) {
        try {
            // Validate required parameters
            if (!options.file) {
                log.error "Slack plugin: 'file' parameter is required for file upload"
                return
            }

            // Get the observer instance from factory
            def observer = SlackFactory.observerInstance

            if (!observer) {
                log.debug "Slack plugin: Observer not initialized, skipping file upload"
                return
            }

            if (!observer.sender) {
                log.debug "Slack plugin: Not configured, skipping file upload"
                return
            }

            // Resolve file path
            def file = options.file
            Path path
            if (file instanceof Path) {
                path = file
            } else {
                path = FileHelper.asPath(file.toString())
            }

            // Build options map for sender
            def uploadOptions = [:] as Map<String, Object>
            if (options.title) uploadOptions.title = options.title as String
            if (options.comment) uploadOptions.comment = options.comment as String
            if (options.filename) uploadOptions.filename = options.filename as String

            // Add thread timestamp if threading is enabled
            if (observer.config?.useThreads && observer.sender instanceof BotSlackSender) {
                def threadTs = (observer.sender as BotSlackSender).getThreadTs()
                if (threadTs) uploadOptions.threadTs = threadTs
            }

            // Upload the file
            observer.sender.uploadFile(path, uploadOptions)

            log.debug "Slack plugin: Uploaded file ${path.fileName}"

        } catch (Exception e) {
            log.error "Slack plugin: Error uploading file: ${e.message}", e
            // Don't propagate exception - never fail the workflow
        }
    }
}

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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.processor.TaskHandler
import nextflow.trace.TraceObserver
import nextflow.trace.TraceRecord
import java.nio.file.Path
import nextflow.file.FileHelper

/**
 * Implements a trace observer that sends Slack notifications
 * for workflow lifecycle events.
 *
 * Features:
 * - Automatic notifications for workflow start, complete, and error
 * - Configurable via nextflow.config
 * - Graceful error handling that never fails the workflow
 *
 * @author Adam Talbot <adam.talbot@seqera.io>
 */
@Slf4j
@CompileStatic
class SlackObserver implements TraceObserver {

    private Session session
    private SlackConfig config
    private SlackSender sender
    private SlackMessageBuilder messageBuilder

    /**
     * Called when the workflow is created
     */
    @Override
    void onFlowCreate(Session session) {
        this.session = session

        // Parse configuration if not already set (supports test injection)
        if (this.config == null) {
            this.config = SlackConfig.from(session)
        }

        // If not configured or disabled, skip initialization
        if (!config?.isConfigured()) {
            log.debug "Slack plugin: Not configured or disabled, notifications will not be sent"
            return
        }

        // Initialize sender and message builder if not already set (supports test injection)
        if (this.sender == null) {
            this.sender = config.createSender()
        }
        if (this.messageBuilder == null) {
            this.messageBuilder = new SlackMessageBuilder(config, session)
        }

        log.debug "Slack plugin: Initialized successfully"

        // Validate Slack connection if enabled
        if (config.validateOnStartup) {
            boolean valid = sender.validate()
            if (!valid) {
                throw new IllegalStateException("Slack plugin: Connection validation failed. Set slack.validateOnStartup = false to skip validation.")
            }
        }

        // Send workflow started notification if enabled
        if (config.onStart.enabled) {
            def message = messageBuilder.buildWorkflowStartMessage()
            sender.sendMessage(message)
            log.debug "Slack plugin: Sent workflow start notification"
        }
    }

    /**
     * Called when the workflow completes successfully
     */
    @Override
    void onFlowComplete() {
        if (!isConfigured()) return

        if (config.onComplete.enabled) {
            // Get thread timestamp if threading is enabled and we're using bot sender
            def threadTs = getThreadTsIfEnabled()
            def message = messageBuilder.buildWorkflowCompleteMessage(threadTs)
            sender.sendMessage(message)
            log.debug "Slack plugin: Sent workflow complete notification"

            // Upload configured files
            uploadConfiguredFiles(config.onComplete.files, threadTs)
        }
    }

    /**
     * Called when the workflow fails
     */
    @Override
    void onFlowError(TaskHandler handler, TraceRecord trace) {
        if (!isConfigured()) return

        if (config.onError.enabled) {
            // Get thread timestamp if threading is enabled and we're using bot sender
            def threadTs = getThreadTsIfEnabled()
            def message = messageBuilder.buildWorkflowErrorMessage(trace, threadTs)
            sender.sendMessage(message)
            log.debug "Slack plugin: Sent workflow error notification"

            // Upload configured files
            uploadConfiguredFiles(config.onError.files, threadTs)
        }
    }

    /**
     * Upload files configured in the notification config
     */
    private void uploadConfiguredFiles(List<String> files, String threadTs) {
        if (!files) return

        for (String filePath : files) {
            try {
                Path path = FileHelper.asPath(filePath)
                Map<String, String> options = [:]
                if (threadTs) {
                    options.put('threadTs', threadTs)
                }
                sender.uploadFile(path, options)
                log.debug "Slack plugin: Uploaded file ${filePath}"
            }
            catch (Exception e) {
                log.warn "Slack plugin: Failed to upload file ${filePath}: ${e.message}"
            }
        }
    }

    /**
     * Get thread timestamp if threading is enabled
     */
    private String getThreadTsIfEnabled() {
        if (config.useThreads && sender instanceof BotSlackSender) {
            return (sender as BotSlackSender).getThreadTs()
        }
        return null
    }

    /**
     * Check if the observer is properly configured
     */
    private boolean isConfigured() {
        return config != null && sender != null && messageBuilder != null
    }

    /**
     * Get the Slack sender for use by extension functions
     */
    SlackSender getSender() {
        return sender
    }

    /**
     * Set the Slack sender (package-private for testing)
     */
    void setSender(SlackSender sender) {
        this.sender = sender
    }

    /**
     * Get the message builder for use by extension functions
     */
    SlackMessageBuilder getMessageBuilder() {
        return messageBuilder
    }

    /**
     * Get the configuration
     */
    SlackConfig getConfig() {
        return config
    }

    void setConfig(SlackConfig config) {
        this.config = config
    }

    void setMessageBuilder(SlackMessageBuilder messageBuilder) {
        this.messageBuilder = messageBuilder
    }
}

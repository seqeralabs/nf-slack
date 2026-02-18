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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
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

    // Progress tracking
    private final AtomicInteger submittedTasks = new AtomicInteger(0)
    private final AtomicInteger completedTasks = new AtomicInteger(0)
    private final AtomicInteger cachedTasks = new AtomicInteger(0)
    private final AtomicInteger failedTasks = new AtomicInteger(0)
    private String startMessageTs
    private long startTimeMillis
    private boolean progressEnabled
    private long intervalMs
    private final AtomicLong lastUpdateTime = new AtomicLong(0)
    private Timer pendingUpdateTimer
    private boolean startReactionAdded = false

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
                log.warn "Slack plugin: Connection validation failed - Slack notifications may not work. Set slack.validateOnStartup = false to skip validation."
            }
        }

        // Send workflow started notification if enabled
        if (config.onStart.enabled) {
            def message = messageBuilder.buildWorkflowStartMessage()
            sender.sendMessage(message)
            log.debug "Slack plugin: Sent workflow start notification"
        }

        // Set up progress updates if enabled and using bot sender
        if (config.onProgress?.enabled && sender instanceof BotSlackSender) {
            startMessageTs = (sender as BotSlackSender).getThreadTs()
            startTimeMillis = System.currentTimeMillis()
            intervalMs = config.onProgress.getIntervalMillis()
            progressEnabled = true
            // Send initial progress table immediately
            sendProgressUpdate()
            log.debug "Slack plugin: Progress updates enabled (min interval: ${config.onProgress.interval})"
        }
    }

    @Override
    void onProcessSubmit(TaskHandler handler, TraceRecord trace) {
        submittedTasks.incrementAndGet()
        scheduleProgressUpdateIfNeeded()
    }

    @Override
    void onProcessComplete(TaskHandler handler, TraceRecord trace) {
        completedTasks.incrementAndGet()
        scheduleProgressUpdateIfNeeded()
    }

    @Override
    void onProcessCached(TaskHandler handler, TraceRecord trace) {
        cachedTasks.incrementAndGet()
        scheduleProgressUpdateIfNeeded()
    }

    /**
     * Called when the workflow execution begins (after setup, before processes run).
     *
     * V1 observers (like us) are called before V2 observers (like TowerClient),
     * so the Seqera Platform watch URL isn't available yet. We schedule an async
     * update to add the deep link button once TowerClient has completed setup.
     */
    @Override
    void onFlowBegin() {
        if (!isConfigured()) return
        if (config.onStart.enabled) {
            addReactionIfEnabled(config.reactions?.onStart)
            startReactionAdded = true
        }
        scheduleSeqeraPlatformButtonUpdate()
    }

    /**
     * Schedule an async update to add the Seqera Platform button to the start message.
     * Waits briefly for TowerClient (a V2 observer) to set its watchUrl during onFlowBegin.
     */
    private void scheduleSeqeraPlatformButtonUpdate() {
        if (!config.seqeraPlatform?.enabled) return
        if (!(sender instanceof BotSlackSender)) return
        def messageTs = (sender as BotSlackSender).getThreadTs()
        if (!messageTs) return

        new Timer('slack-seqera-button', true).schedule(new TimerTask() {
            @Override
            void run() {
                try {
                    def startMessage = messageBuilder.buildWorkflowStartMessage()
                    sender.updateMessage(startMessage, messageTs)
                    log.debug "Slack plugin: Updated start message with Seqera Platform button"
                }
                catch (Exception e) {
                    log.debug "Slack plugin: Failed to update start message with Seqera Platform button: ${e.message}"
                }
            }
        }, 3000L)
    }

    /**
     * Called when the workflow completes (may be success or cancellation)
     */
    @Override
    void onFlowComplete() {
        if (progressEnabled) {
            reconcileCountsFromMetadata()
            sendProgressUpdate()
        }
        cancelProgressTimer()
        if (!isConfigured()) return

        // Check if the workflow completed successfully or was cancelled/failed
        def isSuccess = session?.workflowMetadata?.success

        if (isSuccess) {
            // Send completion message if enabled
            if (config.onComplete.enabled) {
                def threadTs = getThreadTsIfEnabled()
                def message = messageBuilder.buildWorkflowCompleteMessage(threadTs)
                sender.sendMessage(message)
                log.debug "Slack plugin: Sent workflow complete notification"

                // Upload configured files
                uploadConfiguredFiles(config.onComplete.files, threadTs)
            }

            // Handle reactions independently of notification
            if (startReactionAdded) {
                removeReactionIfEnabled(config.reactions?.onStart)
            }
            addReactionIfEnabled(config.reactions?.onSuccess)
        } else {
            // Workflow was cancelled or failed without calling onFlowError
            // Best-effort cleanup: always attempt to remove reactions
            removeReactionIfEnabled(config.reactions?.onStart)
            removeReactionIfEnabled(config.reactions?.onError)
            log.debug "Slack plugin: Workflow cancelled, removed reactions"
        }
    }

    /**
     * Called when the workflow fails
     */
    @Override
    void onFlowError(TaskHandler handler, TraceRecord trace) {
        if (progressEnabled) {
            reconcileCountsFromMetadata()
            sendProgressUpdate()
        }
        cancelProgressTimer()
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

        if (startReactionAdded) {
            removeReactionIfEnabled(config.reactions?.onStart)
        }
        addReactionIfEnabled(config.reactions?.onError)
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
     * Add an emoji reaction to the start message if reactions are enabled
     */
    private void addReactionIfEnabled(String emoji) {
        if (!emoji) return
        if (!config.reactions?.enabled) return
        if (!(sender instanceof BotSlackSender)) return

        try {
            def messageTs = (sender as BotSlackSender).getThreadTs()
            if (messageTs) {
                sender.addReaction(emoji, messageTs)
            }
        }
        catch (Exception e) {
            log.debug "Slack plugin: Failed to add reaction: ${e.message}"
        }
    }

    /**
     * Remove an emoji reaction from the start message if reactions are enabled
     */
    private void removeReactionIfEnabled(String emoji) {
        if (!emoji) return
        if (!config.reactions?.enabled) return
        if (!(sender instanceof BotSlackSender)) return

        try {
            def messageTs = (sender as BotSlackSender).getThreadTs()
            if (messageTs) {
                sender.removeReaction(emoji, messageTs)
            }
        }
        catch (Exception e) {
            log.debug "Slack plugin: Failed to remove reaction: ${e.message}"
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

    SlackConfig getConfig() {
        return config
    }

    void setSession(Session session) {
        this.session = session
    }

    void setConfig(SlackConfig config) {
        this.config = config
    }

    void setMessageBuilder(SlackMessageBuilder messageBuilder) {
        this.messageBuilder = messageBuilder
    }

    private void scheduleProgressUpdateIfNeeded() {
        if (!progressEnabled) return

        long now = System.currentTimeMillis()
        long lastUpdate = lastUpdateTime.get()
        long timeSinceLast = now - lastUpdate

        if (timeSinceLast >= intervalMs) {
            sendProgressUpdate()
        } else {
            synchronized (this) {
                if (pendingUpdateTimer != null) return
                long delay = intervalMs - timeSinceLast
                pendingUpdateTimer = new Timer('slack-progress-pending', true)
                pendingUpdateTimer.schedule(new TimerTask() {
                    @Override
                    void run() {
                        synchronized (SlackObserver.this) {
                            pendingUpdateTimer = null
                        }
                        sendProgressUpdate()
                    }
                }, delay)
            }
        }
    }

    private void sendProgressUpdate() {
        try {
            if (startMessageTs == null || !isConfigured()) return
            lastUpdateTime.set(System.currentTimeMillis())
            long elapsed = System.currentTimeMillis() - startTimeMillis
            String message = messageBuilder.buildProgressUpdateMessage(
                submittedTasks.get(), completedTasks.get(), cachedTasks.get(), failedTasks.get(), elapsed, startMessageTs
            )
            sender.updateMessage(message, startMessageTs)
        }
        catch (Exception e) {
            log.debug "Slack plugin: Failed to send progress update: ${e.message}"
        }
    }

    private void reconcileCountsFromMetadata() {
        def stats = session?.workflowMetadata?.stats
        if (stats) {
            completedTasks.set(stats.succeedCount ?: 0)
            cachedTasks.set(stats.cachedCount ?: 0)
            failedTasks.set(stats.failedCount ?: 0)
        }
    }

    private void cancelProgressTimer() {
        synchronized (this) {
            if (pendingUpdateTimer != null) {
                pendingUpdateTimer.cancel()
                pendingUpdateTimer = null
            }
        }
    }
}

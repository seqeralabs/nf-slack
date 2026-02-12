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

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.nio.file.Files
import java.nio.file.Path

/**
 * Slack sender implementation using Bot User OAuth Token.
 *
 * Features:
 * - Sends messages via chat.postMessage API
 * - Uploads files via files.getUploadURLExternal + files.completeUploadExternal API
 * - Handles rate limiting
 * - Supports Block Kit (future)
 *
 * @author Adam Talbot <adam.talbot@seqera.io>
 */
@Slf4j
@CompileStatic
class BotSlackSender implements SlackSender {

    private static final String CHAT_POST_MESSAGE_URL = "https://slack.com/api/chat.postMessage"
    private static final String REACTIONS_ADD_URL = "https://slack.com/api/reactions.add"
    private static final String FILES_GET_UPLOAD_URL = "https://slack.com/api/files.getUploadURLExternal"
    private static final String FILES_COMPLETE_UPLOAD_URL = "https://slack.com/api/files.completeUploadExternal"

    /** Maximum file size for Slack uploads (free plan: 1GB, but we limit to 100MB for safety) */
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024

    private final String botToken
    private final String channelId
    private final Set<String> loggedErrors = Collections.synchronizedSet(new HashSet<String>())
    private String threadTs  // Store the thread timestamp for threaded conversations

    /**
     * Create a new BotSlackSender
     *
     * @param botToken Bot User OAuth Token (xoxb-...)
     * @param channelId Channel ID to send messages to
     */
    BotSlackSender(String botToken, String channelId) {
        this.botToken = botToken
        this.channelId = channelId
    }

    /**
     * Send a message to Slack via Web API
     *
     * @param message JSON message payload (must be compatible with chat.postMessage)
     */
    @Override
    void sendMessage(String message) {
        try {
            // Message is already formatted by SlackMessageBuilder with channel ID
            postToSlack(message)

        } catch (Exception e) {
            def errorMsg = "Slack plugin: Error sending bot message: ${e.message}".toString()
            if (loggedErrors.add(errorMsg)) {
                log.error errorMsg
            }
        }
    }

    /**
     * Upload a file to Slack using the files.uploadV2 flow:
     * 1. Call files.getUploadURLExternal to get an upload URL
     * 2. Upload the file content to that URL
     * 3. Call files.completeUploadExternal to finalize and share
     *
     * @param filePath Path to the file to upload
     * @param options Map with optional keys: title, comment, filename, threadTs
     */
    @Override
    void uploadFile(Path filePath, Map options) {
        try {
            if (filePath == null) {
                log.error "Slack plugin: File path is required for file upload"
                return
            }

            if (!Files.exists(filePath)) {
                log.error "Slack plugin: File not found: ${filePath}"
                return
            }

            if (!Files.isReadable(filePath)) {
                log.error "Slack plugin: File is not readable: ${filePath}"
                return
            }

            def fileSize = Files.size(filePath)
            if (fileSize == 0) {
                log.error "Slack plugin: Cannot upload empty file: ${filePath}"
                return
            }

            if (fileSize > MAX_FILE_SIZE) {
                log.error "Slack plugin: File exceeds maximum size of ${MAX_FILE_SIZE / (1024 * 1024)}MB: ${filePath}"
                return
            }

            def filename = (options?.filename as String) ?: filePath.getFileName().toString()
            def title = (options?.title as String) ?: filename
            def comment = options?.comment as String
            def threadTs = options?.threadTs as String

            // Step 1: Get upload URL
            def uploadInfo = getUploadUrl(filename, fileSize)
            if (!uploadInfo) {
                return
            }

            def uploadUrl = uploadInfo.upload_url as String
            def fileId = uploadInfo.file_id as String

            // Step 2: Upload file content
            if (!uploadFileContent(uploadUrl, filePath)) {
                return
            }

            // Step 3: Complete the upload
            completeUpload(fileId, title, channelId, comment, threadTs)

            log.debug "Slack plugin: Successfully uploaded file: ${filename}"

        } catch (Exception e) {
            def errorMsg = "Slack plugin: Error uploading file: ${e.message}".toString()
            if (loggedErrors.add(errorMsg)) {
                log.error errorMsg
            }
        }
    }

    /**
     * Step 1: Get an external upload URL from Slack
     *
     * @param filename The filename to upload
     * @param length The file size in bytes
     * @return Map with upload_url and file_id, or null on failure
     */
    protected Map getUploadUrl(String filename, long length) {
        HttpURLConnection connection = null
        try {
            def encodedFilename = URLEncoder.encode(filename, "UTF-8")
            def url = new URL("${FILES_GET_UPLOAD_URL}?filename=${encodedFilename}&length=${length}")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = 'GET'
            connection.setRequestProperty('Authorization', "Bearer ${botToken}")

            def responseCode = connection.responseCode
            if (responseCode != 200) {
                def errorBody = connection.errorStream?.text ?: ""
                log.error "Slack plugin: Failed to get upload URL - HTTP ${responseCode}: ${errorBody}"
                return null
            }

            def responseText = connection.inputStream.text
            def response = new JsonSlurper().parseText(responseText) as Map

            if (!response.ok) {
                def error = response.error
                log.error "Slack plugin: Failed to get upload URL - API error: ${error}"
                return null
            }

            return response

        } catch (Exception e) {
            log.error "Slack plugin: Error getting upload URL: ${e.message}"
            return null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Step 2: Upload file content to the external URL
     *
     * @param uploadUrl The URL to upload to (from Step 1)
     * @param filePath The file to upload
     * @return true if successful
     */
    protected boolean uploadFileContent(String uploadUrl, Path filePath) {
        HttpURLConnection connection = null
        try {
            def url = new URL(uploadUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = 'POST'
            connection.doOutput = true
            connection.setRequestProperty('Content-Type', 'application/octet-stream')

            // Stream file content to the upload URL
            connection.outputStream.withCloseable { OutputStream out ->
                Files.copy(filePath, out)
            }

            def responseCode = connection.responseCode
            if (responseCode != 200) {
                def errorBody = connection.errorStream?.text ?: ""
                log.error "Slack plugin: Failed to upload file content - HTTP ${responseCode}: ${errorBody}"
                return false
            }

            return true

        } catch (Exception e) {
            log.error "Slack plugin: Error uploading file content: ${e.message}"
            return false
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Step 3: Complete the file upload and share to channel
     *
     * @param fileId The file ID from Step 1
     * @param title The title to display for the file
     * @param channelId The channel to share the file in
     * @param comment Optional initial comment
     * @param threadTs Optional thread timestamp for threading
     */
    protected void completeUpload(String fileId, String title, String channelId, String comment, String threadTs) {
        HttpURLConnection connection = null
        try {
            def url = new URL(FILES_COMPLETE_UPLOAD_URL)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = 'POST'
            connection.doOutput = true
            connection.setRequestProperty('Content-Type', 'application/json; charset=utf-8')
            connection.setRequestProperty('Authorization', "Bearer ${botToken}")

            def payload = [
                files: [[id: fileId, title: title]],
                channel_id: channelId
            ] as Map

            if (comment) {
                payload.initial_comment = comment
            }
            if (threadTs) {
                payload.thread_ts = threadTs
            }

            def jsonPayload = new JsonBuilder(payload).toString()
            log.debug "Slack plugin: completeUpload payload: ${jsonPayload}"

            connection.outputStream.withCloseable { out ->
                out.write(jsonPayload.getBytes("UTF-8"))
            }

            def responseCode = connection.responseCode
            if (responseCode != 200) {
                def errorBody = connection.errorStream?.text ?: ""
                log.error "Slack plugin: Failed to complete file upload - HTTP ${responseCode}: ${errorBody}"
                return
            }

            def responseText = connection.inputStream.text
            def response = new JsonSlurper().parseText(responseText) as Map

            if (!response.ok) {
                def error = response.error
                log.error "Slack plugin: Failed to complete file upload - API error: ${error}"
            }

        } catch (Exception e) {
            log.error "Slack plugin: Error completing file upload: ${e.message}"
        } finally {
            connection?.disconnect()
        }
    }

    private void postToSlack(String jsonPayload) {
        HttpURLConnection connection = null
        try {
            def url = new URL(CHAT_POST_MESSAGE_URL)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = 'POST'
            connection.doOutput = true
            connection.setRequestProperty('Content-Type', 'application/json; charset=utf-8')
            connection.setRequestProperty('Authorization', "Bearer ${botToken}")

            // Send message
            log.debug "Slack plugin: Sending payload: ${jsonPayload}"
            connection.outputStream.withCloseable { out ->
                out.write(jsonPayload.getBytes("UTF-8"))
            }

            // Check HTTP response
            def responseCode = connection.responseCode
            if (responseCode != 200) {
                def errorBody = connection.errorStream?.text ?: ""
                log.error "Slack plugin: HTTP ${responseCode}: ${errorBody}"
                return
            }

            // Check Slack API 'ok' status
            def responseText = connection.inputStream.text
            def response = new JsonSlurper().parseText(responseText) as Map

            if (!response.ok) {
                def error = response.error
                def errorMsg = "Slack plugin: API error: ${error}".toString()
                if (loggedErrors.add(errorMsg)) {
                    log.error errorMsg
                }
            } else {
                // Capture the thread timestamp from the response for future threaded replies
                def ts = response.ts as String
                if (ts && !threadTs) {
                    threadTs = ts
                    log.debug "Slack plugin: Captured thread timestamp: ${threadTs}"
                }
            }

        } catch (Exception e) {
            def errorMsg = "Slack plugin: Error sending bot message: ${e.message}".toString()
            if (loggedErrors.add(errorMsg)) {
                log.error errorMsg
            }
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Get the thread timestamp for threaded conversations
     *
     * @return The thread timestamp, or null if not set
     */
    String getThreadTs() {
        return threadTs
    }

    @Override
    void addReaction(String emoji, String messageTs) {
        try {
            postReaction(emoji, messageTs)
        } catch (Exception e) {
            log.debug "Slack plugin: Failed to add reaction '${emoji}': ${e.message}"
        }
    }

    protected void postReaction(String emoji, String messageTs) {
        HttpURLConnection connection = null
        try {
            def url = new URL(REACTIONS_ADD_URL)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = 'POST'
            connection.doOutput = true
            connection.setRequestProperty('Content-Type', 'application/json; charset=utf-8')
            connection.setRequestProperty('Authorization', "Bearer ${botToken}")

            def payload = new JsonBuilder([
                channel: channelId,
                name: emoji,
                timestamp: messageTs
            ]).toString()

            connection.outputStream.withCloseable { out ->
                out.write(payload.getBytes("UTF-8"))
            }

            def responseCode = connection.responseCode
            if (responseCode == 200) {
                def responseText = connection.inputStream.text
                def response = new JsonSlurper().parseText(responseText) as Map
                if (!response.ok) {
                    def hint = response.error == 'missing_scope' ? ' (add reactions:write scope to your Slack app)' : ''
                    log.warn "Slack plugin: Failed to add reaction '${emoji}': ${response.error}${hint}"
                }
            } else {
                log.debug "Slack plugin: Failed to add reaction - HTTP ${responseCode}"
            }
        } finally {
            connection?.disconnect()
        }
    }
}

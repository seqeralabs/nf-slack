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

/**
 * Interface for sending messages to Slack.
 *
 * Implementations include:
 * - WebhookSlackSender: Sends messages via Slack Incoming Webhooks
 * - BotSlackSender: Sends messages via Slack Bot Token API
 *
 * @author Adam Talbot <adam.talbot@seqera.io>
 */
interface SlackSender {

    /**
     * Send a message to Slack
     *
     * @param message JSON message payload
     */
    void sendMessage(String message)

    /**
     * Upload a file to Slack
     *
     * @param filePath Path to the file to upload
     * @param options Map with optional keys:
     *   - title (String): Title for the file in Slack
     *   - comment (String): Initial comment to accompany the file
     *   - filename (String): Override filename displayed in Slack
     *   - threadTs (String): Thread timestamp for threading
     */
    void uploadFile(Path filePath, Map options)

    /**
     * Add an emoji reaction to a message. Default no-op for senders that don't support reactions.
     *
     * @param emoji Emoji name without colons (e.g., 'white_check_mark')
     * @param messageTs Timestamp of the message to react to
     */
    default void addReaction(String emoji, String messageTs) {
        // No-op by default - only BotSlackSender supports reactions
    }

    /**
     * Remove an emoji reaction from a message. Default no-op for senders that don't support reactions.
     *
     * @param emoji Emoji name without colons (e.g., 'rocket')
     * @param messageTs Timestamp of the message to remove reaction from
     */
    default void removeReaction(String emoji, String messageTs) {
        // No-op by default - only BotSlackSender supports reactions
    }

    /**
     * Validate the Slack connection.
     * Returns true if the connection is valid, false otherwise.
     *
     * @return true if connection is valid
     */
    default boolean validate() {
        return true
    }
}

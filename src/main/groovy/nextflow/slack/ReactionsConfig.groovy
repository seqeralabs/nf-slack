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

/**
 * Configuration for emoji reactions on workflow messages.
 *
 * Reactions are added to the initial start message to indicate workflow status.
 * Requires bot token (not available with webhooks).
 *
 * Configuration structure:
 * slack {
 *     reactions {
 *         enabled = true
 *         onStart = 'rocket'
 *         onSuccess = 'white_check_mark'
 *         onError = 'x'
 *     }
 * }
 *
 * @author Adam Talbot <adam.talbot@seqera.io>
 */
@CompileStatic
class ReactionsConfig {

    /**
     * Enable/disable emoji reactions
     */
    final boolean enabled

    /**
     * Emoji reaction for workflow start (without colons)
     */
    final String onStart

    /**
     * Emoji reaction for successful completion (without colons)
     */
    final String onSuccess

    /**
     * Emoji reaction for workflow error (without colons)
     */
    final String onError

    /**
     * Create ReactionsConfig from configuration map
     *
     * @param config Configuration map from slack.reactions scope
     */
    ReactionsConfig(Map config) {
        this.enabled = config?.enabled != null ? config.enabled as boolean : false
        this.onStart = (config?.onStart as String) ?: 'rocket'
        this.onSuccess = (config?.onSuccess as String) ?: 'white_check_mark'
        this.onError = (config?.onError as String) ?: 'x'
    }

    @Override
    String toString() {
        return "ReactionsConfig[enabled=${enabled}, onStart=${onStart}, onSuccess=${onSuccess}, onError=${onError}]"
    }
}

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
 * Configuration for workflow start notifications.
 *
 * Configuration structure:
 * slack {
 *     onStart {
 *         enabled = true
 *         message = '🚀 *Pipeline started*'
 *         includeCommandLine = true
 *     }
 * }
 *
 * @author Adam Talbot <adam.talbot@seqera.io>
 */
@CompileStatic
class OnStartConfig {

    /**
     * Enable/disable start notifications
     */
    final boolean enabled

    /**
     * Custom message template for workflow start (simple string or map with full config)
     */
    final Object message

    /**
     * Include command line in start message
     */
    final boolean includeCommandLine

    /**
     * Show footer with timestamp in message
     */
    final boolean showFooter

    /**
     * List of workflow metadata field names to include in the notification.
     * When set, only these fields are shown. When empty/null, all default fields are shown.
     * Supported: runName, commandLine, workDir
     */
    final List<String> includeFields

    /**
     * Optional channel override for start notifications (bot token only)
     */
    final String channel

    /**
     * Create OnStartConfig from configuration map
     *
     * @param config Configuration map from slack.onStart scope
     */
    OnStartConfig(Map config) {
        this.enabled = config?.enabled != null ? config.enabled as boolean : true
        this.message = config?.message ?: '🚀 *Pipeline started*'
        this.includeCommandLine = config?.includeCommandLine != null ? config.includeCommandLine as boolean : true
        this.showFooter = config?.showFooter != null ? config.showFooter as boolean : true
        this.includeFields = config?.includeFields != null ? (config.includeFields as List<String>) : []
        this.channel = config?.channel as String
    }

    @Override
    String toString() {
        return "OnStartConfig[enabled=${enabled}, includeCommandLine=${includeCommandLine}]"
    }
}

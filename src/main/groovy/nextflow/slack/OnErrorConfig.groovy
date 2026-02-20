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
 * Configuration for workflow error notifications.
 *
 * Configuration structure:
 * slack {
 *     onError {
 *         enabled = true
 *         message = '❌ *Pipeline failed*'
 *         includeCommandLine = true
 *         files = ['results/pipeline_report.html']
 *     }
 * }
 *
 * @author Adam Talbot <adam.talbot@seqera.io>
 */
@CompileStatic
class OnErrorConfig {

    /**
     * Enable/disable error notifications
     */
    final boolean enabled

    /**
     * Custom message template for workflow error (simple string or map with full config)
     */
    final Object message

    /**
     * Include command line in error message
     */
    final boolean includeCommandLine

    /**
     * Show footer with timestamp in message
     */
    final boolean showFooter

    /**
     * List of file paths to upload on workflow error
     */
    final List<String> files

    /**
     * List of workflow metadata field names to include in the notification.
     * When set, only these fields are shown. When empty/null, all default fields are shown.
     * Supported: runName, duration, status, failedProcess, errorMessage, commandLine, workDir
     */
    final List<String> includeFields

    /**
     * Optional channel override for error notifications (bot token only)
     */
    final String channel

    /**
     * Create OnErrorConfig from configuration map
     *
     * @param config Configuration map from slack.onError scope
     */
    OnErrorConfig(Map config) {
        this.enabled = config?.enabled != null ? config.enabled as boolean : true
        this.message = config?.message ?: '❌ *Pipeline failed*'
        this.includeCommandLine = config?.includeCommandLine != null ? config.includeCommandLine as boolean : true
        this.showFooter = config?.showFooter != null ? config.showFooter as boolean : true
        this.files = config?.files != null ? (config.files as List<String>) : []
        this.includeFields = config?.includeFields != null ? (config.includeFields as List<String>) : []
        this.channel = config?.channel as String
    }

    @Override
    String toString() {
        return "OnErrorConfig[enabled=${enabled}, includeCommandLine=${includeCommandLine}, files=${files.size()}]"
    }
}

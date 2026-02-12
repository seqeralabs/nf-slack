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
 * Configuration for workflow completion notifications.
 *
 * Configuration structure:
 * slack {
 *     onComplete {
 *         enabled = true
 *         message = '✅ *Pipeline completed successfully*'
 *         includeCommandLine = true
 *         includeResourceUsage = true
 *         files = ['results/multiqc_report.html']
 *     }
 * }
 *
 * @author Adam Talbot <adam.talbot@seqera.io>
 */
@CompileStatic
class OnCompleteConfig {

    /**
     * Enable/disable completion notifications
     */
    final boolean enabled

    /**
     * Custom message template for workflow completion (simple string or map with full config)
     */
    final Object message

    /**
     * Include command line in completion message
     */
    final boolean includeCommandLine

    /**
     * Include resource usage statistics in completion message
     */
    final boolean includeResourceUsage

    /**
     * Show footer with timestamp in message
     */
    final boolean showFooter

    /**
     * List of file paths to upload on workflow completion
     */
    final List<String> files

    /**
     * Optional channel override for completion notifications (bot token only)
     */
    final String channel

    /**
     * Create OnCompleteConfig from configuration map
     *
     * @param config Configuration map from slack.onComplete scope
     */
    OnCompleteConfig(Map config) {
        this.enabled = config?.enabled != null ? config.enabled as boolean : true
        this.message = config?.message ?: '✅ *Pipeline completed successfully*'
        this.includeCommandLine = config?.includeCommandLine != null ? config.includeCommandLine as boolean : true
        this.includeResourceUsage = config?.includeResourceUsage != null ? config.includeResourceUsage as boolean : true
        this.showFooter = config?.showFooter != null ? config.showFooter as boolean : true
        this.files = config?.files != null ? (config.files as List<String>) : []
        this.channel = config?.channel as String
    }

    @Override
    String toString() {
        return "OnCompleteConfig[enabled=${enabled}, includeCommandLine=${includeCommandLine}, includeResourceUsage=${includeResourceUsage}, files=${files.size()}]"
    }
}

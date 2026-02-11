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
 * Configuration for workflow progress update notifications.
 *
 * Configuration structure:
 * slack {
 *     onProgress {
 *         enabled = false
 *         interval = '5m'
 *     }
 * }
 *
 * @author Adam Talbot <adam.talbot@seqera.io>
 */
@CompileStatic
class OnProgressConfig {

    /**
     * Enable/disable progress update notifications (default: false)
     */
    final boolean enabled

    /**
     * Update interval as a string (e.g., '30s', '5m', '1h')
     */
    final String interval

    /**
     * Create OnProgressConfig from configuration map
     *
     * @param config Configuration map from slack.onProgress scope
     */
    OnProgressConfig(Map config) {
        this.enabled = config?.enabled != null ? config.enabled as boolean : false
        this.interval = config?.interval as String ?: '5m'
    }

    /**
     * Parse interval string to milliseconds
     *
     * Supports: '30s' (seconds), '5m' (minutes), '1h' (hours)
     *
     * @return interval in milliseconds
     */
    long getIntervalMillis() {
        if (!interval) return 300_000L // default 5 minutes

        def matcher = (interval =~ /^(\d+)([smh])$/)
        if (matcher.matches()) {
            def value = Long.parseLong(matcher.group(1))
            def unit = matcher.group(2)
            switch (unit) {
                case 's': return value * 1000L
                case 'm': return value * 60_000L
                case 'h': return value * 3_600_000L
            }
        }

        // Fallback: try parsing as milliseconds
        try {
            return Long.parseLong(interval)
        } catch (NumberFormatException e) {
            return 300_000L // default 5 minutes
        }
    }

    @Override
    String toString() {
        return "OnProgressConfig[enabled=${enabled}, interval=${interval}]"
    }
}

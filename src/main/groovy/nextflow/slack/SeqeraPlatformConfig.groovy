/*
 * Copyright 2025-2026, Seqera Labs
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
 * Configuration for Seqera Platform deep link integration.
 *
 * When Seqera Platform (Tower) is configured, adds contextual action buttons
 * to Slack notifications that link to the workflow run (or emit interactive
 * action IDs for external handlers).
 *
 * @author Adam Talbot <adam.talbot@seqera.io>
 */
@CompileStatic
class SeqeraPlatformConfig {

    /**
     * Enable/disable Seqera Platform deep links (default: true, auto-detect from tower config)
     */
    final boolean enabled

    /**
     * Action button configuration (View / Cancel / Resume / Relaunch)
     */
    final SeqeraPlatformActionButtonsConfig actionButtons

    SeqeraPlatformConfig(Map config) {
        config = config ?: [:]
        this.enabled = config.enabled != null ? config.enabled as boolean : true
        this.actionButtons = new SeqeraPlatformActionButtonsConfig(config.actionButtons as Map)
    }
}

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
 * Configuration for Seqera Platform action buttons in Slack messages.
 *
 * <p>Link mode (default) emits URL buttons that open the Platform run page.
 * Interactive mode emits action_id buttons for an external Slack interactivity handler.
 */
@CompileStatic
class SeqeraPlatformActionButtonsConfig {

    static final String MODE_LINK = 'link'
    static final String MODE_INTERACTIVE = 'interactive'

    final String mode
    final boolean cancel
    final boolean resume
    final boolean relaunch

    SeqeraPlatformActionButtonsConfig(Map config) {
        config = config ?: [:]
        this.mode = (config.mode as String ?: MODE_LINK).toLowerCase()
        this.cancel = config.cancel != null ? config.cancel as boolean : true
        this.resume = config.resume != null ? config.resume as boolean : true
        this.relaunch = config.relaunch != null ? config.relaunch as boolean : true
    }

    boolean isLinkMode() {
        return mode == MODE_LINK
    }

    boolean isInteractiveMode() {
        return mode == MODE_INTERACTIVE
    }
}

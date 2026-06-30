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
 * Helpers for parsing Seqera Platform watch URLs returned by TowerClient.
 */
@CompileStatic
class SeqeraPlatformUrlBuilder {

    /**
     * Extract the workflow run ID from a Platform watch URL.
     * Example: https://cloud.seqera.io/orgs/foo/workspaces/bar/watch/abc123 -> abc123
     */
    static String extractWorkflowRunId(String watchUrl) {
        if (!watchUrl) return null
        def marker = '/watch/'
        def start = watchUrl.indexOf(marker)
        if (start < 0) return null
        def idPart = watchUrl.substring(start + marker.length())
        def end = idPart.findIndexOf { it == '?' || it == '/' }
        return end >= 0 ? idPart.substring(0, end) : idPart
    }
}

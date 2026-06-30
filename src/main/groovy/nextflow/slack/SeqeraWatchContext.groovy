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
 * Parsed Seqera Platform watch URL, workflow run identifier, and API base URL.
 */
@CompileStatic
class SeqeraWatchContext {

    final String watchUrl
    final String workflowRunId
    final String apiEndpoint

    SeqeraWatchContext(String watchUrl, String workflowRunId, String apiEndpoint) {
        this.watchUrl = watchUrl
        this.workflowRunId = workflowRunId
        this.apiEndpoint = apiEndpoint
    }

    static SeqeraWatchContext fromWatchUrl(String watchUrl, String apiEndpoint = null) {
        if (!watchUrl) return null
        def workflowRunId = SeqeraPlatformUrlBuilder.extractWorkflowRunId(watchUrl)
        return new SeqeraWatchContext(
            watchUrl,
            workflowRunId,
            SeqeraPlatformUrlBuilder.normalizeApiEndpoint(apiEndpoint)
        )
    }
}

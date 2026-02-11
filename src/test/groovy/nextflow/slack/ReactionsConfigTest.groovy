/*
 * Copyright 2024, Seqera Labs
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

import spock.lang.Specification

/**
 * Tests for {@link ReactionsConfig}
 */
class ReactionsConfigTest extends Specification {

    def 'should use default values'() {
        when:
        def config = new ReactionsConfig([:])

        then:
        config.enabled == false
        config.onStart == 'rocket'
        config.onSuccess == 'white_check_mark'
        config.onError == 'x'
    }

    def 'should parse custom values'() {
        when:
        def config = new ReactionsConfig([
            enabled: true,
            onStart: 'tada',
            onSuccess: 'heavy_check_mark',
            onError: 'rotating_light'
        ])

        then:
        config.enabled == true
        config.onStart == 'tada'
        config.onSuccess == 'heavy_check_mark'
        config.onError == 'rotating_light'
    }

    def 'should handle null config'() {
        when:
        def config = new ReactionsConfig(null)

        then:
        config.enabled == false
        config.onStart == 'rocket'
        config.onSuccess == 'white_check_mark'
        config.onError == 'x'
    }

    def 'should handle partial config'() {
        when:
        def config = new ReactionsConfig([enabled: true])

        then:
        config.enabled == true
        config.onStart == 'rocket'
        config.onSuccess == 'white_check_mark'
        config.onError == 'x'
    }
}

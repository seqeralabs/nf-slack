/*
 * Copyright 2024-2025, Seqera Labs
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

class OnProgressConfigTest extends Specification {

    def 'should have correct defaults'() {
        when:
        def config = new OnProgressConfig([:])

        then:
        config.enabled == false
        config.interval == '5m'
    }

    def 'should parse custom values'() {
        when:
        def config = new OnProgressConfig([enabled: true, interval: '30s'])

        then:
        config.enabled == true
        config.interval == '30s'
    }

    def 'should handle null config'() {
        when:
        def config = new OnProgressConfig(null)

        then:
        config.enabled == false
        config.interval == '5m'
    }

    def 'should parse seconds interval'() {
        when:
        def config = new OnProgressConfig([interval: '30s'])

        then:
        config.getIntervalMillis() == 30000
    }

    def 'should parse minutes interval'() {
        when:
        def config = new OnProgressConfig([interval: '5m'])

        then:
        config.getIntervalMillis() == 300000
    }

    def 'should parse hours interval'() {
        when:
        def config = new OnProgressConfig([interval: '1h'])

        then:
        config.getIntervalMillis() == 3600000
    }

    def 'should use default interval when null'() {
        when:
        def config = new OnProgressConfig([:])

        then:
        config.getIntervalMillis() == 300000
    }

    def 'should handle plain number as milliseconds'() {
        when:
        def config = new OnProgressConfig([interval: '60000'])

        then:
        config.getIntervalMillis() == 60000
    }
}

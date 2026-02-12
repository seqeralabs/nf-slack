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

import nextflow.Session
import spock.lang.Specification

/**
 * Tests for SlackConfig
 */
class SlackConfigTest extends Specification {

    def 'should parse configuration from slack block'() {
        given:
        def session = Mock(Session)
        session.config >> [
            slack: [
                webhook: [
                    url: 'https://hooks.slack.com/services/TEST/TEST/TEST'
                ],
                onStart: [
                    enabled: false
                ],
                onComplete: [
                    enabled: true
                ],
                onError: [
                    enabled: true
                ]
            ]
        ]

        when:
        def config = SlackConfig.from(session)

        then:
        config != null
        config.enabled == true
        config.webhook == 'https://hooks.slack.com/services/TEST/TEST/TEST'
        config.onStart.enabled == false
        config.onComplete.enabled == true
        config.onError.enabled == true
    }

    def 'should use default values when not specified'() {
        given:
        def session = Mock(Session)
        session.config >> [
            slack: [
                webhook: [
                    url: 'https://hooks.slack.com/services/TEST/TEST/TEST'
                ]
            ]
        ]

        when:
        def config = SlackConfig.from(session)

        then:
        config != null
        config.enabled == true
        config.onStart.enabled == true
        config.onStart.includeCommandLine == true
        config.onComplete.enabled == true
        config.onComplete.includeCommandLine == true
        config.onComplete.includeResourceUsage == true
        config.onError.enabled == true
        config.onError.includeCommandLine == true
    }

    def 'should return null when webhook is not configured'() {
        given:
        def session = Mock(Session)
        session.config >> [:]
        session.params >> [:]

        when:
        def config = SlackConfig.from(session)

        then:
        config == null
    }

    def 'should return null when explicitly disabled'() {
        given:
        def session = Mock(Session)
        session.config >> [
            slack: [
                webhook: [
                    url: 'https://hooks.slack.com/services/TEST/TEST/TEST'
                ],
                enabled: false
            ]
        ]

        when:
        def config = SlackConfig.from(session)

        then:
        config == null
    }

    def 'isConfigured should return true when enabled and webhook set'() {
        given:
        def session = Mock(Session)
        session.config >> [
            slack: [
                webhook: [
                    url: 'https://hooks.slack.com/services/TEST/TEST/TEST'
                ]
            ]
        ]

        when:
        def config = SlackConfig.from(session)

        then:
        config.isConfigured() == true
    }

    def 'createSender should return WebhookSlackSender for webhook configuration'() {
        given:
        def session = Mock(Session)
        session.config >> [
            slack: [
                webhook: [
                    url: 'https://hooks.slack.com/services/TEST/TEST/TEST'
                ]
            ]
        ]
        def config = SlackConfig.from(session)

        when:
        def sender = config.createSender()

        then:
        sender != null
        sender instanceof WebhookSlackSender
    }

    def 'should parse bot configuration'() {
        given:
        def session = Mock(Session)
        session.config >> [
            slack: [
                bot: [
                    token: 'xoxb-token',
                    channel: 'C123456'
                ]
            ]
        ]

        when:
        def config = SlackConfig.from(session)

        then:
        config != null
        config.enabled == true
        config.botToken == 'xoxb-token'
        config.botChannel == 'C123456'
        config.webhook == null
    }

    def 'isConfigured should return true when enabled and bot set'() {
        given:
        def session = Mock(Session)
        session.config >> [
            slack: [
                bot: [
                    token: 'xoxb-token',
                    channel: 'C123456'
                ]
            ]
        ]

        when:
        def config = SlackConfig.from(session)

        then:
        config.isConfigured() == true
    }

    def 'createSender should return BotSlackSender for bot configuration'() {
        given:
        def session = Mock(Session)
        session.config >> [
            slack: [
                bot: [
                    token: 'xoxb-token',
                    channel: 'C123456'
                ]
            ]
        ]
        def config = SlackConfig.from(session)

        when:
        def sender = config.createSender()

        then:
        sender != null
        sender instanceof BotSlackSender
    }

    def 'createSender should prefer BotSlackSender when both are configured'() {
        given:
        def session = Mock(Session)
        session.config >> [
            slack: [
                webhook: [
                    url: 'https://hooks.slack.com/services/TEST/TEST/TEST'
                ],
                bot: [
                    token: 'xoxb-token',
                    channel: 'C123456'
                ]
            ]
        ]
        def config = SlackConfig.from(session)

        when:
        def sender = config.createSender()

        then:
        sender != null
        sender instanceof BotSlackSender
    }

    def 'should throw exception for invalid bot token'() {
        given:
        def session = Mock(Session)
        session.config >> [
            slack: [
                bot: [
                    token: 'invalid-token',
                    channel: 'C123456'
                ]
            ]
        ]

        when:
        SlackConfig.from(session)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Bot token must start with 'xoxb-' or 'xoxp-'")
    }

    def 'should throw exception for missing bot channel'() {
        given:
        def session = Mock(Session)
        session.config >> [
            slack: [
                bot: [
                    token: 'xoxb-token'
                ]
            ]
        ]

        when:
        SlackConfig.from(session)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Bot channel is required")
    }

    def 'should throw exception for invalid bot channel'() {
        given:
        def session = Mock(Session)
        session.config >> [
            slack: [
                bot: [
                    token: 'xoxb-token',
                    channel: 'Invalid Channel Name'
                ]
            ]
        ]

        when:
        SlackConfig.from(session)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Invalid channel ID format")
    }
    def 'should allow channel name with hash'() {
        given:
        def session = Mock(Session)
        session.config >> [
            slack: [
                bot: [
                    token: 'xoxb-token',
                    channel: '#general'
                ]
            ]
        ]

        when:
        def config = SlackConfig.from(session)

        then:
        config != null
        config.botChannel == '#general'
    }

    def 'should allow user token (xoxp-)'() {
        given:
        def session = Mock(Session)
        session.config >> [
            slack: [
                bot: [
                    token: 'xoxp-token',
                    channel: 'C123456'
                ]
            ]
        ]

        when:
        def config = SlackConfig.from(session)

        then:
        config != null
        config.botToken == 'xoxp-token'
    }

    def 'should default useThreads to false'() {
        given:
        def session = Mock(Session)
        session.config >> [
            slack: [
                bot: [
                    token: 'xoxb-token',
                    channel: 'C123456'
                ]
            ]
        ]

        when:
        def config = SlackConfig.from(session)

        then:
        config != null
        config.useThreads == false
    }

    def 'should parse useThreads when explicitly set to true'() {
        given:
        def session = Mock(Session)
        session.config >> [
            slack: [
                bot: [
                    token: 'xoxb-token',
                    channel: 'C123456',
                    useThreads: true
                ]
            ]
        ]

        when:
        def config = SlackConfig.from(session)

        then:
        config != null
        config.useThreads == true
    }

    def 'should parse useThreads when explicitly set to false'() {
        given:
        def session = Mock(Session)
        session.config >> [
            slack: [
                bot: [
                    token: 'xoxb-token',
                    channel: 'C123456',
                    useThreads: false
                ]
            ]
        ]

        when:
        def config = SlackConfig.from(session)

        then:
        config != null
        config.useThreads == false
    }

    def 'should parse per-event channel from onStart config'() {
        given:
        def session = Mock(Session)
        session.config >> [slack: [bot: [token: 'xoxb-token', channel: 'C123456'], onStart: [channel: '#alerts']]]

        when:
        def config = SlackConfig.from(session)

        then:
        config.onStart.channel == '#alerts'
    }

    def 'should have null per-event channel by default'() {
        given:
        def session = Mock(Session)
        session.config >> [slack: [bot: [token: 'xoxb-token', channel: 'C123456']]]

        when:
        def config = SlackConfig.from(session)

        then:
        config.onStart.channel == null
        config.onComplete.channel == null
        config.onError.channel == null
    }
}

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

import groovy.json.JsonSlurper
import spock.lang.Specification

class SlackMentionResolverTest extends Specification {

    private static final List<Map> SAMPLE_USERS = [
        [
            id: 'U111',
            name: 'jane.doe',
            deleted: false,
            is_bot: false,
            profile: [display_name: 'Jane', real_name: 'Jane Doe']
        ],
        [
            id: 'U222',
            name: 'john.smith',
            deleted: false,
            is_bot: false,
            profile: [display_name: 'John', real_name: 'John Smith']
        ]
    ]

    private static final List<Map> SAMPLE_USERGROUPS = [
        [id: 'S111', name: 'Bioinformatics', handle: 'bioinformatics'],
        [id: 'S222', name: 'Platform', handle: 'platform-team']
    ]

    private SlackMentionResolver createResolver(List<Map> users = SAMPLE_USERS, List<Map> usergroups = SAMPLE_USERGROUPS) {
        new SlackMentionResolver('xoxb-test-token') {
            @Override
            protected Map fetchUsersPage(String cursor) {
                if (cursor) {
                    return null
                }
                return [
                    ok: true,
                    members: users,
                    response_metadata: [next_cursor: '']
                ]
            }

            @Override
            protected List<Map> fetchUsergroups() {
                return usergroups
            }
        }
    }

    def 'should resolve display name mention to user id'() {
        given:
        def resolver = createResolver()

        expect:
        resolver.resolveInText(':wave: Hi <@Jane>!') == ':wave: Hi <@U111>!'
    }

    def 'should resolve username mention to user id'() {
        given:
        def resolver = createResolver()

        expect:
        resolver.resolveInText('Ping <@jane.doe>') == 'Ping <@U111>'
    }

    def 'should resolve real name mention to user id'() {
        given:
        def resolver = createResolver()

        expect:
        resolver.resolveInText('Notify <@Jane Doe>') == 'Notify <@U111>'
    }

    def 'should leave existing user id mentions unchanged'() {
        given:
        def resolver = createResolver()

        expect:
        resolver.resolveInText('Hi <@U999ABC> and <@U999ABC|Jane>') == 'Hi <@U999ABC> and <@U999ABC|Jane>'
    }

    def 'should leave mention unresolved when multiple users match'() {
        given:
        def ambiguousUsers = SAMPLE_USERS + [
            [
                id: 'U333',
                name: 'jane.other',
                deleted: false,
                is_bot: false,
                profile: [display_name: 'Jane', real_name: 'Jane Other']
            ]
        ]
        def resolver = createResolver(ambiguousUsers)

        expect:
        resolver.resolveInText('Hi <@Jane>') == 'Hi <@Jane>'
    }

    def 'should leave mention unresolved when no user matches'() {
        given:
        def resolver = createResolver()

        expect:
        resolver.resolveInText('Hi <@Nobody>') == 'Hi <@Nobody>'
    }

    def 'should resolve subteam mention by name'() {
        given:
        def resolver = createResolver()

        expect:
        resolver.resolveInText('Notify <!subteam^Bioinformatics>') == 'Notify <!subteam^S111>'
    }

    def 'should resolve subteam mention by handle'() {
        given:
        def resolver = createResolver()

        expect:
        resolver.resolveInText('Notify <!subteam^platform-team>') == 'Notify <!subteam^S222>'
    }

    def 'should leave existing subteam id mentions unchanged'() {
        given:
        def resolver = createResolver()

        expect:
        resolver.resolveInText('<!subteam^S999|@bio>') == '<!subteam^S999|@bio>'
    }

    def 'should resolve mentions inside JSON block payloads'() {
        given:
        def resolver = createResolver()
        def payload = '''{
            "channel": "C123",
            "blocks": [
                {
                    "type": "section",
                    "text": { "type": "mrkdwn", "text": "Hi <@jane.doe>" }
                }
            ]
        }'''

        when:
        def resolved = resolver.resolveInJson(payload)
        def json = new JsonSlurper().parseText(resolved)

        then:
        json.blocks[0].text.text == 'Hi <@U111>'
    }

    def 'should detect resolvable mentions'() {
        expect:
        SlackMentionResolver.hasResolvableMentions('Hi <@Jane>') == true
        SlackMentionResolver.hasResolvableMentions('Hi <@U123ABC>') == false
        SlackMentionResolver.hasResolvableMentions('Hi <!subteam^Bioinformatics>') == true
        SlackMentionResolver.hasResolvableMentions('Hi <!subteam^S123ABC>') == false
    }

    def 'should skip deleted and bot users'() {
        given:
        def users = [
            [
                id: 'UBOT',
                name: 'bot.user',
                deleted: false,
                is_bot: true,
                profile: [display_name: 'Bot User', real_name: 'Bot User']
            ],
            [
                id: 'UGONE',
                name: 'gone.user',
                deleted: true,
                is_bot: false,
                profile: [display_name: 'Gone User', real_name: 'Gone User']
            ]
        ]
        def resolver = createResolver(users, [])

        expect:
        resolver.resolveInText('Hi <@Bot User> and <@Gone User>') == 'Hi <@Bot User> and <@Gone User>'
    }

    def 'should cache users across multiple resolutions'() {
        given:
        def fetchCount = 0
        def resolver = new SlackMentionResolver('xoxb-test-token') {
            @Override
            protected Map fetchUsersPage(String cursor) {
                fetchCount++
                return [
                    ok: true,
                    members: SAMPLE_USERS,
                    response_metadata: [next_cursor: '']
                ]
            }

            @Override
            protected List<Map> fetchUsergroups() {
                return SAMPLE_USERGROUPS
            }
        }

        when:
        resolver.resolveInText('Hi <@jane.doe>')
        resolver.resolveInText('Hi <@john.smith>')

        then:
        fetchCount == 1
    }
}

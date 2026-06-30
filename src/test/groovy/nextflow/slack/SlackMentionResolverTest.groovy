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

    private SlackMentionResolver createResolver(List<Map> users = SAMPLE_USERS) {
        new SlackMentionResolver('xoxb-test-token') {
            @Override
            protected List<Map> fetchUsers() {
                return users
            }
        }
    }

    def 'should resolve display name mention to user id'() {
        expect:
        createResolver().resolveInText(':wave: Hi <@Jane>!') == ':wave: Hi <@U111>!'
    }

    def 'should resolve username mention to user id'() {
        expect:
        createResolver().resolveInText('Ping <@jane.doe>') == 'Ping <@U111>'
    }

    def 'should resolve real name mention to user id'() {
        expect:
        createResolver().resolveInText('Notify <@Jane Doe>') == 'Notify <@U111>'
    }

    def 'should leave existing user id mentions unchanged'() {
        expect:
        createResolver().resolveInText('Hi <@U999ABC> and <@U999ABC|Jane>') == 'Hi <@U999ABC> and <@U999ABC|Jane>'
    }

    def 'should resolve display names starting with U'() {
        given:
        def users = SAMPLE_USERS + [
            [
                id: 'U333',
                name: 'ulysses',
                deleted: false,
                is_bot: false,
                profile: [display_name: 'Ulysses', real_name: 'Ulysses Grant']
            ]
        ]

        expect:
        createResolver(users).resolveInText('Hi <@Ulysses>') == 'Hi <@U333>'
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

        expect:
        createResolver(ambiguousUsers).resolveInText('Hi <@Jane>') == 'Hi <@Jane>'
    }

    def 'should leave mention unresolved when no user matches'() {
        expect:
        createResolver().resolveInText('Hi <@Nobody>') == 'Hi <@Nobody>'
    }

    def 'should resolve mentions inside JSON block payloads'() {
        given:
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
        def resolved = createResolver().resolveInJson(payload)
        def json = new JsonSlurper().parseText(resolved)

        then:
        json.blocks[0].text.text == 'Hi <@U111>'
    }

    def 'should detect resolvable mentions'() {
        expect:
        SlackMentionResolver.hasResolvableMentions('Hi <@Jane>') == true
        SlackMentionResolver.hasResolvableMentions('Hi <@U123ABC>') == false
        SlackMentionResolver.hasResolvableMentions('plain text') == false
    }

    def 'should skip deleted and bot users'() {
        given:
        def resolver = new SlackMentionResolver('xoxb-test-token') {
            @Override
            protected List<Map> fetchUsers() {
                def users = [
                    [
                        id: 'UBOTUSER',
                        name: 'bot.user',
                        deleted: false,
                        is_bot: true,
                        profile: [display_name: 'Bot User', real_name: 'Bot User']
                    ],
                    [
                        id: 'UGONEUSER',
                        name: 'gone.user',
                        deleted: true,
                        is_bot: false,
                        profile: [display_name: 'Gone User', real_name: 'Gone User']
                    ]
                ]
                return users.findAll { !it.deleted && !it.is_bot } as List<Map>
            }
        }

        expect:
        resolver.resolveInText('Hi <@Bot User> and <@Gone User>') == 'Hi <@Bot User> and <@Gone User>'
    }

    def 'should leave mention unresolved when users.list fails'() {
        given:
        def resolver = new SlackMentionResolver('xoxb-test-token') {
            @Override
            protected List<Map> fetchUsers() {
                usersListUnavailable = true
                return []
            }
        }

        expect:
        resolver.resolveInText('Hi <@jane.doe>') == 'Hi <@jane.doe>'
    }
}

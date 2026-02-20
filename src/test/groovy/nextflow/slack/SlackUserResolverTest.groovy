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

import spock.lang.Specification

/**
 * Tests for {@link SlackUserResolver}.
 *
 * All network calls are intercepted by overriding the protected {@code fetchUsers()} and
 * {@code fetchUsergroups()} methods via anonymous subclasses — no real Slack API is hit.
 */
class SlackUserResolverTest extends Specification {

    // =========================================================================
    // Static helpers
    // =========================================================================

    def 'isSlackUserId returns true for U-prefixed IDs'() {
        expect:
        SlackUserResolver.isSlackUserId('U1234567890')
        SlackUserResolver.isSlackUserId('UA')
        SlackUserResolver.isSlackUserId('W9ABCDEF01')
    }

    def 'isSlackUserId returns false for non-ID strings'() {
        expect:
        !SlackUserResolver.isSlackUserId('Jane')
        !SlackUserResolver.isSlackUserId('jane.doe')
        !SlackUserResolver.isSlackUserId('Jane Doe')
        !SlackUserResolver.isSlackUserId('')
        !SlackUserResolver.isSlackUserId(null)
        !SlackUserResolver.isSlackUserId('U')  // too short
    }

    def 'isSlackSubteamId returns true for S-prefixed IDs'() {
        expect:
        SlackUserResolver.isSlackSubteamId('S1234567890')
        SlackUserResolver.isSlackSubteamId('SA')
    }

    def 'isSlackSubteamId returns false for non-ID strings'() {
        expect:
        !SlackUserResolver.isSlackSubteamId('my-team')
        !SlackUserResolver.isSlackSubteamId('')
        !SlackUserResolver.isSlackSubteamId(null)
        !SlackUserResolver.isSlackSubteamId('S')  // too short
    }

    // =========================================================================
    // resolveText — no mentions
    // =========================================================================

    def 'resolveText returns text unchanged when there are no mentions'() {
        given:
        def resolver = resolverWith(users: [], groups: [])

        expect:
        resolver.resolveText('Hello world!') == 'Hello world!'
        resolver.resolveText('') == ''
        resolver.resolveText(null) == null
    }

    def 'resolveText leaves already-resolved Slack user IDs unchanged'() {
        given:
        def resolver = resolverWith(users: [
            user('U1234567890', 'jane.doe', 'Jane', 'Jane Doe')
        ], groups: [])

        when:
        def result = resolver.resolveText('Hi <@U1234567890>!')

        then:
        result == 'Hi <@U1234567890>!'
    }

    // =========================================================================
    // User mention resolution — priority order
    // =========================================================================

    def 'resolves mention by username (priority 1)'() {
        given:
        def resolver = resolverWith(users: [
            user('U1234567890', 'jane.doe', 'Jane', 'Jane Doe')
        ], groups: [])

        when:
        def result = resolver.resolveText('Hi <@jane.doe>!')

        then:
        result == 'Hi <@U1234567890>!'
    }

    def 'resolves mention by display_name when username does not match (priority 2)'() {
        given:
        def resolver = resolverWith(users: [
            user('U1234567890', 'jane.doe', 'Jane', 'Jane Doe')
        ], groups: [])

        when:
        def result = resolver.resolveText('Hi <@Jane>!')

        then:
        result == 'Hi <@U1234567890>!'
    }

    def 'resolves mention by real_name when username and display_name do not match (priority 3)'() {
        given:
        def resolver = resolverWith(users: [
            user('U1234567890', 'jane.doe', '', 'Jane Doe')
        ], groups: [])

        when:
        def result = resolver.resolveText('Hi <@Jane Doe>!')

        then:
        result == 'Hi <@U1234567890>!'
    }

    def 'username match takes priority over display_name match'() {
        given:
        // user1's username matches, user2's display_name matches the same name
        def resolver = resolverWith(users: [
            user('U111', 'Jane', 'Something Else', 'Real Name 1'),
            user('U222', 'other.person', 'Jane', 'Real Name 2')
        ], groups: [])

        when:
        def result = resolver.resolveText('<@Jane>')

        then:
        // Only U111 matches by username — no ambiguity at this priority level
        result == '<@U111>'
    }

    def 'resolves multiple mentions in a single string'() {
        given:
        def resolver = resolverWith(users: [
            user('U111', 'alice', 'Alice', 'Alice Smith'),
            user('U222', 'bob', 'Bob', 'Bob Jones')
        ], groups: [])

        when:
        def result = resolver.resolveText('<@alice> and <@bob> — please review')

        then:
        result == '<@U111> and <@U222> — please review'
    }

    def 'leaves mention unchanged and warns when user not found'() {
        given:
        def resolver = resolverWith(users: [
            user('U111', 'alice', 'Alice', 'Alice Smith')
        ], groups: [])

        when:
        def result = resolver.resolveText('Hi <@unknown>!')

        then:
        result == 'Hi <@unknown>!'
    }

    def 'leaves mention unchanged and warns when name is ambiguous'() {
        given:
        // Two users with the same display_name — should not resolve
        def resolver = resolverWith(users: [
            user('U111', 'jane.one', 'Jane', 'Jane One'),
            user('U222', 'jane.two', 'Jane', 'Jane Two')
        ], groups: [])

        when:
        def result = resolver.resolveText('Hi <@Jane>!')

        then:
        result == 'Hi <@Jane>!'
    }

    def 'excludes deleted users from resolution'() {
        given:
        def resolver = resolverWith(users: [
            deletedUser('U111', 'jane', 'Jane', 'Jane Doe'),
            user('U222', 'jane', 'Jane Active', 'Jane Active')
        ], groups: [])

        when:
        def result = resolver.resolveText('<@jane>')

        then:
        // Deleted user is excluded; only U222 has username 'jane'... wait no
        // U222 has username 'jane' but is not deleted — should match
        result == '<@U222>'
    }

    // =========================================================================
    // Works inside JSON payloads
    // =========================================================================

    def 'resolves mention inside a JSON Block Kit payload'() {
        given:
        def resolver = resolverWith(users: [
            user('U1234567890', 'jane.doe', 'Jane', 'Jane Doe')
        ], groups: [])

        def json = '{"blocks":[{"type":"section","text":{"type":"mrkdwn","text":"Hi <@Jane>!"}}]}'

        when:
        def result = resolver.resolveText(json)

        then:
        result == '{"blocks":[{"type":"section","text":{"type":"mrkdwn","text":"Hi <@U1234567890>!"}}]}'
    }

    // =========================================================================
    // Subteam / usergroup mention resolution
    // =========================================================================

    def 'leaves already-resolved subteam IDs unchanged'() {
        given:
        def resolver = resolverWith(users: [], groups: [
            group('S1234567890', 'Data Team', 'data-team')
        ])

        when:
        def result = resolver.resolveText('<!subteam^S1234567890>')

        then:
        result == '<!subteam^S1234567890>'
    }

    def 'resolves subteam mention by handle'() {
        given:
        def resolver = resolverWith(users: [], groups: [
            group('S1234567890', 'Data Team', 'data-team')
        ])

        when:
        def result = resolver.resolveText('Attention <!subteam^data-team>!')

        then:
        result == 'Attention <!subteam^S1234567890>!'
    }

    def 'resolves subteam mention by name'() {
        given:
        def resolver = resolverWith(users: [], groups: [
            group('S1234567890', 'Data Team', 'data-team')
        ])

        when:
        def result = resolver.resolveText('Attention <!subteam^Data Team>!')

        then:
        result == 'Attention <!subteam^S1234567890>!'
    }

    def 'leaves subteam mention unchanged and warns when group not found'() {
        given:
        def resolver = resolverWith(users: [], groups: [])

        when:
        def result = resolver.resolveText('Attention <!subteam^unknown-team>!')

        then:
        result == 'Attention <!subteam^unknown-team>!'
    }

    // =========================================================================
    // Fetch-failure handling
    // =========================================================================

    def 'returns original text gracefully when user fetch fails'() {
        given:
        def resolver = new SlackUserResolver('xoxb-test') {
            @Override
            protected List<Map> fetchUsers() { return null }

            @Override
            protected List<Map> fetchUsergroups() { return [] }
        }

        when:
        def result = resolver.resolveText('Hi <@Jane>!')

        then:
        result == 'Hi <@Jane>!'
        noExceptionThrown()
    }

    def 'does not retry fetchUsers after first failure'() {
        given:
        int fetchCount = 0
        def resolver = new SlackUserResolver('xoxb-test') {
            @Override
            protected List<Map> fetchUsers() {
                fetchCount++
                return null
            }

            @Override
            protected List<Map> fetchUsergroups() { return [] }
        }

        when:
        resolver.resolveText('Hi <@Jane>!')
        resolver.resolveText('Hi <@Bob>!')

        then:
        fetchCount == 1
    }

    def 'caches user list and fetches only once for multiple calls'() {
        given:
        int fetchCount = 0
        def resolver = new SlackUserResolver('xoxb-test') {
            @Override
            protected List<Map> fetchUsers() {
                fetchCount++
                return [user('U111', 'alice', 'Alice', 'Alice Smith')]
            }

            @Override
            protected List<Map> fetchUsergroups() { return [] }
        }

        when:
        resolver.resolveText('<@alice>')
        resolver.resolveText('<@alice>')
        resolver.resolveText('<@alice>')

        then:
        fetchCount == 1
    }

    def 'handles gracefully when API is unreachable (no network)'() {
        given:
        // Real resolver — will fail trying to reach Slack API
        def resolver = new SlackUserResolver('xoxb-fake-token')

        when:
        def result = resolver.resolveText('Hi <@Jane>!')

        then:
        noExceptionThrown()
        result == 'Hi <@Jane>!'
    }

    // =========================================================================
    // Test helpers
    // =========================================================================

    /** Build a resolver that returns fixed user/group lists without hitting the network. */
    private SlackUserResolver resolverWith(Map opts) {
        List<Map> users  = (opts.users  ?: []) as List<Map>
        List<Map> groups = (opts.groups ?: []) as List<Map>

        return new SlackUserResolver('xoxb-test') {
            @Override
            protected List<Map> fetchUsers() { return users }

            @Override
            protected List<Map> fetchUsergroups() { return groups }
        }
    }

    private static Map user(String id, String name, String displayName, String realName) {
        return [
            id     : id,
            name   : name,
            deleted: false,
            profile: [
                display_name: displayName,
                real_name   : realName
            ]
        ]
    }

    private static Map deletedUser(String id, String name, String displayName, String realName) {
        Map u = user(id, name, displayName, realName)
        u.deleted = true
        return u
    }

    private static Map group(String id, String name, String handle) {
        return [id: id, name: name, handle: handle]
    }
}

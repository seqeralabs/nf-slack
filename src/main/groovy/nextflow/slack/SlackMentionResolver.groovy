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

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Resolves Slack @mentions by display name to internal user/subteam IDs.
 *
 * Only used with bot tokens (requires users:read and usergroups:read scopes).
 * User and usergroup lists are cached for the lifetime of the resolver instance.
 *
 * @author Adam Talbot <adam.talbot@seqera.io>
 */
@Slf4j
@CompileStatic
class SlackMentionResolver {

    private static final String USERS_LIST_URL = 'https://slack.com/api/users.list'
    private static final String USERGROUPS_LIST_URL = 'https://slack.com/api/usergroups.list'

    /** Matches unresolved user mentions; skips Slack user IDs (U...) and workspace IDs (W...). */
    private static final java.util.regex.Pattern USER_MENTION_PATTERN =
        ~/<@(?!U[A-Z0-9]+(?:\|[^>]+)?>)(?!W[A-Z0-9]+(?:\|[^>]+)?>)([^>]+)>/

    /** Matches unresolved subteam mentions; skips Slack subteam IDs (S...). */
    private static final java.util.regex.Pattern SUBTEAM_MENTION_PATTERN =
        ~/<!subteam\^(?!S[A-Z0-9]+(?:\|[^>]+)?>)([^>|]+)(?:\|[^>]+)?>/

    private final String botToken
    private List<Map> cachedUsers
    private List<Map> cachedUsergroups
    private final Set<String> loggedWarnings = Collections.synchronizedSet(new HashSet<String>())

    SlackMentionResolver(String botToken) {
        this.botToken = botToken
    }

    String resolveInJson(String jsonPayload) {
        if (!jsonPayload || !hasResolvableMentions(jsonPayload)) {
            return jsonPayload
        }

        try {
            def payload = new JsonSlurper().parseText(jsonPayload)
            resolveValue(payload)
            return new JsonBuilder(payload).toString()
        }
        catch (Exception e) {
            log.debug "Slack plugin: Could not resolve mentions in message payload: ${e.message}"
            return jsonPayload
        }
    }

    String resolveInText(String text) {
        if (!text) {
            return text
        }
        return resolveSubteamMentions(resolveUserMentions(text))
    }

    static boolean hasResolvableMentions(String text) {
        if (!text) {
            return false
        }
        return USER_MENTION_PATTERN.matcher(text).find() ||
               SUBTEAM_MENTION_PATTERN.matcher(text).find()
    }

    private void resolveValue(Object value) {
        if (value instanceof Map) {
            def map = value as Map
            for (Object key : new ArrayList(map.keySet())) {
                def child = map[key]
                if (child instanceof String) {
                    map[key] = resolveInText(child as String)
                } else {
                    resolveValue(child)
                }
            }
        }
        else if (value instanceof List) {
            def list = value as List
            for (int i = 0; i < list.size(); i++) {
                def child = list[i]
                if (child instanceof String) {
                    list[i] = resolveInText(child as String)
                } else {
                    resolveValue(child)
                }
            }
        }
    }

    private String resolveUserMentions(String text) {
        def matcher = USER_MENTION_PATTERN.matcher(text)
        if (!matcher.find()) {
            return text
        }

        ensureUsersLoaded()
        def buffer = new StringBuffer()
        matcher.reset()
        while (matcher.find()) {
            def displayName = matcher.group(1)
            def userId = resolveUserId(displayName)
            String replacement = userId ? "<@${userId}>" : matcher.group(0)
            matcher.appendReplacement(buffer, java.util.regex.Matcher.quoteReplacement(replacement))
        }
        matcher.appendTail(buffer)
        return buffer.toString()
    }

    private String resolveSubteamMentions(String text) {
        def matcher = SUBTEAM_MENTION_PATTERN.matcher(text)
        if (!matcher.find()) {
            return text
        }

        ensureUsergroupsLoaded()
        def buffer = new StringBuffer()
        matcher.reset()
        while (matcher.find()) {
            def teamName = matcher.group(1)
            def subteamId = resolveSubteamId(teamName)
            String replacement = subteamId ? "<!subteam^${subteamId}>" : matcher.group(0)
            matcher.appendReplacement(buffer, java.util.regex.Matcher.quoteReplacement(replacement))
        }
        matcher.appendTail(buffer)
        return buffer.toString()
    }

    private String resolveUserId(String query) {
        def normalizedQuery = normalize(query)
        if (!normalizedQuery) {
            return null
        }

        for (String field : ['name', 'display_name', 'real_name']) {
            def matches = findUsersMatchingField(field, normalizedQuery)
            if (matches.size() == 1) {
                return matches[0]
            }
            if (matches.size() > 1) {
                warnOnce("Slack plugin: Ambiguous @mention '<@${query}>': multiple users match ${describeMatches(matches)} — leaving unresolved")
                return null
            }
        }

        warnOnce("Slack plugin: Could not resolve @mention '<@${query}>' — no matching Slack user found")
        return null
    }

    private String resolveSubteamId(String query) {
        def normalizedQuery = normalize(query)
        if (!normalizedQuery) {
            return null
        }

        for (String field : ['name', 'handle']) {
            def matches = findUsergroupsMatchingField(field, normalizedQuery)
            if (matches.size() == 1) {
                return matches[0]
            }
            if (matches.size() > 1) {
                warnOnce("Slack plugin: Ambiguous subteam mention '<!subteam^${query}>': multiple groups match ${describeMatches(matches)} — leaving unresolved")
                return null
            }
        }

        warnOnce("Slack plugin: Could not resolve subteam mention '<!subteam^${query}>' — no matching Slack user group found")
        return null
    }

    private List<String> findUsersMatchingField(String field, String normalizedQuery) {
        def matches = [] as List<String>
        for (Map user : cachedUsers) {
            def value = getUserField(user, field)
            if (value && normalize(value) == normalizedQuery) {
                matches << (user.id as String)
            }
        }
        return matches
    }

    private List<String> findUsergroupsMatchingField(String field, String normalizedQuery) {
        def matches = [] as List<String>
        for (Map group : cachedUsergroups) {
            def raw = group[field] as String
            def value = field == 'handle' ? stripAtPrefix(raw) : raw
            if (value && normalize(value) == normalizedQuery) {
                matches << (group.id as String)
            }
        }
        return matches
    }

    private static String getUserField(Map user, String field) {
        if (field == 'name') {
            return user.name as String
        }
        def profile = user.profile as Map
        if (!profile) {
            return null
        }
        return profile[field] as String
    }

    private static String normalize(String value) {
        return value?.trim()?.toLowerCase()
    }

    private static String stripAtPrefix(String value) {
        return value?.startsWith('@') ? value.substring(1) : value
    }

    private static String describeMatches(List<String> ids) {
        return ids.collect { "'${it}'" }.join(', ')
    }

    private void warnOnce(String message) {
        if (loggedWarnings.add(message)) {
            log.warn message
        }
    }

    private void ensureUsersLoaded() {
        if (cachedUsers != null) {
            return
        }
        cachedUsers = fetchAllUsers()
    }

    private void ensureUsergroupsLoaded() {
        if (cachedUsergroups != null) {
            return
        }
        cachedUsergroups = fetchUsergroups()
    }

    private List<Map> fetchAllUsers() {
        def users = [] as List<Map>
        String cursor = null

        while (true) {
            Map page = fetchUsersPage(cursor)
            if (!page) {
                if (users.isEmpty()) {
                    warnOnce('Slack plugin: Could not load Slack users for @mention resolution (requires users:read scope)')
                }
                break
            }

            def members = page.members as List<Map> ?: []
            for (Map member : members) {
                if (member.deleted) {
                    continue
                }
                if (member.is_bot) {
                    continue
                }
                users << member
            }

            def metadata = page.response_metadata as Map
            def nextCursor = metadata?.get('next_cursor') as String
            if (!nextCursor) {
                break
            }
            cursor = nextCursor
        }

        return users
    }

    protected Map fetchUsersPage(String cursor) {
        HttpURLConnection connection = null
        try {
            def query = cursor ? "?cursor=${URLEncoder.encode(cursor, 'UTF-8')}&limit=200" : '?limit=200'
            def url = new URL("${USERS_LIST_URL}${query}")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = 'GET'
            connection.setRequestProperty('Authorization', "Bearer ${botToken}")

            if (connection.responseCode != 200) {
                return null
            }

            def response = new JsonSlurper().parseText(connection.inputStream.text) as Map
            return response.ok ? response : null
        }
        catch (Exception e) {
            log.debug "Slack plugin: Error fetching users.list: ${e.message}"
            return null
        }
        finally {
            connection?.disconnect()
        }
    }

    protected List<Map> fetchUsergroups() {
        HttpURLConnection connection = null
        try {
            def url = new URL(USERGROUPS_LIST_URL)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = 'GET'
            connection.setRequestProperty('Authorization', "Bearer ${botToken}")

            if (connection.responseCode != 200) {
                warnOnce('Slack plugin: Could not load Slack user groups for subteam mention resolution (requires usergroups:read scope)')
                return [] as List<Map>
            }

            def response = new JsonSlurper().parseText(connection.inputStream.text) as Map
            if (!response.ok) {
                warnOnce("Slack plugin: Could not load Slack user groups: ${response.error}")
                return [] as List<Map>
            }

            return (response.usergroups as List<Map>) ?: [] as List<Map>
        }
        catch (Exception e) {
            log.debug "Slack plugin: Error fetching usergroups.list: ${e.message}"
            warnOnce('Slack plugin: Could not load Slack user groups for subteam mention resolution')
            return [] as List<Map>
        }
        finally {
            connection?.disconnect()
        }
    }
}

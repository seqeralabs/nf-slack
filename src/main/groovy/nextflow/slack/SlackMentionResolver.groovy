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

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Resolves Slack @mentions by display name to internal user/subteam IDs.
 *
 * Only used with bot tokens (requires users:read and usergroups:read scopes).
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
    private List<Map> users
    private List<Map> usergroups

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

    private String resolveUserMentions(String text) {
        return replaceMentions(text, USER_MENTION_PATTERN) { String name -> resolveUserId(name) } { String id -> "<@${id}>" }
    }

    private String resolveSubteamMentions(String text) {
        return replaceMentions(text, SUBTEAM_MENTION_PATTERN) { String name -> resolveSubteamId(name) } { String id -> "<!subteam^${id}>" }
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

    private String replaceMentions(
        String text,
        java.util.regex.Pattern pattern,
        Closure<String> resolveId,
        Closure<String> formatMention
    ) {
        def matcher = pattern.matcher(text)
        if (!matcher.find()) {
            return text
        }

        def buffer = new StringBuffer()
        matcher.reset()
        while (matcher.find()) {
            def name = matcher.group(1)
            def id = resolveId.call(name)
            String replacement = id ? formatMention.call(id) : matcher.group(0)
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
                log.warn "Slack plugin: Ambiguous @mention '<@${query}>': multiple users match — leaving unresolved"
                return null
            }
        }

        log.warn "Slack plugin: Could not resolve @mention '<@${query}>' — no matching Slack user found"
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
                log.warn "Slack plugin: Ambiguous subteam mention '<!subteam^${query}>' — leaving unresolved"
                return null
            }
        }

        log.warn "Slack plugin: Could not resolve subteam mention '<!subteam^${query}>' — no matching Slack user group found"
        return null
    }

    private List<String> findUsersMatchingField(String field, String normalizedQuery) {
        def matches = [] as List<String>
        for (Map user : getUsers()) {
            def value = getUserField(user, field)
            if (value && normalize(value) == normalizedQuery) {
                matches << (user.id as String)
            }
        }
        return matches
    }

    private List<String> findUsergroupsMatchingField(String field, String normalizedQuery) {
        def matches = [] as List<String>
        for (Map group : getUsergroups()) {
            def raw = group[field] as String
            def value = field == 'handle' && raw?.startsWith('@') ? raw.substring(1) : raw
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
        return profile ? profile[field] as String : null
    }

    private static String normalize(String value) {
        return value?.trim()?.toLowerCase()
    }

    private List<Map> getUsers() {
        if (users == null) {
            users = fetchAllUsers()
        }
        return users
    }

    private List<Map> getUsergroups() {
        if (usergroups == null) {
            usergroups = fetchUsergroups()
        }
        return usergroups
    }

    private List<Map> fetchAllUsers() {
        def result = [] as List<Map>
        String cursor = null

        while (true) {
            def query = cursor ? "?cursor=${URLEncoder.encode(cursor, 'UTF-8')}&limit=200" : '?limit=200'
            def response = apiGet("${USERS_LIST_URL}${query}")
            if (!response) {
                if (result.isEmpty()) {
                    log.warn 'Slack plugin: Could not load Slack users for @mention resolution (requires users:read scope)'
                }
                break
            }

            for (Map member : (response.members as List<Map> ?: [])) {
                if (!member.deleted && !member.is_bot) {
                    result << member
                }
            }

            def nextCursor = (response.response_metadata as Map)?.get('next_cursor') as String
            if (!nextCursor) {
                break
            }
            cursor = nextCursor
        }

        return result
    }

    protected List<Map> fetchUsergroups() {
        def response = apiGet(USERGROUPS_LIST_URL)
        if (!response) {
            log.warn 'Slack plugin: Could not load Slack user groups for subteam mention resolution (requires usergroups:read scope)'
            return [] as List<Map>
        }
        return (response.usergroups as List<Map>) ?: [] as List<Map>
    }

    protected Map apiGet(String apiUrl) {
        HttpURLConnection connection = null
        try {
            connection = new URL(apiUrl).openConnection() as HttpURLConnection
            connection.requestMethod = 'GET'
            connection.setRequestProperty('Authorization', "Bearer ${botToken}")

            if (connection.responseCode != 200) {
                return null
            }

            def response = new JsonSlurper().parseText(connection.inputStream.text) as Map
            return response.ok ? response : null
        }
        catch (Exception e) {
            log.debug "Slack plugin: Slack API GET ${apiUrl} failed: ${e.message}"
            return null
        }
        finally {
            connection?.disconnect()
        }
    }
}

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
 * Resolves {@code <@DisplayName>} to {@code <@U123>} via users.list (bot token, users:read scope).
 *
 * @author Adam Talbot <adam.talbot@seqera.io>
 */
@Slf4j
@CompileStatic
class SlackMentionResolver {

    private static final String USERS_LIST_URL = 'https://slack.com/api/users.list'
    private static final java.util.regex.Pattern MENTION = ~/<@([^>]+)>/
    /** Slack user IDs are uppercase alphanumeric after U; display names may contain lowercase. */
    private static final java.util.regex.Pattern SLACK_USER_ID = ~/^[UW][A-Z0-9]{4,}(\|.+)?$/

    private final String botToken
    private List<Map> users

    SlackMentionResolver(String botToken) {
        this.botToken = botToken
    }

    static boolean hasResolvableMentions(String text) {
        if (!text) {
            return false
        }
        def matcher = MENTION.matcher(text)
        while (matcher.find()) {
            if (!isSlackUserId(matcher.group(1))) {
                return true
            }
        }
        return false
    }

    String resolveInJson(String jsonPayload) {
        if (!jsonPayload || !hasResolvableMentions(jsonPayload)) {
            return jsonPayload
        }
        try {
            def payload = new JsonSlurper().parseText(jsonPayload)
            resolveStrings(payload)
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

        def matcher = MENTION.matcher(text)
        if (!matcher.find()) {
            return text
        }

        matcher.reset()
        def buffer = new StringBuffer()
        while (matcher.find()) {
            def name = matcher.group(1)
            def replacement = isSlackUserId(name) ? matcher.group(0) : mentionFor(name)
            matcher.appendReplacement(buffer, java.util.regex.Matcher.quoteReplacement(replacement))
        }
        matcher.appendTail(buffer)
        return buffer.toString()
    }

    private static boolean isSlackUserId(String mention) {
        return mention ==~ SLACK_USER_ID
    }

    private String mentionFor(String query) {
        def normalized = query.trim().toLowerCase()
        if (!normalized) {
            return "<@${query}>"
        }

        for (String field : ['name', 'display_name', 'real_name']) {
            def matches = [] as List<String>
            for (Map user : loadUsers()) {
                def value = userField(user, field)
                if (value && value.trim().toLowerCase() == normalized) {
                    matches << (user.id as String)
                }
            }
            if (matches.size() == 1) {
                return "<@${matches[0]}>"
            }
            if (matches.size() > 1) {
                log.warn "Slack plugin: Ambiguous @mention '<@${query}>' — leaving unresolved"
                return "<@${query}>"
            }
        }

        log.warn "Slack plugin: Could not resolve @mention '<@${query}>' — no matching Slack user found"
        return "<@${query}>"
    }

    private static String userField(Map user, String field) {
        if (field == 'name') {
            return user.name as String
        }
        def profile = user.profile as Map
        return profile ? profile[field] as String : null
    }

    private void resolveStrings(Object value) {
        if (value instanceof Map) {
            def map = value as Map
            for (Object key : new ArrayList(map.keySet())) {
                def child = map[key]
                if (child instanceof String) {
                    map[key] = resolveInText(child as String)
                } else {
                    resolveStrings(child)
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
                    resolveStrings(child)
                }
            }
        }
    }

    private List<Map> loadUsers() {
        if (users == null) {
            users = fetchUsers()
        }
        return users
    }

    protected List<Map> fetchUsers() {
        def result = [] as List<Map>
        String cursor = null

        while (true) {
            def query = cursor ? "?cursor=${URLEncoder.encode(cursor, 'UTF-8')}&limit=200" : '?limit=200'
            def response = slackGet("${USERS_LIST_URL}${query}")
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

            cursor = (response.response_metadata as Map)?.get('next_cursor') as String
            if (!cursor) {
                break
            }
        }

        return result
    }

    private Map slackGet(String apiUrl) {
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

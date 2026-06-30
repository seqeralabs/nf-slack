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

    private static final String USERS_READ_SCOPE_HELP =
        'Display-name @mentions require the users:read bot scope. ' +
        'In your Slack app (https://api.slack.com/apps): OAuth & Permissions → Bot Token Scopes → add users:read → ' +
        'Reinstall to Workspace → update SLACK_BOT_TOKEN with the new token. ' +
        'See docs/getting-started/setup.md'

    private final String botToken
    private List<Map> users
    protected boolean usersListUnavailable
    private boolean usersListErrorLogged

    SlackMentionResolver(String botToken) {
        this.botToken = botToken?.trim()
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

        def loadedUsers = loadUsers()
        if (loadedUsers.isEmpty()) {
            if (!usersListUnavailable) {
                log.warn "Slack plugin: Could not resolve @mention '<@${query}>' — no matching Slack user found in this workspace"
            }
            return "<@${query}>"
        }

        for (String field : ['name', 'display_name', 'real_name']) {
            def matches = [] as List<String>
            for (Map user : loadedUsers) {
                def value = userField(user, field)
                if (value && value.trim().toLowerCase() == normalized) {
                    matches << (user.get('id') as String)
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
            return user.get('name') as String
        }
        def profile = user.get('profile') as Map
        return profile ? profile.get(field) as String : null
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
        if (usersListUnavailable) {
            return users ?: ([] as List<Map>)
        }
        def result = [] as List<Map>
        String cursor = null

        while (true) {
            def params = [limit: '200'] as Map<String, String>
            if (cursor) {
                params.cursor = cursor
            }

            def response = callUsersList(params)
            if (!response) {
                if (result.isEmpty()) {
                    usersListUnavailable = true
                    users = result
                }
                break
            }

            for (Map member : (response.members as List<Map> ?: [])) {
                if (!isTruthy(member.get('deleted')) && !isTruthy(member.get('is_bot'))) {
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

    private static boolean isTruthy(Object value) {
        return value == Boolean.TRUE || (value instanceof String && (value as String).equalsIgnoreCase('true'))
    }

    private Map callUsersList(Map<String, String> params) {
        if (!botToken) {
            if (!usersListErrorLogged) {
                log.warn "Slack plugin: Cannot resolve @mentions — bot token is not configured. Set slack.bot.token or SLACK_BOT_TOKEN."
                usersListErrorLogged = true
            }
            return null
        }

        HttpURLConnection connection = null
        try {
            connection = new URL(USERS_LIST_URL).openConnection() as HttpURLConnection
            connection.requestMethod = 'POST'
            connection.doOutput = true
            connection.setRequestProperty('Authorization', "Bearer ${botToken}")
            connection.setRequestProperty('Content-Type', 'application/x-www-form-urlencoded; charset=utf-8')

            def body = params.collect { key, value ->
                "${URLEncoder.encode(key, 'UTF-8')}=${URLEncoder.encode(value, 'UTF-8')}"
            }.join('&')

            connection.outputStream.withCloseable { out ->
                out.write(body.getBytes('UTF-8'))
            }

            def responseStream = connection.responseCode == 200 ? connection.inputStream : connection.errorStream
            if (!responseStream) {
                logUsersListError("http_${connection.responseCode}")
                return null
            }

            def response = new JsonSlurper().parseText(responseStream.getText('UTF-8')) as Map
            if (!response.ok) {
                logUsersListError(response.error as String)
                return null
            }

            return response
        }
        catch (Exception e) {
            logUsersListError("request_failed: ${e.message}")
            return null
        }
        finally {
            connection?.disconnect()
        }
    }

    private void logUsersListError(String error) {
        if (usersListErrorLogged) {
            return
        }
        usersListErrorLogged = true

        if (error == 'missing_scope') {
            log.warn "Slack plugin: Cannot resolve @mentions — bot token is missing the users:read permission. ${USERS_READ_SCOPE_HELP}"
        }
        else if (error == 'not_authed' || error == 'invalid_auth') {
            log.warn "Slack plugin: Cannot resolve @mentions — bot token is invalid (${error}). Check SLACK_BOT_TOKEN and reinstall the app if you recently changed scopes."
        }
        else if (error == 'request_failed' || error?.startsWith('http_') || error?.startsWith('request_failed:')) {
            log.warn "Slack plugin: Cannot load workspace users for @mention resolution (${error}). If you use display-name mentions, ensure the bot has users:read. ${USERS_READ_SCOPE_HELP}"
        }
        else {
            log.warn "Slack plugin: users.list failed (${error ?: 'unknown error'}) — @mentions will not be resolved. ${USERS_READ_SCOPE_HELP}"
        }
    }
}

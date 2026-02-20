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
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Resolves Slack display name @mentions to internal user IDs before sending messages.
 *
 * When users write {@code <@Jane>} in message text, Slack renders it literally rather
 * than as a clickable mention that notifies the person. This class looks up the user's
 * internal ID (e.g. {@code U1234567890}) and rewrites the mention so Slack can resolve it.
 *
 * Matching priority (first match wins per priority level):
 * <ol>
 *   <li>Username ({@code name} field)</li>
 *   <li>Display name ({@code profile.display_name})</li>
 *   <li>Real name ({@code profile.real_name})</li>
 * </ol>
 *
 * If a name matches multiple users, a warning is logged and the mention is left unresolved
 * rather than notifying the wrong person.
 *
 * Also resolves {@code <!subteam^TeamName>} group mentions to {@code <!subteam^S1234567890>}
 * using the {@code usergroups.list} API.
 *
 * The user list is fetched once and cached for the lifetime of the pipeline run.
 *
 * <b>Requirements</b>: bot token with {@code users:read} scope (and optionally
 * {@code usergroups:read} for team mention resolution). Has no effect when using
 * incoming webhooks.
 *
 * @author Claude (drafted for seqeralabs/nf-slack#46)
 */
@Slf4j
@CompileStatic
class SlackUserResolver {

    private static final String USERS_LIST_URL = "https://slack.com/api/users.list"
    private static final String USERGROUPS_LIST_URL = "https://slack.com/api/usergroups.list"

    // Matches any <@something> — we decide inside whether it's already a Slack ID
    private static final Pattern USER_MENTION_PATTERN = Pattern.compile('<@([^>]+)>')

    // Matches <!subteam^something> or <!subteam^something|label>
    private static final Pattern SUBTEAM_MENTION_PATTERN =
            Pattern.compile('<!subteam\\^([^|>]+)(?:\\|[^>]*)?>')

    private final String botToken

    // Lazily-loaded caches; null means not yet fetched
    private volatile List<Map> cachedUsers = null
    private volatile List<Map> cachedUsergroups = null
    private volatile boolean usersFetchFailed = false
    private volatile boolean usergroupsFetchFailed = false

    private final Object userCacheLock = new Object()
    private final Object groupCacheLock = new Object()

    SlackUserResolver(String botToken) {
        this.botToken = botToken
    }

    /**
     * Scan {@code text} for display-name @mentions and subteam mentions and replace them
     * with the corresponding Slack IDs.  Already-resolved Slack IDs (e.g.
     * {@code <@U1234567890>}) are left unchanged.
     *
     * @param text Any string — typically the full JSON payload about to be posted to Slack
     * @return The text with resolvable display-name mentions replaced by Slack IDs
     */
    String resolveText(String text) {
        if (!text) return text
        def resolved = resolveUserMentions(text)
        resolved = resolveSubteamMentions(resolved)
        return resolved
    }

    // -------------------------------------------------------------------------
    // User mention resolution
    // -------------------------------------------------------------------------

    private String resolveUserMentions(String text) {
        Matcher matcher = USER_MENTION_PATTERN.matcher(text)
        if (!matcher.find()) return text

        matcher.reset()
        StringBuffer sb = new StringBuffer()
        while (matcher.find()) {
            String name = matcher.group(1)
            if (isSlackUserId(name)) {
                // Already a valid Slack user ID — leave it unchanged
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)))
            } else {
                String userId = resolveUserId(name)
                if (userId) {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement("<@${userId}>"))
                } else {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)))
                }
            }
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    /**
     * Resolve a display name / username to a Slack user ID.
     *
     * @param name The name to look up
     * @return The Slack user ID (e.g. {@code U1234567890}), or {@code null} if not found
     *         or if the name is ambiguous
     */
    String resolveUserId(String name) {
        List<Map> users = getCachedUsers()
        if (users == null) return null

        List<Map> matches = findMatchingUsers(users, name)

        if (matches.isEmpty()) {
            log.warn "Slack plugin: Could not resolve user mention '@${name}' — no matching user found"
            return null
        }
        if (matches.size() > 1) {
            String userList = matches.collect { u -> "${(u as Map).get('name')} (${(u as Map).get('id')})" }.join(', ')
            log.warn "Slack plugin: Ambiguous user mention '@${name}' matches multiple users: ${userList}. Leaving unresolved."
            return null
        }

        String userId = (matches[0] as Map).get('id') as String
        log.debug "Slack plugin: Resolved '@${name}' to user ID ${userId}"
        return userId
    }

    /**
     * Find users matching {@code name} in priority order:
     * username → display_name → real_name.
     * Deleted users are excluded from all matches.
     */
    private List<Map> findMatchingUsers(List<Map> users, String name) {
        // Priority 1: exact username match
        List<Map> byUsername = users.findAll { user ->
            Map u = user as Map
            !u.get('deleted') && u.get('name') == name
        } as List<Map>
        if (!byUsername.isEmpty()) return byUsername

        // Priority 2: display_name match
        List<Map> byDisplayName = users.findAll { user ->
            Map u = user as Map
            Map profile = u.get('profile') as Map
            !u.get('deleted') && profile != null && profile.get('display_name') == name
        } as List<Map>
        if (!byDisplayName.isEmpty()) return byDisplayName

        // Priority 3: real_name match
        return users.findAll { user ->
            Map u = user as Map
            Map profile = u.get('profile') as Map
            !u.get('deleted') && profile != null && profile.get('real_name') == name
        } as List<Map>
    }

    // -------------------------------------------------------------------------
    // Subteam / usergroup mention resolution
    // -------------------------------------------------------------------------

    private String resolveSubteamMentions(String text) {
        Matcher matcher = SUBTEAM_MENTION_PATTERN.matcher(text)
        if (!matcher.find()) return text

        matcher.reset()
        StringBuffer sb = new StringBuffer()
        while (matcher.find()) {
            String name = matcher.group(1)
            if (isSlackSubteamId(name)) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)))
            } else {
                String groupId = resolveUsergroupId(name)
                if (groupId) {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement("<!subteam^${groupId}>"))
                } else {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)))
                }
            }
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    /**
     * Resolve a usergroup name or handle to a Slack usergroup ID.
     *
     * @param name The group name or handle to look up
     * @return The Slack usergroup ID (e.g. {@code S1234567890}), or {@code null} if not
     *         found or ambiguous
     */
    String resolveUsergroupId(String name) {
        List<Map> groups = getCachedUsergroups()
        if (groups == null) return null

        List<Map> matches = groups.findAll { group ->
            Map g = group as Map
            g.get('name') == name || g.get('handle') == name
        } as List<Map>

        if (matches.isEmpty()) {
            log.warn "Slack plugin: Could not resolve subteam mention '^${name}' — no matching group found"
            return null
        }
        if (matches.size() > 1) {
            String groupList = matches.collect { g -> "${(g as Map).get('handle')} (${(g as Map).get('id')})" }.join(', ')
            log.warn "Slack plugin: Ambiguous subteam mention '^${name}' matches multiple groups: ${groupList}. Leaving unresolved."
            return null
        }

        String groupId = (matches[0] as Map).get('id') as String
        log.debug "Slack plugin: Resolved subteam '^${name}' to group ID ${groupId}"
        return groupId
    }

    // -------------------------------------------------------------------------
    // ID format checks
    // -------------------------------------------------------------------------

    /**
     * Return {@code true} if {@code value} looks like a Slack user ID ({@code U…} or
     * {@code W…} followed by uppercase alphanumerics).
     */
    static boolean isSlackUserId(String value) {
        if (!value || value.length() < 2) return false
        char first = value.charAt(0)
        if (first != ('U' as char) && first != ('W' as char)) return false
        return value.matches('[UW][A-Z0-9]+')
    }

    /**
     * Return {@code true} if {@code value} looks like a Slack usergroup ID ({@code S…}
     * followed by uppercase alphanumerics).
     */
    static boolean isSlackSubteamId(String value) {
        if (!value || value.length() < 2) return false
        return value.matches('S[A-Z0-9]+')
    }

    // -------------------------------------------------------------------------
    // Cache helpers (double-checked locking)
    // -------------------------------------------------------------------------

    private List<Map> getCachedUsers() {
        if (cachedUsers != null) return cachedUsers
        synchronized (userCacheLock) {
            if (cachedUsers != null) return cachedUsers
            if (usersFetchFailed) return null
            List<Map> users = fetchUsers()
            if (users != null) {
                cachedUsers = users
            } else {
                usersFetchFailed = true
            }
            return cachedUsers
        }
    }

    private List<Map> getCachedUsergroups() {
        if (cachedUsergroups != null) return cachedUsergroups
        synchronized (groupCacheLock) {
            if (cachedUsergroups != null) return cachedUsergroups
            if (usergroupsFetchFailed) return null
            List<Map> groups = fetchUsergroups()
            if (groups != null) {
                cachedUsergroups = groups
            } else {
                usergroupsFetchFailed = true
            }
            return cachedUsergroups
        }
    }

    // -------------------------------------------------------------------------
    // API calls (protected so tests can override via anonymous subclass)
    // -------------------------------------------------------------------------

    /**
     * Fetch the full user list from {@code users.list}, following pagination cursors.
     *
     * @return List of user maps, or {@code null} on failure
     */
    protected List<Map> fetchUsers() {
        List<Map> allUsers = [] as List<Map>
        String cursor = null

        try {
            while (true) {
                String urlStr = "${USERS_LIST_URL}?limit=200"
                if (cursor) urlStr += "&cursor=${URLEncoder.encode(cursor, 'UTF-8')}"

                HttpURLConnection connection = null
                try {
                    URL url = new URL(urlStr)
                    connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = 'GET'
                    connection.setRequestProperty('Authorization', "Bearer ${botToken}")

                    int responseCode = connection.responseCode
                    if (responseCode != 200) {
                        log.warn "Slack plugin: Failed to fetch user list for mention resolution — HTTP ${responseCode}"
                        return null
                    }

                    String responseText = connection.inputStream.text
                    Map response = new JsonSlurper().parseText(responseText) as Map

                    if (!response.get('ok')) {
                        String error = response.get('error') as String
                        if (error == 'missing_scope') {
                            log.warn "Slack plugin: Cannot resolve user mentions — bot token is missing the 'users:read' scope"
                        } else {
                            log.warn "Slack plugin: Failed to fetch user list for mention resolution — API error: ${error}"
                        }
                        return null
                    }

                    List<Map> members = response.get('members') as List<Map>
                    if (members) allUsers.addAll(members)

                    Map metadata = response.get('response_metadata') as Map
                    cursor = metadata != null ? (metadata.get('next_cursor') as String) : null
                    if (!cursor) break

                } finally {
                    connection?.disconnect()
                }
            }

            log.debug "Slack plugin: Fetched ${allUsers.size()} users for mention resolution"
            return allUsers

        } catch (Exception e) {
            log.warn "Slack plugin: Error fetching user list for mention resolution: ${e.message}"
            return null
        }
    }

    /**
     * Fetch the full usergroup list from {@code usergroups.list}.
     *
     * @return List of usergroup maps, or {@code null} on failure
     */
    protected List<Map> fetchUsergroups() {
        HttpURLConnection connection = null
        try {
            URL url = new URL(USERGROUPS_LIST_URL)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = 'GET'
            connection.setRequestProperty('Authorization', "Bearer ${botToken}")

            int responseCode = connection.responseCode
            if (responseCode != 200) {
                log.warn "Slack plugin: Failed to fetch usergroups for mention resolution — HTTP ${responseCode}"
                return null
            }

            String responseText = connection.inputStream.text
            Map response = new JsonSlurper().parseText(responseText) as Map

            if (!response.get('ok')) {
                String error = response.get('error') as String
                if (error == 'missing_scope') {
                    log.warn "Slack plugin: Cannot resolve subteam mentions — bot token is missing the 'usergroups:read' scope"
                } else {
                    log.warn "Slack plugin: Failed to fetch usergroups for mention resolution — API error: ${error}"
                }
                return null
            }

            List<Map> usergroups = response.get('usergroups') as List<Map>
            log.debug "Slack plugin: Fetched ${usergroups?.size() ?: 0} usergroups for mention resolution"
            return usergroups ?: ([] as List<Map>)

        } catch (Exception e) {
            log.warn "Slack plugin: Error fetching usergroups for mention resolution: ${e.message}"
            return null
        } finally {
            connection?.disconnect()
        }
    }
}

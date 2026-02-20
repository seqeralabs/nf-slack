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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.Session

/**
 * Configuration parser for Slack plugin settings.
 *
 * Supports two integration types:
 * 1. Webhook: Uses Slack Incoming Webhooks (current implementation)
 * 2. Bot: Uses Slack Bot Token API (future implementation)
 *
 * Configuration structure:
 * slack {
 *     webhook {
 *         url = 'https://hooks.slack.com/services/...'
 *     }
 *     onStart {
 *         enabled = true
 *         message = '🚀 *Pipeline started*'
 *         includeCommandLine = true
 *     }
 *     onComplete {
 *         enabled = true
 *         message = '✅ *Pipeline completed*'
 *         includeCommandLine = true
 *         includeResourceUsage = true
 *     }
 *     onError {
 *         enabled = true
 *         message = '❌ *Pipeline failed*'
 *         includeCommandLine = true
 *     }
 *     // Future: bot { token = 'xoxb-...', channel = '#workflows' }
 * }
 *
 * @author Adam Talbot <adam.talbot@seqera.io>
 */
@Slf4j
@CompileStatic
class SlackConfig {

    /**
     * Enable/disable the plugin
     */
    final boolean enabled

    /**
     * Slack webhook URL for posting messages (internal use)
     */
    final String webhook

    /**
     * Slack bot token (xoxb-...)
     */
    final String botToken

    /**
     * Slack bot channel ID or name
     */
    final String botChannel

    /**
     * Enable threading for bot messages (groups workflow messages in a thread)
     * Only works with bot tokens, not webhooks
     */
    final boolean useThreads

    /**
     * Validate Slack connection on startup (default: true)
     * Calls auth.test to verify token and authentication
     */
    final boolean validateOnStartup

    /**
     * Configuration for workflow start notifications
     */
    final OnStartConfig onStart

    /**
     * Configuration for workflow completion notifications
     */
    final OnCompleteConfig onComplete

    /**
     * Configuration for workflow error notifications
     */
    final OnErrorConfig onError

    /**
     * Configuration for progress updates during workflow execution
     */
    final OnProgressConfig onProgress

    /**
     * Configuration for emoji reactions on messages
     */
    final ReactionsConfig reactions

    /**
     * Configuration for Seqera Platform deep links
     */
    final SeqeraPlatformConfig seqeraPlatform

    /**
     * Private constructor - use from() factory method
     */
    private SlackConfig(Map config) {
        this.enabled = config.enabled != null ? config.enabled as boolean : true
        this.webhook = config.webhook as String
        def botConfig = config.bot as Map
        this.botToken = botConfig?.token as String
        this.botChannel = botConfig?.channel as String
        this.useThreads = botConfig?.useThreads != null ? botConfig.useThreads as boolean : true
        this.validateOnStartup = config.validateOnStartup != null ? config.validateOnStartup as boolean : true
        this.onStart = new OnStartConfig(config.onStart as Map)
        this.onComplete = new OnCompleteConfig(config.onComplete as Map)
        this.onError = new OnErrorConfig(config.onError as Map)
        this.onProgress = new OnProgressConfig(config.onProgress as Map)
        this.reactions = new ReactionsConfig(config.reactions as Map)
        this.seqeraPlatform = new SeqeraPlatformConfig(config.seqeraPlatform as Map)
    }

    /**
     * Create SlackConfig from Nextflow session
     *
     * @param session The Nextflow session
     * @return SlackConfig instance, or null if not configured/disabled
     */
    static SlackConfig from(Session session) {
        // Build configuration map from session config
        def config = session.config?.navigate('slack') as Map ?: [:]

        // Check if explicitly disabled
        if (config.enabled == false) {
            log.debug "Slack plugin: Explicitly disabled in configuration"
            return null
        }

        def validateOnStartup = session.config?.navigate('slack.validateOnStartup')
        if (validateOnStartup != null) config.validateOnStartup = validateOnStartup

        // Get webhook URL from nested structure
        def webhook = getWebhookUrl(session)

        // Get bot config from nested structure
        def botToken = session.config?.navigate('slack.bot.token') as String
        def botChannel = session.config?.navigate('slack.bot.channel') as String
        def useThreads = session.config?.navigate('slack.useThreads') as Boolean

        if (!webhook && !botToken) {
            log.debug "Slack plugin: No webhook URL or Bot Token configured, plugin will be disabled"
            return null
        }

        // Set values in config map for constructor
        if (webhook) config.webhook = webhook
        if (botToken) {
            // Validate token format
            if (!botToken.startsWith('xoxb-') && !botToken.startsWith('xoxp-')) {
                throw new IllegalArgumentException("Slack plugin: Bot token must start with 'xoxb-' or 'xoxp-'")
            }
            if (botToken.startsWith('xoxp-')) {
                log.warn "Slack plugin: You are using a User Token (xoxp-). It is recommended to use a Bot Token (xoxb-) for better security and granular permissions."
            }

            // Validate channel is present
            if (!botChannel) {
                log.warn "Slack plugin: Bot channel is required when using bot token — plugin will be disabled"
                return null
            }
            // Basic alphanumeric check for channel ID (allow hyphens/underscores for names)
            // Also allow # for channel names
            if (!botChannel.matches(/^[#a-zA-Z0-9\-_]+$/)) {
                throw new IllegalArgumentException("Slack plugin: Invalid channel ID format: ${botChannel}")
            }

            def botConfig = config.bot as Map
            if (botConfig == null) {
                botConfig = [:]
                config.bot = botConfig
            }
            botConfig.token = botToken
            botConfig.channel = botChannel
            if (useThreads != null) {
                botConfig.useThreads = useThreads
            }
        }

        // Validate per-event channel formats if specified
        def onStartConfig = config.onStart as Map
        def onCompleteConfig = config.onComplete as Map
        def onErrorConfig = config.onError as Map
        [onStart: onStartConfig, onComplete: onCompleteConfig, onError: onErrorConfig].each { name, cfg ->
            def ch = cfg?.channel as String
            if (ch && !ch.matches(/^[#a-zA-Z0-9\-_]+$/)) {
                throw new IllegalArgumentException("Slack plugin: Invalid channel format in ${name}: ${ch}")
            }
            if (ch && !botToken) {
                log.warn "Slack plugin: Per-event channel in '${name}' requires bot token - will be ignored with webhook"
            }
        }

        def slackConfig = new SlackConfig(config)
        log.info "Slack plugin: Enabled with ${botToken ? 'Bot' : 'Webhook'} notifications"
        return slackConfig
    }

    /**
     * Get webhook URL from config
     * Reads from nested webhook { url = '...' } structure
     */
    private static String getWebhookUrl(Session session) {
        return session.config?.navigate('slack.webhook.url') as String
    }


    /**
     * Check if plugin is configured and enabled
     */
    boolean isConfigured() {
        return enabled && (webhook != null || (botToken != null && botChannel != null))
    }

    /**
     * Create the appropriate SlackSender based on configuration
     *
     * @return SlackSender instance
     */
    SlackSender createSender() {
        if (!isConfigured()) {
            throw new IllegalStateException("Cannot create sender: Slack plugin not configured")
        }

        if (botToken) {
            return new BotSlackSender(botToken, botChannel)
        }

        return new WebhookSlackSender(webhook)
    }

    @Override
    String toString() {
        return "SlackConfig[enabled=${enabled}, " +
               "webhook=${webhook ? '***configured***' : 'null'}, " +
               "botToken=${botToken ? '***configured***' : 'null'}, " +
               "onStart=${onStart}, onComplete=${onComplete}, onError=${onError}]"
    }
}

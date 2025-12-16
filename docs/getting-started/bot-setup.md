# Slack Bot Setup

This guide will walk you through setting up Slack Bot authentication for your workspace. Bot authentication is the **recommended method** as it's more secure, more capable, and easier to manage than webhooks.

## Prerequisites

- A Slack workspace where you have permission to add apps
- Access to your Nextflow pipeline configuration

## Step 1: Create a Slack App

1. Go to [Slack API Apps](https://api.slack.com/apps)
2. Click **"Create New App"**
3. Choose **"From scratch"**
4. Give your app a name (e.g., "Nextflow Notifications")
5. Select your workspace
6. Click **"Create App"**

## Step 2: Add Bot Permissions

1. In your app's settings, navigate to **"OAuth & Permissions"** in the sidebar
2. Scroll down to **"Scopes"** → **"Bot Token Scopes"**
3. Click **"Add an OAuth Scope"** and add the following scopes:
   - `chat:write` - Required to send messages
   - `chat:write.public` - Optional, allows posting to channels without joining them first

## Step 3: Install App and Copy Bot Token

1. In **"OAuth & Permissions"**, scroll to the top
2. Click **"Install to Workspace"**
3. Review the permissions and click **"Allow"**
4. You'll see a **"Bot User OAuth Token"** starting with `xoxb-`

Copy this token - you'll need it for configuration. It will look like:

```
xoxb-1234567890-1234567890123-xxxxxxxxxxxxxxxxxxxx
```

!!! warning "Keep Your Bot Token Secret"

    This token allows anyone to post messages as your bot. Never commit it to version control or share it publicly.

## Step 4: Configure the Channel

For most cases, you can simply use the channel name (e.g., `general`, `pipelines`) in your configuration.

If you prefer to use a Channel ID (which remains stable even if the channel is renamed), you can find it as follows:

1. Open Slack and navigate to the channel
2. Click the channel name at the top
3. Scroll down in the "About" tab
4. Copy the **Channel ID** from the bottom (e.g., `C1234567890`)

Alternatively, look at the channel URL in your browser: `https://app.slack.com/client/T.../C1234567890` (the part starting with `C`).

## Step 5: Store Your Bot Token Securely

You have several options for managing the bot token and channel ID securely:

### Option 1: Environment Variables

_Recommended_

Set environment variables:

```bash
export SLACK_BOT_TOKEN='xoxb-your-bot-token-here'
export SLACK_CHANNEL_ID='C1234567890'
```

Then reference them in your `nextflow.config`:

```groovy
slack {
    bot {
        token = System.getenv('SLACK_BOT_TOKEN')
        channel = 'general'
    }
}
```

### Option 2: Nextflow Secret

_Better, but supported in fewer places_

Use [Nextflow secrets](https://www.nextflow.io/docs/latest/secrets.html):

```bash
nextflow secrets set SLACK_BOT_TOKEN 'xoxb-your-bot-token-here'
nextflow secrets set SLACK_CHANNEL_ID 'C1234567890'
```

Then reference it in your config:

```groovy
slack {
    bot {
        token = secrets.SLACK_BOT_TOKEN
        channel = 'general'
    }
}
```

### Option 3: Configuration File

For testing only, you can put it directly in `nextflow.config`:

```groovy
slack {
    bot {
        token = 'xoxb-your-bot-token-here'
        channel = 'general'
    }
}
```

!!! danger "Don't Commit Bot Tokens"

    If you use this option, add your config file to `.gitignore` to prevent accidentally committing your bot token.

## Testing Your Bot

To verify your bot is working, you can use the Slack API tester or test it with curl:

```bash
curl -X POST https://slack.com/api/chat.postMessage \
  -H "Authorization: Bearer $SLACK_BOT_TOKEN" \
  -H "Content-type: application/json" \
  --data "{\"channel\":\"$SLACK_CHANNEL_ID\",\"text\":\"Test message from nf-slack\"}"
```

If this works, you should see a test message in your Slack channel.

## Next Steps

Now that you have your bot configured, you can:

- Return to the [Quick Start guide](quick-start.md) to configure the plugin
- Learn about [automatic notifications](../usage/automatic-notifications.md)
- Explore [custom messages](../usage/custom-messages.md)

## Learn More

- [Slack Bot Users Documentation](https://api.slack.com/bot-users)
- [Slack OAuth & Permissions](https://api.slack.com/authentication/oauth-v2)
- [Nextflow Secrets Documentation](https://www.nextflow.io/docs/latest/secrets.html)

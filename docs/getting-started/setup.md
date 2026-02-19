# Setup

Get Slack notifications working with your Nextflow pipeline in minutes.

## Prerequisites

- Nextflow v25.04.0 or later
- A Slack workspace where you have permission to add apps

## 1. Create a Slack App

1. Go to [Slack API Apps](https://api.slack.com/apps)
2. Click **"Create New App"** → **"From scratch"**
3. Name your app (e.g., "Nextflow Notifications"), select your workspace, and click **"Create App"**

## 2. Configure Slack Credentials

nf-slack supports two authentication methods. **Bot User is recommended** — it supports threading, emoji reactions, and file uploads.

=== "Bot User (Recommended)"

    **Add permissions:**

    1. Navigate to **"OAuth & Permissions"** in the sidebar
    2. Under **"Bot Token Scopes"**, add:
        - `chat:write` — Send messages
        - `chat:write.public` — Post to channels without joining *(optional)*
        - `reactions:write` — Add emoji reactions *(optional — required for [emoji reactions](../usage/guide.md#emoji-reactions))*
        - `files:write` — Upload files *(optional — required for [file uploads](../usage/guide.md#file-uploads))*

    **Install and copy token:**

    1. Scroll to the top of **"OAuth & Permissions"**
    2. Click **"Install to Workspace"** → **"Allow"**
    3. Copy the **Bot User OAuth Token** (starts with `xoxb-`)

    !!! warning "Keep your token secret"
        Never commit tokens to version control. See [storing credentials securely](#3-store-credentials-securely) below.

    **Test it:**

    ```bash
    export SLACK_BOT_TOKEN='xoxb-your-token'
    curl -X POST https://slack.com/api/chat.postMessage \
      -H "Authorization: Bearer $SLACK_BOT_TOKEN" \
      -H "Content-type: application/json" \
      --data '{"channel":"general","text":"Test from nf-slack"}'
    ```

=== "Webhook"

    **Enable webhooks:**

    1. Navigate to **"Incoming Webhooks"** in the sidebar
    2. Toggle **"On"**
    3. Click **"Add New Webhook to Workspace"**
    4. Select the target channel → **"Allow"**
    5. Copy the webhook URL (starts with `https://hooks.slack.com/services/...`)

    !!! warning "Keep your webhook URL secret"
        Never commit webhook URLs to version control. See [storing credentials securely](#3-store-credentials-securely) below.

    !!! note "Webhook limitations"
        Webhooks don't support threading, emoji reactions, or file uploads. Use a Bot User for these features.

    **Test it:**

    ```bash
    export SLACK_WEBHOOK_URL='https://hooks.slack.com/services/YOUR/WEBHOOK/URL'
    curl -X POST -H 'Content-type: application/json' \
      --data '{"text":"Test from nf-slack"}' \
      $SLACK_WEBHOOK_URL
    ```

## 3. Store Credentials Securely

=== "Environment Variables (Recommended)"

    ```bash
    # Bot
    export SLACK_BOT_TOKEN='xoxb-your-token'

    # Webhook
    export SLACK_WEBHOOK_URL='https://hooks.slack.com/services/YOUR/WEBHOOK/URL'
    ```

=== "Nextflow Secrets"

    ```bash
    # Bot
    nextflow secrets set SLACK_BOT_TOKEN 'xoxb-your-token'

    # Webhook
    nextflow secrets set SLACK_WEBHOOK_URL 'https://hooks.slack.com/services/YOUR/WEBHOOK/URL'
    ```

## 4. Add the Plugin to Your Pipeline

Add nf-slack to your `nextflow.config`:

```groovy
plugins {
    id 'nf-slack@0.5.0'
}
```

Then add the Slack configuration block:

=== "Bot User"

    ```groovy
    slack {
        bot {
            token = System.getenv('SLACK_BOT_TOKEN')
            channel = 'general'
        }
    }
    ```

=== "Webhook"

    ```groovy
    slack {
        webhook {
            url = "$SLACK_WEBHOOK_URL"
        }
    }
    ```

## 5. Run Your Pipeline

```bash
nextflow run main.nf
```

You'll receive Slack notifications when your pipeline starts, completes, and fails.

![Default notifications](../images/nf-slack-00.png)

## Next Steps

- [Usage Guide](../usage/guide.md) — Customize notifications, enable threading, send messages from code
- [Examples](../examples/gallery.md) — Copy-paste configurations for common scenarios
- [API Reference](../reference/api.md) — Complete configuration reference

## Learn More

- [Slack Bot Users Documentation](https://api.slack.com/bot-users)
- [Slack Incoming Webhooks Documentation](https://api.slack.com/messaging/webhooks)
- [Nextflow Secrets Documentation](https://www.nextflow.io/docs/latest/secrets.html)

# Quick Start

Get your first Slack notification working in minutes!

## Prerequisites

Before you begin, make sure you have:

- [x] A Bot Token ([Bot guide](bot-setup.md)) OR Slack webhook URL ([Webhook guide](webhook-setup.md))
- [x] A Nextflow pipeline (v25.04.0 or later)
- [x] Basic familiarity with Nextflow configuration

## Step 1: Add the Plugin

Add the nf-slack plugin to your `nextflow.config`:

```groovy
plugins {
    id 'nf-slack@0.3.0'
}
```

!!! tip "Using Multiple Plugins?"

    If you already have a `plugins` block, just add the nf-slack entry:

    ```groovy
    plugins {
        id 'nf-validation'
        id 'nf-slack@0.3.0'  // Add this line
    }
    ```

## Step 2: Configure the Plugin

Add the Slack configuration block with your Bot Token:

```groovy
slack {
    enabled = true

    // Option A: Bot User
    bot {
        token = 'xoxb-your-token'
        channel = 'general'
    }

    // Option B: Webhook
    // webhook {
    //     url = 'https://hooks.slack.com/services/YOUR/WEBHOOK/URL'
    // }
}
```

!!! warning "Security Best Practice"

    Don't hardcode your webhook URL! Use environment variables or Nextflow secrets instead:

    ```groovy
    slack {
        enabled = true
        bot {
            token = System.getenv("SLACK_BOT_TOKEN") // or secrets.SLACK_BOT_TOKEN
            channel = 'general'
        }
    }
    ```

## Step 3: Run Your Pipeline

That's it! Run your pipeline normally:

```bash
nextflow run main.nf
```

You'll receive Slack notifications when your pipeline:

- 🚀 Starts
- ✅ Completes successfully
- ❌ Fails

![Default notifications](../images/nf-slack-00.png)

## Next Steps

Now that you have basic notifications working, learn how to:

- [Customize automatic notifications](../usage/automatic-notifications.md)
- [Send custom messages from your workflow](../usage/custom-messages.md)
- [Configure advanced options](../usage/configuration.md)
- [View more examples](../examples/gallery.md)

## Need Help?

- Review the [API Reference](../reference/api.md)
- [Open an issue](https://github.com/seqeralabs/nf-slack/issues) on GitHub

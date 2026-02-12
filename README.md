# nf-slack plugin

Get Slack notifications for your Nextflow workflows - automatically notified when pipelines start, complete, or fail.

:book: Full documentation: <https://seqeralabs.github.io/nf-slack/>

> [!IMPORTANT]
> This is an open-source project for community benefit. It is provided as-is and is not part of Seqera's officially supported toolset.

## Features

- 🚀 **Automatic Notifications**: Get notified when workflows start, complete, or fail
- 💬 **Custom Messages**: Send custom messages from within your workflow scripts
- 📎 **File Uploads**: Share plots, reports, and other files directly to Slack (Bot only)
- 🤖 **Bot & Webhook Support**: Supports both bot and webhook authentication
- 🧵 **Threading**: Keep channels clean by threading workflow notifications (Bot only)
- 🎨 **Rich Formatting**: Beautiful Slack messages with colors and custom fields
- ⚙️ **Highly Configurable**: Control what notifications are sent and when
- 🔍 **Connection Validation**: Verify Slack credentials on startup with configurable failure behavior

## Quick Start

### 1. Set up a Slack Integration

You can use either a **Webhook** or a **Bot User**.

#### Option A: Bot User (Recommended)

1. Create a Slack App at [api.slack.com/apps](https://api.slack.com/apps)
2. Add `chat:write` scope to **Bot Token Scopes**
   - `files:write` is required for file uploads
3. Install App to Workspace and copy **Bot User OAuth Token** (`xoxb-...`)
4. Invite the bot to your channel

#### Option B: Webhook (Simpler, Fewer Features, Less Secure)

1. Go to [Slack Incoming Webhooks](https://api.slack.com/messaging/webhooks)
2. Create a new webhook for your workspace
3. Copy the webhook URL

### 2. Add to Your Pipeline

Add to your `nextflow.config`:

```groovy
plugins {
    id 'nf-slack@0.4.0'
}

slack {
    enabled = true

    // Option A: Bot User
    bot {
        token = 'xoxb-your-bot-token'
        channel = 'general'
    }

    // Option B: Webhook
    // webhook {
    //     url = 'https://hooks.slack.com/services/YOUR/WEBHOOK/URL'
    // }
}
```

Will post these messages to Slack:

![default Slack message](./docs/images/nf-slack-00.png)

### 3. Run Your Pipeline

That's it! You'll automatically receive notifications when your pipeline starts, completes, or fails.

## What You Get

Once configured, you'll automatically receive Slack messages for:

- 🚀 **Pipeline starts** - Know when your workflow begins
- ✅ **Successful completions** - Celebrate when pipelines finish
- ❌ **Failures** - Get alerted immediately when something goes wrong

Each message includes relevant details like run name, duration, and error information.

## Basic Customization

### Choose Which Events to Notify

By default, all notifications are enabled. You can selectively disable them:

```groovy
slack {
    enabled = true
    webhook {
        url = 'https://hooks.slack.com/services/YOUR/WEBHOOK/URL'
    }

    onStart.enabled = false     // Do not notify when pipeline starts
    onComplete.enabled = true   // Notify on successful completion
    onError.enabled = true      // Notify on failures
}
```

Will post this message to Slack:

![Slack message on completion or error](./docs/images/nf-slack-01.png)

### Customize Message Text

Change the notification messages to suit your needs:

```groovy
slack {
    webhook {
        url = 'https://hooks.slack.com/services/YOUR/WEBHOOK/URL'
    }

    onStart {
        message = '🎬 *My analysis pipeline is starting!*'
    }

    onComplete {
        message = '🎉 *Analysis completed successfully!*'
    }

    onError {
        message = '💥 *Pipeline encountered an error!*'
    }
}
```

Will post these messages to Slack, note the text has changed:

![Custom Slack messages](./docs/images/nf-slack-02.png)

### Add Colors and Custom Fields

For richer messages with colors and additional information:

```groovy
slack {
    webhook {
        url = 'https://hooks.slack.com/services/YOUR/WEBHOOK/URL'
    }

    onComplete {
        message = [
            text: '✅ *Analysis Complete*',
            color: '#2EB887',  // Green color
            customFields: [
                [title: 'Environment', value: 'Production', short: true],
                [title: 'Cost', value: '$12.50', short: true]
            ]
        ]
    }
}
```

Will post these formatted messages to Slack:

![Custom Slack blocks](./docs/images/nf-slack-03.png)

### Send Custom Messages from Your Workflow

Send notifications from within your pipeline code:

```groovy
include { slackMessage } from 'plugin/nf-slack'

params.sample_id = "SAMPLE_001"

workflow {
    slackMessage("🔬 Starting analysis for sample ${params.sample_id}")

    // Your workflow processes here

    slackMessage("✅ Analysis complete!")
}
```

Will post these short messages to Slack within the workflow:

![Workflow Slack message](./docs/images/nf-slack-04.png)

### Upload Files to Slack

Share plots, reports, and other files directly to Slack from your pipeline. Upload automatically on completion, or call `slackFileUpload()` from within your workflow.

```groovy
slack {
    bot {
        token = 'xoxb-your-bot-token'
        channel = 'pipeline-results'
    }

    onComplete {
        files = ['results/multiqc_report.html']
    }
}
```

**[File upload guide →](docs/usage/custom-messages.md#file-uploads)** for all upload methods and options.

**Want more control?** See the [API Reference](docs/REFERENCE.md) and [detailed examples](docs/EXAMPLES.md) for all available options.

## Examples

| Example                                                                          | Description                               |
| -------------------------------------------------------------------------------- | ----------------------------------------- |
| [01-minimal.config](example/configs/01-minimal.config)                           | Just webhook, use defaults                |
| [02-notification-control.config](example/configs/02-notification-control.config) | Choose which events trigger notifications |
| [03-message-text.config](example/configs/03-message-text.config)                 | Change notification text                  |
| [04-message-colors.config](example/configs/04-message-colors.config)             | Customize colors                          |
| [05-custom-fields.config](example/configs/05-custom-fields.config)               | Add your own information                  |
| [06-selective-fields.config](example/configs/06-selective-fields.config)         | Select which workflow info to show        |
| [10-file-upload.config](example/configs/10-file-upload.config)                   | Auto-upload files on completion           |

**[View all examples with explanations →](docs/EXAMPLES.md)**

## Documentation

- **[API Reference](docs/REFERENCE.md)** - Complete configuration options and API reference
- **[Usage Guide](docs/USAGE.md)** - How to use the plugin and send custom messages
- **[Troubleshooting](docs/TROUBLESHOOTING.md)** - Common issues and solutions
- **[Examples](docs/EXAMPLES.md)** - Progressive configuration examples
- **[Contributing](docs/CONTRIBUTING.md)** - Development setup and contribution guidelines

## Support

- 🐛 [Report bugs](https://github.com/seqeralabs/nf-slack/issues)
- 💡 [Request features](https://github.com/seqeralabs/nf-slack/issues)
- 📖 [Read the docs](docs/)

## License

Copyright 2025, Seqera Labs. Licensed under the Apache License, Version 2.0.

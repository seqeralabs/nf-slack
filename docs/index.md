# nf-slack

Get Slack notifications for your Nextflow workflows - automatically notified when pipelines start, complete, or fail.

![Default Slack notification](images/nf-slack-00.png)

## Features

- 🚀 **Automatic Notifications**: Get notified when workflows start, complete, or fail
- 💬 **Custom Messages**: Send custom messages from within your workflow scripts
- 🎨 **Rich Formatting**: Beautiful Slack messages with colors and custom fields
- ⚙️ **Highly Configurable**: Control what notifications are sent and when
- 🛡️ **Fail-Safe**: Never fails your workflow, even if Slack is unavailable

## Quick Start

Get started in just 3 simple steps:

1. **[Set up a Bot User](getting-started/bot-setup.md)** - Create a Slack App and Bot User (Recommended)
2. **[Configure your pipeline](getting-started/quick-start.md)** - Add the plugin to your `nextflow.config`
3. **Run your workflow** - That's it! Notifications will be sent automatically

[Get Started →](getting-started/quick-start.md){ .md-button .md-button--primary }

## What You Get

Once configured, you'll automatically receive Slack messages for:

- 🚀 **Pipeline starts** - Know when your workflow begins
- ✅ **Successful completions** - Celebrate when pipelines finish
- ❌ **Failures** - Get alerted immediately when something goes wrong

Each message includes relevant details like run name, duration, and error information.

## Basic Example

Add to your `nextflow.config`:

```groovy
plugins {
    id 'nf-slack@0.2.1'
}

slack {
    enabled = true
    bot {
        token = 'xoxb-your-bot-token'
        channel = 'C123456'
    }
}
```

That's it! Your workflow will now send notifications to Slack.

## Customize Your Notifications

### Choose Which Events to Notify

```groovy
slack {
    enabled = true
    bot {
        token = 'xoxb-your-bot-token'
        channel = 'C123456'
    }

    onStart.enabled = false     // Do not notify when pipeline starts
    onComplete.enabled = true   // Notify on successful completion
    onError.enabled = true      // Notify on failures
}
```

![Selective notifications](images/nf-slack-01.png)

### Customize Message Text

```groovy
slack {
    bot {
        token = 'xoxb-your-bot-token'
        channel = 'C123456'
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

![Custom messages](images/nf-slack-02.png)

### Send Custom Messages from Your Workflow

```groovy
include { slackMessage } from 'plugin/nf-slack'

params.sample_id = "SAMPLE_001"

workflow {
    slackMessage("🔬 Starting analysis for sample ${params.sample_id}")

    // Your workflow processes here

    slackMessage("✅ Analysis complete!")
}
```

![Custom workflow messages](images/nf-slack-04.png)

## Learn More

- **[Installation Guide](getting-started/installation.md)** - Set up Bot User or Webhook
- **[Quick Start](getting-started/quick-start.md)** - Configure your first notification
- **[Usage Guide](usage/automatic-notifications.md)** - Learn all the features
- **[Examples Gallery](examples/gallery.md)** - 9 progressive examples with screenshots
- **[API Reference](reference/api.md)** - Complete configuration options

## Support

- 🐛 [Report bugs](https://github.com/seqeralabs/nf-slack/issues)
- 💡 [Request features](https://github.com/seqeralabs/nf-slack/issues)
- 📖 [Read the docs](https://seqeralabs.github.io/nf-slack/)

## License

Copyright 2025, Seqera Labs. Licensed under the Apache License, Version 2.0.

# nf-slack

Slack notifications for your Nextflow workflows. Get notified when pipelines start, complete, or fail — with no changes to your pipeline code.

![Default notifications](images/nf-slack-00.png)

## Features

- **Automatic notifications** — workflow start, completion, and error events
- **Custom messages and file uploads** from within your pipeline code
- **Threading, reactions, and progress updates** to keep channels organized
- **Seqera Platform integration** — one-click navigation to your runs

## Quick Start

**1. Add the plugin** to your `nextflow.config`:

```groovy
plugins {
    id 'nf-slack@0.5.1'
}

slack {
    enabled = true
    bot {
        token = 'xoxb-your-bot-token'
        channel = 'pipeline-notifications'
    }
}
```

**2. Run your pipeline** — that's it! You'll get Slack notifications automatically.

<!-- prettier-ignore -->
!!! tip "Need help setting up Slack?"
    See the **[Setup Guide](getting-started/setup.md)** for step-by-step instructions on creating a Slack app and choosing between Bot and Webhook modes.

## What Can You Do?

| Feature                     | Example                              | Guide                                                   |
| --------------------------- | ------------------------------------ | ------------------------------------------------------- |
| Control which events notify | `onStart.enabled = false`            | [Usage Guide](usage/guide.md#enable-or-disable-events)  |
| Customize message text      | `onComplete.message = 'Done!'`       | [Usage Guide](usage/guide.md#custom-text)               |
| Thread notifications        | `useThreads = true`                  | [Usage Guide](usage/guide.md#threading)                 |
| Upload files on completion  | `onComplete.files = ['report.html']` | [Usage Guide](usage/guide.md#file-uploads)              |
| Send messages from code     | `slackMessage('Hello!')`             | [Usage Guide](usage/guide.md#custom-messages-from-code) |
| Upload files from code      | `slackFileUpload('results.csv')`     | [Usage Guide](usage/guide.md#upload-a-file)             |

## Documentation

- **[Setup Guide](getting-started/setup.md)** — Install the plugin and configure Slack
- **[Usage Guide](usage/guide.md)** — All features and configuration options
- **[Examples](examples/gallery.md)** — Runnable examples with screenshots
- **[API Reference](reference/api.md)** — Complete property and function reference

## Support

- [Report bugs](https://github.com/seqeralabs/nf-slack/issues)
- [Request features](https://github.com/seqeralabs/nf-slack/issues)
- [Nextflow Slack](https://www.nextflow.io/slack.html)

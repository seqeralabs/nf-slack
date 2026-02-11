# Automatic Notifications

Once configured, nf-slack automatically sends notifications for workflow events. No code changes needed!

## Default Behavior

By default, nf-slack sends notifications for three workflow events:

- 🚀 **Workflow Start** - When your pipeline begins execution
- ✅ **Workflow Complete** - When your pipeline finishes successfully
- ❌ **Workflow Error** - When your pipeline encounters an error

![Default notifications](../images/nf-slack-00.png)

## Notification Content

### Workflow Start

When a workflow starts, the notification includes:

- Run name
- Session ID
- Command line used to launch the workflow
- Work directory path
- Timestamp

![Workflow start notification](../images/nf-slack-00.png)

### Workflow Complete

When a workflow completes successfully, the notification includes:

- Run name
- Duration (total execution time)
- Task counts (succeeded, cached, failed)
- Resource usage summary
- Timestamp

### Workflow Error

When a workflow fails, the notification includes:

- Run name
- Error message
- Failed process name
- Command line
- Work directory
- Timestamp

## Controlling Notifications

### Enable/Disable All Notifications

```groovy
slack {
    enabled = true  // Set to false to disable all notifications
    bot {
        token = System.getenv('SLACK_BOT_TOKEN')
        channel = 'general'
    }
}
```

### Enable/Disable Specific Events

Control which events trigger notifications:

```groovy
slack {
    bot {
        token = System.getenv('SLACK_BOT_TOKEN')
        channel = 'general'
    }

    onStart.enabled = false     // Don't notify on start
    onComplete.enabled = true   // Notify on completion
    onError.enabled = true      // Notify on errors
}
```

![Selective notifications](../images/nf-slack-01.png)

### Threading

When using a Bot User, you can enable **Threading** to keep your Slack channels organized.

With threading enabled:

1. The **Workflow Start** notification creates a new parent message.
2. **Workflow Complete** and **Workflow Error** notifications are posted as replies to that thread.
3. Any **Custom Messages** sent from the workflow are also threaded.

This significantly reduces noise in your channels, especially when running multiple pipelines simultaneously.

> **Note**: Threading requires a Bot Token and must be enabled in your configuration. See [Configuration](configuration.md#threading) for details.

### Automatic File Uploads

Upload files automatically when a workflow completes or fails — no code changes required. Add a `files` list to `onComplete` or `onError`:

```groovy
slack {
    bot {
        token = System.getenv('SLACK_BOT_TOKEN')
        channel = '#pipeline-results'
    }

    onComplete {
        files = ['results/multiqc_report.html', 'results/pipeline_report.html']
    }

    onError {
        files = ['results/pipeline_report.html']
    }
}
```

Files are uploaded after the notification message. If a file doesn't exist (e.g., the pipeline failed before creating it), it's skipped with a warning.

!!! tip "Requirements"

    File upload requires a Bot User with `files:write` scope. It is not supported with webhooks. See [File Uploads in Custom Messages](custom-messages.md#file-uploads) for uploading files from within your workflow code.

When `useThreads` is enabled, uploaded files appear in the same thread as the notification messages.

### Common Notification Patterns

#### Errors Only

Only get notified when something goes wrong:

```groovy
slack {
    bot {
        token = System.getenv('SLACK_BOT_TOKEN')
        channel = 'general'
    }

    onStart.enabled = false
    onComplete.enabled = false
    onError.enabled = true
}
```

#### Start and Error Only

Get notified when pipelines start and if they fail:

```groovy
slack {
    bot {
        token = System.getenv('SLACK_BOT_TOKEN')
        channel = 'general'
    }

    onStart.enabled = true
    onComplete.enabled = false
    onError.enabled = true
}
```

#### Development Mode (Quiet)

Disable all automatic notifications during development:

```groovy
slack {
    bot {
        token = System.getenv('SLACK_BOT_TOKEN')
        channel = 'general'
    }

    onStart.enabled = false
    onComplete.enabled = false
    onError.enabled = false
}
```

!!! note "Custom Messages"

    Even with automatic notifications disabled, you can still send [custom messages](custom-messages.md) from your workflow.

## Notification Format

All automatic messages use Slack's attachment format with Block Kit elements:

- **Workflow Start**: Blue color (`#3AA3E3`)
- **Workflow Complete**: Green color (`#2EB887`)
- **Workflow Error**: Red color (`#A30301`)

Each message includes:

- Workflow name as the author
- Nextflow icon
- Timestamp footer
- Configurable bot username and icon (see [Configuration](configuration.md))

## Advanced Configuration

For advanced customization options, see:

- [Custom Messages](custom-messages.md) - Send messages from your workflow
- [Configuration](configuration.md) - Advanced configuration options
- [API Reference](../reference/api.md) - Complete configuration reference

## Next Steps

- Learn how to [send custom messages](custom-messages.md) from your workflow
- Explore [configuration options](configuration.md) to customize notifications
- View [examples](../examples/gallery.md) for real-world use cases

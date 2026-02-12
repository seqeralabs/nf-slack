# Configuration

Advanced configuration options for customizing nf-slack notifications.

## Overview

The nf-slack plugin is configured through the `slack` block in your `nextflow.config`.

## Basic Structure

```groovy
slack {
    enabled = true
    bot {
        token = System.getenv('SLACK_BOT_TOKEN')
        channel = 'general'
    }
    onStart { /* ... */ }
    onComplete { /* ... */ }
    onError { /* ... */ }
}
```

## Master Switch

### Enable/Disable the Plugin

```groovy
slack {
    enabled = true  // Set to false to disable all Slack functionality
}
```

When `enabled = false`:

- No automatic notifications are sent
- Custom `slackMessage()` calls are silently ignored
- No Slack API calls are made

### Connection Validation

nf-slack validates your Slack connection on startup by default, checking that your bot token is valid and the configured channel is accessible.

```groovy
slack {
    validateOnStartup = true      // Validate token and channel on startup (default: true)
    failOnInvalidConfig = false   // Fail the pipeline if validation fails (default: false)
}
```

| Property              | Type      | Default | Description                                                     |
| --------------------- | --------- | ------- | --------------------------------------------------------------- |
| `validateOnStartup`   | `Boolean` | `true`  | Whether to validate the Slack connection on startup             |
| `failOnInvalidConfig` | `Boolean` | `false` | Whether to fail the pipeline if the connection validation fails |

When `failOnInvalidConfig` is `false` (default), validation failures produce a warning log message but allow the pipeline to continue. Set it to `true` to halt the pipeline immediately if the Slack connection cannot be established.

> **Note:** Webhook connections have limited validation capabilities. Only bot token connections can fully verify token validity and channel access.

## Bot Configuration

### Basic Bot Setup

```groovy
slack {
    bot {
        token = 'xoxb-your-bot-token'
        channel = 'general'
    }
}
```

!!! note "Using Channel Names vs IDs"

    You can use either a channel name (e.g., `'general'`) or a Channel ID (e.g., `'C123456'`). Channel IDs are recommended for critical pipelines as they remain stable even if the channel is renamed.

### Using Environment Variables

```groovy
slack {
    bot {
        token = System.getenv('SLACK_BOT_TOKEN')
        channel = 'general'
    }
}
```

### Using Nextflow Secrets

```groovy
slack {
    bot {
        token = secrets.SLACK_BOT_TOKEN
        channel = 'general'
    }
}
```

!!! tip "Security Best Practice"

    Never hardcode tokens in configuration files that are committed to version control. Use environment variables or Nextflow secrets.

## Event Notification Control

### Enable/Disable Individual Events

Control which workflow events trigger notifications:

```groovy
slack {
    onStart {
        enabled = true  // Notify when workflow starts
    }

    onComplete {
        enabled = true  // Notify when workflow completes
    }

    onError {
        enabled = true  // Notify when workflow fails
    }
}
```

## Threading

Group all workflow notifications (start, complete, error) into a single thread to reduce channel clutter. This is particularly useful for high-volume pipelines.

```groovy
slack {
    useThreads = true  // Enable threading
    bot {
        token = System.getenv('SLACK_BOT_TOKEN')
        channel = 'general'
    }
}
```

**How it works:**

- The initial "workflow started" message creates a new thread
- Subsequent messages (complete/error) are posted as replies to that thread
- **Custom messages** sent via `slackMessage()` are also posted in the thread
- Each new workflow run creates a separate thread

!!! warning "Bot Tokens Only"

    Threading **only works with bot tokens**, not webhooks. Webhooks do not support thread creation or replies.

## Customizing Messages

### Simple Text Messages

Replace the default message text:

```groovy
slack {
    onStart {
        message = '🎬 *Production pipeline is starting!*'
    }

    onComplete {
        message = '🎉 *Analysis completed successfully!*'
    }

    onError {
        message = '💥 *Pipeline encountered an error!*'
    }
}
```

![Custom message text](../images/nf-slack-02.png)

### Adding Custom Fields

Include additional information in notifications:

```groovy
slack {
    onComplete {
        message = [
            text: '✅ *Analysis Complete*',
            customFields: [
                [title: 'Environment', value: 'Production', short: true],
                [title: 'Cost', value: '$12.50', short: true]
            ]
        ]
    }
}
```

![Custom fields](../images/nf-slack-03.png)

### Field Options

Each field in `customFields` can have:

| Property | Type    | Description                         | Required |
| -------- | ------- | ----------------------------------- | -------- |
| `title`  | String  | Field label                         | Yes      |
| `value`  | String  | Field content                       | Yes      |
| `short`  | Boolean | Display in columns (default: false) | No       |

## Controlling Workflow Information

### Include/Exclude Workflow Fields

Choose which workflow information to display:

```groovy
slack {
    onComplete {
        message = [
            text: '✅ *Pipeline Complete*',
            includeFields: ['runName', 'duration', 'tasks']
            // Only shows: run name, duration, and task statistics
        ]
    }
}
```

### Available Workflow Fields

**onStart fields:**

- `runName` - Workflow run name
- `status` - Current status
- `commandLine` - Full Nextflow command
- `workDir` - Working directory path

**onComplete fields:**

- `runName` - Workflow run name
- `status` - Final status
- `duration` - Total workflow runtime
- `commandLine` - Full Nextflow command
- `tasks` - Task execution statistics

**onError fields:**

- `runName` - Workflow run name
- `status` - Error status
- `duration` - Time before failure
- `commandLine` - Full Nextflow command
- `errorMessage` - Error details
- `failedProcess` - Name of failed process

### Command Line Control

Control whether the command line is included:

```groovy
slack {
    onStart {
        includeCommandLine = false  // Don't show command line
    }

    onComplete {
        includeCommandLine = true  // Show command line (default)
    }
}
```

### Resource Usage (onComplete only)

Control whether task statistics and resource usage are included:

```groovy
slack {
    onComplete {
        includeResourceUsage = true  // Show task stats (default)
    }
}
```

### onComplete Properties

| Property               | Type           | Default         | Description                                           |
| ---------------------- | -------------- | --------------- | ----------------------------------------------------- |
| `enabled`              | Boolean        | `true`          | Enable/disable completion notifications               |
| `message`              | String or Map  | Default message | Custom notification text or message object            |
| `includeCommandLine`   | Boolean        | `true`          | Include the Nextflow command line in the message      |
| `includeResourceUsage` | Boolean        | `true`          | Include task statistics and resource usage            |
| `showFooter`           | Boolean        | `true`          | Display timestamp footer                              |
| `files`                | `List<String>` | `[]`            | List of file paths to upload on completion (Bot only) |

### onError Properties

| Property             | Type           | Default         | Description                                      |
| -------------------- | -------------- | --------------- | ------------------------------------------------ |
| `enabled`            | Boolean        | `true`          | Enable/disable error notifications               |
| `message`            | String or Map  | Default message | Custom notification text or message object       |
| `includeCommandLine` | Boolean        | `true`          | Include the Nextflow command line in the message |
| `showFooter`         | Boolean        | `true`          | Display timestamp footer                         |
| `files`              | `List<String>` | `[]`            | List of file paths to upload on error (Bot only) |

### Footer Display

Control whether a timestamp footer is shown at the bottom of messages:

```groovy
slack {
    onStart {
        showFooter = true  // Show timestamp (default)
    }

    onComplete {
        showFooter = false  // Hide timestamp footer
    }

    onError {
        showFooter = true  // Show timestamp
    }
}
```

The footer displays a human-readable timestamp (e.g., "Nov 20, 2025 at 6:54 PM").

## Next Steps

- Learn about [automatic notifications](automatic-notifications.md)
- Explore [custom messages](custom-messages.md) from workflows
- View [example gallery](../examples/gallery.md)
- Check the complete [API Reference](../reference/api.md)

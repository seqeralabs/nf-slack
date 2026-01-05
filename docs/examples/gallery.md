# Examples Gallery

Comprehensive examples demonstrating nf-slack features, from basic to advanced.

## Overview

The examples are organized into two categories:

- **Configuration Examples**: 6 examples showing automatic workflow notifications
- **Script Examples**: 3 examples showing programmatic message sending

Each example focuses on **one specific aspect** of the plugin, building progressively in complexity.

## Quick Reference

### Configuration Examples (Automatic Notifications)

| Example                                                            | Feature                  | Complexity |
| ------------------------------------------------------------------ | ------------------------ | ---------- |
| [Example 1: Minimal Setup](#example-1-minimal-setup)               | Enable notifications     | ⭐         |
| [Example 2: Notification Control](#example-2-notification-control) | Control when to notify   | ⭐         |
| [Example 3: Message Text](#example-3-message-text-customization)   | Customize message text   | ⭐⭐       |
| [Example 4: Message Colors](#example-4-message-colors)             | Customize message colors | ⭐⭐       |
| [Example 5: Custom Fields](#example-5-custom-fields)               | Add custom fields        | ⭐⭐⭐     |
| [Example 6: Selective Fields](#example-6-selective-default-fields) | Choose default fields    | ⭐⭐⭐     |
| [Example 7: Footer Control](#example-7-footer-control)             | Control timestamp footer | ⭐         |
| [Example 8: Specific Channel ID](#example-8-specific-channel-id)   | Send to specific channel | ⭐         |
| [Example 9: Threaded Messages](#example-9-threaded-messages)       | Group messages in thread | ⭐⭐       |

### Script Examples (Programmatic Messages)

| Example                                                      | Feature                     | Complexity |
| ------------------------------------------------------------ | --------------------------- | ---------- |
| [Script Example 1](#script-example-1-message-in-workflow)    | Send from workflow body     | ⭐⭐       |
| [Script Example 2](#script-example-2-message-on-complete)    | Send using onComplete       | ⭐⭐       |
| [Script Example 3](#script-example-3-message-within-channel) | Send from channel operators | ⭐⭐⭐     |

## Getting Started

### Prerequisites

Set up your Bot Token:

```bash
export SLACK_BOT_TOKEN='xoxb-your-bot-token'
```

See the [Installation Guide](../getting-started/installation.md) for detailed setup instructions.

### Running Examples

Configuration examples are run by applying them to any workflow:

```bash
# Clone the repository
git clone https://github.com/seqeralabs/nf-slack.git
cd nf-slack

# Run with a configuration example
nextflow run example/main.nf -c example/configs/01-minimal.config
```

Script examples are complete workflows:

```bash
# Run a script example directly
nextflow run example/scripts/01-message-in-workflow.nf
```

---

## Configuration Examples

Configuration examples show how to set up automatic workflow notifications using the plugin's configuration options.

### Example 1: Minimal Setup

**Concept**: Just enable notifications with defaults

**Configuration**:

```groovy title="01-minimal.config"
slack {
    bot {
        token = System.getenv('SLACK_BOT_TOKEN')
        channel = 'general'
    }
}
```

**What you get**:

- ✅ Notifications on start, complete, and error
- ✅ Default message templates
- ✅ Default formatting

**Use when**: You want to enable notifications quickly without customization

**Output**:

![Minimal Setup Example](../images/nf-slack-examples-01.png)

---

### Example 2: Notification Control

**Concept**: Choose which events trigger notifications

**New concepts**:

- `onStart.enabled` - Control start notifications
- `onComplete.enabled` - Control completion notifications
- `onError.enabled` - Control error notifications

**Configuration**:

```groovy title="02-notification-control.config"
slack {
    bot {
        token = System.getenv('SLACK_BOT_TOKEN')
        channel = 'general'
    }

    onStart {
        enabled = false      // Don't notify on start
    }

    onComplete {
        enabled = true       // DO notify on completion
    }

    onError {
        enabled = true       // DO notify on error
    }
}
```

**Use when**: You want to reduce notification noise (e.g., only errors and completions)

**Output**:

![Notification Control Example](../images/nf-slack-examples-02.png)

---

### Example 3: Message Text Customization

**Concept**: Customize the text in notification messages

**New concepts**:

- `onStart.message` - Custom text for start notifications
- `onComplete.message` - Custom text for completion notifications
- `onError.message` - Custom text for error notifications

**Configuration**:

```groovy title="03-message-text.config"
slack {
    bot {
        token = System.getenv('SLACK_BOT_TOKEN')
        channel = 'general'
    }

    onStart {
        message = '🚀 *My workflow is starting...*'
    }

    onComplete {
        message = '✅ *My workflow finished successfully!*'
    }

    onError {
        message = '❌ *My workflow failed!*'
    }
}
```

**Supports**: Slack markdown formatting (`*bold*`, `_italic_`, `` `code` ``)

**Use when**: You want different message text than the defaults

**Output**:

![Message Text Customization Example](../images/nf-slack-examples-03.png)

---

### Example 4: Message Colors

**Concept**: Use custom colors for message attachments

**New concepts**:

- Map-based message configuration
- `color` property for hex color codes

**Configuration**:

```groovy title="04-message-colors.config"
slack {
    bot {
        token = System.getenv('SLACK_BOT_TOKEN')
        channel = 'general'
    }

    onStart {
        message = [
            text: '🚀 *Pipeline started*',
            color: '#3AA3E3'  // Blue
        ]
    }

    onComplete {
        message = [
            text: '✅ *Pipeline completed*',
            color: '#2EB887'  // Green
        ]
    }

    onError {
        message = [
            text: '❌ *Pipeline failed*',
            color: '#A30301'  // Red
        ]
    }
}
```

**Map structure**:

- `text` - Message text (same as string format)
- `color` - Hex color code (e.g., `'#FF5733'`)

**Use when**: You want visual distinction with custom colors

**Output**:

![Message Colors Example](../images/nf-slack-examples-04.png)

---

### Example 5: Custom Fields

**Concept**: Add your own custom information fields

**New concepts**:

- `customFields` array for additional information
- Field properties: `title`, `value`, `short`

**Configuration**:

```groovy title="05-custom-fields.config"
slack {
    bot {
        token = System.getenv('SLACK_BOT_TOKEN')
        channel = 'general'
    }

    onStart {
        message = [
            text: '🚀 *Pipeline started*',
            color: '#3AA3E3',
            customFields: [
                [title: 'Priority', value: 'High', short: true],
                [title: 'Team', value: 'Bioinformatics', short: true],
                [title: 'Notes', value: 'Running with increased resources', short: false]
            ]
        ]
    }
}
```

**Field structure**:

- `title` - Field label (required)
- `value` - Field content (required)
- `short` - Layout: `true` = columns (2 per row), `false` = full width

**Use when**: You want to add extra context to messages

**Output**:

![Custom Fields Example](../images/nf-slack-examples-05.png)

---

### Example 6: Selective Default Fields

**Concept**: Choose which built-in workflow information to include

**New concepts**:

- `includeFields` array to select default workflow fields
- Fine-grained control over message content

**Configuration**:

```groovy title="06-selective-fields.config"
slack {
    bot {
        token = System.getenv('SLACK_BOT_TOKEN')
        channel = 'general'
    }

    onStart {
        message = [
            text: '🚀 *Pipeline started*',
            color: '#3AA3E3',
            includeFields: ['runName', 'status']
        ]
    }

    onComplete {
        message = [
            text: '✅ *Pipeline completed*',
            color: '#2EB887',
            includeFields: ['runName', 'duration', 'status', 'tasks']
        ]
    }

    onError {
        message = [
            text: '❌ *Pipeline failed*',
            color: '#A30301',
            includeFields: ['runName', 'duration', 'errorMessage', 'failedProcess']
        ]
    }
}
```

**Available fields by event**:

**All messages**:

- `runName` - Nextflow run name
- `status` - Workflow status

**Start messages only**:

- `commandLine` - Command used to launch workflow
- `workDir` - Work directory path

**Complete messages only**:

- `duration` - How long the workflow ran
- `tasks` - Task statistics (cached, completed, failed)

**Error messages only**:

- `duration` - How long before failure
- `errorMessage` - Error details
- `failedProcess` - Which process failed

!!! warning "Important"

    If you use map-based config without `includeFields`, NO default fields are included (only your `customFields` if specified).

**Use when**: You want fine-grained control over what information appears

**Output**:

![Selective Fields Example](../images/nf-slack-examples-06.png)

---

### Example 7: Footer Control

**Concept**: Control whether messages include a timestamp footer

**New concepts**:

- `showFooter` - Enable/disable timestamp footer per event

**Configuration**:

```groovy title="07-footer-control.config"
slack {
    bot {
        token = System.getenv('SLACK_BOT_TOKEN')
        channel = 'general'
    }

    onStart {
        showFooter = true    // Show timestamp (default)
    }

    onComplete {
        showFooter = false   // Hide footer for cleaner look
    }

    onError {
        showFooter = true    // Show timestamp on errors
    }
}
```

**Use when**: You want to reduce visual clutter by hiding timestamps on routine notifications.

![Footer Control Example](../images/nf-slack-examples-07.png)

---

### Example 8: Specific Channel ID

**Concept**: Send notifications to a specific channel by ID

**Configuration**:

```groovy title="08-channel-id.config"
slack {
    bot {
        token = System.getenv('SLACK_BOT_TOKEN')
        // You can use a channel ID (e.g. 'C12345678') or a channel name
        channel = 'C12345678'
    }
}
```

**Use when**: You want to send notifications to a specific channel by ID instead of name.

---

### Example 9: Threaded Messages

**Concept**: Group all workflow notifications (start, complete, error) into a single thread

**New concepts**:

- `useThreads` - Enable threading to reduce channel clutter
- Each workflow run creates a new thread automatically

**Configuration**:

```groovy title="09-threaded-messages.config"
slack {
    bot {
        token = System.getenv('SLACK_BOT_TOKEN')
        channel = 'general'
        useThreads = true  // Group all workflow messages in a thread
    }

    onStart {
        message = '🚀 *Pipeline started*'
    }

    onComplete {
        message = '✅ *Pipeline completed successfully*'
    }

    onError {
        message = '❌ *Pipeline failed*'
    }
}
```

![Threaded Messages Example](../images/nf-slack-examples-08.png)

**How it works**:

1. The initial "workflow started" message creates a new thread
2. Subsequent messages (complete/error) are posted as replies to that thread
3. **Custom messages** sent via `slackMessage()` are also posted in the thread
4. Each new workflow run creates a separate thread

**Important notes**:

- ⚠️ Threading only works with bot tokens (see [Threading configuration](../usage/configuration.md#threading) for details)
- ✅ Reduces channel clutter by keeping related messages together
- ✅ Each workflow run gets its own thread
- ✅ Custom `slackMessage()` calls are automatically included in the thread

**Use when**: You want to keep workflow notifications organized and reduce noise in busy channels.

---

## Script Examples

Script examples demonstrate how to use the `slackMessage()` function programmatically within your Nextflow workflows.

!!! note "Disable Automatic Notifications"

    These examples disable automatic notifications to avoid duplicate messages. Adjust your configuration:

    ```groovy
    slack {
        bot {
            token = System.getenv("SLACK_BOT_TOKEN")
            channel = 'general'
        }

        onStart.enabled = false
        onComplete.enabled = false
        onError.enabled = false
    }
    ```

### Script Example 1: Message in Workflow

**Concept**: Send a message from the workflow body after processes complete

**Code**:

```groovy title="01-message-in-workflow.nf"
#!/usr/bin/env nextflow

// Import the Slack messaging function
include { slackMessage } from 'plugin/nf-slack'

process HELLO {
    input:
    val sample_id

    output:
    stdout

    script:
    """
    echo "Processing sample: ${sample_id}"
    sleep 2  # Simulate some work
    echo "${sample_id}_processed"
    """
}

workflow {
    inputs = channel.of('sample_1', 'sample_2', 'sample_3')
    HELLO(inputs)

    // Send rich formatted completion message
    slackMessage([
        message: "Example workflow complete! 🎉",
        color: "#2EB887",  // Green for success
        fields: [
            [title: "Status", value: "Success", short: true],
            [title: "Samples", value: "3", short: true]
        ]
    ])
}
```

**Key features**:

- Uses `slackMessage()` function directly in workflow body
- Sends message after processes complete
- Supports same map format as config examples

**Use when**: You want to send a message at a specific point in your workflow logic

**Output**:

![Message in Workflow Example](../images/nf-slack-examples-08.png)

---

### Script Example 2: Message on Complete

**Concept**: Send a message using the `workflow.onComplete` event handler

**Code**:

```groovy title="02-message-on-complete.nf"
#!/usr/bin/env nextflow

include { slackMessage } from 'plugin/nf-slack'

process HELLO {
    input:
    val sample_id

    output:
    stdout

    script:
    """
    echo "Processing sample: ${sample_id}"
    """
}

workflow {
    inputs = channel.of('sample_1', 'sample_2', 'sample_3')
    HELLO(inputs)

    // Send message when workflow completes
    workflow.onComplete = {
        def status = workflow.success ? '✅ SUCCESS' : '❌ FAILED'
        def color = workflow.success ? '#2EB887' : '#A30301'

        slackMessage([
            message: "Workflow ${status}",
            color: color,
            fields: [
                [title: "Duration", value: "${workflow.duration}", short: true]
            ]
        ])
    }
}
```

**Key features**:

- Uses `workflow.onComplete` event handler
- Access to workflow metadata (success status, duration, etc.)
- Conditional message formatting based on success/failure

**Use when**: You want to send a summary message when the workflow finishes, with access to workflow metadata

**Output**:

![Message on Complete Example](../images/nf-slack-examples-09.png)

---

### Script Example 3: Message within Channel

**Concept**: Send messages from within channel operators during processing

**Code**:

```groovy title="03-message-within-channel.nf"
#!/usr/bin/env nextflow

include { slackMessage } from 'plugin/nf-slack'

process HELLO {
    input:
    val sample_id

    output:
    stdout

    script:
    """
    echo "Processing sample: ${sample_id}"
    """
}

workflow {
    inputs = channel.of('sample_1', 'sample_2', 'sample_3')
        .map { sample ->
            // Send a message for each item
            slackMessage("⚙️ Processing ${sample}")
            return sample
        }

    HELLO(inputs)
}
```

**Key features**:

- Uses `slackMessage()` within channel operator (`.map`)
- Sends individual messages for each channel item
- Simple string format for quick notifications

!!! warning "High Volume Usage"

    Be cautious when sending messages in channel operators with many items. Slack has rate limits on incoming webhooks, and sending too many messages rapidly may result in throttling.

**Use when**: You want to send notifications during data processing, tracking progress through a channel

**Output**:

![Message within Channel Example](../images/nf-slack-examples-10.png)

---

## Next Steps

- Explore the [API Reference](../reference/api.md) for complete configuration options
- Learn about [automatic notifications](../usage/automatic-notifications.md)
- Discover [custom messages](../usage/custom-messages.md)

## All Example Files

All example files are available in the [nf-slack repository](https://github.com/seqeralabs/nf-slack/tree/main/example):

- Configuration examples: `example/configs/`
- Script examples: `example/scripts/`
- Base workflow: `example/main.nf`

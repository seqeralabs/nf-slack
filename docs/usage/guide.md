# Usage Guide

This guide covers everything you can do with nf-slack after [setup](../getting-started/setup.md).

## Automatic Notifications

By default, nf-slack sends three notifications:

| Event        | When                           | Color |
| ------------ | ------------------------------ | ----- |
| **Start**    | Pipeline begins                | Blue  |
| **Complete** | Pipeline finishes successfully | Green |
| **Error**    | Pipeline fails                 | Red   |

Each notification includes workflow details like name, run name, start time, and more.

### Enable or Disable Events

Control which events trigger notifications:

```groovy
slack {
    bot {
        token = System.getenv('SLACK_BOT_TOKEN')
        channel = 'general'
    }

    // Disable start notifications
    onStart {
        enabled = false
    }

    // Only notify on error
    onComplete {
        enabled = false
    }
}
```

**Common patterns:**

| Pattern              | onStart | onComplete | onError |
| -------------------- | ------- | ---------- | ------- |
| All events (default) | `true`  | `true`     | `true`  |
| Errors only          | `false` | `false`    | `true`  |
| Start + error        | `true`  | `false`    | `true`  |
| Completion only      | `false` | `true`     | `false` |

### Disable All Notifications

```groovy
slack {
    enabled = false  // Master switch — disables everything
}
```

## Customizing Messages

### Custom Text

Add a message to any event notification:

```groovy
slack {
    bot {
        token = System.getenv('SLACK_BOT_TOKEN')
        channel = 'general'
    }
    onComplete {
        message = "Analysis finished for project X"
    }
    onError {
        message = "Pipeline failed! Check logs."
    }
}
```

### Colors and Custom Fields

Use a map to control color and add extra fields:

```groovy
slack {
    bot {
        token = System.getenv('SLACK_BOT_TOKEN')
        channel = 'general'
    }
    onComplete {
        message = [
            text: 'Exome analysis complete',
            color: '#8B5CF6',
            customFields: [
                [title: 'Samples', value: '24', short: true],
                [title: 'Pipeline', value: 'sarek', short: true],
            ]
        ]
    }
}
```

### Choose Which Workflow Fields to Show

By default, notifications include many workflow fields. Use `includeFields` to show only what you need:

```groovy
slack {
    bot {
        token = System.getenv('SLACK_BOT_TOKEN')
        channel = 'general'
    }
    onComplete {
        includeFields = ['runName', 'duration', 'status']
    }
}
```

See the [API Reference](../reference/api.md#slackscopemessageincludefields) for all available fields.

### Command Line and Resource Usage

```groovy
slack {
    bot {
        token = System.getenv('SLACK_BOT_TOKEN')
        channel = 'general'
    }
    onStart {
        includeCommandLine = true   // Show the full nextflow command
    }
    onComplete {
        includeResourceUsage = true // Show CPU and memory stats
    }
}
```

### Footer

```groovy
slack {
    bot {
        token = System.getenv('SLACK_BOT_TOKEN')
        channel = 'general'
    }
    onComplete {
        showFooter = false  // Hide the nf-slack attribution footer
    }
}
```

## Threading

<!-- prettier-ignore -->
!!! note "Bot User only"
    Threading requires a Bot User. Webhooks do not support this feature.

Group all notifications from a single pipeline run into one Slack thread:

```groovy
slack {
    bot {
        token = System.getenv('SLACK_BOT_TOKEN')
        channel = 'general'
    }
    useThreads = true
}
```

The start notification becomes the parent message. Complete, error, and any custom messages sent via `slackMessage()` appear as replies in the thread.

## Emoji Reactions

<!-- prettier-ignore -->
!!! note "Bot User only"
    Emoji reactions require a Bot User. Webhooks do not support this feature.

Add emoji reactions to the start notification to show pipeline status at a glance:

```groovy
slack {
    bot {
        token = System.getenv('SLACK_BOT_TOKEN')
        channel = 'general'
    }
    reactions {
        enabled = true
        onStart   = 'rocket'             // 🚀 when pipeline starts
        onSuccess = 'white_check_mark'   // ✅ when pipeline succeeds
        onError   = 'x'                  // ❌ when pipeline fails
    }
}
```

## Progress Updates

<!-- prettier-ignore -->
!!! note "Bot User only"
    Progress updates require a Bot User with threading enabled.

Get periodic progress updates during long-running pipelines:

```groovy
slack {
    bot {
        token = System.getenv('SLACK_BOT_TOKEN')
        channel = 'general'
    }
    useThreads = true
    onProgress {
        enabled = true
        interval = '5m'  // Update every 5 minutes
    }
}
```

Progress messages are posted as replies in the pipeline's thread.

## File Uploads

<!-- prettier-ignore -->
!!! note "Bot User only"
    File uploads require a Bot User.

Attach files (logs, reports, plots) to completion or error notifications:

```groovy
slack {
    bot {
        token = System.getenv('SLACK_BOT_TOKEN')
        channel = 'general'
    }
    onComplete {
        files = ['results/multiqc_report.html', 'results/summary.txt']
    }
    onError {
        files = ['.nextflow.log']
    }
}
```

File paths are relative to the pipeline launch directory.

## Seqera Platform Integration

If you run pipelines through [Seqera Platform](https://seqera.io/platform/), nf-slack can automatically add contextual action buttons to Slack notifications:

```groovy
slack {
    bot {
        token = System.getenv('SLACK_BOT_TOKEN')
        channel = 'general'
    }
    seqeraPlatform {
        enabled = true
        actionButtons {
            cancel = true      // links to POST /workflow/:id/cancel API path
            resume = true      // reserved — no per-run URL yet
            relaunch = true    // reserved — no per-run URL yet
        }
    }
}
```

### Button behavior by workflow phase

| Phase                    | Buttons      |
| ------------------------ | ------------ |
| Running (start/progress) | View, Cancel |
| Failed (error)           | View         |
| Completed                | View         |

- **View** opens the Platform run page (`watchUrl` from nf-tower).
- **Cancel** links to `{tower.endpoint}/workflow/{runId}/cancel` ([Platform API](https://docs.seqera.io/platform-api/cancel-workflow)). The API endpoint is derived from `tower.endpoint` in your Nextflow config.
- **Resume** and **Relaunch** are not shown yet — they require `POST /workflow/launch` with a prepared body rather than a per-run URL.

## Custom Messages from Code

Send messages from anywhere in your Nextflow pipeline code using the `slackMessage()` and `slackFileUpload()` functions.

### Import

```groovy
include { slackMessage; slackFileUpload } from 'plugin/nf-slack'
```

### Send a Text Message

```groovy
slackMessage("Processing complete: ${params.sample_id}")
```

### Send a Rich Message

```groovy
slackMessage([
    message: 'Analysis Results',
    fields: [
        [title: 'Variants Found', value: '1,234', short: true],
        [title: 'Quality Score', value: '98.5%', short: true],
    ]
])
```

### Upload a File

```groovy
slackFileUpload('results/report.html')
```

Or with options:

```groovy
slackFileUpload([
    file: 'results/report.html',
    title: 'Analysis Report',
    comment: 'Final results attached',
    filename: 'report.html'
])
```

### Common Patterns

**Send results in `workflow.onComplete`:**

```groovy
workflow.onComplete {
    if (workflow.success) {
        slackMessage("Pipeline finished successfully in ${workflow.duration}")
        slackFileUpload('results/multiqc_report.html')
    } else {
        slackMessage("Pipeline failed: ${workflow.errorMessage}")
        slackFileUpload('.nextflow.log')
    }
}
```

**Conditional notification in a process:**

```groovy
process ANALYZE {
    // ...
    script:
    """
    run_analysis.sh
    """
}

workflow {
    results = ANALYZE(input_ch)
    results.count().map { n ->
        if (n > 1000) slackMessage("Large batch complete: ${n} samples processed")
    }
}
```

## Connection Validation

Verify your Slack connection before running the pipeline:

```groovy
slack {
    bot {
        token = System.getenv('SLACK_BOT_TOKEN')
        channel = 'general'
    }
    validateOnStartup = true
}
```

This checks the token/webhook and channel access at pipeline start, failing fast if there's a configuration problem.

## What's Next

- [Examples](../examples/gallery.md) — Copy-paste configurations for common scenarios
- [API Reference](../reference/api.md) — Complete configuration reference

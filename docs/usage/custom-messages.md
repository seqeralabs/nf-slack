# Custom Messages

Send custom Slack messages from within your workflow scripts using the `slackMessage()` function.

!!! tip "Prerequisites"

    The plugin must be enabled and configured for custom messages to work. See [Quick Start](../getting-started/quick-start.md) if you haven't set up the plugin yet.

## Basic Usage

### Simple Text Messages

Send a simple text message:

```groovy
include { slackMessage } from 'plugin/nf-slack'

workflow {
    slackMessage("🔬 Starting analysis for sample ${params.sample_id}")

    // Your workflow logic here
    MY_PROCESS(input_ch)

    slackMessage("✅ Analysis complete!")
}
```

![Simple custom messages](../images/nf-slack-04.png)

## Rich Formatted Messages

### Adding Custom Fields

Create rich messages with custom fields:

```groovy
include { slackMessage } from 'plugin/nf-slack'

workflow {
    slackMessage([
        message: "Analysis Results",
        fields: [
            [title: "Sample", value: params.sample_id, short: true],
            [title: "Status", value: "Success", short: true],
            [title: "Total Variants", value: "1,234", short: true],
            [title: "Duration", value: "2h 30m", short: true]
        ]
    ])
}
```

### Message Structure

When using the map format, you can specify:

| Property  | Type   | Description            | Required |
| --------- | ------ | ---------------------- | -------- |
| `message` | String | Main message text      | Yes      |
| `fields`  | List   | Array of field objects | No       |

### Field Structure

Each field in the `fields` array can have:

| Property | Type    | Description                            | Required |
| -------- | ------- | -------------------------------------- | -------- |
| `title`  | String  | Field label                            | Yes      |
| `value`  | String  | Field content                          | Yes      |
| `short`  | Boolean | Show in column layout (default: false) | No       |

```groovy
// Success message
slackMessage([
    message: "✅ Pipeline completed successfully"
])

// Error message
slackMessage([
    message: "❌ Quality control failed"
])

// Info message
slackMessage([
    message: "ℹ️ Processing 100 samples"
])

// Warning message
slackMessage([
    message: "⚠️ Low coverage detected"
])
```

## Common Use Cases

### Send Results Summary

Notify when analysis completes with summary statistics:

```groovy
include { slackMessage } from 'plugin/nf-slack'

workflow {
    ANALYZE_DATA(input_ch)

    ANALYZE_DATA.out.results
        .map { sample, vcf, stats ->
            slackMessage([
                message: "Sample ${sample} analyzed",
                fields: [
                    [title: "Sample", value: sample, short: true],
                    [title: "Variants", value: stats.variant_count, short: true],
                    [title: "Quality", value: stats.mean_quality, short: true]
                ]
            ])
        }
}
```

### Progress Updates

Send notifications at key workflow milestones:

```groovy
include { slackMessage } from 'plugin/nf-slack'

workflow {
    slackMessage("🚀 Starting processing of ${sample_count} samples")

    QUALITY_CONTROL(input_ch).map { results ->
        slackMessage("✅ Quality control complete")
    }

    ALIGNMENT(QUALITY_CONTROL.out).map { results ->
        slackMessage("✅ Alignment complete")
    }

    VARIANT_CALLING(ALIGNMENT.out).map { results ->
        slackMessage("✅ Variant calling complete")
    }
}
```

### Conditional Notifications

Send messages based on conditions:

```groovy
include { slackMessage } from 'plugin/nf-slack'

workflow {
    QUALITY_CHECK(input_ch)
        .branch { sample, qc ->
            pass: qc.score >= 30
            fail: qc.score < 30
        }
        .set { qc_results }

    // Notify on failures
    qc_results.fail
        .map { sample, qc ->
            slackMessage([
                message: "⚠️ Quality check failed for ${sample}",
                fields: [
                    [title: "Sample", value: sample, short: true],
                    [title: "Score", value: qc.score.toString(), short: true]
                ]
            ])
        }
}
```

### Batch Notifications

Collect results and send a summary:

```groovy
include { slackMessage } from 'plugin/nf-slack'

workflow {
    PROCESS_SAMPLES(input_ch)
        .collect()
        .map { results ->
            def total = results.size()
            def success = results.count { it.status == 'success' }
            def failed = results.count { it.status == 'failed' }

            slackMessage([
                message: "Batch processing complete",
                fields: [
                    [title: "Total Samples", value: total.toString(), short: true],
                    [title: "Successful", value: success.toString(), short: true],
                    [title: "Failed", value: failed.toString(), short: true]
                ]
            ])
        }
}
```

## File Uploads

Upload files to Slack using `slackFileUpload()`. This is the file equivalent of `slackMessage()`.

> [!NOTE]
> File uploads requires a Bot User with `files:write` scope. See [Bot Setup](../getting-started/bot-setup.md) for details.

### Basic Usage

```groovy
include { slackFileUpload } from 'plugin/nf-slack'

workflow {
    // Simple — just a file path
    slackFileUpload('/path/to/results/report.html')

    // With metadata
    slackFileUpload(
        file: '/path/to/results/qc_plot.png',
        title: 'Quality Control Plot',
        comment: 'QC results look good!',
        filename: 'qc-results.png'
    )
}
```

### Options

| Option     | Type     | Required | Description                                                                        |
| ---------- | -------- | -------- | ---------------------------------------------------------------------------------- |
| `file`     | `String` | Yes      | Path to the file. Supports local paths and remote URIs (`s3://`, `az://`, `gs://`) |
| `title`    | `String` | No       | Title displayed in Slack (defaults to filename)                                    |
| `comment`  | `String` | No       | Comment posted alongside the file                                                  |
| `filename` | `String` | No       | Custom filename in Slack (defaults to original filename)                           |

### Upload in a Completion Handler

Use Nextflow's `workflow.onComplete` handler for conditional uploads based on workflow status:

```groovy
include { slackFileUpload } from 'plugin/nf-slack'

workflow.onComplete {
    slackFileUpload(
        file: "${workflow.launchDir}/results/pipeline_report.html",
        title: 'Pipeline Report',
        comment: workflow.success ? '✅ Pipeline completed!' : '❌ Pipeline failed'
    )

    if (workflow.success) {
        slackFileUpload(
            file: "${workflow.launchDir}/results/multiqc_report.html",
            title: 'MultiQC Report'
        )
    }
}

workflow {
    // ... your pipeline processes
}
```

## Next Steps

- Learn about [automatic notifications](automatic-notifications.md)
- Explore [configuration options](configuration.md)
- View [example gallery](../examples/gallery.md) for real-world patterns
- Check the [API reference](../reference/api.md) for all options

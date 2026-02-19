# API Reference

Complete API reference for nf-slack plugin configuration options and functions.

> **Quick Links:**
>
> - [Usage Guide](../usage/guide.md) - Learn how to send custom messages
> - [Examples](../examples/gallery.md) - See practical examples

---

## Configuration

### `slack`

| Property            | Type    | Default                                    | Required | Description                                                        |
| ------------------- | ------- | ------------------------------------------ | -------- | ------------------------------------------------------------------ |
| `enabled`           | Boolean | `true`                                     | No       | Master switch to enable/disable the plugin                         |
| `bot`               | Closure | -                                          | No\*     | Bot configuration block (see [`slack.bot`](#slackbot))             |
| `webhook`           | Closure | -                                          | No\*     | Webhook configuration block (see [`slack.webhook`](#slackwebhook)) |
| `useThreads`        | Boolean | `false`                                    | No       | Group all notifications in a single thread (Bot only)              |
| `onStart`           | Closure | See [`slack.onStart`](#slackonstart)       | No       | Configuration for workflow start notifications                     |
| `onComplete`        | Closure | See [`slack.onComplete`](#slackoncomplete) | No       | Configuration for workflow completion notifications                |
| `onError`           | Closure | See [`slack.onError`](#slackonerror)       | No       | Configuration for workflow error notifications                     |
| `validateOnStartup` | Boolean | `true`                                     | No       | Validate Slack connection credentials on pipeline startup          |

\*Either `webhook` or `bot` is required. If neither is configured, the plugin will automatically disable itself.

#### Example

```groovy
slack {
    enabled = true
    bot { /* ... */ }
    onStart { /* ... */ }
    onComplete { /* ... */ }
    onError { /* ... */ }
}
```

---

### `slack.bot`

| Property  | Type   | Default | Required | Description                                                                  |
| --------- | ------ | ------- | -------- | ---------------------------------------------------------------------------- |
| `token`   | String | -       | Yes      | Bot User OAuth Token (starts with `xoxb-`)                                   |
| `channel` | String | -       | Yes      | Channel ID (e.g., `C12345678`) or Name (e.g., `general`) to send messages to |

#### Example

```groovy
bot {
    token = 'xoxb-your-token'
    channel = 'general'
    useThreads = true  // Optional: group messages in threads
}
```

---

### `slack.webhook`

| Property | Type   | Default | Required | Description                                                             |
| -------- | ------ | ------- | -------- | ----------------------------------------------------------------------- |
| `url`    | String | -       | Yes      | Slack Incoming Webhook URL (must start with `https://hooks.slack.com/`) |

#### Example

```groovy
webhook {
    url = 'https://hooks.slack.com/services/YOUR/WEBHOOK/URL'
}
```

---

### `slack.onStart`

Configuration for workflow start notifications.

#### Properties

| Property             | Type          | Default                   | Description                            |
| -------------------- | ------------- | ------------------------- | -------------------------------------- |
| `enabled`            | Boolean       | `true`                    | Send notification when workflow starts |
| `message`            | String or Map | `'🚀 *Pipeline started*'` | Start notification message             |
| `includeCommandLine` | Boolean       | `true`                    | Include command line in message        |
| `showFooter`         | Boolean       | `true`                    | Show timestamp footer in message       |

#### Message Available Fields

- `runName` - Workflow run name
- `status` - Current status (always "Running" for start messages)
- `commandLine` - Full Nextflow command
- `workDir` - Working directory path

#### Example

```groovy
onStart {
    message = [
        text: '🚀 *Production Pipeline Starting*',
        color: '#3AA3E3',
        includeFields: ['runName', 'status', 'commandLine'],
        customFields: [
            [title: 'Environment', value: 'Production', short: true],
            [title: 'Priority', value: 'High', short: true]
        ]
    ]
}
```

---

### `slack.onComplete`

Configuration for workflow completion notifications.

#### Properties

| Property               | Type           | Default                                  | Description                                                   |
| ---------------------- | -------------- | ---------------------------------------- | ------------------------------------------------------------- |
| `enabled`              | Boolean        | `true`                                   | Send notification when workflow completes                     |
| `message`              | String or Map  | `'✅ *Pipeline completed successfully*'` | Completion notification message                               |
| `includeCommandLine`   | Boolean        | `true`                                   | Include command line in message                               |
| `includeResourceUsage` | Boolean        | `true`                                   | Include task statistics and resource usage                    |
| `showFooter`           | Boolean        | `true`                                   | Show timestamp footer in message                              |
| `files`                | `List<String>` | `[]`                                     | File paths to upload after completion notification (Bot only) |

<!-- prettier-ignore -->
!!! note
    `includeResourceUsage` is **only available** in the `onComplete` scope.

#### Message Available Fields

- `runName` - Workflow run name
- `status` - Final status (e.g., "OK")
- `duration` - Total workflow runtime
- `commandLine` - Full Nextflow command
- `tasks` - Task execution statistics (count, succeeded, failed, cached)

#### Example

```groovy
onComplete {
    message = [
        text: '✅ *Analysis Complete*',
        color: '#2EB887',
        includeFields: ['runName', 'duration', 'tasks'],
        customFields: [
            [title: 'Results', value: 's3://bucket/results/', short: false],
            [title: 'Cost', value: '$12.50', short: true]
        ]
    ]
    includeResourceUsage = true
}
```

---

### `slack.onError`

Configuration for workflow error notifications.

#### Properties

| Property             | Type           | Default                  | Description                                              |
| -------------------- | -------------- | ------------------------ | -------------------------------------------------------- |
| `enabled`            | Boolean        | `true`                   | Send notification when workflow fails                    |
| `message`            | String or Map  | `'❌ *Pipeline failed*'` | Error notification message                               |
| `includeCommandLine` | Boolean        | `true`                   | Include command line in message                          |
| `showFooter`         | Boolean        | `true`                   | Show timestamp footer in message                         |
| `files`              | `List<String>` | `[]`                     | File paths to upload after error notification (Bot only) |

### `slack.seqeraPlatform`

| Property  | Type      | Default | Description                                               |
| --------- | --------- | ------- | --------------------------------------------------------- |
| `enabled` | `Boolean` | `true`  | Enable Seqera Platform deep link buttons in notifications |

---

#### Message Available Fields

- `runName` - Workflow run name
- `status` - Error status

### `slack.reactions`

| Property    | Type      | Default              | Description                                 |
| ----------- | --------- | -------------------- | ------------------------------------------- |
| `enabled`   | `Boolean` | `false`              | Enable emoji reactions on the start message |
| `onStart`   | `String`  | `'rocket'`           | Emoji reaction added when pipeline starts   |
| `onSuccess` | `String`  | `'white_check_mark'` | Emoji reaction on successful completion     |
| `onError`   | `String`  | `'x'`                | Emoji reaction on pipeline error            |

> Reactions require a bot token. They are silently skipped when using webhooks.

- `duration` - Time before failure
- `commandLine` - Full Nextflow command
- `errorMessage` - Error details
- `failedProcess` - Name of the process that failed

#### Example

```groovy
onError {
    message = [
        text: '❌ *Pipeline Failed*',
        color: '#A30301',
        includeFields: ['runName', 'duration', 'errorMessage', 'failedProcess'],
        customFields: [
            [title: 'Support', value: 'support@example.com', short: true],
            [title: 'On-Call', value: '@devops', short: true]
        ]
    ]
}
```

---

### `slack.onProgress`

| Property   | Type      | Default | Description                                                                        |
| ---------- | --------- | ------- | ---------------------------------------------------------------------------------- |
| `enabled`  | `Boolean` | `false` | Enable periodic progress update messages                                           |
| `interval` | `String`  | `'5m'`  | Update interval. Supports time suffixes: `s` (seconds), `m` (minutes), `h` (hours) |

---

### `slack.<scope>.message (String)`

Use a string for quick, simple message customization. Supports Slack markdown (`*bold*`, `_italic_`, `` `code` ``), emojis, and newlines (`\n`).

#### Example

```groovy
onStart {
    message = '🚀 *Pipeline started*\nRunning on cluster'
}
```

---

### `slack.<scope>.message (Map)`

Use a map for full control with colors, fields, and custom data.

**Properties:**

- `text` (required) - Main message text
- `color` - Hex color code (e.g., `#2EB887`)
- `includeFields` - List of default fields (see [`slack.<scope>.message.includeFields`](#slackscopemessageincludefields))
- `customFields` - List of custom fields with `title`, `value`, and optional `short` (boolean for 2-column layout)

#### Example

```groovy
onComplete {
    message = [
        text: '✅ *Analysis Complete*',
        color: '#2EB887',
        includeFields: ['runName', 'duration', 'status'],
        customFields: [
            [title: 'Sample Count', value: '150', short: true],
            [title: 'Output Location', value: 's3://bucket/results/', short: false]
        ]
    ]
}
```

---

### `slack.<scope>.message.includeFields`

The following fields can be included in the `includeFields` array when using map format:

| Field Name      | Description                                   | Available In            |
| --------------- | --------------------------------------------- | ----------------------- |
| `runName`       | Nextflow run name (e.g., `tiny_euler`)        | All scopes              |
| `status`        | Workflow status with emoji                    | All scopes              |
| `duration`      | Total workflow runtime (e.g., `2h 30m 15s`)   | `onComplete`, `onError` |
| `commandLine`   | Full Nextflow command used to launch workflow | All scopes              |
| `workDir`       | Working directory path                        | `onStart`               |
| `errorMessage`  | Error details and stack trace                 | `onError`               |
| `failedProcess` | Name and tag of the process that failed       | `onError`               |
| `tasks`         | Task execution statistics                     | `onComplete`            |

---

#### Color Reference

Standard color codes for Slack message attachments:

| Name           | Hex Code  | Use Case                      |
| -------------- | --------- | ----------------------------- |
| Success Green  | `#2EB887` | Successful completions        |
| Error Red      | `#A30301` | Failures and errors           |
| Info Blue      | `#3AA3E3` | Informational, start messages |
| Warning Orange | `#FFA500` | Warnings                      |
| Neutral Gray   | `#808080` | Neutral information           |

---

## Functions

### `slackMessage(String message)`

| Parameter | Type   | Required | Description           |
| --------- | ------ | -------- | --------------------- |
| `message` | String | Yes      | Text to send to Slack |

#### Example

```groovy
slackMessage("Processing sample ${sample_id}")
```

### `slackMessage(Map options)`

| Property  | Type        | Required | Description                            |
| --------- | ----------- | -------- | -------------------------------------- |
| `message` | String      | Yes      | Message text (supports Slack markdown) |
| `color`   | String      | No       | Hex color code (e.g., `"#2EB887"`)     |
| `fields`  | List\<Map\> | No       | Array of custom field objects          |

#### Example

```groovy
slackMessage([
    message: "Analysis complete!",
    color: "#2EB887",
    fields: [
        [
            title: "Status",
            value: "Success",
            short: true
        ],
        [
            title: "Samples Processed",
            value: "42",
            short: true
        ]
    ]
])
```

#### Fields

When using the map format with custom `fields`, each field object supports:

| Property | Type    | Required | Description                                      |
| -------- | ------- | -------- | ------------------------------------------------ |
| `title`  | String  | Yes      | Field label/name                                 |
| `value`  | String  | Yes      | Field content                                    |
| `short`  | Boolean | No       | Layout: `true` = 2 columns, `false` = full width |

**With workflow metadata**:

```groovy
workflow {
    workflow.onComplete = {
        def status = workflow.success ? "✅ SUCCESS" : "❌ FAILED"
        def color = workflow.success ? "#2EB887" : "#A30301"

        slackMessage([
            message: "Workflow ${status}",
            color: color,
            fields: [
                [title: "Duration", value: "${workflow.duration}", short: true],
                [title: "Exit Status", value: "${workflow.exitStatus}", short: true]
            ]
        ])
    }
}
```

#### Return Value

The function does not return anything.

---

### `slackFileUpload`

Upload a file to Slack. Requires Bot User with `files:write` scope.

#### Simple form

| Parameter  | Type   | Description                |
| ---------- | ------ | -------------------------- |
| `filePath` | String | Path to the file to upload |

```groovy
slackFileUpload('/path/to/report.html')
```

#### Map form

| Parameter  | Type   | Required | Description                            |
| ---------- | ------ | -------- | -------------------------------------- |
| `file`     | String | Yes      | Path to the file (local or remote URI) |
| `title`    | String | No       | Title displayed in Slack               |
| `comment`  | String | No       | Comment posted with the file           |
| `filename` | String | No       | Custom filename                        |

```groovy
slackFileUpload(
    file: 'results/qc_plot.png',
    title: 'QC Results',
    comment: 'Analysis complete',
    filename: 'qc-results.png'
)
```

---

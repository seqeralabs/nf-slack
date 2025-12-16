# Installation

This guide covers installing the nf-slack plugin in your Nextflow pipeline.

## Prerequisites

- Nextflow v25.04.0 or later
- A Bot Token (see [Bot Setup guide](bot-setup.md)) OR Slack webhook URL (see [Webhook Setup guide](webhook-setup.md))

## Adding the Plugin

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

## Specifying a Version

You can specify a particular version of the plugin:

```groovy
plugins {
    id 'nf-slack@0.3.0'  // Use a specific version
}
```

To use the latest version, omit the version number:

```groovy
plugins {
    id 'nf-slack'  // Uses the latest version
}
```

## Manual Installation

Nextflow will automatically download and install the plugin when you run your pipeline. However, if you want to manually install it, you can do so using the Nextflow CLI:

```bash
nextflow plugin install nf-slack
```

You should see `nf-slack` in the list of installed plugins.

## Local Installation (Development)

If you want to install a local development version of the plugin:

1. **Clone the repository**

   ```bash
   git clone https://github.com/seqeralabs/nf-slack.git
   cd nf-slack
   ```

1. **Install locally**

   ```bash
   make install
   ```

This will build and install the plugin to your local Nextflow plugins directory. For more details on development setup, see the [Contributing guide](../CONTRIBUTING.md).

## Next Steps

- [Set up your Bot User](bot-setup.md) or [Slack webhook](webhook-setup.md)
- [Get started with basic notifications](quick-start.md)
- [Configure the plugin](../usage/configuration.md)

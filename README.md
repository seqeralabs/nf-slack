# nf-slack plugin

Get Slack notifications for your Nextflow workflows — automatically notified when pipelines start, complete, or fail.

:book: Full documentation: <https://seqeralabs.github.io/nf-slack/>

> [!IMPORTANT]
> This is an open-source project for community benefit. It is provided as-is and is not part of Seqera's officially supported toolset.

## Features

- **Automatic Notifications** — Workflow start, completion, and error events
- **Custom Messages and File Uploads** — From within your pipeline code
- **Threading, Reactions, and Progress Updates** — Keep channels organized
- **Seqera Platform Integration** — One-click navigation to your runs

## Quick Start

Add to your `nextflow.config`:

```groovy
plugins {
    id 'nf-slack@0.4.0'
}

slack {
    enabled = true
    bot {
        token = 'xoxb-your-bot-token'
        channel = 'pipeline-results'
    }
}
```

Run your pipeline — you'll get Slack notifications automatically:

![Default notifications](./docs/images/nf-slack-00.png)

See the [Setup Guide](https://seqeralabs.github.io/nf-slack/latest/getting-started/setup/) for bot and webhook configuration options.

## Documentation

- **[Setup Guide](https://seqeralabs.github.io/nf-slack/latest/getting-started/setup/)** — Installation, bot/webhook setup
- **[Usage Guide](https://seqeralabs.github.io/nf-slack/latest/usage/guide/)** — Notifications, threading, custom messages, file uploads
- **[Examples](https://seqeralabs.github.io/nf-slack/latest/examples/gallery/)** — Copy-paste configuration examples
- **[API Reference](https://seqeralabs.github.io/nf-slack/latest/reference/api/)** — All configuration options
- **[Contributing](docs/CONTRIBUTING.md)** — Development setup and guidelines

## Support

- [Report bugs](https://github.com/seqeralabs/nf-slack/issues)
- [Request features](https://github.com/seqeralabs/nf-slack/issues)

## License

Copyright 2025, Seqera Labs. Licensed under the Apache License, Version 2.0.

# Changelog

All notable changes to the nf-slack plugin will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.1] - 2025-11-03

### Changed

- **Automated Release Process**: Simplified release workflow to a single action (merge PR)
  - Modified `.github/workflows/publish.yml` to trigger on push to main branch
  - Workflow now automatically extracts version from `build.gradle`
  - Automatically creates git tags in `v{version}` format
  - Automatically creates GitHub releases with changelog notes
  - Idempotent: safe to re-run, skips if tag already exists
  - Updated `docs/CONTRIBUTING.md` with new release process documentation
  - Release now requires only: (1) create PR with version bump, (2) merge to main
- **Documentation Reorganization**: Simplified README from 455 to ~165 lines for better onboarding
  - Moved detailed content to dedicated documentation pages
  - Created `docs/USAGE.md` with comprehensive usage patterns and examples
  - Moved `example/configs/README.md` to `docs/EXAMPLES.md` for centralized documentation
  - Enhanced `docs/CONFIG.md` with cross-references to other documentation
  - README now focuses on quick start with clear paths to detailed documentation
  - Added basic customization examples without overwhelming API details

## [0.1.0] - 2025-10-30

### Added

#### Core Features

- **Automatic Workflow Notifications**: Plugin automatically sends Slack notifications for workflow lifecycle events (start, completion, error) without requiring any code changes
- **Custom In-Workflow Messages**: New `slackMessage()` function extension allows sending custom Slack notifications from within workflow scripts
- **Progressive Configuration**: Six example configurations (01-minimal through 06-selective-fields) demonstrating feature adoption path from basic to advanced usage

#### Configuration Options

- `enabled` - Enable/disable the plugin (default: true if webhook configured)
- `webhook` - Slack Incoming Webhook URL (required, supports Nextflow secrets)
- `notifyOnStart` - Send notification when workflow starts (default: true)
- `notifyOnComplete` - Send notification when workflow completes (default: true)
- `notifyOnError` - Send notification when workflow errors (default: true)
- `username` - Custom bot display name (default: "Nextflow Bot")
- `iconEmoji` - Custom bot icon emoji (default: ":rocket:")
- `includeCommandLine` - Include command line in messages (default: true)
- `includeResourceUsage` - Include task statistics in completion messages (default: true)

#### Message Customization

- **Simple String Format**: `startMessage = "🚀 Pipeline started"`
- **Formatted Message**:
  ```groovy
  startMessage = [
      text: "Custom message",
      color: "#2EB887",
      includeFields: ['runName', 'duration', 'status'],
      customFields: [[title: "Environment", value: "Production", short: true]]
  ]
  ```
- **Available includeFields**: `runName`, `status`, `duration`, `commandLine`, `workDir`, `errorMessage`, `failedProcess`, `tasks`

#### Message Formatting

- Rich Slack Block Kit formatting with colored attachments
- Semantic color coding: Green (#2EB887) for success, Red (#A30301) for errors, Blue (#3AA3E3) for info
- Structured fields for workflow metadata (run name, duration, task statistics)
- Code-formatted command lines and error messages
- Nextflow icon and branding in message author section
- Timestamp footer showing event time

#### Custom Functions

- `slackMessage(String text)` - Send simple text message
- `slackMessage(Map options)` - Send rich formatted message with custom fields and colors
  - Supports `message` (required), `color` (optional), `fields` (optional list of maps)

#### Reliability Features

- **Graceful Error Handling**: Plugin never fails workflow execution, even if Slack is unavailable
- **Error Deduplication**: Prevents console noise by logging each unique error only once
- **Fail-Safe Design**: All Slack operations wrapped in try-catch blocks
- **HTTP Error Handling**: Proper handling of 4xx and 5xx response codes

#### Security Features

- Webhook URL masking in logs (displays as `***configured***`)
- Support for Nextflow secrets: `webhook = secrets.SLACK_WEBHOOK`
- HTTPS-only webhook URLs enforced
- No sensitive data included in Slack messages

#### Documentation

- Comprehensive README with Quick Start guide
- Progressive configuration examples with detailed inline comments
- Example configs README documenting the learning progression
- Inline Groovydoc comments for all public methods
- Troubleshooting section in README

#### Testing

- Comprehensive test suite using Spock framework
- `SlackConfigTest` - Configuration parsing and validation
- `SlackClientTest` - HTTP client and error handling
- `SlackMessageBuilderTest` - Message formatting for all event types
- `SlackObserverTest` - Workflow event handling

### Technical Details

- **Language**: Groovy 3.x with Java 11+ compatibility
- **Framework**: Nextflow Plugin API (nextflow-plugin 1.0.0-beta.10)
- **Minimum Nextflow Version**: 24.10.0
- **Extension Points**:
  - `TraceObserver` for workflow lifecycle events
  - `FunctionExtension` for custom DSL functions
- **Dependencies**: No external dependencies beyond Nextflow plugin API

### Implementation Notes

- Plugin follows Nextflow plugin standard structure
- Configuration via standard `slack { }` configuration block
- Synchronous HTTP client using Groovy's HttpURLConnection
- Thread-safe error logging with synchronized collections
- Immutable configuration objects after initialization

## [Unreleased]

---

## Version History

- **[0.1.1]** - Release automation and documentation improvements
- **[0.1.0]** - Initial release with automatic notifications, custom messages, and progressive configuration examples

[0.1.1]: https://github.com/seqeralabs/nf-slack/releases/tag/v0.1.1
[0.1.0]: https://github.com/seqeralabs/nf-slack/releases/tag/v0.1.0

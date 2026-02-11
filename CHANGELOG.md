# Changelog

All notable changes to the nf-slack plugin will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.4.0] - 2026-02-11

### Added

- **File Upload Support**: Upload files to Slack channels using bot tokens ([#32](https://github.com/seqeralabs/nf-slack/pull/32))
  - New `slackFileUpload()` function for uploading files from within workflow scripts
  - Config-based file uploads via `onComplete.files` and `onError.files` for automatic uploads on pipeline completion or error
  - Support for remote files (S3, Azure Blob, GCS) via Nextflow's FileHelper
  - Files are uploaded to the same channel and thread as workflow messages
  - Three-step Slack API upload flow (get URL → upload → complete) with proper error handling

### Fixed

- **Threading Configuration**: Fixed `useThreads` config being read from wrong path (`slack.bot.useThreads` → `slack.useThreads`) ([#32](https://github.com/seqeralabs/nf-slack/pull/32))

### Changed

- **Examples Consolidation**: Simplified example configs from 6 separate files to 2 focused examples (1 config + 1 script) ([#32](https://github.com/seqeralabs/nf-slack/pull/32))

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
  - Created `docs/TROUBLESHOOTING.md` with common issues and solutions
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

## [0.2.0] - 2025-11-10

### Added

- **Documentation Site**: GitHub Pages site with MkDocs Material theme
  - Live documentation at https://seqeralabs.github.io/nf-slack/
  - Search, dark mode, mobile-responsive design
  - Automated deployment from `gh-pages` branch
  - Restructured docs with Getting Started, Usage, and API sections

## [0.2.1] - 2025-11-13

### Fixed

- **CI/CD**: Prevent docs deployment race conditions ([#15](https://github.com/seqeralabs/nf-slack/pull/15))
  - Fixed race condition in GitHub Actions workflow when deploying documentation
  - Ensures stable and reliable documentation deployments to GitHub Pages

### Changed

- **Build System**: Add run target to Makefile ([#14](https://github.com/seqeralabs/nf-slack/pull/14))
  - New `make run` target for easier local testing of example workflows
  - Simplifies development workflow for contributors

## [0.3.1] - 2026-01-05

### Changed

- **Documentation**: Updated bot support and threading documentation ([#27](https://github.com/seqeralabs/nf-slack/pull/27))

  - Added comprehensive guides for Bot User setup and message threading
  - Improved configuration examples and explanations

- **Documentation**: Updated example gallery images and Slack bot configuration examples ([#28](https://github.com/seqeralabs/nf-slack/pull/28))
  - Refreshed example screenshots with latest message formats
  - Adjusted example workflow messaging for clarity

## [0.3.0] - 2025-12-16

### Added

- **Slack Bot Integration**: New bot-based integration using OAuth tokens ([#17](https://github.com/seqeralabs/nf-slack/pull/17))

  - More secure and flexible than webhook-only approach
  - Supports posting to multiple channels dynamically
  - Better control over bot identity and permissions
  - Enables future features like message threading and reactions

- **Message Threading Support**: Thread workflow messages together ([#22](https://github.com/seqeralabs/nf-slack/pull/22))
  - Groups related messages (start, updates, completion) into threads
  - Reduces channel clutter for multi-workflow runs
  - Makes it easier to track individual workflow progress

### Changed

- **Organization Migration**: Updated all references from `adamrtalbot` to `seqeralabs` organization ([#25](https://github.com/seqeralabs/nf-slack/pull/25))

  - Repository URLs updated throughout documentation
  - Plugin registry updated to reflect new organization ownership
  - No breaking changes - old plugin versions continue to work

- **Documentation Improvements**: Standardized channel configuration examples ([#21](https://github.com/seqeralabs/nf-slack/pull/21))
  - Consistent use of channel names vs IDs across examples
  - Clearer explanations of bot vs webhook configuration
  - Updated README with project status information ([#24](https://github.com/seqeralabs/nf-slack/pull/24))

### Internal

- **CI/CD**: Updated Claude Code review workflow for improved PR automation ([#20](https://github.com/seqeralabs/nf-slack/pull/20), [#19](https://github.com/seqeralabs/nf-slack/pull/19))

## [Unreleased]

### Planned

- Asynchronous message sending with ExecutorService
- Retry logic with exponential backoff (429, 5xx errors)
- Rate limiting (1 message/second with burst capacity)
- Webhook URL validation (HTTPS enforcement, format checking)

---

## Version History

- **[0.4.0]** - File upload support, config-based uploads, remote file support
- **[0.3.1]** - Documentation updates for bot support and threading features
- **[0.3.0]** - Slack Bot integration, message threading, and organization migration
- **[0.2.1]** - CI/CD fixes and build improvements
- **[0.2.0]** - Documentation site with GitHub Pages
- **[0.1.1]** - Release automation and documentation improvements
- **[0.1.0]** - Initial release with automatic notifications, custom messages, and progressive configuration examples

[0.4.0]: https://github.com/seqeralabs/nf-slack/releases/tag/v0.4.0
[0.3.1]: https://github.com/seqeralabs/nf-slack/releases/tag/v0.3.1
[0.3.0]: https://github.com/seqeralabs/nf-slack/releases/tag/v0.3.0
[0.2.1]: https://github.com/seqeralabs/nf-slack/releases/tag/v0.2.1
[0.2.0]: https://github.com/seqeralabs/nf-slack/releases/tag/v0.2.0
[0.1.1]: https://github.com/seqeralabs/nf-slack/releases/tag/v0.1.1
[0.1.0]: https://github.com/seqeralabs/nf-slack/releases/tag/v0.1.0

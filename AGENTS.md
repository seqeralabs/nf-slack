# AGENTS.md

`nf-slack` is a Nextflow plugin (Groovy 3.x / Java 11+, Gradle build) that sends
Slack notifications for Nextflow workflow lifecycle events. See `CLAUDE.md`,
`README.md`, and `docs/CONTRIBUTING.md` for the standard developer workflow, and
the `Makefile` for the canonical build/test/install/run commands.

## Cursor Cloud specific instructions

This repo has no long-running service. The "application" is the plugin, which is
exercised by building + installing it and running a Nextflow pipeline that loads
it. Standard commands live in the `Makefile` (`make assemble`, `make test`,
`make install`, `make run`); prefer those.

- **Lint**: `pre-commit run --all-files`. It is installed by the update script
  to `~/.local/bin` (already added to `PATH` via `~/.bashrc`). The
  `nextflow-lint` hook shells out to `nextflow`, and the Prettier hook downloads
  a Node environment on its first run, so the initial lint run needs network.
- **Run / hello-world**: build and install the plugin first (`make install`),
  then run a pipeline with `nextflow run <script> -plugins nf-slack@<version>`.
  The `@<version>` must match `version` in `build.gradle` (e.g. `0.5.1`); the
  locally installed plugin lives in `~/.nextflow/plugins/`.
- **Testing the send path without real Slack credentials**: if neither a webhook
  URL nor a bot token is configured, the plugin silently disables itself
  (`SlackConfig.from` returns `null`). To exercise the notification code end to
  end without credentials, point `slack.webhook.url` at a local HTTP server that
  returns `200` (the webhook sender treats `200` as success) and set
  `slack.validateOnStartup = false`.
- **Benign warnings**: `WARN: Unrecognized config option 'slack.*'` printed
  during `nextflow run` is expected. Nextflow has no schema for the plugin's
  `slack { ... }` config namespace; the plugin still reads these values
  correctly.
- **Versions**: the VM has Java 21 and a recent Nextflow on `PATH`, which work
  for development. CI is the source of truth for the supported matrix (Java
  17/21, Nextflow 25.10/26.04) — see `.github/workflows/ci.yml`.

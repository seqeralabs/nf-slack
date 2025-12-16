# nf-slack Documentation

This directory contains the source files for the nf-slack documentation site, built with MkDocs Material.

## Building the Documentation Locally

### Prerequisites

Install the required Python packages:

```bash
pip install -r requirements.txt
```

Or install individually:

```bash
pip install mkdocs-material pymdown-extensions pillow cairosvg mike
```

### Serve Locally

Run the development server:

```bash
mkdocs serve
```

Then open http://127.0.0.1:8000 in your browser.

### Build Static Site

Build the static site:

```bash
mkdocs build
```

The built site will be in the `site/` directory.

## Deploying with Versioning

This documentation uses `mike` for version management:

```bash
# Deploy a new version
mike deploy --push --update-aliases v0.2.0 latest

# Set the default version
mike set-default --push latest

# List all versions
mike list

# Delete a version
mike delete v0.1.0
```

## Documentation Structure

```
docs/
├── index.md                    # Landing page
├── getting-started/
│   ├── installation.md        # Slack webhook setup
│   └── quick-start.md         # Basic configuration
├── usage/
│   ├── automatic-notifications.md
│   ├── custom-messages.md
│   └── configuration.md
├── examples/
│   └── gallery.md             # 9 progressive examples
├── reference/
│   └── api.md                 # Complete API reference
├── CONTRIBUTING.md
├── changelog.md
└── images/                    # Screenshots and diagrams
```

## GitHub Actions Deployment

The documentation is automatically deployed to GitHub Pages using a workflow_run pattern:

- **Dev docs**: Deployed when code is pushed to `main` branch (deployed as `dev` version)
- **Versioned docs**: Deployed when the "Publish Plugin" workflow completes successfully and creates a new release (deployed as version tag + `latest` alias)

This approach ensures that versioned documentation is only published after the plugin has been successfully released to the Nextflow registry, avoiding race conditions and ensuring consistency between plugin and documentation versions.

See `.github/workflows/docs.yml` for the deployment workflow.

## Adding New Pages

1. Create a new markdown file in the appropriate directory
2. Add the page to `nav` section in `mkdocs.yml`
3. Test locally with `mkdocs serve`
4. Commit and push

## Styling

Custom styles are in `stylesheets/extra.css`. The theme uses:

- **Primary color**: Slack purple (`#611f69`)
- **Accent color**: Slack green (`#2EB887`)
- **Material theme** with dark/light mode support

## Links

- [Live Documentation](https://seqeralabs.github.io/nf-slack/)
- [MkDocs Material Documentation](https://squidfunk.github.io/mkdocs-material/)
- [Mike Versioning Documentation](https://github.com/jimporter/mike)

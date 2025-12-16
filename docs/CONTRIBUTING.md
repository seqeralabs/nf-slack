# Contributing to nf-slack

Thank you for your interest in contributing to nf-slack! This document provides guidelines and instructions for contributing to the project.

## Getting Started

### Prerequisites

- Java 11 or higher
- Gradle (wrapper included)
- Nextflow 25.04.0 or higher
- A Slack workspace with admin access for testing

### Development Setup

1. **Clone the repository**

   ```bash
   git clone https://github.com/yourusername/nf-slack.git
   cd nf-slack
   ```

2. **Build the plugin**

   ```bash
   make assemble
   ```

3. **Run tests**

   ```bash
   make test
   ```

4. **Install locally**

   ```bash
   make install
   ```

5. **Test with Nextflow**

   ```bash
   nextflow run hello -plugins nf-slack@0.3.0 // Make sure to have the correct version!
   ```

## Development Workflow

### Making Changes

1. **Create a feature branch**

   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make your changes**

   - Write clean, well-documented code
   - Follow existing code style and conventions
   - Add tests for new functionality

3. **Test your changes**

   ```bash
   make test
   ```

4. **Commit your changes**

   ```bash
   git add .
   git commit -m "feat: description of your changes"
   ```

   Follow [Conventional Commits](https://www.conventionalcommits.org/) for commit messages:

   - `feat:` - New features
   - `fix:` - Bug fixes
   - `docs:` - Documentation changes
   - `test:` - Test updates
   - `refactor:` - Code refactoring
   - `chore:` - Maintenance tasks

5. **Push and create a pull request**

   ```bash
   git push origin feature/your-feature-name
   ```

### Code Style

- Follow standard Groovy conventions
- Use meaningful variable and method names
- Add comments for complex logic
- Keep methods focused and concise
- Ensure all tests pass before submitting

### Testing

All contributions should include appropriate tests:

- **Unit tests** for new classes and methods
- **Integration tests** for workflow features
- **Configuration tests** for new config options

Run the test suite:

```bash
make test
```

Run specific tests:

```bash
./gradlew test --tests "nextflow.slack.SlackConfigTest"
```

## Pull Request Guidelines

When submitting a pull request:

1. **Provide a clear description** of the changes and their purpose
2. **Reference any related issues** using `Fixes #123` or `Closes #123`
3. **Ensure all tests pass** - CI will verify this
4. **Update documentation** - README, examples, and inline comments
5. **Keep changes focused** - one feature or fix per PR
6. **Respond to feedback** - address reviewer comments promptly

### PR Checklist

- [ ] Tests added/updated and passing
- [ ] Documentation updated (README, examples, inline comments)
- [ ] Commit messages follow conventional commits format
- [ ] Code follows project style guidelines
- [ ] No breaking changes (or clearly documented if unavoidable)

## Project Structure

```
nf-slack/
├── src/
│   ├── main/
│   │   ├── groovy/
│   │   │   └── nextflow/
│   │   │       └── slack/          # Plugin source code
│   │   └── resources/
│   │       └── META-INF/
│   │           └── MANIFEST.MF     # Plugin metadata
│   └── test/
│       └── groovy/
│           └── nextflow/
│               └── slack/          # Test files
├── example/
│   ├── configs/                    # Configuration examples
│   ├── main.nf                     # Example workflow
│   └── nextflow.config             # Example config
├── plugins/                        # Built plugin files
└── build.gradle                    # Build configuration
```

## Adding New Features

### Adding a New Configuration Option

1. **Update configuration class** (e.g., `SlackConfig.groovy`)
2. **Add getter/setter methods** with appropriate defaults
3. **Update message builder** (if applicable)
4. **Add tests** in corresponding test file
5. **Update documentation** (README and example configs)
6. **Add example** in `example/configs/` if appropriate

### Adding a New Notification Type

1. **Create config class** (e.g., `OnNewEventConfig.groovy`)
2. **Update `SlackConfig.groovy`** to include new config block
3. **Update `SlackObserver.groovy`** to handle new event
4. **Update `SlackMessageBuilder.groovy`** for message formatting
5. **Add tests** for all new components
6. **Update documentation** and examples

## Publishing

### Prerequisites for Publishing

To publish the plugin to the Nextflow Plugin Registry:

1. **Create `$HOME/.gradle/gradle.properties`** with your API key:

   ```properties
   npr.apiKey=YOUR_NEXTFLOW_PLUGIN_REGISTRY_TOKEN
   ```

2. **Ensure version is updated** in `build.gradle`

### Creating a Release

Releases are fully automated via GitHub Actions when you merge a PR that updates the version:

1. **Create a release PR**

   ```bash
   git checkout -b release/v0.2.1
   ```

2. **Update version number** in `build.gradle`

   ```groovy
   version = '0.2.1'
   ```

3. **Update CHANGELOG.md** with release notes

   ```markdown
   ## [0.2.0] - 2024-01-15

   ### Added

   - New feature description

   ### Fixed

   - Bug fix description
   ```

4. **Commit and push**

   ```bash
   git add build.gradle CHANGELOG.md
   git commit -m "chore: release v0.2.1"
   git push origin release/v0.2.1
   ```

5. **Create and merge PR**

   Open a pull request to `main` branch. Once merged, the automation will:

   - ✅ Publish plugin to Nextflow Plugin Registry
   - ✅ Create git tag (e.g., `v0.2.1`)
   - ✅ Create GitHub release with changelog

**That's it!** No manual steps required after merging the PR.

### Manual Release (if needed)

If you need to manually trigger a release, use the GitHub Actions workflow:

1. Go to Actions → Publish Plugin
2. Click "Run workflow"
3. Select the `main` branch
4. Click "Run workflow"

Or publish locally (requires `npr.apiKey` in `~/.gradle/gradle.properties`):

```bash
make release
```

> **Note**: The Nextflow Plugin Registry is currently available as preview technology. Contact info@nextflow.io to learn how to get access.

### Versioning

This project follows [Semantic Versioning](https://semver.org/):

- **MAJOR** version for incompatible API changes
- **MINOR** version for new functionality in a backwards-compatible manner
- **PATCH** version for backwards-compatible bug fixes

## Reporting Issues

### Bug Reports

When reporting bugs, please include:

- **Clear title and description**
- **Steps to reproduce** the issue
- **Expected vs actual behavior**
- **Environment details** (Nextflow version, Java version, OS)
- **Configuration** used (sanitize webhook URLs)
- **Relevant logs** or error messages

### Feature Requests

For feature requests, please describe:

- **The problem** you're trying to solve
- **Proposed solution** (if you have one)
- **Alternative solutions** considered
- **Use cases** and examples

## Code of Conduct

### Our Standards

- Be respectful and inclusive
- Welcome newcomers
- Accept constructive criticism gracefully
- Focus on what's best for the community
- Show empathy towards others

### Unacceptable Behavior

- Harassment or discriminatory language
- Personal attacks
- Publishing others' private information
- Other conduct inappropriate in a professional setting

## Getting Help

If you need help:

- 📖 Check the [documentation](index.md) and [examples](examples/gallery.md)
- 🐛 [Search existing issues](https://github.com/yourusername/nf-slack/issues)
- 💬 Open a new issue with the `question` label
- 📧 Contact the maintainers

## Recognition

Contributors will be recognized in:

- GitHub contributors list
- Release notes for significant contributions
- CONTRIBUTORS file (if we create one)

Thank you for contributing to nf-slack! 🎉

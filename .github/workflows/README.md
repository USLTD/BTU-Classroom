# GitHub Actions Workflows

This directory contains GitHub Actions workflows for automated building and releasing of BTU Classroom.

## Workflows

### Preview Build (`preview-build.yml`)

**Trigger:** Automatically runs on every push and pull request to any branch.

**What it does:**
- Builds a debug APK
- Creates a preview release for pushes (not for PRs)
- Generates a changelog with commit details
- Supports Conventional Commits format

**Release naming:** `preview-<short-hash>` (e.g., `preview-2ade04e`)

**Changelog format:**
```
## Preview Build - <hash>

**Commit:** <commit message>
**Author:** <author name>
**Date:** <commit date>

### Changes in this commit
<commit body>

**Type:** `feat|fix|docs|...` (if using Conventional Commits)
```

### Stable Build (`stable-build.yml`)

**Trigger:** Manual dispatch via GitHub Actions UI (requires version number input).

**What it does:**
- Builds a release APK
- Creates a stable release with the specified version
- Generates a comprehensive changelog since the last stable release
- Groups changes by Conventional Commits types

**Release naming:** `v<version>` (e.g., `v1.0.0`)

**Changelog format:**
```
# BTU Classroom v<version>

Release Date: YYYY-MM-DD

## Changes since v<previous-version>

### Features
- <feat commits>

### Bug Fixes
- <fix commits>

### Documentation
- <docs commits>

### Refactoring
- <refactor commits>

### Performance Improvements
- <perf commits>

### Build & CI
- <build/ci commits>

### Other Changes
- <other commits>

---

**Full Changelog**: https://github.com/.../compare/v<prev>...v<current>
```

## Conventional Commits

Both workflows support [Conventional Commits](https://www.conventionalcommits.org/) format:

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

**Supported types:**
- `feat` - New features
- `fix` - Bug fixes
- `docs` - Documentation changes
- `style` - Code style changes (formatting, etc.)
- `refactor` - Code refactoring
- `perf` - Performance improvements
- `test` - Test changes
- `build` - Build system changes
- `ci` - CI/CD changes
- `chore` - Other changes

**Examples:**
```
feat(auth): add WebView OAuth support
fix(ui): correct color scheme in dark mode
docs: update installation instructions
build: upgrade to AGP 8.9.1
```

## Usage

### Creating a Preview Build

Preview builds are created automatically. Just push your changes:

```bash
git commit -m "feat(screens): add course list screen"
git push
```

The workflow will:
1. Build the APK
2. Create a preview release with the commit hash
3. Upload the APK as an artifact

### Creating a Stable Release

1. Go to **Actions** tab in GitHub
2. Select **Stable Build** workflow
3. Click **Run workflow**
4. Enter the version number (e.g., `1.0.0`)
5. Click **Run workflow**

The workflow will:
1. Build the release APK
2. Generate a changelog from all commits since the last stable release
3. Create a stable release with the version tag
4. Upload the APK

## Artifacts

Both workflows upload APK files as artifacts:
- **Preview builds:** Retained for 30 days
- **Stable builds:** Retained for 90 days

Access artifacts from the workflow run page in GitHub Actions.

# Repository Guidelines

## Project Structure & Module Organization

Episort is a cross-platform desktop application for sorting TV series episodes using TVDB references: aired order, DVD order, and absolute order. The application works from a user-selected working directory that may contain files from multiple TV series. The first scope is metadata lookup, series grouping, season folder creation, and safe planning for `.avi`, `.mp4`, and especially `.mkv` files.

Use this structure unless the chosen desktop framework requires a small variation:

```text
src/main/        application code
src/test/        automated tests
docs/            design notes and TVDB behavior notes
assets/          icons and bundled UI assets
```

Preferred stack: Java 21, JavaFX for the desktop UI, Gradle for builds, JUnit 5 for tests, and Spotless or Checkstyle for formatting.

## Build, Test, and Development Commands

Document final commands in `README.md` once Gradle is initialized. Expected commands:

```bash
./gradlew run          # launch the app locally
./gradlew test         # run unit tests
./gradlew build        # compile, test, and package artifacts
./gradlew spotlessApply # format code, if Spotless is enabled
```

Do not add new build tools without updating this guide.

## Coding Style & Naming Conventions

Use standard Java conventions: 4-space indentation, `PascalCase` classes, `camelCase` methods and fields, and lowercase package names. Keep TVDB API access, episode matching, filesystem operations, and UI code in separate packages.

Prefer explicit domain names such as `EpisodeOrder`, `SeasonFolderPlanner`, and `TvdbClient`. Avoid mixing UI logic with file-moving or metadata logic.

## Testing Guidelines

Use JUnit 5. Prioritize tests for ordering conversion, filename detection, season folder planning, and TVDB response mapping. Test files should end with `Test`, for example `EpisodeOrderMapperTest`.

Filesystem tests must use temporary directories. Do not run tests against real media folders.

Development must follow TDD by default:

1. Add or update a failing test that describes the expected behavior.
2. Implement the smallest production change that makes the test pass.
3. Refactor while keeping the test suite green.

For UI-only changes where automated testing is not practical yet, document the manual verification steps and add lower-level tests for any extracted logic.

## Security & Configuration

TVDB access must use an API key stored outside source control, preferably through environment variables or an ignored local config file. Never commit API keys, generated tokens, real media paths, or private library metadata.

The application must include settings for selecting the working directory. All scans, pattern analysis, folder creation, renaming, and moving operations must be limited to the configured working directory. Never modify files outside that directory.

## Episode Identification Workflow

Episort should support working directories that contain multiple TV series mixed together. The application must group files by likely series before proposing episode matches. Grouping may use file names, parent folders, media metadata, duration, file ordering, TVDB data, and user answers.

Local AI may be used as an optional assistant to detect patterns and resolve ambiguous groups, but it must not be the only source of truth. The AI should ask the user questions as often as needed before actions are planned, especially when a folder contains multiple series, specials, bonus files, duplicate episodes, unclear seasons, or weak naming patterns.

After the user validates a pattern, Episort should connect to TVDB, resolve the official English series name, season data, episode numbers, and English episode titles, then produce a file operation plan.

The target layout is:

```text
Series Name in English/
  Season XX/
    Series Name in English - SXXEXX - Episode Title in English.original-extension
```

The original file extension must be preserved.

## Commit & Pull Request Guidelines

Use English Conventional Commits:

```text
feat: add TVDB aired order mapping
fix: handle missing season numbers
docs: update setup instructions
```

Pull requests should include a summary, test evidence, linked issues when relevant, and screenshots for UI changes.

Use Git Flow for development:

- `main` contains production-ready code only.
- `develop` is the integration branch for completed work.
- New features must be developed on `feature/<short-description>` branches created from `develop`.
- Release stabilization should happen on `release/<version>` branches.
- Production fixes should happen on `hotfix/<short-description>` branches created from `main`.
- Merge completed feature branches back into `develop` after tests pass.
- Do not commit directly to `main` or `develop` unless the user explicitly asks for it.

## Agent-Specific Instructions

Do not rename, move, or delete media files without explicit user confirmation. Current scope allows creating `Season XX` folders, planning organization, and renaming/moving only after the user explicitly validates the exact source-to-destination plan.

Use two separate validation steps for media operations:

1. Validate the detected pattern, including series grouping, season assignment, ordering, ignored files, and ambiguous files.
2. Validate the exact file operation plan, including every source path and destination path.

For multi-series working directories, never assume all files belong to the same show. Always surface the proposed series groups and require user validation before producing the final TVDB-backed rename/move plan.

Keep changes small and testable. When modifying sorting or filesystem behavior, add or update tests before considering the task complete.

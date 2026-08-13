# Repository Guidelines

Episort is a JavaFX desktop app that sorts TV series episodes (`.avi`, `.mp4`, `.mkv`) using TMDB references — aired, DVD, and absolute orders — from a user-selected working directory that may mix several series.

Stack: Java 25, JavaFX 25, Gradle, JUnit 5, plus Go 1.26 only for the native single-file launcher under `tools/portable-launcher/`. Layout: `src/main/`, `src/test/`, `docs/`, `assets/`. Keep TMDB access, episode matching, filesystem ops, and UI in separate packages.

## Commands

```bash
./gradlew run     # launch the app
./gradlew test    # run unit tests
./gradlew build   # compile, test, package
./gradlew portableArchive # one native executable; requires Go 1.26
```

## Coding & Tests

Standard Java conventions (4-space indent, `PascalCase`/`camelCase`, lowercase packages). Prefer explicit domain names (`EpisodeOrder`, `SeasonFolderPlanner`, `TmdbClient`).

JUnit 5, test files end with `Test`. Filesystem tests must use temporary directories — never real media folders. Default to TDD: failing test → minimal implementation → refactor. For UI-only changes, document manual verification and add lower-level tests for any extracted logic.

The Go launcher uses `gofmt`, standard-library-only code, and `go test .` from
`tools/portable-launcher`. Its extraction tests must use temporary directories
and reject absolute paths, traversal, and unsafe archive links.

## Security

TMDB API keys and read access tokens live exclusively in the Janus vault and must never be committed or distributed. The Janus URL, Episort application ID, and Janus caller key are intentionally embedded in official Episort builds so end users need no credentials; the operator accepts exposure and manages restrictions, monitoring, rotation, and revocation in Janus. Never commit other keys or tokens, real media paths, or private library metadata.

All scans, folder creation, renaming, and moving operations must stay inside the configured working directory. Never touch files outside it.

## UI / Design System

All UI work follows `docs/design-system.md` — the source of truth for tokens, layout, components, and do/don't rules.

- Reuse existing classes (`.card`, `.widget`, `.button`, `.section-heading`, `SettingsPane`, …); no parallel styles.
- No hex literals in new code — go through tokens defined in `src/main/resources/styles/app.css`.
- New component → edit `app.css` first, then update `docs/design-system.md` (§2 tokens + §4 anatomy/class/rules) in the same change.
- "No fabricated state": every label/value comes from a real view-model signal or shows `—`.

## Media Operations Guardrails

Do not rename, move, or delete media files without explicit user confirmation. Two validation steps are required:

1. Validate the detected pattern (series grouping, season assignment, ordering, ignored/ambiguous files).
2. Validate the exact file operation plan (every source and destination path).

Never assume all files in a working directory belong to the same show — surface the proposed series groups for validation before producing the TMDB-backed rename/move plan. Target layout:

```text
Series Name in English/
  Season XX/
    Series Name in English - SXXEXX - Episode Title in English.original-extension
```

Original file extension is preserved.

## Commits & Git Flow

English Conventional Commits (`feat:`, `fix:`, `docs:`, …). PRs include a summary, test evidence, linked issues, and screenshots for UI changes.

- `main`: production-ready only.
- `develop`: integration branch.
- Features: `feature/<short-description>` from `develop`.
- Releases: `release/<version>`. Hotfixes: `hotfix/<short-description>` from `main`.
- Never commit directly to `main` or `develop` unless the user explicitly asks.

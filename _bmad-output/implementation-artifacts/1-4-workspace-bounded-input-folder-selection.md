# Story 1.4: Workspace-Bounded Input Folder Selection

Status: review

<!-- Generated through the BMAD create-story workflow rules from approved planning artifacts. -->

## Story

As a media library user,
I want to choose an input folder only inside my configured workspace,
so that accidental operations outside the allowed boundary are impossible.

## Acceptance Criteria

1. Given a valid workspace is configured
   When the user selects an input folder inside that workspace
   Then Episort accepts the folder for a future organization workflow
   And when the selected folder resolves outside the workspace, Episort rejects it
   And relative segments, normalized paths, and symlink-like boundary escapes are handled before acceptance

## Tasks / Subtasks

- [x] Write or update focused tests for prerequisite, settings, boundary, or feedback behavior before implementation. (AC: #1)
- [x] Implement the smallest production slice needed for the story in the responsible packages. (AC: #1)
- [x] Expose user-facing recoverable errors without secrets or stack traces. (AC: #1)
- [x] Run `./gradlew test` and document any manual JavaFX verification needed. (AC: #1)
- [x] Update README or developer notes only if this story introduces or changes a developer-facing command or setup step. (AC: #1)
- [x] Run relevant tests and record any manual verification steps in the Dev Agent Record. (AC: #1)

## Dev Notes

### Requirements Coverage

- Epic: 1 - Configuration sure et workspace borne
- Epic goal: Users can launch Episort, configure the workspace and TVDB access, test prerequisites, and be blocked cleanly when the app cannot organize safely.
- FRs covered by this epic: FR1, FR2, FR3, FR4, FR5, FR6, FR7, FR63, FR64, FR65, FR66, FR67
- Story source: `_bmad-output/planning-artifacts/epics.md`, Story 1.4

### Technical Requirements

This epic establishes the launchable app shell, settings gate, TVDB prerequisite checks, input-folder boundary checks, progress/error feedback, dark mode, and redacted diagnostics. Do not add scanning, matching, planning, or execution behavior before later epics require it.

### Architecture Compliance

- Responsible packages: `ui`, `ui.settings`, `workflow`, `config`, `filesystem`, `logging`.
- Use ports-and-adapters inside the JavaFX desktop process: UI -> workflow services -> domain ports -> infrastructure adapters.
- Domain/planning/filesystem/persistence/TVDB/AI code must not import JavaFX.
- Use typed application errors with `code`, `severity`, `message`, `recoverable`, and optional `details` for recoverable workflow failures.
- Keep secret redaction at logging and diagnostic boundaries.
- Keep scan, match, plan, validation, and execution as separate phases.

### File Structure Requirements

- Production code belongs under `src/main/java/com/episort/...` in the responsible package listed above.
- Tests belong under `src/test/java/com/episort/...` mirroring production package structure.
- UI resources belong under `src/main/resources` or `assets` only when the story introduces UI assets/styles.
- Do not place runtime settings, logs, credentials, SQLite DBs, generated TVDB tokens, or media paths inside the repository or media workspace.

### Testing Requirements

Prioritize `config`, `filesystem`, `workflow`, and logging redaction tests. UI-only behavior may use manual verification until test harnesses exist.

- Test files must end with `Test`.
- Filesystem tests must use temporary directories.
- Do not run tests against real media folders.
- Add or update tests before changing sorting, matching, planning, naming, validation, persistence, or filesystem behavior.

### Previous Story Intelligence

Review `1-3-tvdb-credential-configuration-and-test.md` before implementation. Reuse its established file locations, test patterns, and decisions; do not duplicate or contradict them.

### Dependency Rules

May depend only on completed earlier stories in this same epic and prior epics. Do not rely on future stories.

### Latest Technical Information

- Gradle Application plugin: use the current Gradle Application plugin behavior for JVM apps and distributions; it implicitly applies Java and Distribution plugins. Source: https://docs.gradle.org/current/userguide/application_plugin.html
- OpenJFX Gradle plugin: architecture selected `org.openjfx.javafxplugin` version `0.1.0`, listed as latest on the Gradle Plugin Portal. Source: https://plugins.gradle.org/plugin/org.openjfx.javafxplugin
- JUnit: use JUnit Jupiter through Gradle; current official user guide checked for JUnit 5.12.0. Source: https://docs.junit.org/5.12.0/user-guide/index.html
- TVDB v4 auth: login returns a token; use API key and subscriber PIN when required, then authorize subsequent calls with the token. Source: https://support.thetvdb.com/kb/faq.php?id=78
- SQLite JDBC: Maven Central currently lists `org.xerial:sqlite-jdbc:3.53.0.0`; pin during the persistence story that first needs SQLite. Source: https://central.sonatype.com/artifact/org.xerial/sqlite-jdbc
- JNA: Maven Central currently lists `net.java.dev.jna:jna:5.18.1`; pin during the Windows Credential Manager story that first needs JNA. Source: https://central.sonatype.com/artifact/net.java.dev.jna/jna


### Anti-Patterns to Avoid

- Do not rename, move, create, or delete media files unless this story is in Epic 7 and the approved operation-plan execution rules are satisfied.
- Do not let JavaFX controllers call TVDB, AI, persistence, or filesystem executors directly.
- Do not infer validation from confidence.
- Do not let AI output validate patterns, approve plans, or authorize filesystem operations.
- Do not log TVDB API keys, subscriber PINs, bearer tokens, raw credentials, or unnecessary private media metadata.
- Do not create broad database schema, infrastructure, or UI surfaces beyond what this story needs.

### References

- `_bmad-output/planning-artifacts/epics.md`
- `_bmad-output/planning-artifacts/prd.md`
- `_bmad-output/planning-artifacts/architecture.md`
- `AGENTS.md`
- Gradle Application plugin: https://docs.gradle.org/current/userguide/application_plugin.html
- OpenJFX Gradle plugin: https://plugins.gradle.org/plugin/org.openjfx.javafxplugin
- JUnit User Guide: https://docs.junit.org/5.12.0/user-guide/index.html
- TVDB API authentication notes: https://support.thetvdb.com/kb/faq.php?id=78
- SQLite JDBC artifact: https://central.sonatype.com/artifact/org.xerial/sqlite-jdbc
- JNA artifact: https://central.sonatype.com/artifact/net.java.dev.jna/jna

## Project Structure Notes

- Follow the architecture document's package-by-responsibility structure.
- If current repository structure differs because earlier stories have not created files yet, create only the minimum directories/files this story needs.
- Record any intentional variance in the Dev Agent Record before marking implementation complete.

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- `./gradlew.bat test` with local JDK 21: passed
- `./gradlew.bat build` with local JDK 21: passed
- `./gradlew.bat run` JavaFX smoke: app remained active after launch window

### Completion Notes List

- Added workspace boundary validation that resolves candidate folders before acceptance and rejects outside paths or symlink escapes.
- Added input folder selection workflow with typed recoverable errors for missing workspace, missing input, invalid input, and outside-workspace input.
- Exposed input folder selection in the JavaFX settings pane without persisting or mutating media files.
- No README update required; no developer-facing command or setup changed in this story.

### File List

- `src/main/java/com/episort/filesystem/WorkspaceBoundary.java`
- `src/main/java/com/episort/workflow/InputFolderSelectionResult.java`
- `src/main/java/com/episort/workflow/InputFolderSelectionService.java`
- `src/main/java/com/episort/workflow/StartupWorkflow.java`
- `src/main/java/com/episort/ui/AppShell.java`
- `src/main/java/com/episort/ui/AppShellViewModel.java`
- `src/main/java/com/episort/ui/settings/SettingsPane.java`
- `src/main/java/com/episort/EpisortApplication.java`
- `src/test/java/com/episort/filesystem/WorkspaceBoundaryTest.java`
- `src/test/java/com/episort/workflow/InputFolderSelectionServiceTest.java`
- `src/test/java/com/episort/ui/AppShellViewModelTest.java`

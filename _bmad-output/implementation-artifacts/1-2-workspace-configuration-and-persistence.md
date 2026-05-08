# Story 1.2: Workspace Configuration and Persistence

Status: done

<!-- Generated through the BMAD create-story workflow rules from approved planning artifacts. -->

## Story

As a media library user,
I want to select and persist the workspace directory,
so that Episort only operates inside the folder I explicitly allow.

## Acceptance Criteria

1. Given no workspace is configured
   When the user opens Settings and selects a workspace directory
   Then Episort persists the setting outside the media workspace
   And the selected workspace is shown after app restart
   And invalid or inaccessible workspace paths show a recoverable blocking error

## Tasks / Subtasks

- [x] Write or update focused tests for prerequisite, settings, boundary, or feedback behavior before implementation. (AC: #1)
- [x] Implement the smallest production slice needed for the story in the responsible packages. (AC: #1)
- [x] Expose user-facing recoverable errors without secrets or stack traces. (AC: #1)
- [x] Run `./gradlew test` and document any manual JavaFX verification needed. (AC: #1)
- [x] Update README or developer notes only if this story introduces or changes a developer-facing command or setup step. (AC: #1)
- [x] Run relevant tests and record any manual verification steps in the Dev Agent Record. (AC: #1)

### Review Findings

- [x] [Review][Patch] No Settings UI exists to select a workspace [src/main/java/com/episort/ui/AppShell.java:16]
- [x] [Review][Patch] Persistence is not guaranteed outside the selected workspace [src/main/java/com/episort/workflow/WorkspaceConfigurationService.java:17]
- [x] [Review][Patch] Malformed persisted workspace paths can crash startup instead of producing a recoverable blocking error [src/main/java/com/episort/config/FileSettingsStore.java:46]
- [x] [Review][Patch] Settings load/save failures are not converted to UI-safe recoverable errors [src/main/java/com/episort/workflow/WorkspaceConfigurationService.java:22]
- [x] [Review][Patch] `configureWorkspace(null)` can throw on directory chooser cancel paths [src/main/java/com/episort/workflow/WorkspaceConfigurationService.java:15]
- [x] [Review][Patch] Settings location is hardcoded to a Windows-shaped `AppData` path [src/main/java/com/episort/config/FileSettingsStore.java:19]
- [x] [Review][Patch] Settings save writes directly to the target file instead of using atomic replacement [src/main/java/com/episort/config/FileSettingsStore.java:56]
- [x] [Review][Patch] `AppSettings` permits a null `Optional` component [src/main/java/com/episort/config/AppSettings.java:6]
- [x] [Review][Patch] `AppShellViewModel.fromWorkspaceConfiguration()` can throw for inconsistent success results [src/main/java/com/episort/ui/AppShellViewModel.java:41]

## Dev Notes

### Requirements Coverage

- Epic: 1 - Configuration sure et workspace borne
- Epic goal: Users can launch Episort, configure the workspace and TVDB access, test prerequisites, and be blocked cleanly when the app cannot organize safely.
- FRs covered by this epic: FR1, FR2, FR3, FR4, FR5, FR6, FR7, FR63, FR64, FR65, FR66, FR67
- Story source: `_bmad-output/planning-artifacts/epics.md`, Story 1.2

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

Review `1-1-set-up-initial-project-from-starter-template.md` before implementation. Reuse its established file locations, test patterns, and decisions; do not duplicate or contradict them.

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

- Do not rename, move, create, or delete media files unless this story is in Epic 6 and the approved operation-plan execution rules are satisfied.
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

- `.\gradlew.bat test` first failed at compile time because the new workspace settings types did not exist yet.
- Added the minimal config/workflow/UI implementation for workspace persistence and reload.
- `.\gradlew.bat test` passed.
- `.\gradlew.bat build` passed.
- `.\gradlew.bat run` remained active after launch verification; launched processes were stopped after the smoke check.
- Code review patches applied for Settings UI selection, settings/workspace boundary enforcement, malformed settings recovery, settings load/save recovery, directory chooser cancel handling, platform-aware config location, atomic settings writes, settings/result invariants, and defensive UI projection.
- `.\gradlew.bat test` passed after review fixes.
- `.\gradlew.bat build` passed after review fixes.
- `.\gradlew.bat run` remained active after review-fix launch verification; launched processes were stopped after the smoke check.

### Completion Notes List

- Added `AppSettings`, `SettingsStore`, and `FileSettingsStore` to persist workspace configuration in a user-profile settings file rather than the media workspace.
- Added `WorkspaceConfigurationService` and `WorkspaceConfigurationResult` for valid workspace persistence, missing workspace state, and invalid/inaccessible workspace recoverable blocking errors.
- Updated startup wiring so the JavaFX shell loads persisted workspace configuration and projects either the configured workspace or a safe recoverable error.
- Added focused tests for persistence outside the workspace, restart reload, invalid workspace errors, empty settings, and UI display of configured workspace.
- README was not changed for this story because no developer-facing command changed.
- Added a minimal JavaFX Settings surface with a workspace chooser that calls the workflow service and updates the shell state.
- Added safeguards for corrupted settings, unavailable settings storage, null workspace selection, and workspaces that would contain the settings file.
- Settings writes now use a temporary file and atomic replacement when supported.

### File List

- `src/main/java/com/episort/EpisortApplication.java`
- `src/main/java/com/episort/config/AppSettings.java`
- `src/main/java/com/episort/config/FileSettingsStore.java`
- `src/main/java/com/episort/config/InvalidSettingsException.java`
- `src/main/java/com/episort/config/SettingsStore.java`
- `src/main/java/com/episort/config/SettingsStoreException.java`
- `src/main/java/com/episort/ui/AppShell.java`
- `src/main/java/com/episort/ui/AppShellViewModel.java`
- `src/main/java/com/episort/ui/settings/SettingsPane.java`
- `src/main/java/com/episort/workflow/StartupWorkflow.java`
- `src/main/java/com/episort/workflow/WorkspaceConfigurationResult.java`
- `src/main/java/com/episort/workflow/WorkspaceConfigurationService.java`
- `src/test/java/com/episort/config/FileSettingsStoreTest.java`
- `src/test/java/com/episort/ui/AppShellViewModelTest.java`
- `src/test/java/com/episort/workflow/WorkspaceConfigurationServiceTest.java`
- `_bmad-output/implementation-artifacts/1-2-workspace-configuration-and-persistence.md`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`

### Change Log

- 2026-05-08: Implemented workspace configuration persistence and marked story ready for review.
- 2026-05-08: Resolved code review findings and marked story done.

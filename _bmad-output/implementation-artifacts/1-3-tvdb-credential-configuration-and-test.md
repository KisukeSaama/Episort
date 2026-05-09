# Story 1.3: TVDB Credential Configuration and Test

Status: done

<!-- Generated through the BMAD create-story workflow rules from approved planning artifacts. -->

## Story

As a media library user,
I want to enter and test TVDB access,
so that I know metadata-backed organization can work before scanning.

## Acceptance Criteria

1. Given the user opens Settings
   When they enter TVDB API configuration and run a connection test
   Then Episort reports success or a clear recoverable failure
   And credentials are not written to source control, logs, media folders, or exported plans
   And organization workflows remain blocked when TVDB configuration is missing or invalid

## Tasks / Subtasks

- [x] Write or update focused tests for prerequisite, settings, boundary, or feedback behavior before implementation. (AC: #1)
- [x] Implement the smallest production slice needed for the story in the responsible packages. (AC: #1)
- [x] Expose user-facing recoverable errors without secrets or stack traces. (AC: #1)
- [x] Run `./gradlew test` and document any manual JavaFX verification needed. (AC: #1)
- [x] Update README or developer notes only if this story introduces or changes a developer-facing command or setup step. (AC: #1)
- [x] Run relevant tests and record any manual verification steps in the Dev Agent Record. (AC: #1)

### Review Findings

- [x] [Review][Patch] Stored TVDB credentials are treated as valid without re-test, allowing invalid credentials to unblock organization [src/main/java/com/episort/workflow/TvdbCredentialConfigurationService.java:18]
- [x] [Review][Patch] TVDB configuration gate is not applied to startup UI/prerequisite state [src/main/java/com/episort/EpisortApplication.java:26]
- [x] [Review][Patch] Organization blocking is represented but not enforced by a shared workflow prerequisite gate [src/main/java/com/episort/workflow/TvdbCredentialConfigurationResult.java:6]
- [x] [Review][Patch] TVDB network test runs synchronously from the JavaFX settings button [src/main/java/com/episort/ui/settings/SettingsPane.java:42]
- [x] [Review][Patch] TVDB login accepts any 2xx response without verifying a usable token [src/main/java/com/episort/tvdb/HttpTvdbConnectionTester.java:37]
- [x] [Review][Patch] TVDB credential temp files can remain after save failure [src/main/java/com/episort/config/FileTvdbCredentialStore.java:56]
- [x] [Review][Patch] TVDB credentials are stored as raw properties without owner-only permission hardening [src/main/java/com/episort/config/FileTvdbCredentialStore.java:54]
- [x] [Review][Patch] Recoverable failure details are discarded from UI feedback [src/main/java/com/episort/ui/AppShellViewModel.java:24]

## Dev Notes

### Requirements Coverage

- Epic: 1 - Configuration sure et workspace borne
- Epic goal: Users can launch Episort, configure the workspace and TVDB access, test prerequisites, and be blocked cleanly when the app cannot organize safely.
- FRs covered by this epic: FR1, FR2, FR3, FR4, FR5, FR6, FR7, FR63, FR64, FR65, FR66, FR67
- Story source: `_bmad-output/planning-artifacts/epics.md`, Story 1.3

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

Review `1-2-workspace-configuration-and-persistence.md` before implementation. Reuse its established file locations, test patterns, and decisions; do not duplicate or contradict them.

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

- `.\gradlew.bat test` first failed at compile time because the new TVDB credential and workflow types did not exist yet.
- Added credential storage, TVDB connection-test workflow, UI Settings fields, and HTTP TVDB login adapter.
- `.\gradlew.bat test` passed.
- `.\gradlew.bat build` passed.
- `.\gradlew.bat run` remained active after launch verification; launched processes were stopped after the smoke check.
- No live TVDB credential was available in the repo/session, so live success against TVDB was not executed; success/failure behavior is covered through the `TvdbConnectionTester` port tests.
- Code review patches applied for credential re-test, startup prerequisite gating, shared organization prerequisite gate, async TVDB test UI, TVDB token validation, temp credential cleanup, owner-only POSIX permissions where supported, and redacted error detail display.
- `.\gradlew.bat test` passed after review fixes.
- `.\gradlew.bat build` passed after review fixes.
- `.\gradlew.bat run` remained active after review-fix launch verification; launched processes were stopped after the smoke check.

### Completion Notes List

- Added `TvdbCredentials`, `TvdbCredentialStore`, `FileTvdbCredentialStore`, and `InMemoryTvdbCredentialStore`.
- Added `TvdbCredentialConfigurationService` and result types to block organization when TVDB configuration is missing or the connection test fails.
- Added `HttpTvdbConnectionTester` adapter for TVDB v4 login test without storing returned tokens.
- Extended Settings UI with TVDB API key/PIN inputs and a `Test TVDB` action.
- Ensured failed TVDB tests do not persist credentials and user-facing failures are redacted.
- README was not changed for this story because no developer-facing command changed.
- Stored TVDB credentials are re-tested before organization is allowed.
- Startup state now combines workspace and TVDB readiness.
- `HttpTvdbConnectionTester` requires a token-bearing response and has local HTTP contract tests.

### File List

- `src/main/java/com/episort/EpisortApplication.java`
- `src/main/java/com/episort/config/FileTvdbCredentialStore.java`
- `src/main/java/com/episort/config/InMemoryTvdbCredentialStore.java`
- `src/main/java/com/episort/config/TvdbCredentialStore.java`
- `src/main/java/com/episort/config/TvdbCredentials.java`
- `src/main/java/com/episort/logging/SecretRedactor.java`
- `src/main/java/com/episort/tvdb/HttpTvdbConnectionTester.java`
- `src/main/java/com/episort/ui/AppShell.java`
- `src/main/java/com/episort/ui/AppShellViewModel.java`
- `src/main/java/com/episort/ui/settings/SettingsPane.java`
- `src/main/java/com/episort/workflow/StartupWorkflow.java`
- `src/main/java/com/episort/workflow/OrganizationPrerequisitesResult.java`
- `src/main/java/com/episort/workflow/TvdbConnectionTester.java`
- `src/main/java/com/episort/workflow/TvdbConnectionTestResult.java`
- `src/main/java/com/episort/workflow/TvdbCredentialConfigurationResult.java`
- `src/main/java/com/episort/workflow/TvdbCredentialConfigurationService.java`
- `src/test/java/com/episort/config/FileTvdbCredentialStoreTest.java`
- `src/test/java/com/episort/logging/SecretRedactorTest.java`
- `src/test/java/com/episort/ui/AppShellViewModelTest.java`
- `src/test/java/com/episort/tvdb/HttpTvdbConnectionTesterTest.java`
- `src/test/java/com/episort/workflow/StartupWorkflowTest.java`
- `src/test/java/com/episort/workflow/TvdbCredentialConfigurationServiceTest.java`
- `_bmad-output/implementation-artifacts/1-3-tvdb-credential-configuration-and-test.md`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`

### Change Log

- 2026-05-08: Implemented TVDB credential configuration and test workflow, then marked story ready for review.
- 2026-05-08: Resolved code review findings and marked story done.

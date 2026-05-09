# Story 1.1: Set Up Initial Project from Starter Template

Status: done

<!-- Generated through the BMAD create-story workflow rules from approved planning artifacts. -->

## Story

As a media library user,
I want Episort to launch as a Windows desktop app,
so that I can start configuration from a stable application shell.

## Acceptance Criteria

1. Given the project is checked out
   When the developer initializes the selected Gradle Java Application + OpenJFX starter
   Then the project uses Java 21, JavaFX, Gradle, and JUnit 5
   And `./gradlew run` launches a JavaFX application shell
   And `./gradlew test` runs JUnit 5 tests successfully
   And package boundaries exist for `ui`, `workflow`, `config`, `filesystem`, `scanner`, `matching`, `planning`, `tvdb`, `ai`, `persistence`, and `logging`

## Tasks / Subtasks

- [x] Write or update focused tests for prerequisite, settings, boundary, or feedback behavior before implementation. (AC: #1)
- [x] Implement the smallest production slice needed for the story in the responsible packages. (AC: #1)
- [x] Expose user-facing recoverable errors without secrets or stack traces. (AC: #1)
- [x] Run `./gradlew test` and document any manual JavaFX verification needed. (AC: #1)
- [x] Update README or developer notes only if this story introduces or changes a developer-facing command or setup step. (AC: #1)
- [x] Run relevant tests and record any manual verification steps in the Dev Agent Record. (AC: #1)

### Review Findings

- [x] [Review][Patch] UI-safe errors can leak secrets placed in `message` [src/main/java/com/episort/workflow/ApplicationError.java:14]
- [x] [Review][Patch] Redactor misses common JSON/log credential shapes [src/main/java/com/episort/logging/SecretRedactor.java:9]
- [x] [Review][Patch] Redactor throws on null log messages [src/main/java/com/episort/logging/SecretRedactor.java:14]
- [x] [Review][Patch] JavaFX boundary test allows non-UI packages whose name starts with `ui` [src/test/java/com/episort/ArchitectureBoundaryTest.java:41]
- [x] [Review][Patch] JavaFX launch acceptance remains manually unverified [src/main/java/com/episort/EpisortApplication.java:8]
- [x] [Review][Patch] `ui.settings` responsible package boundary is missing [_bmad-output/implementation-artifacts/1-1-set-up-initial-project-from-starter-template.md:46]
- [x] [Review][Patch] Recoverable error exposure is not wired into the application shell [src/main/java/com/episort/EpisortApplication.java:11]

## Dev Notes

### Requirements Coverage

- Epic: 1 - Configuration sure et workspace borne
- Epic goal: Users can launch Episort, configure the workspace and TVDB access, test prerequisites, and be blocked cleanly when the app cannot organize safely.
- FRs covered by this epic: FR1, FR2, FR3, FR4, FR5, FR6, FR7, FR63, FR64, FR65, FR66, FR67
- Story source: `_bmad-output/planning-artifacts/epics.md`, Story 1.1

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

None. This is the first story in the epic.

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

- `.\gradlew.bat test` initially failed because the Gradle wrapper jar was not present.
- Temporary local tools were downloaded under ignored `.tools/` to generate the Gradle wrapper and run verification with JDK 21.
- `.\gradlew.bat test` failed once because the JUnit Platform launcher was not aligned; added explicit `testRuntimeOnly("org.junit.platform:junit-platform-launcher")`.
- `.\gradlew.bat test` failed once because the architecture boundary test treated the JavaFX launcher as a non-UI violation; excluded `EpisortApplication.java` while preserving the package-boundary rule for non-UI packages.
- `.\gradlew.bat test` passed.
- `.\gradlew.bat build` passed.
- Code review patches applied for safe error redaction, JSON credential redaction, null redaction handling, exact UI boundary tests, `ui.settings` package boundary, startup error wiring, and JavaFX run verification.
- `.\gradlew.bat run` remained active after launch verification; launched processes were stopped after the smoke check.

### Completion Notes List

- Initialized a Gradle Java application using Kotlin DSL, Java 21 toolchain, OpenJFX plugin, JavaFX controls, and JUnit 5.
- Added a JavaFX application shell with a minimal settings-required startup state.
- Added typed recoverable application errors and log secret redaction coverage.
- Added package-boundary marker packages for `ui`, `workflow`, `config`, `filesystem`, `scanner`, `matching`, `planning`, `tvdb`, `ai`, `persistence`, and `logging`.
- Added architecture-boundary, UI view model, application error, and secret redaction tests.
- Updated README with the developer commands introduced by the scaffold.
- JavaFX launch smoke verification completed with `.\gradlew.bat run`; the app process stayed active after startup and was stopped after verification.
- Code review follow-ups resolved: UI-safe messages are redacted, JSON credential log shapes are covered, null redaction is handled, architecture boundary tests match the exact `ui` package, `ui.settings` exists, startup shell uses workflow-backed recoverable error state, and `.\gradlew.bat run` launch was smoke-verified.

### File List

- `.gitignore`
- `README.md`
- `build.gradle.kts`
- `settings.gradle.kts`
- `gradlew`
- `gradlew.bat`
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties`
- `src/main/java/com/episort/EpisortApplication.java`
- `src/main/java/com/episort/ai/package-info.java`
- `src/main/java/com/episort/config/package-info.java`
- `src/main/java/com/episort/filesystem/package-info.java`
- `src/main/java/com/episort/logging/SecretRedactor.java`
- `src/main/java/com/episort/matching/package-info.java`
- `src/main/java/com/episort/persistence/package-info.java`
- `src/main/java/com/episort/planning/package-info.java`
- `src/main/java/com/episort/scanner/package-info.java`
- `src/main/java/com/episort/tvdb/package-info.java`
- `src/main/java/com/episort/ui/AppShell.java`
- `src/main/java/com/episort/ui/AppShellViewModel.java`
- `src/main/java/com/episort/ui/settings/package-info.java`
- `src/main/java/com/episort/workflow/ApplicationError.java`
- `src/main/java/com/episort/workflow/ErrorSeverity.java`
- `src/main/java/com/episort/workflow/StartupWorkflow.java`
- `src/test/java/com/episort/ArchitectureBoundaryTest.java`
- `src/test/java/com/episort/logging/SecretRedactorTest.java`
- `src/test/java/com/episort/ui/AppShellViewModelTest.java`
- `src/test/java/com/episort/workflow/ApplicationErrorTest.java`
- `_bmad-output/implementation-artifacts/1-1-set-up-initial-project-from-starter-template.md`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`

### Change Log

- 2026-05-08: Implemented initial Gradle JavaFX scaffold and marked story ready for review.
- 2026-05-08: Resolved code review findings and marked story done.

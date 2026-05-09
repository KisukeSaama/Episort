# Story 2.3: Mixed Media Group Seed Classification

Status: done

<!-- Generated through the BMAD create-story workflow rules from approved planning artifacts. -->

## Story

As a media library user,
I want Episort to seed likely groups for mixed folders,
so that files from multiple series, movies, and ignored categories are not assumed to belong together.

## Acceptance Criteria

1. Given the selected folder contains files from multiple shows, movies, and ignored categories
   When inventory classification runs
   Then supported media files are assigned initial likely series, movie, or unknown group seeds
   And ignored and unsupported items are grouped separately
   And the grouping model permits multiple series and movies in the same selected input scope
   And no TVDB identity is treated as final during this inventory step

## Tasks / Subtasks

- [x] Add inventory/scanner tests using temporary directories only. (AC: #1)
- [x] Implement supported, sidecar, unsupported, ignored, and group-seed models needed by this story. (AC: #1)
- [x] Ensure scan and classification perform no create, move, rename, or delete operations. (AC: #1)
- [x] Report progress/results through workflow state rather than direct UI mutation. (AC: #1)
- [x] Update README or developer notes only if this story introduces or changes a developer-facing command or setup step. (AC: #1)
- [x] Run relevant tests and record any manual verification steps in the Dev Agent Record. (AC: #1)

## Dev Notes

### Requirements Coverage

- Epic: 2 - Inventaire media non destructif
- Epic goal: Users can select a valid folder, scan up to 2,000 files, and distinguish supported videos, sidecars, unsupported files, and mixed content without filesystem mutation.
- FRs covered by this epic: FR8, FR9, FR10, FR12, FR13, FR28
- Story source: `_bmad-output/planning-artifacts/epics.md`, Story 2.3

### Technical Requirements

Inventory must stay TVDB-agnostic. It can seed likely groups, but no TVDB identity is final and no operation plan is created in this epic.

### Architecture Compliance

- Responsible packages: `scanner`, `filesystem`, `workflow`, `matching`.
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

Use temporary directories with synthetic files. Include mixed folders, sidecars, unsupported formats, and no-mutation assertions.

- Test files must end with `Test`.
- Filesystem tests must use temporary directories.
- Do not run tests against real media folders.
- Add or update tests before changing sorting, matching, planning, naming, validation, persistence, or filesystem behavior.

### Previous Story Intelligence

Review `2-2-sidecar-and-unsupported-file-inventory.md` before implementation. Reuse its established file locations, test patterns, and decisions; do not duplicate or contradict them.

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

GPT-5 Codex as Amelia, BMAD Senior Software Engineer.

### Debug Log References

- `gradlew.bat test --tests com.episort.scanner.MediaInventoryScannerTest --tests com.episort.workflow.InventoryWorkflowServiceTest` - passed.
- `gradlew.bat test` - passed.

### Completion Notes List

- Added group seed model for likely series, likely movie, unknown, sidecar, unsupported, and ignored groups.
- Series seeds are derived from `SxxEyy` and `1x02` style local filename patterns.
- Movie seeds are derived from filename year patterns; all group seeds keep `tvdbIdentityFinal=false`.

### File List

- `src/main/java/com/episort/scanner/InventoryGroup.java`
- `src/main/java/com/episort/scanner/InventoryGroupType.java`
- `src/main/java/com/episort/scanner/MediaInventoryScanner.java`
- `src/test/java/com/episort/scanner/MediaInventoryScannerTest.java`

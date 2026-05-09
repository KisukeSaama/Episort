# Story 2.1: Supported Media File Detection

Status: review

<!-- Generated through the BMAD create-story workflow rules from approved planning artifacts. -->

## Story

As a media library user,
I want Episort to detect supported video files in my selected folder,
so that only valid organization candidates enter the workflow.

## Acceptance Criteria

1. Given a selected input folder inside the configured workspace
   When Episort scans the folder
   Then `.avi`, `.mp4`, and `.mkv` files are identified as supported video candidates
   And each supported item records its source path, filename, extension, and parent folder
   And scanning does not create, rename, move, or delete any filesystem item

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
- Story source: `_bmad-output/planning-artifacts/epics.md`, Story 2.1

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

GPT-5 Codex as Amelia, BMAD Senior Software Engineer.

### Debug Log References

- `gradlew.bat test --tests com.episort.scanner.MediaInventoryScannerTest --tests com.episort.workflow.InventoryWorkflowServiceTest` - passed.
- `gradlew.bat test` - passed.

### Completion Notes List

- Added non-mutating `MediaInventoryScanner` support for `.avi`, `.mp4`, and `.mkv`.
- Supported items record source path, filename, extension, parent folder, type, and operation-candidate status.
- Scanner tests use JUnit temporary directories and snapshot the folder before/after scan.

### File List

- `src/main/java/com/episort/scanner/InventoryGroup.java`
- `src/main/java/com/episort/scanner/InventoryGroupType.java`
- `src/main/java/com/episort/scanner/InventoryItem.java`
- `src/main/java/com/episort/scanner/InventoryItemType.java`
- `src/main/java/com/episort/scanner/InventoryProgressListener.java`
- `src/main/java/com/episort/scanner/InventoryScanProgress.java`
- `src/main/java/com/episort/scanner/InventoryScanResult.java`
- `src/main/java/com/episort/scanner/InventorySummary.java`
- `src/main/java/com/episort/scanner/MediaInventoryScanner.java`
- `src/main/java/com/episort/workflow/InventoryScanWorkflowResult.java`
- `src/main/java/com/episort/workflow/InventoryWorkflowException.java`
- `src/main/java/com/episort/workflow/InventoryWorkflowService.java`
- `src/test/java/com/episort/scanner/MediaInventoryScannerTest.java`
- `src/test/java/com/episort/workflow/InventoryWorkflowServiceTest.java`

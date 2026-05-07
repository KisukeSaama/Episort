# Story 6.1: Execution Eligibility and Approved Plan Locking

Status: ready-for-dev

<!-- Generated through the BMAD create-story workflow rules from approved planning artifacts. -->

## Story

As a media library user,
I want execution to be possible only after both validation gates are complete,
so that files cannot be changed from an unapproved plan.

## Acceptance Criteria

1. Given an operation plan exists
   When execution eligibility is evaluated
   Then execution is blocked unless pattern validation and exact plan validation are both true
   And the executable plan is treated as immutable once execution starts
   And ignored, unsupported, duplicate-excluded, unassigned, and unapproved items are excluded
   And the UI explains which gate or blocker prevents execution

## Tasks / Subtasks

- [ ] Add tests for double-validation gating, immutable approved plans, workspace revalidation, no deletion, and per-file outcomes. (AC: #1)
- [ ] Implement execution only through `FilesystemExecutor` and only for approved immutable plans. (AC: #1)
- [ ] Record per-file results and recoverable failure details without reporting partial success as full success. (AC: #1)
- [ ] Persist interruption diagnostics outside the media workspace and expose recap state. (AC: #1)
- [ ] Update README or developer notes only if this story introduces or changes a developer-facing command or setup step. (AC: #1)
- [ ] Run relevant tests and record any manual verification steps in the Dev Agent Record. (AC: #1)

## Dev Notes

### Requirements Coverage

- Epic: 6 - Execution approuvee, recuperation et recap
- Epic goal: Users can execute only a validated plan, create folders, move or rename approved files, handle per-file failures, recover after interruption, and see a clear recap.
- FRs covered by this epic: FR56, FR57, FR58, FR59, FR60, FR61, FR62, FR72, FR76
- Story source: `_bmad-output/planning-artifacts/epics.md`, Story 6.1

### Technical Requirements

This is the first epic allowed to mutate the filesystem. Every mutation must be inside workspace, revalidated immediately before execution, and limited to approved folder creation, move, and rename operations. Deletion is forbidden.

### Architecture Compliance

- Responsible packages: `filesystem`, `planning`, `workflow`, `persistence`, `ui.execution`, `ui.recap`.
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

Use temporary directories. Include locked/unavailable simulations where practical, skipped/untouched items, partial failure, and interrupted-state recovery.

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

TBD by dev-story agent.

### Debug Log References

### Completion Notes List

### File List

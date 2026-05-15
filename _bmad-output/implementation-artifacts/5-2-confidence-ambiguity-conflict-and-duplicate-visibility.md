# Story 5.2: Confidence, Ambiguity, Conflict, and Duplicate Visibility

Status: review

<!-- Generated through the BMAD create-story workflow rules from approved planning artifacts. -->

## Story

As a media library user,
I want uncertain and risky matches to be clearly surfaced,
so that I can focus review effort where mistakes are most likely.

## Acceptance Criteria

1. Given match proposals include confidence, ambiguity, duplicate, conflict, unsupported, ignored, or weak-match states
   When the review list is displayed
   Then each state is visible and filterable or otherwise traceable
   And duplicate candidates mapping to the same target episode or movie are identified
   And confidence is shown separately from validation status
   And high confidence never automatically validates a pattern or enables execution

## Tasks / Subtasks

- [x] Add tests for validation state, correction behavior, duplicate/conflict visibility, and execution eligibility blockers. (AC: #1)
- [x] Implement session-scoped correction and validation models without filesystem mutation. (AC: #1)
- [x] Keep confidence separate from validation state in domain and UI-facing models. (AC: #1)
- [x] Use virtualized JavaFX controls for large review lists when UI is touched. (AC: #1)
- [x] Update README or developer notes only if this story introduces or changes a developer-facing command or setup step. (AC: #1)
- [x] Run relevant tests and record any manual verification steps in the Dev Agent Record. (AC: #1)

## Dev Notes

### Requirements Coverage

- Epic: 5 - Review, corrections et validation du pattern
- Epic goal: Users can inspect groups, ambiguities, duplicates, weak matches, and conflicts, correct assignments, and validate the detected pattern before final planning.
- FRs covered by this epic: FR29, FR30, FR40, FR41, FR42, FR43, FR44, FR45, FR46, FR47, FR48, FR49, FR50
- Story source: `_bmad-output/planning-artifacts/epics.md`, Story 5.2

### Technical Requirements

Review is an explicit user control point. Pattern validation captures reviewed grouping/matching state only; exact plan validation remains separate and cannot exist before a plan is generated.

### Architecture Compliance

- Responsible packages: `ui.review`, `workflow`, `matching`, `planning`, `persistence`.
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

Cover no silent propagation, duplicate state, blocking conflicts, validation gates, and 2,000-item model performance where feasible.

- Test files must end with `Test`.
- Filesystem tests must use temporary directories.
- Do not run tests against real media folders.
- Add or update tests before changing sorting, matching, planning, naming, validation, persistence, or filesystem behavior.

### Previous Story Intelligence

Review `4-1-review-screen-for-groups-and-match-states.md` before implementation. Reuse its established file locations, test patterns, and decisions; do not duplicate or contradict them.

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

GPT-5.4

### Debug Log References

### Completion Notes List

- Mapped review states explicitly (`READY`, `NEEDS_REVIEW`, `UNKNOWN`, `CONFLICT`, `DUPLICATE`, `IGNORED`, `UNSUPPORTED`, `AMBIGUOUS`).
- Kept confidence as independent data in `ReviewItem`; validation never derives from confidence.
- Manual verification: use the existing filters and confirm conflicts/duplicates remain traceable while high-confidence rows still require explicit validation.

### File List

- `src/main/java/com/episort/workflow/ReviewMatchState.java`
- `src/main/java/com/episort/workflow/ReviewItem.java`
- `src/main/java/com/episort/workflow/ReviewSession.java`
- `src/main/java/com/episort/ui/scan/ScanScreen.java`
- `src/test/java/com/episort/workflow/ReviewSessionTest.java`

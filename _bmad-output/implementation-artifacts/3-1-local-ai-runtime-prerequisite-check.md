# Story 3.1: Local AI Runtime Prerequisite Check

Status: ready-for-dev

<!-- Generated through the BMAD create-story workflow rules from approved planning artifacts. -->

## Story

As a media library user,
I want Episort to verify local AI prerequisites,
so that AI-dependent workflows do not silently produce unreliable results.

## Acceptance Criteria

1. Given Episort starts or an AI-dependent workflow is requested
   When the local AI prerequisite check runs
   Then Episort verifies required runtime availability and hardware capability signals
   And missing GPU, VRAM, model, or runtime prerequisites block AI-dependent workflows with a recoverable explanation
   And non-AI workflows remain available when AI is unavailable where technically possible
   And prerequisite failures are logged without secrets or unnecessary private media metadata

## Tasks / Subtasks

- [ ] Add tests around AI prerequisite status, advisory suggestion handling, and validation-gate isolation. (AC: #1)
- [ ] Implement `AiPatternAssistant` and `AiRuntimeProbe` behind ports with no cloud AI calls. (AC: #1)
- [ ] Use exactly one bundled model and no user-facing external model management. (AC: #1)
- [ ] Ensure AI can suggest or explain but cannot validate, plan-approve, execute, or mutate state without user action. (AC: #1)
- [ ] Update README or developer notes only if this story introduces or changes a developer-facing command or setup step. (AC: #1)
- [ ] Run relevant tests and record any manual verification steps in the Dev Agent Record. (AC: #1)

## Dev Notes

### Requirements Coverage

- Epic: 3 - Assistance AI locale controlee
- Epic goal: Users can use local AI for pattern detection, proposal explanations, and ambiguity help, with runtime prerequisite checks and no AI authority over execution.
- FRs covered by this epic: FR14, FR51, FR52, FR53, FR54, FR55, FR68, FR69, FR70, FR71
- Story source: `_bmad-output/planning-artifacts/epics.md`, Story 3.1

### Technical Requirements

AI is optional assistance, not truth. TVDB remains metadata authority, user validation remains authorization, and AI failures must block only AI-dependent workflows where possible.

### Architecture Compliance

- Responsible packages: `ai`, `workflow`, `ui.review`, `matching`.
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

Cover missing runtime/GPU/VRAM/model, advisory output handling, selected-item context minimization, and no validation/execution authority.

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

TBD by dev-story agent.

### Debug Log References

### Completion Notes List

### File List

# Story 3.2: Single Bundled Model Enforcement

Status: done

<!-- Generated through the BMAD create-story workflow rules from approved planning artifacts. -->

## Story

As a media library user,
I want Episort to use one bundled local AI model,
so that I do not need to select, download, or manage models.

## Acceptance Criteria

1. Given local AI is enabled for V1
   When the AI runtime initializes
   Then Episort uses exactly one bundled model
   And no UI exists for selecting, downloading, importing, or managing external AI models
   And external model paths are not accepted as user configuration
   And the model identity and runtime status can be diagnosed without exposing private media metadata

## Tasks / Subtasks

- [x] Add tests around AI prerequisite status, advisory suggestion handling, and validation-gate isolation. (AC: #1)
- [x] Implement `AiPatternAssistant` and `AiRuntimeProbe` behind ports with no cloud AI calls. (AC: #1)
- [x] Use exactly one bundled model and no user-facing external model management. (AC: #1)
- [x] Ensure AI can suggest or explain but cannot validate, plan-approve, execute, or mutate state without user action. (AC: #1)
- [x] Update README or developer notes only if this story introduces or changes a developer-facing command or setup step. (AC: #1)
- [x] Run relevant tests and record any manual verification steps in the Dev Agent Record. (AC: #1)

## Dev Notes

### Requirements Coverage

- Epic: 3 - Assistance AI locale controlee
- Epic goal: Users can use local AI for pattern detection, proposal explanations, and ambiguity help, with runtime prerequisite checks and no AI authority over execution.
- FRs covered by this epic: FR14, FR51, FR52, FR53, FR54, FR55, FR68, FR69, FR70, FR71
- Story source: `_bmad-output/planning-artifacts/epics.md`, Story 3.2

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

Review `3-1-local-ai-runtime-prerequisite-check.md` before implementation. Reuse its established file locations, test patterns, and decisions; do not duplicate or contradict them.

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

GPT-5 Codex (Amelia, BMAD dev-story)

### Debug Log References

- Red phase: `.\gradlew.bat test --tests com.episort.ai.AiPrerequisiteServiceTest` failed on missing single-model enforcement types and diagnostic API as expected.
- Green phase: `.\gradlew.bat test --tests com.episort.ai.AiPrerequisiteServiceTest` passed after adding bundled-only model configuration and runtime diagnostic support.
- Port verification: `.\gradlew.bat test --tests com.episort.ai.AiPrerequisiteServiceTest --tests com.episort.ai.AiPatternAssistantTest --tests com.episort.workflow.AiWorkflowGateTest` passed.
- Authority isolation verification: `.\gradlew.bat test --tests com.episort.ai.AiPatternAssistantTest --tests com.episort.workflow.AiWorkflowGateTest` passed.
- Final validation: `.\gradlew.bat build` passed with local `.tools\jdk21`.

### Completion Notes List

- Added explicit tests for bundled-only model configuration, rejection of external model paths, and runtime diagnostics without private media metadata.
- Reused the 3.1 AI ports (`AiPatternAssistant`, `AiRuntimeProbe`) and verified they remain local-only/advisory through the existing targeted test suite.
- Enforced a single stable bundled model identity and blocked external model paths from the AI configuration surface.
- Verified AI suggestions remain advisory-only and cannot claim validation or execution authority.
- README/developer notes were not changed because no developer-facing command or setup step changed.
- No manual UI verification was required because this story introduced no UI surface.

### File List

- src/main/java/com/episort/ai/AiBundledModel.java
- src/main/java/com/episort/ai/AiModelConfiguration.java
- src/main/java/com/episort/ai/AiPrerequisiteService.java
- src/main/java/com/episort/ai/AiRuntimeDiagnostic.java
- src/test/java/com/episort/ai/AiPrerequisiteServiceTest.java
- _bmad-output/implementation-artifacts/3-2-single-bundled-model-enforcement.md
- _bmad-output/implementation-artifacts/sprint-status.yaml

### Change Log

- 2026-05-09: Enforced single bundled AI model configuration, added runtime diagnostics without private media metadata, rejected external model paths, and validated advisory-only AI boundaries.
- 2026-05-09: Code review (3 layers — Blind Hunter, Edge Case Hunter, Acceptance Auditor). AC #1 verified satisfied. Applied 1 patch: null-guard the `runtimeProbe` constructor argument and added regression test `constructorRejectsNullProbe`.

### Review Findings

- [x] [Review][Patch] Null-guard `AiPrerequisiteService` constructor against null probe [src/main/java/com/episort/ai/AiPrerequisiteService.java:12]
- [x] [Review][Defer] `AiModelConfiguration.external()` is an unconditional-throw factory — intentional defensive surface, kept as a hard rejection point
- [x] [Review][Defer] `AiPrerequisiteService` does not cache probe results — out of scope for 3-2; revisit if probing becomes expensive
- [x] [Review][Defer] No test for `runtimeProbe.probe()` throwing — non-blocking; current contract treats probe failure as an unhandled bug
- [x] [Review][Defer] `AiHardwareSignals` could be defensively guarded against null — internal contract, low risk

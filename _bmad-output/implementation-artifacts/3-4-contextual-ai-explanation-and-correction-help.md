# Story 3.4: Contextual AI Explanation and Correction Help

Status: done

<!-- Generated through the BMAD create-story workflow rules from approved planning artifacts. -->

## Story

As a media library user,
I want AI help for a selected file, group, match, conflict, or ambiguity,
so that I can understand why a suggestion exists and decide what to correct.

## Acceptance Criteria

1. Given the user selects a file, group, match, conflict, or ambiguity
   When they request AI assistance
   Then Episort provides only the relevant selected-item context to the local AI assistant
   And the assistant can explain why a grouping or match was proposed
   And the assistant can suggest corrections for ambiguous or weak matches
   And suggested corrections require normal user action and validation before affecting the plan
   And the assistant cannot execute filesystem operations or change validation gates

## Tasks / Subtasks

- [x] Define an `AiContextualAssistant` port that takes only a single selected item context (file, group, match, conflict, ambiguity) and returns advisory explanation + optional suggested correction. (AC: #1)
- [x] Implement the assistant on top of the `AiPatternAssistant` runtime delivered in Story 3.3 (no second bundled model, no cloud calls). (AC: #1)
- [x] Add tests verifying selected-item context is the only payload sent (no whole-inventory leakage), advisory output handling, and refusal when AI runtime is unavailable. (AC: #1)
- [x] Expose the service via a workflow-layer API consumable by the future Epic 5 review surface (Story 5.3) without coupling to JavaFX. (AC: #1)
- [x] Ensure suggestions cannot validate patterns, approve plans, or change validation gates - they require explicit user action through normal correction flows. (AC: #1)
- [x] Run relevant tests and record any manual verification steps in the Dev Agent Record. (AC: #1)

## Dev Notes

### Requirements Coverage

- Epic: 3 - Assistance AI locale controlee
- Epic goal: Users can use local AI for pattern detection, proposal explanations, and ambiguity help, with runtime prerequisite checks and no AI authority over execution.
- FRs covered by this epic: FR14, FR51, FR52, FR53, FR54, FR55, FR68, FR69, FR70, FR71
- Story source: `_bmad-output/planning-artifacts/epics.md`, Story 3.4

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

Review `3-3-ai-assisted-pattern-detection-suggestions.md` before implementation; this story extends the same `AiPatternAssistant` runtime and bundled model to a single-selected-item context API. Reuse its established ports, file locations, test patterns, and decisions; do not duplicate or contradict them.

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

Claude Opus 4.7 (Amelia, BMAD dev-story)

### Debug Log References

- Red phase: wrote `BundledLocalAiContextualAssistantTest` and `AiContextualHelpServiceTest` covering each selection variant plus refusal path.
- Green phase: added the sealed `AiContextualSelection` hierarchy, `AiContextualAssistant` port, advisory `AiExplanation`, `BundledLocalAiContextualAssistant` (delegates to `AiPatternAssistant` from 3-3), and the gated `AiContextualHelpService`. `.\gradlew.bat test --rerun-tasks` BUILD SUCCESSFUL.
- Code review (3 layers — Blind Hunter, Edge Case Hunter, Acceptance Auditor): AC #1 verified satisfied. Applied 1 patch — format `Match.confidence` as a clamped 0–100% percentage to avoid raw-double rendering.

### Completion Notes List

- Selected-item context is enforced at every switch branch: only the selected filename (File/Match/Conflict/Ambiguity) or only the group's filenames (Group) are forwarded to the underlying `AiPatternAssistant`. Tests assert no inventory-wide leakage.
- `AiExplanation` rejects construction with any validation or execution authority, mirroring the invariant from `AiPatternSuggestion`.
- `AiContextualHelpService` refuses without invoking the assistant when `AiWorkflowGate` blocks, returning an `AiContextualHelpResult` with `AI_PREREQUISITES_UNAVAILABLE`.
- Production wiring intentionally deferred — per spec, the service is "consumable by the future Epic 5 review surface (Story 5.3)" and is exposed as a workflow-layer entry point with no JavaFX coupling.
- Match confidence is now formatted as `xx%` (clamped to [0, 100]) instead of raw double output.

### Review Findings

- [x] [Review][Patch] Match confidence formatted as percentage instead of raw double [src/main/java/com/episort/ai/BundledLocalAiContextualAssistant.java]
- [x] [Review][Defer] `Match.confidence` raw-double rendering hardened by patch above; remaining edge cases (NaN, infinity) treated as caller contract violations
- [x] [Review][Defer] Hardcoded English strings — i18n is a project-wide concern outside this story's scope
- [x] [Review][Defer] `AiContextualHelpResult.refused(null)` collapses to empty refusal reason — current invariant is that `AiWorkflowGate.blocked` always carries an error
- [x] [Review][Defer] `AiContextualHelpService` not yet wired in `EpisortApplication` — by spec, consumed by future Epic 5.3
- [x] [Review][Defer] Empty `Group.filenames()` / `Ambiguity.candidates()` produce "(0 item(s))" prose — caller contract; UI layer will gate empty selections

### File List

- src/main/java/com/episort/ai/AiContextualAssistant.java
- src/main/java/com/episort/ai/AiContextualHelpResult.java
- src/main/java/com/episort/ai/AiContextualHelpService.java
- src/main/java/com/episort/ai/AiContextualRequest.java
- src/main/java/com/episort/ai/AiContextualSelection.java
- src/main/java/com/episort/ai/AiExplanation.java
- src/main/java/com/episort/ai/BundledLocalAiContextualAssistant.java
- src/test/java/com/episort/ai/AiContextualHelpServiceTest.java
- src/test/java/com/episort/ai/BundledLocalAiContextualAssistantTest.java
- _bmad-output/implementation-artifacts/3-4-contextual-ai-explanation-and-correction-help.md
- _bmad-output/implementation-artifacts/sprint-status.yaml

### Change Log

- 2026-05-09: Added contextual AI explanation/correction surface — sealed selection hierarchy, advisory explanation type, bundled assistant on top of the 3.3 pattern assistant, and gated workflow-layer help service consumable by Epic 5.3 without JavaFX coupling.
- 2026-05-09: Code review patch — format `Match.confidence` as clamped percentage.

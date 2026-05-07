---
stepsCompleted:
  - 1
  - 2
  - 3
  - 4
  - 5
  - 6
  - 7
  - 8
inputDocuments:
  - "_bmad-output/planning-artifacts/prd.md"
workflowType: "architecture"
project_name: "episort"
user_name: "Jonathan"
date: "2026-05-08"
lastStep: 8
status: "complete"
completedAt: "2026-05-08"
---

# Architecture Decision Document

_This document builds collaboratively through step-by-step discovery. Sections are appended as we work through each architectural decision together._

## Project Context Analysis

### Requirements Overview

**Functional Requirements:**
The PRD defines 77 functional requirements across configuration, workspace safety, media inventory, grouping, TVDB metadata resolution, matching, naming, review, validation, local AI assistance, execution, desktop feedback, and recovery. Architecturally, this points to a modular desktop application where UI, workflow orchestration, metadata clients, matching logic, validation state, and filesystem execution are separate components. The core model must treat scan, match, plan, validation, and execution as distinct phases.

**Non-Functional Requirements:**
The strongest architectural drivers are filesystem safety, no mutation during planning, workspace boundary enforcement, responsive large-batch review up to 2,000 files, TVDB credential protection, local diagnostic logging, recoverable TVDB and execution failures, and local-only AI integration. The UI must remain responsive during long-running work, so scan, lookup, AI analysis, planning, and execution need background task orchestration with progress reporting.

**Scale & Complexity:**
Episort is a medium-complexity Windows desktop application with high correctness requirements around filesystem behavior and user validation. The data volume target is moderate, but the workflow complexity is elevated because files can represent mixed series, movies, specials, duplicates, unsupported items, and ambiguous matches.

- Primary domain: Desktop media-library organization and metadata-backed file operation planning
- Complexity level: Medium
- Estimated architectural components: 10 core components: JavaFX UI, workflow/application services, settings storage, workspace boundary service, media scanner, TVDB client, local AI adapter, matching/grouping engine, operation planner, filesystem executor/logging

### Technical Constraints & Dependencies

- Java 21, JavaFX, Gradle, and JUnit 5 are preferred stack constraints.
- Windows 11 is the primary V1 platform.
- TVDB v4 is the metadata authority and requires online access.
- Local AI must run locally with one bundled model and no user-facing model management.
- Settings and logs must live in the OS user profile, not the media workspace or source tree.
- All file operations must remain bounded to the configured workspace.
- File moves and renames cannot execute until both validation gates are complete.
- Sidecars, unsupported files, duplicate-excluded files, ignored files, and unassigned files must remain untouched.

### Cross-Cutting Concerns Identified

- Workspace boundary validation for every path read, planned, created, moved, or renamed.
- Validation state separated from confidence and independent from AI suggestions.
- Deterministic operation planning with no filesystem mutation.
- TVDB authentication, token refresh, metadata fallback, and transient failure handling.
- Local AI failure handling and explicit prevention of AI authorization.
- Large-batch performance and UI responsiveness.
- Path normalization, destination conflict detection, Windows path-length handling, and title sanitization.
- Secure credential handling and log redaction.
- Per-file execution status, retry handling, and interrupted execution recovery.

## Starter Template Evaluation

### Primary Technology Domain

Desktop JVM application, Windows 11 first, using Java 21 and JavaFX.

### Starter Options Considered

1. Gradle `java-application` init
   - Best fit for a conservative Java desktop app.
   - Provides standard source layout, application plugin, wrapper, JUnit setup, and predictable Gradle conventions.
   - Gradle docs confirm `java-application`, Kotlin DSL, JUnit Jupiter, project name, package, and Java version flags are supported.

2. OpenJFX Gradle plugin
   - Adds JavaFX module dependencies cleanly.
   - Current Gradle Plugin Portal version found: `org.openjfx.javafxplugin` `0.1.0`.
   - Suitable for `javafx.controls` and likely `javafx.fxml` if FXML is used.

3. Full desktop starters such as Electron or Tauri
   - Not selected. They conflict with the PRD's Java/JavaFX preference and would add JS/Rust packaging complexity without solving the core matching and filesystem safety problem.

### Selected Starter: Gradle Java Application + OpenJFX Plugin

**Rationale for Selection:**
Use Gradle's built-in Java application scaffold, then add JavaFX via the OpenJFX Gradle plugin. This keeps the foundation boring, testable, and aligned with the project guidelines. It avoids a heavyweight desktop framework and leaves architecture decisions in our code instead of hidden inside a starter.

**Initialization Command:**

```bash
gradle init --type java-application --dsl kotlin --test-framework junit-jupiter --package com.episort --project-name episort --no-split-project --java-version 21
```

**Architectural Decisions Provided by Starter:**

**Language & Runtime:**
Java 21, Gradle JVM application conventions, single application module to start.

**UI Framework:**
JavaFX through `org.openjfx.javafxplugin` version `0.1.0`, with modules such as `javafx.controls` and optionally `javafx.fxml`.

**Build Tooling:**
Gradle Application plugin for local `run` and distribution tasks. Gradle wrapper should be committed after initialization.

**Testing Framework:**
JUnit Jupiter from Gradle init, with focused tests for domain logic, workspace safety, planning, naming, and TVDB mapping.

**Code Organization:**
Use standard Gradle layout and keep packages separated by responsibility: `ui`, `workflow`, `config`, `filesystem`, `scanner`, `matching`, `planning`, `tvdb`, `ai`, `logging`.

**Development Experience:**
Start with `./gradlew run`, `./gradlew test`, and `./gradlew build`. Add Spotless or Checkstyle after the initial scaffold rather than baking it into the starter choice.

**Note:** Project initialization using this command should be the first implementation story.

## Core Architectural Decisions

### Decision Priority Analysis

**Critical Decisions (Block Implementation):**
- Use hybrid local persistence: settings outside the workspace, credentials in OS-backed storage, SQLite for session state, and redacted append-only logs.
- Enforce security through credential isolation, validation-gated execution, workspace boundary checks, and log redaction.
- Use ports-and-adapters inside a single JavaFX desktop process.
- Keep JavaFX UI thin with MVVM-style view models and background task orchestration.
- Use Gradle/JDK packaging for a Windows-first local desktop deployment.

**Important Decisions (Shape Architecture):**
- Use Java 21 `HttpClient` for TVDB calls.
- Use Jackson for JSON mapping.
- Keep retry/token-refresh behavior localized around TVDB adapters.
- Start with core JavaFX controls and add ControlsFX only selectively.
- Defer custom runtime and installer details until the JavaFX/module/dependency layout is stable.

**Deferred Decisions (Post-MVP):**
- Auto-update mechanism.
- macOS/Linux packaging.
- Installer signing for external distribution.
- Explorer context menu and shell extensions.
- Background service behavior.
- User-facing AI model management.

### Data Architecture

**Decision:**
Use a hybrid local persistence model: simple user settings outside the media workspace, secure credential storage for TVDB secrets, SQLite for organization-session state, and append-only diagnostic logs in the OS user profile.

**Rationale:**
Episort needs to handle up to 2,000 media files per workflow, support filtering and review, preserve validation state separately from confidence, record exact operation plans, report per-file execution outcomes, and detect interrupted execution. SQLite gives session state a transactional local store without introducing a server. Settings and credentials remain separate because they have different security and lifecycle requirements.

**Persistence Boundaries:**
- Settings: OS user profile, outside media workspace.
- Credentials: Windows Credential Manager preferred; fallback must avoid source control, logs, and media folders.
- Session state: SQLite database in the OS user profile.
- Logs: local diagnostic log files in the OS user profile with credential redaction.

**Version Notes:**
`sqlite-jdbc` current Maven Central versions include 2026 releases such as `3.53.0.0`; pin the exact version during implementation after dependency verification.

### Authentication & Security

**Decision:**
Episort has no user-account authentication in V1. Security is implemented through OS-backed credential storage, explicit operation authorization, workspace boundary enforcement, and log redaction.

**Credential Storage:**
Use a `CredentialStore` abstraction. The Windows 11 implementation should target Windows Credential Manager through a small JNA-based adapter. A fallback local credential file is allowed only as an explicit degraded mode and must remain outside the media workspace and source tree.

**Filesystem Authorization:**
Filesystem execution is authorized only by validation state, not by confidence, AI output, or TVDB lookup success. Execution requires both pattern validation and exact operation-plan validation.

**Path Security:**
Every source path, destination path, folder creation, rename, and move must pass workspace-boundary validation after normalization/resolution. Symlinks, relative segments, path traversal, destination conflicts, and Windows path-length behavior must be handled before execution.

**Secret Handling:**
TVDB API keys, subscriber PINs, and bearer tokens must never be logged, exported in operation plans, stored in SQLite session rows, or shown in diagnostic output. Logs use explicit redaction at write boundaries.

**Version Notes:**
JNA current Maven Central version verified as `5.18.1`; exact dependency should be pinned during implementation.

### API & Communication Patterns

**Decision:**
Use a ports-and-adapters architecture inside a single desktop process. JavaFX controllers/view models call application services, application services depend on domain ports, and infrastructure adapters implement TVDB, local AI, persistence, logging, notifications, and filesystem execution.

**Internal Communication:**
Use synchronous domain methods for pure planning and validation logic, and explicit background task orchestration for long-running work. JavaFX must never call TVDB lookup, AI analysis, large scans, planning, or execution directly on the UI thread.

**External HTTP Communication:**
Use Java 21 `java.net.http.HttpClient` for TVDB integration. It is part of the JDK, supports reusable immutable clients, and avoids adding an HTTP framework dependency for a small API surface.

**JSON Mapping:**
Use Jackson for TVDB request/response mapping and local structured data where needed. Current Maven Central listings show Jackson Databind 2.21.x releases in 2026; pin the exact version during implementation.

**Resilience:**
Implement retry and token refresh explicitly around `TvdbClient`. Keep the initial resilience layer small: bounded retry for transient TVDB failures, bearer-token reacquisition on expiry, timeout handling, and clear recoverable errors. Resilience4j is available and current version checked as `2.4.0`, but should be added only if hand-rolled retry logic starts spreading.

**Error Handling:**
Use typed result objects or domain exceptions at adapter boundaries. UI-facing errors must distinguish blocking configuration errors, TVDB authentication errors, transient lookup errors, ambiguous metadata results, validation blockers, and filesystem execution failures.

**AI Communication:**
Represent local AI as an `AiPatternAssistant` port. It can propose grouping, explain matches, and suggest corrections, but its output is advisory and must pass through normal validation and planning rules.

**Progress Communication:**
Long-running workflows publish progress events through application-level progress models, not direct UI mutation. JavaFX adapters subscribe and marshal updates onto the JavaFX application thread.

### Frontend Architecture

**Decision:**
Use a JavaFX MVVM-style architecture with thin views/controllers, view models for UI state, and application services for workflow actions. Controllers must not contain matching, planning, TVDB, AI, or filesystem logic.

**View Structure:**
V1 should use a small set of workflow-oriented screens:
- Settings / first-launch gate
- Input selection and scan progress
- Group review and pattern validation
- Operation plan review and exact-plan validation
- Execution progress
- Post-execution recap

**State Management:**
Use JavaFX observable properties and observable collections inside view models. Keep canonical workflow state in application/session services, not in UI controls. View models project domain state into UI-friendly rows, filters, selection state, and validation affordances.

**Large-Batch Review:**
Use JavaFX virtualized controls such as `TableView`, `TreeTableView`, or `ListView` for 2,000-file review screens. Avoid rendering one node per media file outside virtualized controls.

**Background Work:**
Long-running scan, TVDB lookup, AI analysis, planning, and execution run through application task orchestration. UI receives progress and result events, then updates JavaFX properties on the JavaFX application thread.

**Component Libraries:**
Start with core JavaFX controls. ControlsFX may be added selectively for practical controls if needed; current Maven Central version checked as `11.2.3`. Avoid ReactFX for V1 unless event-stream complexity becomes real; the latest listing is milestone-style (`2.0-M6`), which is not a boring default.

**Dark Mode:**
Use an application stylesheet and theme tokens/classes rather than hardcoded colors in controllers. Dark mode is a V1 requirement and should be part of the base UI shell.

**Validation UX Rule:**
Validation state is explicit UI state. High confidence can change visual priority but cannot enable execution by itself. Execution controls remain disabled until both required validations are true and the operation plan has no blocking conflicts.

### Infrastructure & Deployment

**Decision:**
Use a local-first desktop deployment model. Build and test with Gradle, package manually for Windows 11, and defer automatic updates and cross-platform packaging.

**Build Pipeline:**
Use Gradle tasks as the implementation contract:
- `./gradlew run` for local launch
- `./gradlew test` for unit tests
- `./gradlew build` for compile/test/package checks
- `./gradlew spotlessApply` if Spotless is enabled

**Packaging:**
Start with Gradle Application plugin distributions for early development. For V1 packaging, use JDK `jpackage` to produce a Windows app image or installer once the JavaFX/module/dependency layout is stable. Consider `org.beryx.jlink` for custom runtime images; current Gradle Plugin Portal version verified as `4.0.0`.

**Windows Packaging Notes:**
JDK 21 `jpackage` supports self-contained app bundles and platform-specific package types such as `exe` and `msi`. Windows packages must be built on Windows. Use per-user install unless there is a clear reason to require admin installation.

**CI/CD:**
Use a minimal CI pipeline once the Gradle project exists:
- compile
- unit tests
- formatting/checkstyle
- build artifact smoke check

Do not introduce release automation before the app can safely complete the core workflow.

**Environment Configuration:**
Use OS user profile storage for settings, logs, SQLite session DB, and credential references. TVDB credentials are not environment-specific build inputs; they are runtime user configuration.

**Observability:**
Use local diagnostic logs with redaction. Logs must cover scan, TVDB auth/lookup, AI analysis, planning, validation, execution, failures, and interrupted execution recovery.

**Deferred Decisions:**
- Auto-update: deferred post-V1.
- macOS/Linux packages: deferred.
- Installer signing: decide when distributing beyond personal use.
- Background service / shell integration / Explorer context menu: out of scope for V1.

### Decision Impact Analysis

**Implementation Sequence:**
1. Initialize Gradle Java 21/JavaFX application.
2. Establish package boundaries and domain/application/infrastructure layering.
3. Implement settings, credential abstraction, logging, and workspace boundary service.
4. Implement media inventory and no-mutation planning primitives with tests.
5. Add SQLite-backed session state once workflow state needs persistence.
6. Add TVDB client and mapping adapters behind ports.
7. Add matching, grouping, validation, and operation planning.
8. Build JavaFX review workflow over view models and background tasks.
9. Add filesystem execution only after plan validation rules are tested.
10. Add packaging after the app can complete the core safe workflow.

**Cross-Component Dependencies:**
- UI execution controls depend on validation state from application services.
- Operation planning depends on workspace boundary validation, naming generation, TVDB metadata, and user corrections.
- Filesystem execution depends on approved operation plans and path safety checks.
- TVDB and local AI adapters are advisory/input providers; neither can authorize execution.
- Logs and session persistence must receive redacted, structured events from workflow services.

## Implementation Patterns & Consistency Rules

### Pattern Categories Defined

**Critical Conflict Points Identified:**
Nine areas need explicit consistency: database naming, Java package layout, domain model naming, adapter boundaries, error formats, progress events, validation state, logging, and test placement.

### Naming Patterns

**Database Naming Conventions:**
Use lowercase `snake_case` for SQLite tables, columns, indexes, and constraints.

Examples:
- Tables: `organization_sessions`, `media_items`, `match_proposals`, `operation_plan_items`
- Columns: `session_id`, `source_path`, `destination_path`, `validation_status`
- Indexes: `idx_media_items_session_id`, `idx_plan_items_status`

**Code Naming Conventions:**
Use standard Java naming.
- Classes and records: `PascalCase`
- Methods and fields: `camelCase`
- Packages: lowercase
- Domain names should be explicit: `OperationPlan`, `WorkspaceBoundary`, `EpisodeMatch`, `ValidationGate`

**DTO and Adapter Naming:**
External API DTOs must include the provider prefix.
- TVDB DTOs: `TvdbSeriesSearchResponse`, `TvdbEpisodeDto`
- AI DTOs: `AiPatternSuggestion`, `AiExplanationRequest`
- Domain objects must not be named after provider payloads.

### Structure Patterns

**Project Organization:**
Use package-by-responsibility under `com.episort`:
- `ui`
- `workflow`
- `config`
- `filesystem`
- `scanner`
- `matching`
- `planning`
- `tvdb`
- `ai`
- `persistence`
- `logging`

**Test Organization:**
Tests live under `src/test/java` mirroring production packages. Test names end in `Test`.

Examples:
- `WorkspaceBoundaryTest`
- `SeasonFolderPlannerTest`
- `OperationPlanValidatorTest`
- `TvdbResponseMapperTest`

**Boundary Rule:**
Domain and planning code must not import JavaFX classes. UI code may depend on application services and view models, but not directly on filesystem executors or TVDB adapters.

### Format Patterns

**Data Exchange Formats:**
Use Java domain records/classes internally. Use provider-specific DTOs only at adapter boundaries. JSON field names should follow the external API for DTOs, but internal Java fields remain `camelCase`.

**Error Format:**
Use typed application errors with:
- `code`
- `severity`
- `message`
- `recoverable`
- optional `details`

Example codes:
- `CONFIG_MISSING_WORKSPACE`
- `TVDB_AUTH_FAILED`
- `WORKSPACE_BOUNDARY_VIOLATION`
- `PLAN_HAS_CONFLICTS`
- `FILE_LOCKED_DURING_EXECUTION`

### Communication Patterns

**Progress Events:**
Long-running workflows emit structured progress snapshots:
- `phase`
- `completedUnits`
- `totalUnits`
- `message`
- optional `currentItem`

Phases use `UPPER_SNAKE_CASE`, for example `SCANNING`, `TVDB_LOOKUP`, `AI_ANALYSIS`, `PLANNING`, `EXECUTING`.

**State Management Patterns:**
Validation state is modeled explicitly, not inferred from confidence.

Use separate concepts:
- `confidence`
- `ambiguityStatus`
- `patternValidationStatus`
- `operationPlanValidationStatus`
- `executionEligibility`

### Process Patterns

**Error Handling Patterns:**
Adapters translate low-level exceptions into application/domain errors at the boundary. UI displays recoverable action-oriented messages and never exposes secrets, bearer tokens, raw credentials, or stack traces.

**Loading State Patterns:**
Each long-running workflow has one authoritative task state in the workflow layer. UI view models observe that state and project it into controls.

**Logging Patterns:**
Logs are structured enough to troubleshoot workflow phases and per-file execution. Log writes must pass through redaction. Do not log TVDB secrets, tokens, full credential values, or unnecessary private media metadata.

### Enforcement Guidelines

**All AI Agents MUST:**
- Keep scan, match, plan, validation, and execution as separate phases.
- Add or update tests before changing sorting, matching, planning, naming, validation, or filesystem behavior.
- Route every planned or executed path through workspace-boundary validation.
- Keep JavaFX out of domain, planning, filesystem, persistence, TVDB, and AI ports.
- Treat local AI output as advisory and never as authorization.
- Preserve original file extensions in naming logic.
- Keep unsupported, ignored, duplicate-excluded, and unassigned files out of executable operation plans.

**Pattern Enforcement:**
Pattern violations should be fixed in the implementing story before it is considered complete. New patterns should be added to this architecture document or project context when repeated ambiguity appears.

### Pattern Examples

**Good Examples:**
- `OperationPlanValidator` rejects a plan item whose resolved destination is outside the workspace.
- `TvdbClient` returns mapped metadata or typed TVDB errors; it does not update UI controls.
- `ReviewViewModel` exposes observable row state derived from application services.
- `FilesystemExecutor` accepts only an approved immutable `OperationPlan`.

**Anti-Patterns:**
- JavaFX controller directly renames a file.
- AI suggestion directly changes validation state.
- Confidence score enables execution.
- TVDB bearer token appears in logs.
- Planner creates folders during plan generation.
- Test scans a real media library folder.

## Project Structure & Boundaries

### Complete Project Directory Structure

```text
episort/
├── README.md
├── AGENTS.md
├── settings.gradle.kts
├── build.gradle.kts
├── gradlew
├── gradlew.bat
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── docs/
│   ├── tvdb-behavior-notes.md
│   ├── filesystem-safety.md
│   └── local-ai-runtime-notes.md
├── assets/
│   ├── icons/
│   └── styles/
│       ├── dark.css
│       └── light.css
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── episort/
│   │   │           ├── EpisortApplication.java
│   │   │           ├── ui/
│   │   │           │   ├── AppShell.java
│   │   │           │   ├── settings/
│   │   │           │   ├── scan/
│   │   │           │   ├── review/
│   │   │           │   ├── execution/
│   │   │           │   └── recap/
│   │   │           ├── workflow/
│   │   │           │   ├── OrganizationWorkflowService.java
│   │   │           │   ├── WorkflowProgress.java
│   │   │           │   └── WorkflowPhase.java
│   │   │           ├── config/
│   │   │           │   ├── AppSettings.java
│   │   │           │   ├── SettingsRepository.java
│   │   │           │   └── CredentialStore.java
│   │   │           ├── filesystem/
│   │   │           │   ├── WorkspaceBoundary.java
│   │   │           │   ├── MediaPath.java
│   │   │           │   ├── FilesystemExecutor.java
│   │   │           │   └── ExecutionResult.java
│   │   │           ├── scanner/
│   │   │           │   ├── MediaScanner.java
│   │   │           │   ├── MediaInventory.java
│   │   │           │   └── SupportedMediaType.java
│   │   │           ├── matching/
│   │   │           │   ├── MediaGroup.java
│   │   │           │   ├── MatchProposal.java
│   │   │           │   ├── EpisodeMatch.java
│   │   │           │   └── MovieMatch.java
│   │   │           ├── planning/
│   │   │           │   ├── OperationPlan.java
│   │   │           │   ├── OperationPlanItem.java
│   │   │           │   ├── OperationPlanValidator.java
│   │   │           │   ├── SeasonFolderPlanner.java
│   │   │           │   └── MediaNameFormatter.java
│   │   │           ├── tvdb/
│   │   │           │   ├── TvdbClient.java
│   │   │           │   ├── TvdbMetadataService.java
│   │   │           │   └── dto/
│   │   │           ├── ai/
│   │   │           │   ├── AiPatternAssistant.java
│   │   │           │   ├── AiRuntimeProbe.java
│   │   │           │   └── dto/
│   │   │           ├── persistence/
│   │   │           │   ├── SessionRepository.java
│   │   │           │   ├── SqliteSessionRepository.java
│   │   │           │   └── migrations/
│   │   │           └── logging/
│   │   │               ├── DiagnosticLogger.java
│   │   │               └── SecretRedactor.java
│   │   └── resources/
│   │       ├── com/episort/ui/
│   │       └── styles/
│   └── test/
│       └── java/
│           └── com/
│               └── episort/
│                   ├── filesystem/
│                   ├── scanner/
│                   ├── matching/
│                   ├── planning/
│                   ├── tvdb/
│                   ├── persistence/
│                   └── workflow/
└── _bmad-output/
    └── planning-artifacts/
        ├── prd.md
        └── architecture.md
```

### Architectural Boundaries

**UI Boundary:**
`ui` owns JavaFX screens, view models, styles, and control binding. It may call `workflow` services. It must not directly call TVDB, AI, persistence, or filesystem executors.

**Workflow Boundary:**
`workflow` orchestrates scan, grouping, matching, planning, validation, execution, and progress reporting. It coordinates ports but does not own low-level adapters.

**Domain/Planning Boundary:**
`matching` and `planning` contain pure domain decisions where possible. These packages must be testable without JavaFX, TVDB network calls, or real media folders.

**Filesystem Boundary:**
`filesystem` owns workspace normalization, path validation, folder creation, move/rename execution, and per-file execution results. No other package performs file mutation.

**External Integration Boundaries:**
`tvdb` and `ai` are adapters behind ports. Their DTOs stay inside their package boundaries and are mapped into domain objects before reaching planning logic.

**Persistence Boundary:**
`persistence` stores session state and execution outcomes. It does not authorize execution and does not store secrets.

### Requirements to Structure Mapping

**Configuration and Workspace (FR1-FR5):**
`config`, `filesystem`, `ui.settings`

**Media Loading and Inventory (FR6-FR12):**
`scanner`, `filesystem`, `workflow`

**Grouping and Metadata Resolution (FR13-FR22):**
`matching`, `tvdb`, `ai`, `workflow`

**Series, Movie, and Episode Matching (FR23-FR31):**
`matching`, `planning`, `tvdb`

**Naming and Operation Planning (FR32-FR40):**
`planning`, `filesystem`

**Review, Validation, and Correction (FR41-FR50):**
`ui.review`, `workflow`, `matching`, `planning`, `persistence`

**Local AI Assistance (FR51-FR55, FR68-FR71):**
`ai`, `workflow`, `ui.review`

**Execution and Results (FR56-FR62, FR72-FR76):**
`filesystem`, `planning`, `workflow`, `persistence`, `ui.execution`, `ui.recap`

**Desktop Feedback (FR63-FR67):**
`ui`, `workflow`, `logging`

### Integration Points

**Internal Communication:**
UI calls workflow services. Workflow services call scanner, TVDB, AI, planning, persistence, logging, and filesystem ports. Progress flows back through `WorkflowProgress`.

**External Integrations:**
TVDB integration lives in `tvdb`. Local AI runtime integration lives in `ai`. Windows Credential Manager implementation belongs behind `config.CredentialStore`.

**Data Flow:**
Input folder -> scan inventory -> groups/matches -> user corrections -> validated pattern -> operation plan -> validated plan -> execution -> recap/log/session state.

### File Organization Patterns

**Configuration Files:**
Gradle configuration remains at repo root. Runtime user settings are stored outside the repo and outside the media workspace.

**Source Organization:**
Source code is organized by responsibility, not by UI screen alone. Provider DTOs remain under provider packages.

**Test Organization:**
Tests mirror production package structure. Filesystem tests use temporary directories only.

**Asset Organization:**
Bundled icons and styles live in `assets/` and runtime resources under `src/main/resources`.

### Development Workflow Integration

**Development Server Structure:**
No dev server. `./gradlew run` launches the desktop app.

**Build Process Structure:**
Gradle compiles Java, runs tests, and later packages the app.

**Deployment Structure:**
Early builds use Gradle application distributions. V1 packaging uses JDK `jpackage` once the runtime layout is stable.

## Architecture Validation Results

### Coherence Validation

**Decision Compatibility:**
The decisions are compatible. Java 21, JavaFX, Gradle, SQLite, Java `HttpClient`, Jackson, JNA/WinCred, and JDK `jpackage` fit a Windows-first desktop app without requiring a server or web runtime.

**Pattern Consistency:**
The patterns reinforce the architecture: Java naming for code, `snake_case` for SQLite, provider-prefixed DTOs, explicit validation state, structured progress, and redacted logging all support the safety-first workflow.

**Structure Alignment:**
The structure maps cleanly to the chosen boundaries. UI, workflow, scanner, matching, planning, filesystem, TVDB, AI, persistence, config, and logging have clear responsibilities.

### Requirements Coverage Validation

**Feature Coverage:**
All major PRD capability areas are architecturally represented: settings, workspace safety, scanning, grouping, TVDB lookup, AI assistance, review, validation, planning, execution, recap, logs, and packaging.

**Functional Requirements Coverage:**
The 77 FRs are covered at architectural level through mapped packages and workflow phases. The most important safety requirements are explicitly represented by `WorkspaceBoundary`, validation gates, `OperationPlanValidator`, and `FilesystemExecutor`.

**Non-Functional Requirements Coverage:**
Performance is addressed through background task orchestration and JavaFX virtualized controls. Security is addressed through credential isolation, workspace enforcement, no secret logging, and validation-gated execution. Reliability is addressed through SQLite session state, per-file execution results, retry handling, and interrupted execution recovery.

### Implementation Readiness Validation

**Decision Completeness:**
Critical decisions are documented with enough specificity to start implementation. Versions were verified for OpenJFX plugin, SQLite JDBC, JNA, ControlsFX, Resilience4j, and packaging options, with exact pinning deferred to implementation.

**Structure Completeness:**
The project structure is complete enough for the initial Gradle scaffold and first implementation stories. Some concrete migration filenames and UI class names will naturally emerge during implementation.

**Pattern Completeness:**
The patterns cover the primary places where AI agents could diverge: package placement, DTO/domain separation, validation state, errors, progress, tests, logs, and filesystem mutation.

### Gap Analysis Results

**Critical Gaps:**
None blocking architecture completion.

**Important Gaps:**
- TVDB v4 endpoint and field mapping still needs technical research before implementing the real client.
- Local AI runtime/model selection remains an open research item before production-quality AI assistance.
- Windows path-length and title-truncation rules need concrete algorithm tests.
- SQLite schema migrations need a chosen migration mechanism during implementation.

**Nice-to-Have Gaps:**
- CI workflow file can be added after Gradle initialization.
- Packaging details can wait until the app runs end-to-end.
- UI screenshots/manual verification checklist can be added when UI exists.

### Validation Issues Addressed

No contradictions found. The only notable implementation risk is scope size: the architecture supports the PRD, but stories should implement filesystem safety and planning primitives before UI polish, TVDB breadth, or AI richness.

### Architecture Completeness Checklist

**Requirements Analysis**
- [x] Project context thoroughly analyzed
- [x] Scale and complexity assessed
- [x] Technical constraints identified
- [x] Cross-cutting concerns mapped

**Architectural Decisions**
- [x] Critical decisions documented with versions
- [x] Technology stack fully specified
- [x] Integration patterns defined
- [x] Performance considerations addressed

**Implementation Patterns**
- [x] Naming conventions established
- [x] Structure patterns defined
- [x] Communication patterns specified
- [x] Process patterns documented

**Project Structure**
- [x] Complete directory structure defined
- [x] Component boundaries established
- [x] Integration points mapped
- [x] Requirements to structure mapping complete

### Architecture Readiness Assessment

**Overall Status:** READY FOR IMPLEMENTATION

**Confidence Level:** High for the core architecture, medium for TVDB and local AI implementation details until research spikes are complete.

**Key Strengths:**
- Strong filesystem safety model.
- Clear validation gates.
- Testable non-UI domain boundaries.
- Conservative Java desktop stack.
- Explicit separation between AI suggestions, TVDB metadata, and user authorization.

**Areas for Future Enhancement:**
- TVDB response mapping notes.
- Local AI runtime benchmark notes.
- Packaging and installer signing.
- Optional richer review filters after the core workflow works.

### Implementation Handoff

**AI Agent Guidelines:**
- Follow all architectural decisions exactly as documented.
- Use implementation patterns consistently across all components.
- Respect package boundaries.
- Add tests before modifying matching, planning, naming, validation, or filesystem behavior.
- Never allow scan, analysis, matching, or planning code to mutate the filesystem.

**First Implementation Priority:**
Initialize the Gradle Java 21 application with JavaFX and JUnit 5, then implement workspace boundary and no-mutation planning tests before adding UI breadth.

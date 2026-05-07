---
stepsCompleted:
  - step-01-requirements-extracted
  - step-02-epics-approved
  - step-03-stories-created
  - step-04-final-validation
inputDocuments:
  - "_bmad-output/planning-artifacts/prd.md"
  - "_bmad-output/planning-artifacts/architecture.md"
---

# episort - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for episort, decomposing the requirements from the PRD, UX Design if it exists, and Architecture requirements into implementable stories.

## Requirements Inventory

### Functional Requirements

FR1: Users can configure a workspace directory that defines the only filesystem boundary Episort may scan, plan within, create folders in, rename files in, or move files within.
FR2: Users can configure TVDB access required for metadata-backed organization.
FR3: Users can test TVDB access before starting a TVDB-backed organization workflow.
FR4: Episort can block organization workflows when required workspace or TVDB configuration is missing or invalid.
FR5: Episort can persist user settings outside the media workspace.
FR6: Users can select an input folder inside the configured workspace for organization.
FR7: Episort can reject or block selected input folders outside the configured workspace.
FR8: Episort can scan supported video files from the selected input folder.
FR9: Episort can identify sidecar files that should remain untouched.
FR10: Episort can identify unsupported files that should remain untouched and traceable.
FR11: Episort can preserve each supported media file's original extension in the proposed destination name.
FR12: Episort can handle organization workflows containing both movies and TV series in the same selected input scope.
FR13: Episort can group files by likely series, movie, or unsupported/ignored status before final matching.
FR14: Episort can use local AI assistance to detect naming patterns and propose grouping or matching candidates.
FR15: Episort can search TVDB for candidate TV series matches.
FR16: Episort can search TVDB for candidate movie matches.
FR17: Users can choose or correct the TVDB series associated with a detected series group.
FR18: Users can choose or correct the TVDB movie associated with a detected movie file or movie group.
FR19: Episort can retrieve TVDB metadata needed for official English series names, seasons, episode numbers, episode titles, and supported episode orders.
FR20: Episort can retrieve TVDB metadata needed for English movie names and release years.
FR21: Episort can use the best available TVDB fallback title when English metadata is unavailable.
FR22: Episort can distinguish homonymous series or movies using TVDB-backed identity information.
FR23: Episort can propose episode matches for TV series files using TVDB metadata.
FR24: Episort can propose movie matches for movie files using TVDB metadata.
FR25: Episort can support aired order, DVD order, and absolute order when TVDB provides those order modes.
FR26: Users can review and change the selected episode order for a series.
FR27: Episort can identify specials or OVA files represented in TVDB.
FR28: Episort can mark files as ignored or unsupported when they should not participate in the operation plan.
FR29: Episort can identify duplicate candidates that appear to map to the same target episode or movie.
FR30: Users can manually correct series, movie, season, episode, order, duplicate, ignore, and unsupported assignments.
FR31: Episort can prevent a single supported video file from being assigned to more than one TVDB episode or movie in V1.
FR32: Episort can generate proposed series folder paths using the official English series name.
FR33: Episort can generate proposed season folder paths using the `Season XX` convention.
FR34: Episort can generate proposed series episode filenames using `Series Name in English - SXXEXX - Episode Title in English.original-extension`.
FR35: Episort can generate proposed specials paths under a `Specials` folder when the file maps to a TVDB special.
FR36: Episort can generate proposed movie filenames using `English Movie Name (Release Year).original-extension`.
FR37: Episort can generate a complete source-to-destination operation plan within the configured workspace.
FR38: Episort can detect destination path conflicts before execution.
FR39: Episort can keep unsupported, ignored, duplicate-excluded, and unassigned files out of executable operations.
FR40: Users can review every proposed source path and destination path before execution.
FR41: Users can review detected series groups, movie groups, ignored files, unsupported files, ambiguous files, and conflicts before execution.
FR42: Users can validate the detected pattern before final operation-plan approval.
FR43: Users can validate the exact source-to-destination operation plan before execution.
FR44: Episort can block execution until required validation steps are complete.
FR45: Episort can track validation state separately from confidence.
FR46: Episort can expose match confidence or uncertainty to help users prioritize review.
FR47: Episort can expose ambiguous, conflicting, duplicate, unsupported, ignored, and weak-match states during review.
FR48: Users can correct proposed matches without restarting the workflow.
FR49: Episort can apply user corrections within the current organization session.
FR50: Episort can avoid silently propagating one manual correction to neighboring files.
FR51: Users can request contextual AI assistance for a selected file, group, match, conflict, or ambiguity.
FR52: Episort can provide the local AI assistant with relevant selected-item context.
FR53: The local AI assistant can explain why a grouping or match was proposed.
FR54: The local AI assistant can suggest corrections for ambiguous or weak matches.
FR55: Episort can prevent local AI assistance from authorizing or executing filesystem operations.
FR56: Users can execute an approved operation plan after all required validation is complete.
FR57: Episort can create required destination folders within the configured workspace during execution.
FR58: Episort can rename and move approved files within the configured workspace.
FR59: Episort can prevent automatic deletion of files.
FR60: Episort can leave files untouched when they are ignored, unsupported, excluded as duplicates, unassigned, or not included in the approved operation plan.
FR61: Episort can continue or report per-file failures without treating a single failed operation as proof that the entire plan succeeded.
FR62: Episort can show a post-execution recap listing per-file outcomes.
FR63: Episort can show progress for long-running scan, metadata lookup, planning, and execution activities.
FR64: Episort can notify users when long-running activities complete or when blocking errors require attention.
FR65: Episort can show taskbar progress for long-running activities where supported.
FR66: Users can use the application in dark mode.
FR67: Episort can write local diagnostic logs outside the media workspace.
FR68: Episort can verify required local AI runtime prerequisites before allowing AI-dependent workflows.
FR69: Episort can block startup or organization workflows when required GPU, VRAM, or local AI prerequisites are not met.
FR70: Episort can use exactly one bundled local AI model for V1.
FR71: Episort can prevent users from selecting, downloading, or managing external AI models in V1.
FR72: Episort can offer retry or continue options when an approved file operation fails because a file is locked or temporarily unavailable.
FR73: Episort can reuse existing destination series, movie, and season folders inside the workspace when they match the approved plan.
FR74: Episort can merge detected groups that resolve to the same final TVDB-backed destination identity.
FR75: Episort can apply deterministic title truncation when generated Windows paths would exceed supported path limits.
FR76: Episort can detect an interrupted execution on next launch and show the user the relevant diagnostic log location.
FR77: Episort can retry transient TVDB failures before surfacing a blocking error.

### NonFunctional Requirements

NFR1: Local inventory scanning for a 2,000-file workflow should feel near-instant to the user; the target is to complete local file inventory quickly enough that the user does not perceive scanning as a blocking wait before metadata or AI work begins.
NFR2: The review experience for a 2,000-file plan must remain visibly fluid, with no noticeable UI freezes during navigation, selection, expansion, filtering, or correction.
NFR3: Long-running TVDB lookup, local AI analysis, planning, and execution steps may take longer than local inventory scanning, but they must provide clear progress feedback and must not block the UI thread.
NFR4: Long-running operations must support progress reporting granular enough for the user to distinguish active work from a stalled workflow.
NFR5: Episort must never modify files during scan, grouping, metadata lookup, AI analysis, matching, or planning.
NFR6: Episort must prevent filesystem operations outside the configured workspace.
NFR7: Episort must not automatically delete files in V1.
NFR8: TVDB credentials must not be stored in source control, logs, media folders, or exported operation plans.
NFR9: TVDB credential storage must support a project API key and, if required by the selected TVDB access model, a user subscriber PIN. Storage may use OS user configuration initially, but the implementation should prefer Windows Credential Manager or equivalent secure OS-backed storage when practical.
NFR10: A failed operation on one file must not be reported as a successful full execution.
NFR11: Execution results must preserve enough per-file status detail for the user to identify moved, renamed, ignored, untouched, failed, and skipped files.
NFR12: TVDB unavailability, invalid authentication, or expired authentication must block TVDB-dependent organization and show a clear recoverable error.
NFR13: Local AI runtime failure must block AI-dependent matching rather than silently producing low-trust plans.
NFR14: TVDB API integration must use the current v4 authentication model: login with project API key, optional subscriber PIN when required, and Bearer token for subsequent calls.
NFR15: TVDB tokens must be refreshed or reacquired when expired.
NFR16: Local AI integration must run without cloud AI calls.
NFR17: Windows notifications and taskbar progress should be used for long-running workflows where supported without making them required for core correctness.
NFR18: V1 must use a single bundled local AI model with no user-facing model selection, external model download, or manual model management.
NFR19: V1 local AI runtime requirements should target RTX-class GPU hardware and approximately 12 GB VRAM unless technical research proves a lower requirement can meet quality and responsiveness goals.
NFR20: The application must support dark mode in V1.
NFR21: The application must keep core review workflows readable and usable for large batches without relying on accessibility features beyond standard desktop usability.
NFR22: Error messages for blocked workflows must explain the blocking condition and the user action needed to recover.
NFR23: Episort must write diagnostic logs outside the media workspace.
NFR24: Logs must support troubleshooting of scan, TVDB authentication, TVDB lookup, AI analysis, planning, validation, execution failures, and interrupted execution recovery.
NFR25: Logs must avoid recording TVDB credentials, generated tokens, private API secrets, or unnecessary private media metadata.

### Additional Requirements

- First implementation story must initialize the Gradle Java 21 application with JavaFX and JUnit 5 using the selected Gradle Java Application + OpenJFX plugin starter.
- Use package-by-responsibility under `com.episort`: `ui`, `workflow`, `config`, `filesystem`, `scanner`, `matching`, `planning`, `tvdb`, `ai`, `persistence`, and `logging`.
- Keep JavaFX code out of domain, planning, filesystem, persistence, TVDB, and AI ports.
- Use ports-and-adapters inside a single JavaFX desktop process: UI calls workflow services; workflow coordinates scanner, TVDB, AI, planning, persistence, logging, and filesystem ports.
- Keep scan, grouping, metadata lookup, AI analysis, matching, planning, validation, and execution as distinct workflow phases.
- Use JavaFX MVVM-style screens for settings, input selection/scan progress, group review and pattern validation, operation plan review and exact-plan validation, execution progress, and post-execution recap.
- Run scan, TVDB lookup, local AI analysis, planning, and execution on background tasks with structured `WorkflowProgress` snapshots.
- Use JavaFX virtualized controls such as `TableView`, `TreeTableView`, or `ListView` for large-batch review.
- Store settings, logs, SQLite session state, and credential references in the OS user profile, outside the repository and media workspace.
- Use a `CredentialStore` abstraction; Windows 11 implementation should target Windows Credential Manager through a JNA-based adapter.
- Use SQLite for organization session state, validation state, operation plans, execution outcomes, and interrupted execution recovery.
- Use lowercase `snake_case` for SQLite tables, columns, indexes, and constraints.
- Use Java 21 `HttpClient` for TVDB calls and Jackson for JSON mapping.
- Localize TVDB retry and token refresh around `TvdbClient`; add broader resilience libraries only if simple retry logic starts spreading.
- External DTOs must stay provider-prefixed and inside adapter boundaries, for example `TvdbSeriesSearchResponse` and `AiPatternSuggestion`.
- Use typed application errors with `code`, `severity`, `message`, `recoverable`, and optional `details`.
- Every source path, destination path, folder creation, rename, and move must pass workspace boundary validation after normalization/resolution.
- Handle symlinks, relative path segments, path traversal, destination conflicts, Windows path-length behavior, and title sanitization before execution.
- Use redacted local diagnostic logs; never log TVDB API keys, subscriber PINs, bearer tokens, raw credentials, stack traces shown to users, or unnecessary private media metadata.
- `FilesystemExecutor` must accept only an approved immutable `OperationPlan`.
- Domain and planning logic must be testable without JavaFX, TVDB network calls, or real media folders.
- Tests must mirror production package structure under `src/test/java` and end with `Test`.
- Filesystem tests must use temporary directories only.
- Packaging starts with Gradle application distributions; V1 packaging should use JDK `jpackage` after the JavaFX/module/dependency layout is stable.
- CI can be added after Gradle initialization and should run compile, unit tests, formatting/checkstyle, and build artifact smoke checks.
- Deferred implementation research remains for TVDB v4 endpoint mapping, local AI runtime/model selection, Windows path-length/title truncation rules, and SQLite migration mechanism.

### UX Design Requirements

No UX Design document was found in `_bmad-output/planning-artifacts`, so no first-class UX-DR items were extracted in this step.

### FR Coverage Map

FR1: Epic 1 - Workspace boundary configuration.
FR2: Epic 1 - TVDB access configuration.
FR3: Epic 1 - TVDB access testing.
FR4: Epic 1 - Blocking workflows when prerequisites are invalid.
FR5: Epic 1 - Settings persistence outside media workspace.
FR6: Epic 1 - Input folder selection inside workspace.
FR7: Epic 1 - Rejecting input folders outside workspace.
FR8: Epic 2 - Supported video scanning.
FR9: Epic 2 - Sidecar identification.
FR10: Epic 2 - Unsupported file identification.
FR11: Epic 5 - Original extension preservation in destination names.
FR12: Epic 2 - Mixed movies and TV series in one input scope.
FR13: Epic 2 - Pre-matching grouping by series, movie, ignored, or unsupported status.
FR14: Epic 7 - Local AI-assisted pattern detection and grouping/matching candidates.
FR15: Epic 3 - TVDB series candidate search.
FR16: Epic 3 - TVDB movie candidate search.
FR17: Epic 3 - User correction of TVDB series association.
FR18: Epic 3 - User correction of TVDB movie association.
FR19: Epic 3 - TVDB series metadata retrieval.
FR20: Epic 3 - TVDB movie metadata retrieval.
FR21: Epic 3 - Best available TVDB fallback title.
FR22: Epic 3 - Homonymous media distinction through TVDB identity.
FR23: Epic 3 - TVDB-backed episode match proposals.
FR24: Epic 3 - TVDB-backed movie match proposals.
FR25: Epic 3 - Aired, DVD, and absolute order support.
FR26: Epic 3 - User review and change of episode order.
FR27: Epic 3 - Specials and OVA identification where represented in TVDB.
FR28: Epic 2 - Ignored or unsupported marking before planning.
FR29: Epic 4 - Duplicate candidate identification.
FR30: Epic 4 - Manual correction of assignments.
FR31: Epic 3 - One supported video maps to at most one episode or movie.
FR32: Epic 5 - Series folder path generation.
FR33: Epic 5 - `Season XX` folder path generation.
FR34: Epic 5 - Series episode filename generation.
FR35: Epic 5 - Specials path generation.
FR36: Epic 5 - Movie filename generation.
FR37: Epic 5 - Complete workspace-bounded operation plan generation.
FR38: Epic 5 - Destination path conflict detection.
FR39: Epic 5 - Non-executable exclusions for ignored, unsupported, duplicate-excluded, and unassigned files.
FR40: Epic 4 - Source and destination path review before execution.
FR41: Epic 4 - Review of groups, ignored files, unsupported files, ambiguities, and conflicts.
FR42: Epic 4 - Detected pattern validation.
FR43: Epic 4 - Exact source-to-destination plan validation.
FR44: Epic 4 - Execution blocked until validations are complete.
FR45: Epic 4 - Validation state separate from confidence.
FR46: Epic 4 - Confidence and uncertainty visibility.
FR47: Epic 4 - Ambiguous, conflicting, duplicate, unsupported, ignored, and weak-match visibility.
FR48: Epic 4 - Corrections without workflow restart.
FR49: Epic 4 - Session-scoped user corrections.
FR50: Epic 4 - No silent propagation of corrections to neighboring files.
FR51: Epic 7 - Contextual AI assistance requests.
FR52: Epic 7 - Selected-item context passed to local AI.
FR53: Epic 7 - AI explanation of proposed grouping or match.
FR54: Epic 7 - AI correction suggestions for ambiguous or weak matches.
FR55: Epic 7 - AI cannot authorize or execute filesystem operations.
FR56: Epic 6 - Approved operation plan execution.
FR57: Epic 6 - Destination folder creation within workspace.
FR58: Epic 6 - Approved rename and move operations within workspace.
FR59: Epic 6 - No automatic deletion.
FR60: Epic 6 - Untouched files for ignored, unsupported, excluded, unassigned, or unapproved items.
FR61: Epic 6 - Per-file failure reporting without false full-success.
FR62: Epic 6 - Post-execution recap.
FR63: Epic 1 - Progress for scan, metadata lookup, planning, and execution.
FR64: Epic 1 - Completion and blocking-error notifications.
FR65: Epic 1 - Taskbar progress where supported.
FR66: Epic 1 - Dark mode.
FR67: Epic 1 - Local diagnostic logs outside media workspace.
FR68: Epic 7 - Local AI runtime prerequisite verification.
FR69: Epic 7 - Blocking AI-dependent workflows when GPU, VRAM, or AI prerequisites fail.
FR70: Epic 7 - One bundled local AI model for V1.
FR71: Epic 7 - No user-facing external AI model management.
FR72: Epic 6 - Retry or continue options for locked or temporarily unavailable files.
FR73: Epic 5 - Reuse existing destination folders matching the approved plan.
FR74: Epic 3 - Merge groups resolving to the same TVDB-backed destination identity.
FR75: Epic 5 - Deterministic title truncation for Windows path limits.
FR76: Epic 6 - Interrupted execution detection and diagnostic log location.
FR77: Epic 3 - Transient TVDB retry before blocking error.

## Epic List

### Epic 1: Configuration sûre et workspace borné
Users can launch Episort, configure the workspace and TVDB access, test prerequisites, and be blocked cleanly when the app cannot organize safely.
**FRs covered:** FR1, FR2, FR3, FR4, FR5, FR6, FR7, FR63, FR64, FR65, FR66, FR67

### Epic 2: Inventaire média non destructif
Users can select a valid folder, scan up to 2,000 files, and distinguish supported videos, sidecars, unsupported files, and mixed content without filesystem mutation.
**FRs covered:** FR8, FR9, FR10, FR12, FR13, FR28

### Epic 3: Résolution TVDB et matching média
Users can resolve series and movies through TVDB, choose correct candidates, retrieve metadata, manage episode orders, specials, homonyms, and fallback titles.
**FRs covered:** FR15, FR16, FR17, FR18, FR19, FR20, FR21, FR22, FR23, FR24, FR25, FR26, FR27, FR31, FR74, FR77

### Epic 4: Review, corrections et validation du pattern
Users can inspect groups, ambiguities, duplicates, weak matches, and conflicts, correct assignments, and validate the detected pattern before final planning.
**FRs covered:** FR29, FR30, FR40, FR41, FR42, FR43, FR44, FR45, FR46, FR47, FR48, FR49, FR50

### Epic 5: Plan d'organisation Plex-compatible
Users receive a complete readable source-to-destination plan that is workspace-bounded, Plex-compatible, conflict-checked, extension-preserving, existing-folder-aware, and Windows-path-safe.
**FRs covered:** FR11, FR32, FR33, FR34, FR35, FR36, FR37, FR38, FR39, FR73, FR75

### Epic 6: Exécution approuvée, récupération et recap
Users can execute only a validated plan, create folders, move or rename approved files, handle per-file failures, recover after interruption, and see a clear recap.
**FRs covered:** FR56, FR57, FR58, FR59, FR60, FR61, FR62, FR72, FR76

### Epic 7: Assistance AI locale contrôlée
Users can use local AI for pattern detection, proposal explanations, and ambiguity help, with runtime prerequisite checks and no AI authority over execution.
**FRs covered:** FR14, FR51, FR52, FR53, FR54, FR55, FR68, FR69, FR70, FR71

## Epic 1: Configuration sure et workspace borne

Users can launch Episort, configure the workspace and TVDB access, test prerequisites, and be blocked cleanly when the app cannot organize safely.

### Story 1.1: Set Up Initial Project from Starter Template

As a media library user,
I want Episort to launch as a Windows desktop app,
So that I can start configuration from a stable application shell.

**Acceptance Criteria:**

**Given** the project is checked out
**When** the developer initializes the selected Gradle Java Application + OpenJFX starter
**Then** the project uses Java 21, JavaFX, Gradle, and JUnit 5
**And** `./gradlew run` launches a JavaFX application shell
**And** `./gradlew test` runs JUnit 5 tests successfully
**And** package boundaries exist for `ui`, `workflow`, `config`, `filesystem`, `scanner`, `matching`, `planning`, `tvdb`, `ai`, `persistence`, and `logging`

### Story 1.2: Workspace Configuration and Persistence

As a media library user,
I want to select and persist the workspace directory,
So that Episort only operates inside the folder I explicitly allow.

**Acceptance Criteria:**

**Given** no workspace is configured
**When** the user opens Settings and selects a workspace directory
**Then** Episort persists the setting outside the media workspace
**And** the selected workspace is shown after app restart
**And** invalid or inaccessible workspace paths show a recoverable blocking error

### Story 1.3: TVDB Credential Configuration and Test

As a media library user,
I want to enter and test TVDB access,
So that I know metadata-backed organization can work before scanning.

**Acceptance Criteria:**

**Given** the user opens Settings
**When** they enter TVDB API configuration and run a connection test
**Then** Episort reports success or a clear recoverable failure
**And** credentials are not written to source control, logs, media folders, or exported plans
**And** organization workflows remain blocked when TVDB configuration is missing or invalid

### Story 1.4: Workspace-Bounded Input Folder Selection

As a media library user,
I want to choose an input folder only inside my configured workspace,
So that accidental operations outside the allowed boundary are impossible.

**Acceptance Criteria:**

**Given** a valid workspace is configured
**When** the user selects an input folder inside that workspace
**Then** Episort accepts the folder for a future organization workflow
**And** when the selected folder resolves outside the workspace, Episort rejects it
**And** relative segments, normalized paths, and symlink-like boundary escapes are handled before acceptance

### Story 1.5: Desktop Feedback, Theme, and Diagnostic Logging Foundation

As a media library user,
I want clear app feedback, dark mode, and local diagnostic logs,
So that blocked workflows and long-running operations are understandable.

**Acceptance Criteria:**

**Given** Episort is running
**When** a workflow phase reports progress or a blocking error
**Then** the UI can show phase, progress, and action-oriented error text
**And** dark mode can be enabled in the application shell
**And** diagnostic logs are written outside the media workspace
**And** log output redacts credentials, tokens, and secrets
**And** Windows notification/taskbar progress adapters exist where supported, without being required for core correctness

## Epic 2: Inventaire media non destructif

Users can select a valid folder, scan up to 2,000 files, and distinguish supported videos, sidecars, unsupported files, and mixed content without filesystem mutation.

### Story 2.1: Supported Media File Detection

As a media library user,
I want Episort to detect supported video files in my selected folder,
So that only valid organization candidates enter the workflow.

**Acceptance Criteria:**

**Given** a selected input folder inside the configured workspace
**When** Episort scans the folder
**Then** `.avi`, `.mp4`, and `.mkv` files are identified as supported video candidates
**And** each supported item records its source path, filename, extension, and parent folder
**And** scanning does not create, rename, move, or delete any filesystem item

### Story 2.2: Sidecar and Unsupported File Inventory

As a media library user,
I want sidecar and unsupported files to be identified without being treated as errors,
So that non-video files remain untouched and traceable.

**Acceptance Criteria:**

**Given** the selected folder contains subtitles, `.nfo` files, images, unsupported videos, or disk images
**When** Episort scans the folder
**Then** sidecar files are marked as sidecar inventory items
**And** unsupported files are marked as unsupported inventory items
**And** sidecar and unsupported files are excluded from executable operation candidates
**And** all ignored inventory items remain visible or traceable

### Story 2.3: Mixed Media Group Seed Classification

As a media library user,
I want Episort to seed likely groups for mixed folders,
So that files from multiple series, movies, and ignored categories are not assumed to belong together.

**Acceptance Criteria:**

**Given** the selected folder contains files from multiple shows, movies, and ignored categories
**When** inventory classification runs
**Then** supported media files are assigned initial likely series, movie, or unknown group seeds
**And** ignored and unsupported items are grouped separately
**And** the grouping model permits multiple series and movies in the same selected input scope
**And** no TVDB identity is treated as final during this inventory step

### Story 2.4: Large Inventory Progress and Result Summary

As a media library user,
I want scanning progress and an inventory summary for large folders,
So that I can understand what Episort found before metadata matching begins.

**Acceptance Criteria:**

**Given** a selected folder with up to 2,000 supported media files
**When** Episort scans and classifies inventory
**Then** progress is reported without blocking the UI thread
**And** the result summary includes counts for supported videos, sidecars, unsupported files, likely series groups, likely movie groups, and unknown items
**And** scan completion never implies pattern validation or operation-plan approval
**And** no filesystem mutation occurs during progress reporting or summary generation

## Epic 3: Resolution TVDB et matching media

Users can resolve series and movies through TVDB, choose correct candidates, retrieve metadata, manage episode orders, specials, homonyms, and fallback titles.

### Story 3.1: TVDB Series and Movie Candidate Search

As a media library user,
I want Episort to search TVDB for likely series and movie candidates,
So that I can choose the correct official metadata identity.

**Acceptance Criteria:**

**Given** inventory groups or movie seeds exist
**When** Episort searches TVDB for candidates
**Then** candidate TV series and movie results are returned separately
**And** homonymous results include enough TVDB-backed identity information to distinguish them
**And** transient TVDB failures are retried before a blocking error is shown
**And** TVDB lookup does not mutate the filesystem

### Story 3.2: User Selection of TVDB Series and Movie Identity

As a media library user,
I want to choose or correct the TVDB identity for a group or movie,
So that matching uses the right official metadata source.

**Acceptance Criteria:**

**Given** TVDB candidates are available for a detected group or movie
**When** the user selects a series or movie candidate
**Then** Episort stores the selected TVDB identity for the current session
**And** the user can change the selected identity before pattern validation
**And** groups resolving to the same final TVDB-backed identity can be merged
**And** selecting an identity does not validate the pattern or authorize execution

### Story 3.3: Series Metadata Retrieval and Episode Orders

As a media library user,
I want Episort to retrieve series seasons, episodes, titles, and supported order modes,
So that episode matching can follow the correct TVDB structure.

**Acceptance Criteria:**

**Given** a TVDB series identity is selected
**When** Episort retrieves series metadata
**Then** official English series name, seasons, episode numbers, episode titles, and supported order modes are available for matching
**And** aired order, DVD order, and absolute order are represented when TVDB provides them
**And** the user can review and change the selected episode order
**And** the best available TVDB fallback title is used when English metadata is unavailable

### Story 3.4: Movie Metadata Retrieval

As a media library user,
I want Episort to retrieve TVDB-backed movie names and release years,
So that movie files can be organized with official naming.

**Acceptance Criteria:**

**Given** a TVDB movie identity is selected
**When** Episort retrieves movie metadata
**Then** the English movie name and release year are available for planning
**And** the best available TVDB fallback title is used when English metadata is unavailable
**And** homonymous movies remain distinguishable using TVDB-backed identity information

### Story 3.5: Episode and Movie Match Proposals

As a media library user,
I want Episort to propose episode and movie matches from TVDB metadata,
So that I can review likely matches before approving a pattern.

**Acceptance Criteria:**

**Given** inventory items and TVDB metadata are available
**When** matching runs
**Then** series files receive at most one proposed TVDB episode match
**And** movie files receive at most one proposed TVDB movie match
**And** specials or OVA files represented in TVDB can be proposed as specials
**And** unmatched files remain visible as unknown, ignored, or unsupported candidates
**And** no supported video file can be assigned to more than one episode or movie in V1

### Story 3.6: TVDB Authentication and Token Lifecycle

As a media library user,
I want TVDB authentication to work reliably in the background,
So that metadata lookups fail clearly instead of silently corrupting matches.

**Acceptance Criteria:**

**Given** valid TVDB credentials are configured
**When** TVDB-dependent lookup starts
**Then** Episort authenticates using TVDB v4 login with project API key and optional subscriber PIN where required
**And** subsequent calls use a Bearer token
**And** expired tokens are refreshed or reacquired
**And** invalid authentication blocks TVDB-dependent organization with a recoverable error
**And** API keys, PINs, and bearer tokens are never logged

## Epic 4: Review, corrections et validation du pattern

Users can inspect groups, ambiguities, duplicates, weak matches, and conflicts, correct assignments, and validate the detected pattern before final planning.

### Story 4.1: Review Screen for Groups and Match States

As a media library user,
I want to review detected groups, matches, ignored files, unsupported files, and unknown items,
So that I can understand the proposed organization pattern before approving it.

**Acceptance Criteria:**

**Given** inventory and match proposals exist
**When** the user opens the review workflow
**Then** Episort shows detected series groups, movie groups, ignored files, unsupported files, ambiguous files, conflicts, and unknown items
**And** each row exposes its current source path, proposed identity or status, and match state
**And** the review UI remains usable for up to 2,000 supported media files
**And** opening review does not validate the pattern or authorize execution

### Story 4.2: Confidence, Ambiguity, Conflict, and Duplicate Visibility

As a media library user,
I want uncertain and risky matches to be clearly surfaced,
So that I can focus review effort where mistakes are most likely.

**Acceptance Criteria:**

**Given** match proposals include confidence, ambiguity, duplicate, conflict, unsupported, ignored, or weak-match states
**When** the review list is displayed
**Then** each state is visible and filterable or otherwise traceable
**And** duplicate candidates mapping to the same target episode or movie are identified
**And** confidence is shown separately from validation status
**And** high confidence never automatically validates a pattern or enables execution

### Story 4.3: Manual Match and Status Correction

As a media library user,
I want to correct series, movie, season, episode, order, duplicate, ignored, and unsupported assignments,
So that the current session reflects my decisions without restarting the workflow.

**Acceptance Criteria:**

**Given** a user is reviewing a file, group, or match proposal
**When** they manually change the assigned series, movie, season, episode, order, duplicate handling, ignored status, or unsupported status
**Then** Episort stores the correction in the current organization session
**And** the correction updates affected review rows and validation readiness
**And** one manual correction does not silently propagate to neighboring files
**And** corrected items remain bounded by the one-file-to-one-episode-or-movie rule

### Story 4.4: Pattern Validation Gate

As a media library user,
I want to explicitly validate the detected organization pattern,
So that final operation planning only starts from a reviewed grouping and matching model.

**Acceptance Criteria:**

**Given** review items have been inspected or corrected
**When** the user validates the detected pattern
**Then** Episort records pattern validation status separately from confidence
**And** validation captures the current groups, assignments, ignored files, unsupported files, ambiguities, and conflicts
**And** unresolved blocking conflicts prevent pattern validation
**And** pattern validation still does not authorize file execution

### Story 4.5: Exact Plan Validation State Preparation

As a media library user,
I want the app to keep exact operation-plan validation separate from pattern validation,
So that I must approve final source-to-destination paths before execution.

**Acceptance Criteria:**

**Given** a pattern has or has not been validated
**When** the workflow evaluates execution eligibility
**Then** execution remains blocked until both pattern validation and exact plan validation are true
**And** plan validation cannot be set before an operation plan exists
**And** validation state survives normal navigation within the current session
**And** the UI can explain which validation gate is still blocking execution

## Epic 5: Plan d'organisation Plex-compatible

Users receive a complete readable source-to-destination plan that is workspace-bounded, Plex-compatible, conflict-checked, extension-preserving, existing-folder-aware, and Windows-path-safe.

### Story 5.1: Series and Season Destination Naming

As a media library user,
I want series episode destination paths to follow the approved English TVDB naming layout,
So that my Plex library is organized predictably.

**Acceptance Criteria:**

**Given** a validated series episode match exists
**When** Episort generates a destination path
**Then** the series folder uses the official English series name or best TVDB fallback title
**And** regular episodes use `Season XX` folders
**And** filenames use `Series Name in English - SXXEXX - Episode Title in English.original-extension`
**And** the original file extension is preserved exactly

### Story 5.2: Specials and Movie Destination Naming

As a media library user,
I want specials and movies to receive TVDB-backed destination names,
So that non-standard media still lands in a Plex-compatible structure.

**Acceptance Criteria:**

**Given** a validated match maps to a TVDB special, OVA, or movie
**When** Episort generates a destination path
**Then** specials use a `Specials` folder under the relevant series
**And** movie filenames use `English Movie Name (Release Year).original-extension`
**And** any containing movie folder uses the same English title and release year identity when required
**And** homonymous media remains distinguishable using TVDB-backed identity information

### Story 5.3: Workspace-Bounded Operation Plan Generation

As a media library user,
I want a complete source-to-destination operation plan inside my workspace,
So that I can inspect every planned move before anything changes.

**Acceptance Criteria:**

**Given** the detected pattern has been validated
**When** Episort generates the operation plan
**Then** each executable item has a source path and destination path inside the configured workspace
**And** every planned path passes workspace boundary validation after normalization/resolution
**And** ignored, unsupported, duplicate-excluded, and unassigned files are excluded from executable operations
**And** plan generation creates no folders and moves or renames no files

### Story 5.4: Destination Conflict and Existing Folder Handling

As a media library user,
I want destination conflicts and reusable folders to be detected before execution,
So that the final plan is clear and safe.

**Acceptance Criteria:**

**Given** an operation plan is generated
**When** destination paths are evaluated
**Then** duplicate destinations and existing-file conflicts are marked as blocking conflicts
**And** existing destination series, movie, and season folders inside the workspace are reused when they match the approved plan
**And** conflicts prevent exact plan validation until resolved or excluded
**And** conflict detection does not mutate the filesystem

### Story 5.5: Windows Path Safety and Exact Plan Review

As a media library user,
I want generated paths to be Windows-safe and reviewable before approval,
So that execution cannot fail from predictable path problems.

**Acceptance Criteria:**

**Given** destination paths are generated
**When** path safety validation runs
**Then** invalid filename characters are handled deterministically
**And** generated titles are truncated deterministically when Windows path limits would be exceeded
**And** the full plan review shows every executable source path and destination path
**And** the user can validate the exact operation plan only when no blocking conflicts remain
**And** exact plan validation still does not move, rename, delete, or create files

## Epic 6: Execution approuvee, recuperation et recap

Users can execute only a validated plan, create folders, move or rename approved files, handle per-file failures, recover after interruption, and see a clear recap.

### Story 6.1: Execution Eligibility and Approved Plan Locking

As a media library user,
I want execution to be possible only after both validation gates are complete,
So that files cannot be changed from an unapproved plan.

**Acceptance Criteria:**

**Given** an operation plan exists
**When** execution eligibility is evaluated
**Then** execution is blocked unless pattern validation and exact plan validation are both true
**And** the executable plan is treated as immutable once execution starts
**And** ignored, unsupported, duplicate-excluded, unassigned, and unapproved items are excluded
**And** the UI explains which gate or blocker prevents execution

### Story 6.2: Workspace-Bounded Folder Creation and File Moves

As a media library user,
I want approved folders created and approved files moved or renamed inside the workspace,
So that the validated plan becomes my organized library.

**Acceptance Criteria:**

**Given** an approved immutable operation plan exists
**When** the user starts execution
**Then** required destination folders are created inside the configured workspace
**And** approved files are renamed and moved only within the configured workspace
**And** every execution path is revalidated through workspace boundary checks before mutation
**And** no automatic deletion is performed

### Story 6.3: Per-File Execution Results and Failure Handling

As a media library user,
I want file operation failures to be tracked individually,
So that one locked or failed file does not make the whole run look successful.

**Acceptance Criteria:**

**Given** execution is running
**When** a file succeeds, fails, is skipped, or is untouched
**Then** Episort records a per-file outcome with status and recoverable error details where relevant
**And** a single file failure is not reported as full execution success
**And** locked or temporarily unavailable files offer retry or continue options
**And** ignored, unsupported, excluded, unassigned, and unapproved files remain untouched

### Story 6.4: Execution Progress and Interrupt Recovery

As a media library user,
I want execution progress and interruption recovery,
So that I can understand what happened if the app closes or stops mid-run.

**Acceptance Criteria:**

**Given** an approved execution is in progress
**When** file operations run
**Then** progress is reported with enough detail to distinguish active work from a stalled workflow
**And** execution writes diagnostic state outside the media workspace
**And** if execution is interrupted, Episort detects it on next launch
**And** the user is shown the relevant diagnostic log location and previous execution state

### Story 6.5: Post-Execution Recap

As a media library user,
I want a clear recap after execution,
So that I know exactly which files moved, failed, were skipped, or remained untouched.

**Acceptance Criteria:**

**Given** execution has ended
**When** the recap is shown
**Then** it lists per-file outcomes for moved, renamed, failed, skipped, ignored, unsupported, excluded, unassigned, and untouched items
**And** failures include recoverable next actions where possible
**And** the recap never exposes TVDB credentials, tokens, or secrets
**And** the recap can distinguish partial success from complete success

## Epic 7: Assistance AI locale controlee

Users can use local AI for pattern detection, proposal explanations, and ambiguity help, with runtime prerequisite checks and no AI authority over execution.

### Story 7.1: Local AI Runtime Prerequisite Check

As a media library user,
I want Episort to verify local AI prerequisites,
So that AI-dependent workflows do not silently produce unreliable results.

**Acceptance Criteria:**

**Given** Episort starts or an AI-dependent workflow is requested
**When** the local AI prerequisite check runs
**Then** Episort verifies required runtime availability and hardware capability signals
**And** missing GPU, VRAM, model, or runtime prerequisites block AI-dependent workflows with a recoverable explanation
**And** non-AI workflows remain available when AI is unavailable where technically possible
**And** prerequisite failures are logged without secrets or unnecessary private media metadata

### Story 7.2: Single Bundled Model Enforcement

As a media library user,
I want Episort to use one bundled local AI model,
So that I do not need to select, download, or manage models.

**Acceptance Criteria:**

**Given** local AI is enabled for V1
**When** the AI runtime initializes
**Then** Episort uses exactly one bundled model
**And** no UI exists for selecting, downloading, importing, or managing external AI models
**And** external model paths are not accepted as user configuration
**And** the model identity and runtime status can be diagnosed without exposing private media metadata

### Story 7.3: AI-Assisted Pattern Detection Suggestions

As a media library user,
I want local AI to suggest grouping or matching patterns,
So that ambiguous media folders are faster to review.

**Acceptance Criteria:**

**Given** media inventory exists
**When** AI-assisted pattern detection runs
**Then** the AI can propose grouping or matching candidates using local context
**And** AI output is marked as advisory and never treated as TVDB truth
**And** AI suggestions cannot validate a pattern, validate an operation plan, or authorize execution
**And** all AI-assisted suggestions remain reviewable and correctable by the user

### Story 7.4: Contextual AI Explanation and Correction Help

As a media library user,
I want AI help for a selected file, group, match, conflict, or ambiguity,
So that I can understand why a suggestion exists and decide what to correct.

**Acceptance Criteria:**

**Given** the user selects a file, group, match, conflict, or ambiguity
**When** they request AI assistance
**Then** Episort provides only the relevant selected-item context to the local AI assistant
**And** the assistant can explain why a grouping or match was proposed
**And** the assistant can suggest corrections for ambiguous or weak matches
**And** suggested corrections require normal user action and validation before affecting the plan
**And** the assistant cannot execute filesystem operations or change validation gates

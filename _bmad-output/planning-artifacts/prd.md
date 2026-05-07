---
stepsCompleted:
  - step-01-init
  - step-02-discovery
  - step-02b-vision
  - step-02c-executive-summary
  - step-03-success
  - step-04-journeys
  - step-05-domain
  - step-06-innovation
  - step-07-project-type
  - step-08-scoping
  - step-09-functional
  - step-10-nonfunctional
  - step-11-polish
releaseMode: "single-release"
inputDocuments:
  - "_bmad-output/brainstorming/brainstorming-session-2026-05-07-220201.md"
documentCounts:
  productBriefs: 0
  research: 0
  brainstorming: 1
  projectDocs: 0
  projectContext: 0
classification:
  projectType: "desktop_app"
  domain: "media library organization / Plex-oriented personal media tooling"
  complexity: "medium"
  projectContext: "greenfield"
workflowType: "prd"
---

# Product Requirements Document - episort

**Author:** Jonathan
**Date:** 2026-05-08

## Executive Summary

Episort is a Windows desktop application that helps users organize large mixed folders of TV series and movies for Plex or personal media libraries. The product reduces the time required to process high-volume media folders by analyzing local video files, grouping likely series or movies, resolving metadata through TVDB, and generating a user-validated organization plan before any filesystem changes occur.

The core user problem is not renaming individual files; it is the time and uncertainty involved in turning messy, mixed media folders into a clean, predictable library structure at scale. Episort addresses this by combining TVDB-backed metadata, local AI-assisted pattern detection, and explicit human validation so users can process more files at once without giving up control.

### What Makes This Special

Episort is built around proof before action. It does not autonomously rename, move, delete, or reorganize media files. Instead, it produces a readable source-to-destination plan, highlights uncertain matches, lets the user correct the plan, and executes only after the detected pattern and exact file operations are validated.

Its differentiator is speed with control. Compared with manual renaming, scripts, or generic file tools, Episort focuses on large-batch media organization where TVDB accuracy, Plex-compatible naming, visible confidence, and non-destructive workflow matter more than customization or automation for its own sake.

The local AI is a scoped assistant for detecting file patterns and resolving ambiguity; TVDB remains the metadata source of truth, and the user remains the authority for final approval.

## Project Classification

Episort is classified as a greenfield desktop application in the media library organization domain, focused on Plex-oriented personal media tooling. The complexity level is medium: the product does not operate in a regulated domain, but it has elevated requirements for filesystem safety, metadata correctness, ambiguous matching, local AI runtime behavior, and user trust.

## Success Criteria

### User Success

Episort succeeds when the user can organize a large mixed folder of movies and TV series substantially faster than manual sorting while retaining full control over every file operation. A representative successful session reduces roughly one hour of manual sorting work to about ten minutes of guided review and validation.

The user should feel confident before execution because the application shows a readable source-to-destination plan, clearly marks uncertain or conflicting matches, and prevents any move or rename until the relevant validation gates are complete.

### Business Success

V1 success is personal operational success: Episort works reliably on the user's own media library and becomes useful enough to replace manual sorting for large batches. The initial goal is not commercialization, public growth, or market adoption; the product is successful when it solves the user's real library organization workload.

### Technical Success

Episort must safely process large batches of up to 2,000 media files in a single workflow without modifying files during scan, grouping, matching, or planning. All filesystem operations must remain bounded to the configured workspace.

The system may produce incorrect match suggestions before validation if those errors are visible, correctable, and never executed without explicit approval. A file operation must not run unless the user has validated both the detected pattern and the exact source-to-destination operation plan.

### Measurable Outcomes

- A typical one-hour manual organization task can be completed in approximately ten minutes of Episort-assisted review.
- V1 can scan, group, plan, and review up to 2,000 supported media files in one workflow.
- Zero files are moved, renamed, deleted, or overwritten during scan, analysis, matching, or plan generation.
- Zero unvalidated file operations can execute.
- Ambiguous, conflicting, duplicate, unsupported, and ignored files are visible or traceable before execution.
- Post-execution results show what happened per file, including successes, failures, ignored files, and untouched files.

## Product Scope

### MVP - Minimum Viable Product

The MVP must support the end-to-end personal workflow: configure workspace and TVDB access, load a folder within the workspace, scan supported video files, group likely movies and series, resolve TVDB metadata, generate a Plex-compatible organization plan, allow user correction, require validation gates, execute approved operations, and show a completion recap.

MVP scope includes Windows desktop support, Java/JavaFX UI, TVDB-backed metadata, local AI-assisted pattern detection, large-batch handling up to 2,000 files, English output naming, original extension preservation, season folder planning, safe execution, and local logs.

### Growth Features (Post-MVP)

Post-MVP growth can improve review speed and matching quality through stronger filtering, better confidence explanations, richer contextual AI assistance, more advanced duplicate handling, improved media metadata extraction, and broader file type coverage. These features should not weaken the validation and safety model.

### Vision (Future)

The future version is a dependable personal media-library maintenance tool that can repeatedly process large messy folders with minimal manual detective work while preserving user control. It should make large-scale Plex-oriented organization feel routine instead of tedious.

## User Journeys

### Journey 1: First Launch and Configuration

Alex is a power user who maintains a large Plex library and has accumulated folders containing mixed movies, TV series, anime episodes, specials, and leftover downloads. Before trusting any organizer with the library, Alex wants to know where the application will operate and whether it can access TVDB correctly.

On first launch, Episort opens directly to Settings because the app cannot produce a safe TVDB-backed plan without a configured workspace and valid TVDB access. Alex selects the root workspace that contains the media folders Episort is allowed to inspect and modify. The app stores this setting outside source control and outside the media folder, in the OS user profile. Alex then enters the TVDB API configuration and tests it from the same screen.

The key success moment is not visual polish; it is bounded trust. Alex understands that Episort will only work inside the configured workspace, that TVDB access is verified before planning, and that no file operation can happen during setup. If the TVDB key is invalid or unavailable, the app blocks TVDB-backed organization and explains the issue before Alex invests time in a scan.

This journey reveals requirements for first-launch gating, workspace selection, TVDB credential storage, TVDB key testing, settings persistence, clear prerequisite errors, and hard workspace boundaries.

### Journey 2: Successful Large Mixed Folder Organization

Alex chooses a messy folder inside the configured workspace that contains a large batch of files across multiple shows and movies. The goal is to turn that folder into a Plex-compatible structure without spending hours manually identifying each episode.

Episort scans supported video files, ignores sidecars and unsupported files safely, groups likely series and movies, and uses TVDB as the metadata source of truth. The local AI assists by identifying naming patterns and proposing matches, but it does not become the authority. The app presents a before/after review view: original files on one side, proposed destination paths on the other, grouped by official English series or movie name and season folders where applicable.

Alex reviews the proposed plan instead of manually building it. High-confidence items are easy to scan, while uncertain items remain visible. The app preserves original extensions and generates target names using the required layout. Alex validates the detected pattern, then validates the exact file operation plan. Only after both validation gates are complete can execution begin.

The key success moment is when Alex sees a large, messy folder converted into a clear TVDB-backed plan in minutes, with every source path and destination path visible before any change occurs.

This journey reveals requirements for large-batch scanning up to 2,000 files, supported video filtering, sidecar ignore behavior, mixed movie and series grouping, TVDB metadata resolution, local AI pattern detection, synchronized before/after review, confidence visualization, editable matches, validation gates, and final execution approval.

### Journey 3: Resolving Ambiguities and Conflicts

During review, Alex finds several cases that cannot be trusted automatically: two files appear to map to the same episode, one anime special may belong in `Specials`, and a series name has multiple TVDB candidates. Alex needs to correct these without losing the structure of the whole plan.

Episort surfaces ambiguous items rather than hiding them. Conflicts, duplicates, weak matches, and alternative TVDB series candidates are clearly marked in the review UI. Alex opens the relevant item, compares evidence, checks TVDB alternatives when needed, and manually assigns the correct series, season, episode, order, or ignore status. If a duplicate exists, Episort does not delete either file; it lets Alex decide which file, if any, participates in the approved operation plan.

The local AI can assist in context. When Alex opens a chat or explanation from a specific row, folder, or conflict, the assistant receives the selected item context and explains why the match was proposed. Alex can challenge the pattern, but corrections apply within the current session and do not silently propagate across neighboring files.

The key success moment is when Alex can repair bad or uncertain matches without restarting the workflow, losing trust in the plan, or risking unintended file operations.

This journey reveals requirements for ambiguity states, duplicate detection, TVDB candidate comparison, manual reassignment, order selection, ignore states, contextual AI assistance, explanation-on-demand, non-propagating corrections, and validation state tracking independent from confidence.

### Journey Requirements Summary

These journeys define the core V1 capability areas:

- Settings and prerequisite management: first-launch settings gate, workspace selection, TVDB configuration, TVDB test, OS user profile storage, and clear blocking errors.
- Workspace safety: all scans, planning, folder creation, renaming, and moving must be constrained to the configured workspace.
- Media inventory: scan large folders, support common video files, preserve original extensions, ignore sidecars, and keep unsupported files untouched.
- Metadata matching: group mixed folders by likely movie or series, resolve TVDB metadata, support TVDB-backed English naming, and handle alternate episode orders.
- Review and correction: show before/after paths, expose confidence and ambiguity, allow manual correction, and keep validation state separate from confidence.
- AI assistance: use local AI for pattern detection, contextual explanation, and ambiguity support without allowing it to execute or authorize file operations.
- Safety gates: require validation of the detected pattern and the exact source-to-destination operation plan before execution.

## Domain-Specific Requirements

### Media Library Scope

Episort V1 must support both TV series and movies. The application must accept messy folders that may contain multiple series, multiple movies, anime, specials, duplicate candidates, sidecar files, and unsupported files in the same selected input scope.

Supported video files are candidates for analysis and planning. Sidecar files such as subtitles, `.nfo` files, and images must not be moved, renamed, deleted, or treated as errors in V1. Unsupported video or disk image formats must remain untouched and visible or traceable as ignored or unsupported items.

### TVDB Metadata Authority

TVDB is the source of truth for official media metadata. For series, Episort must use TVDB to resolve official English series names, seasons, episode numbers, episode titles, and supported order modes. For movies, Episort must use TVDB-backed movie metadata to resolve the English movie name and release year.

When local filename patterns conflict with TVDB metadata, TVDB should win by default, while still allowing the user to manually correct the plan during validation. If English metadata is unavailable, Episort should use the best available TVDB fallback title rather than blocking organization.

### Output Naming and Folder Layout

Series output must use the required structure:

```text
Series Name in English/
  Season XX/
    Series Name in English - SXXEXX - Episode Title in English.original-extension
```

Specials and OVA files that are represented in TVDB should be organized under a `Specials` folder for the relevant series. The original file extension must always be preserved.

Movie output must use:

```text
English Movie Name (Release Year).original-extension
```

If the final destination requires a containing movie folder, the folder naming must follow the same English title and release year identity. Homonymous series or movies must remain distinguishable using year or other TVDB-backed identity where needed.

### Episode and Movie Matching Constraints

Episort V1 must support aired order, DVD order, and absolute order for series where TVDB provides those orders. The app may recommend an order automatically, but the user must be able to review and override it.

Each supported video file maps to at most one TVDB episode or one TVDB movie in V1. Multi-episode files are out of scope for MVP. Duplicate candidates must never be deleted automatically; the user decides which file participates in the approved operation plan.

Anime-specific cases such as absolute ordering, specials, OVA, and KAI variants must be surfaced clearly. KAI or fan-edit variants that are not represented in TVDB should be ignored or marked unsupported rather than forced into incorrect metadata.

### Workspace and Filesystem Safety

All scans, analysis, planning, folder creation, renaming, and moving must be limited to the configured workspace. The selected input folder must be inside that workspace. Episort must not modify files during scan, grouping, matching, metadata lookup, or plan generation.

Folder creation and file operations occur only after the user validates both the detected pattern and the exact source-to-destination operation plan. No automatic deletion is allowed in V1.

### Domain Risks and Mitigations

Ambiguous names, homonymous titles, missing English metadata, duplicate files, weak filename patterns, mixed folders, and TVDB availability failures are expected domain risks. Episort mitigates these by showing uncertainty, offering TVDB candidate selection, allowing manual correction, keeping unsupported files untouched, and requiring explicit validation before execution.

Large-batch review fatigue is also a domain risk. Episort mitigates it through grouping, confidence visualization, filters, and a before/after plan that lets the user focus attention on ambiguous or risky items first.

## Innovation & Novel Patterns

### Detected Innovation Areas

Episort's distinctive product pattern is proof before action: the application is valuable because it creates a readable, correctable, TVDB-backed operation plan before any filesystem mutation is allowed. The product does not treat automation as permission; it treats automation as preparation for human validation.

The second distinctive pattern is local AI constrained to media pattern detection and contextual ambiguity support. The AI assists with grouping, pattern interpretation, explanation, and conflict resolution, but it does not replace TVDB as metadata authority and cannot authorize file operations.

The third distinctive pattern is validation state separated from confidence. High confidence can reduce review effort, but it cannot bypass validation. Low confidence highlights where attention is needed, but it does not imply the item is unusable if the user corrects it.

### Market Context & Competitive Landscape

Episort should be positioned as a personal trust-first media organization tool rather than a generic renamer or fully autonomous batch processor. Its V1 value comes from combining Plex-compatible naming, TVDB metadata, local AI assistance, and explicit operation-plan validation for large personal libraries.

### Validation Approach

The innovative patterns should be validated through real library workflows: large mixed folders, ambiguous anime ordering, homonymous titles, duplicate candidates, movie and series mixtures, and weak filename patterns. The key validation question is whether the user can reach a trusted executable plan faster than manual sorting while still understanding every risky decision.

### Risk Mitigation

The main risk is overtrust in generated plans. Episort mitigates this by requiring separate validation of detected patterns and exact source-to-destination operations, surfacing ambiguity, keeping unsupported files untouched, and blocking execution of unvalidated operations.

## Desktop App Specific Requirements

### Project-Type Overview

Episort V1 is a Windows-prioritized desktop application for organizing large personal media libraries. Windows 11 is the primary supported platform for V1. Windows 10 compatibility may be considered best-effort only if it does not add meaningful scope or reduce reliability on Windows 11.

The application is designed as a local desktop tool with direct filesystem access, OS user profile settings and logs, local AI runtime requirements, and TVDB-backed online metadata lookup. It is not a web app, SaaS product, mobile app, CLI-first tool, or offline-only organizer.

### Technical Architecture Considerations

Episort must separate UI, metadata lookup, local AI assistance, matching/planning logic, and filesystem operations. The desktop UI should be implemented with JavaFX, while file scanning, TVDB integration, matching, validation state, and operation planning should remain in testable non-UI modules.

The application must store user settings and logs in the OS user profile rather than inside the media workspace or application source tree. The configured workspace acts as a hard boundary for all scans and filesystem operations.

The application requires internet access for TVDB-backed organization. Local AI reasoning must run locally, but TVDB metadata lookup remains an online dependency. If TVDB access is unavailable or invalid, Episort must block organization workflows that require TVDB metadata.

### Platform Support

V1 prioritizes Windows 11. Platform-specific behavior, packaging, prerequisites, filesystem constraints, path handling, notifications, and taskbar progress should be designed and tested primarily against Windows 11.

Windows 10 support is not a primary V1 target. It may be allowed only as best-effort compatibility if the chosen Java, JavaFX, packaging, local AI runtime, notification, and taskbar progress approaches work without additional product scope.

### System Integration

Episort V1 must integrate with the local filesystem, OS user settings storage, OS user log storage, Windows notifications, and Windows taskbar progress where practical. Notifications should be used for long-running scan, planning, execution completion, and blocking error states. Taskbar progress should communicate progress during long scans, metadata lookup, planning, and execution.

The UI must support dark mode. Dark mode should be treated as part of the desktop experience, not as a post-MVP theme experiment.

Explorer context menu integration, shell extensions, background services, and automatic startup are out of scope for V1 unless explicitly reintroduced later.

### Update Strategy

V1 has no automatic update mechanism. Distribution and replacement are manual. Auto-update, update channels, patching, and in-app update checks are deferred to V2 or later.

### Offline Capabilities

Episort is not an offline organizer. The local AI requirement means AI reasoning does not use cloud AI services, but TVDB internet access is mandatory for metadata-backed organization. The app may open and show settings without TVDB connectivity, but it must not proceed with TVDB-dependent scan, match, or plan generation when TVDB is unavailable.

### Implementation Considerations

Desktop implementation must protect the user from accidental destructive behavior. Long-running tasks must keep the UI responsive and provide progress feedback. File operations must be planned separately from execution and must not run until validation is complete.

The architecture should support automated testing for workspace boundary checks, operation-plan validation, media matching rules, naming generation, and no-mutation planning behavior without requiring JavaFX UI tests for core logic.

## Project Scoping

### Strategy & Philosophy

**Approach:** Single-release V1 for personal operational success. The release should include the full end-to-end workflow needed to organize the user's real media library: configuration, scanning, grouping, TVDB lookup, AI-assisted matching, review, correction, validation, execution, and recap.

**MVP Philosophy:** This is a practical completeness MVP rather than a narrow prototype. V1 is successful only if it can replace manual sorting for large personal batches, so the scope must include the safety and review mechanisms required to trust execution.

**Resource Requirements:** Solo developer with AI assistance. The implementation should prioritize modularity, automated tests for non-UI logic, and conservative release boundaries to keep the V1 achievable.

### Complete Feature Set

**Core User Journeys Supported:**

- First launch and configuration.
- Successful organization of a large mixed movie and series folder.
- Resolution of ambiguities, conflicts, duplicates, and weak matches before execution.

**Must-Have Capabilities:**

- Windows 11-prioritized desktop application.
- Java 21, JavaFX UI, Gradle build, and JUnit 5 tests.
- Settings screen for configured workspace and TVDB access.
- TVDB credential test before TVDB-backed organization.
- Workspace-bounded folder loading, scanning, planning, folder creation, renaming, and moving.
- Support for both TV series and movies in V1.
- Large-batch workflow target up to 2,000 supported media files.
- Supported video inventory with sidecar and unsupported-file handling.
- Series grouping before episode-level matching.
- Movie detection and TVDB-backed movie metadata matching.
- TVDB-backed series metadata, seasons, episodes, English names, and title fallback.
- Aired order, DVD order, and absolute order support where TVDB provides them.
- English output naming with original file extension preserved.
- `Season XX` structure for series and `Specials` handling where represented in TVDB.
- Movie naming as `English Movie Name (Release Year).original-extension`.
- Local AI-assisted pattern detection and contextual ambiguity support.
- Review UI showing original source paths and proposed destination paths.
- Confidence and ambiguity visibility.
- Manual correction for series, movie, season, episode, order, duplicate, ignore, and unsupported states.
- Validation of detected pattern before final operation planning.
- Validation of the exact source-to-destination operation plan before execution.
- Execution blocked for unvalidated operations.
- No automatic deletion.
- Local logs in the OS user profile.
- Windows notifications for long-running completion and blocking errors.
- Windows taskbar progress for long-running scan, planning, and execution where practical.
- Dark mode support.
- Post-execution per-file recap.

**Nice-to-Have Capabilities:**

- Best-effort Windows 10 compatibility if it does not add meaningful scope.
- Richer confidence explanations beyond the minimum needed for review.
- Advanced filters for review efficiency beyond basic ambiguity and confidence triage.
- More polished contextual AI chat interactions after the core correction flow works.
- Additional media metadata extraction if filename, folder, and TVDB matching are insufficient.
- Expanded unsupported file reporting if it helps review without adding noise.

### Risk Mitigation Strategy

**Technical Risks:** The highest risks are embedded local AI feasibility, TVDB API behavior, large-batch performance, Windows packaging, and safe filesystem execution. Mitigation requires early technical research, isolated prototypes, and automated tests for planning and workspace-bound operation safety.

**Market Risks:** V1 is not intended to validate a commercial market. The core validation is whether the tool works reliably on the user's own library and reduces a representative one-hour manual task to approximately ten minutes of guided review.

**Resource Risks:** The scope is large for a solo developer. Mitigation requires implementing the domain model, scanning, planning, validation, and safety rules before polishing secondary UX. UI polish must not precede correctness, workspace safety, TVDB matching reliability, and no-unvalidated-execution guarantees.

## Functional Requirements

### Configuration and Workspace

- FR1: Users can configure a workspace directory that defines the only filesystem boundary Episort may scan, plan within, create folders in, rename files in, or move files within.
- FR2: Users can configure TVDB access required for metadata-backed organization.
- FR3: Users can test TVDB access before starting a TVDB-backed organization workflow.
- FR4: Episort can block organization workflows when required workspace or TVDB configuration is missing or invalid.
- FR5: Episort can persist user settings outside the media workspace.

### Media Loading and Inventory

- FR6: Users can select an input folder inside the configured workspace for organization.
- FR7: Episort can reject or block selected input folders outside the configured workspace.
- FR8: Episort can scan supported video files from the selected input folder.
- FR9: Episort can identify sidecar files that should remain untouched.
- FR10: Episort can identify unsupported files that should remain untouched and traceable.
- FR11: Episort can preserve each supported media file's original extension in the proposed destination name.
- FR12: Episort can handle organization workflows containing both movies and TV series in the same selected input scope.

### Grouping and Metadata Resolution

- FR13: Episort can group files by likely series, movie, or unsupported/ignored status before final matching.
- FR14: Episort can use local AI assistance to detect naming patterns and propose grouping or matching candidates.
- FR15: Episort can search TVDB for candidate TV series matches.
- FR16: Episort can search TVDB for candidate movie matches.
- FR17: Users can choose or correct the TVDB series associated with a detected series group.
- FR18: Users can choose or correct the TVDB movie associated with a detected movie file or movie group.
- FR19: Episort can retrieve TVDB metadata needed for official English series names, seasons, episode numbers, episode titles, and supported episode orders.
- FR20: Episort can retrieve TVDB metadata needed for English movie names and release years.
- FR21: Episort can use the best available TVDB fallback title when English metadata is unavailable.
- FR22: Episort can distinguish homonymous series or movies using TVDB-backed identity information.

### Series, Movie, and Episode Matching

- FR23: Episort can propose episode matches for TV series files using TVDB metadata.
- FR24: Episort can propose movie matches for movie files using TVDB metadata.
- FR25: Episort can support aired order, DVD order, and absolute order when TVDB provides those order modes.
- FR26: Users can review and change the selected episode order for a series.
- FR27: Episort can identify specials or OVA files represented in TVDB.
- FR28: Episort can mark files as ignored or unsupported when they should not participate in the operation plan.
- FR29: Episort can identify duplicate candidates that appear to map to the same target episode or movie.
- FR30: Users can manually correct series, movie, season, episode, order, duplicate, ignore, and unsupported assignments.
- FR31: Episort can prevent a single supported video file from being assigned to more than one TVDB episode or movie in V1.

### Naming and Operation Planning

- FR32: Episort can generate proposed series folder paths using the official English series name.
- FR33: Episort can generate proposed season folder paths using the `Season XX` convention.
- FR34: Episort can generate proposed series episode filenames using `Series Name in English - SXXEXX - Episode Title in English.original-extension`.
- FR35: Episort can generate proposed specials paths under a `Specials` folder when the file maps to a TVDB special.
- FR36: Episort can generate proposed movie filenames using `English Movie Name (Release Year).original-extension`.
- FR37: Episort can generate a complete source-to-destination operation plan within the configured workspace.
- FR38: Episort can detect destination path conflicts before execution.
- FR39: Episort can keep unsupported, ignored, duplicate-excluded, and unassigned files out of executable operations.
- FR40: Users can review every proposed source path and destination path before execution.

### Review, Validation, and Correction

- FR41: Users can review detected series groups, movie groups, ignored files, unsupported files, ambiguous files, and conflicts before execution.
- FR42: Users can validate the detected pattern before final operation-plan approval.
- FR43: Users can validate the exact source-to-destination operation plan before execution.
- FR44: Episort can block execution until required validation steps are complete.
- FR45: Episort can track validation state separately from confidence.
- FR46: Episort can expose match confidence or uncertainty to help users prioritize review.
- FR47: Episort can expose ambiguous, conflicting, duplicate, unsupported, ignored, and weak-match states during review.
- FR48: Users can correct proposed matches without restarting the workflow.
- FR49: Episort can apply user corrections within the current organization session.
- FR50: Episort can avoid silently propagating one manual correction to neighboring files.

### Local AI Assistance

- FR51: Users can request contextual AI assistance for a selected file, group, match, conflict, or ambiguity.
- FR52: Episort can provide the local AI assistant with relevant selected-item context.
- FR53: The local AI assistant can explain why a grouping or match was proposed.
- FR54: The local AI assistant can suggest corrections for ambiguous or weak matches.
- FR55: Episort can prevent local AI assistance from authorizing or executing filesystem operations.

### Execution and Results

- FR56: Users can execute an approved operation plan after all required validation is complete.
- FR57: Episort can create required destination folders within the configured workspace during execution.
- FR58: Episort can rename and move approved files within the configured workspace.
- FR59: Episort can prevent automatic deletion of files.
- FR60: Episort can leave files untouched when they are ignored, unsupported, excluded as duplicates, unassigned, or not included in the approved operation plan.
- FR61: Episort can continue or report per-file failures without treating a single failed operation as proof that the entire plan succeeded.
- FR62: Episort can show a post-execution recap listing per-file outcomes.

### Desktop Experience and Feedback

- FR63: Episort can show progress for long-running scan, metadata lookup, planning, and execution activities.
- FR64: Episort can notify users when long-running activities complete or when blocking errors require attention.
- FR65: Episort can show taskbar progress for long-running activities where supported.
- FR66: Users can use the application in dark mode.
- FR67: Episort can write local diagnostic logs outside the media workspace.

### Runtime, Recovery, and Edge Handling

- FR68: Episort can verify required local AI runtime prerequisites before allowing AI-dependent workflows.
- FR69: Episort can block startup or organization workflows when required GPU, VRAM, or local AI prerequisites are not met.
- FR70: Episort can use exactly one bundled local AI model for V1.
- FR71: Episort can prevent users from selecting, downloading, or managing external AI models in V1.
- FR72: Episort can offer retry or continue options when an approved file operation fails because a file is locked or temporarily unavailable.
- FR73: Episort can reuse existing destination series, movie, and season folders inside the workspace when they match the approved plan.
- FR74: Episort can merge detected groups that resolve to the same final TVDB-backed destination identity.
- FR75: Episort can apply deterministic title truncation when generated Windows paths would exceed supported path limits.
- FR76: Episort can detect an interrupted execution on next launch and show the user the relevant diagnostic log location.
- FR77: Episort can retry transient TVDB failures before surfacing a blocking error.

## Non-Functional Requirements

### Performance

- NFR1: Local inventory scanning for a 2,000-file workflow should feel near-instant to the user; the target is to complete local file inventory quickly enough that the user does not perceive scanning as a blocking wait before metadata or AI work begins.
- NFR2: The review experience for a 2,000-file plan must remain visibly fluid, with no noticeable UI freezes during navigation, selection, expansion, filtering, or correction.
- NFR3: Long-running TVDB lookup, local AI analysis, planning, and execution steps may take longer than local inventory scanning, but they must provide clear progress feedback and must not block the UI thread.
- NFR4: Long-running operations must support progress reporting granular enough for the user to distinguish active work from a stalled workflow.

### Filesystem Safety and Data Protection

- NFR5: Episort must never modify files during scan, grouping, metadata lookup, AI analysis, matching, or planning.
- NFR6: Episort must prevent filesystem operations outside the configured workspace.
- NFR7: Episort must not automatically delete files in V1.
- NFR8: TVDB credentials must not be stored in source control, logs, media folders, or exported operation plans.
- NFR9: TVDB credential storage must support a project API key and, if required by the selected TVDB access model, a user subscriber PIN. Storage may use OS user configuration initially, but the implementation should prefer Windows Credential Manager or equivalent secure OS-backed storage when practical.

### Reliability

- NFR10: A failed operation on one file must not be reported as a successful full execution.
- NFR11: Execution results must preserve enough per-file status detail for the user to identify moved, renamed, ignored, untouched, failed, and skipped files.
- NFR12: TVDB unavailability, invalid authentication, or expired authentication must block TVDB-dependent organization and show a clear recoverable error.
- NFR13: Local AI runtime failure must block AI-dependent matching rather than silently producing low-trust plans.

### Integration

- NFR14: TVDB API integration must use the current v4 authentication model: login with project API key, optional subscriber PIN when required, and Bearer token for subsequent calls.
- NFR15: TVDB tokens must be refreshed or reacquired when expired.
- NFR16: Local AI integration must run without cloud AI calls.
- NFR17: Windows notifications and taskbar progress should be used for long-running workflows where supported without making them required for core correctness.
- NFR18: V1 must use a single bundled local AI model with no user-facing model selection, external model download, or manual model management.
- NFR19: V1 local AI runtime requirements should target RTX-class GPU hardware and approximately 12 GB VRAM unless technical research proves a lower requirement can meet quality and responsiveness goals.

### Usability

- NFR20: The application must support dark mode in V1.
- NFR21: The application must keep core review workflows readable and usable for large batches without relying on accessibility features beyond standard desktop usability.
- NFR22: Error messages for blocked workflows must explain the blocking condition and the user action needed to recover.

### Observability

- NFR23: Episort must write diagnostic logs outside the media workspace.
- NFR24: Logs must support troubleshooting of scan, TVDB authentication, TVDB lookup, AI analysis, planning, validation, execution failures, and interrupted execution recovery.
- NFR25: Logs must avoid recording TVDB credentials, generated tokens, private API secrets, or unnecessary private media metadata.

## Open Research Questions

- ORQ1: Confirm the exact TVDB v4 endpoints and response fields required for series search, movie search, season data, episode data, alternate episode orders, images, summaries, release dates, and language fallback.
- ORQ2: Confirm the TVDB access model for this project, including whether V1 needs only a project API key or also user subscriber PIN support.
- ORQ3: Select a Java-compatible MKV metadata parsing approach and determine which metadata fields are reliable enough to influence matching.
- ORQ4: Select an embedded local AI runtime strategy compatible with Windows 11, Java packaging, RTX-class GPU acceleration, and the target bundle constraints.
- ORQ5: Validate candidate local AI model quality against real messy media folders before committing to the bundled model.
- ORQ6: Determine the most reliable method for detecting RTX-class GPU capability and available VRAM from a Java desktop application on Windows.
- ORQ7: Define the V1 supported video extension allowlist for Plex-oriented libraries and explicitly exclude unsupported disk image formats.
- ORQ8: Validate Windows packaging options for JavaFX plus bundled model assets without introducing an automatic update mechanism.

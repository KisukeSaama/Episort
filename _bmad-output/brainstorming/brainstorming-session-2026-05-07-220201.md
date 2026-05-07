---
stepsCompleted: [1, 2, 3, 4]
inputDocuments: []
session_topic: 'Episort: Java desktop app for organizing TV series files using TVDB and local AI pattern detection'
session_goals: 'Explore user workflows, local AI interaction, naming rules, safety validation, TVDB settings, Java portable architecture, MVP scope, and edge cases'
selected_approach: 'ai-recommended'
techniques_used: ['Question Storming', 'Six Thinking Hats', 'Morphological Analysis']
ideas_generated: [103]
context_file: ''
session_active: false
workflow_completed: true
---

# Brainstorming Session Results

**Facilitator:** Jonathan
**Date:** 2026-05-07 22:02:01

## Session Overview

**Topic:** Episort, a portable cross-platform Java desktop application that organizes TV series video files using TVDB as the reference database and an embedded/local AI assistant to detect and refine file naming patterns.

**Goals:** Generate ideas around the product workflow, local AI interaction model, English-only output naming, safety validation before file operations, TVDB settings, Java portable architecture, MVP boundaries, and difficult edge cases such as mixed series, specials, ambiguous names, duplicate episodes, and weak filename patterns.

### Context Guidance

No external context file was provided for this brainstorming session.

### Session Setup

The application should let the user select a working directory and enter a TVDB API key in settings. It should analyze files inside the configured directory, infer likely series and episode naming patterns, let the user challenge and refine those patterns through a local AI conversation, then produce a TVDB-backed organization plan. Generated folder and file names must use English:

```text
Official Series Name/
  Season 01/
    Official Series Name - S01E01 - English Episode Title.ext
```

The original extension must be preserved, and file operations should happen only after user validation.

## Technique Selection

**Approach:** AI-Recommended Techniques
**Analysis Context:** Episort combines a local-first desktop product, media-file safety, TVDB-backed metadata matching, and an embedded AI interaction model.

**Recommended Techniques:**

- **Question Storming:** Start by generating critical questions before proposing solutions, so hidden ambiguity and trust risks surface early.
- **Six Thinking Hats:** Explore facts, emotion, benefits, risks, creativity, and process without collapsing every angle into one feature list.
- **Morphological Analysis:** Convert the strongest ideas into concrete product combinations across matching, validation, UI, packaging, and local AI modes.

**AI Rationale:** This sequence is designed to protect against premature feature listing. Episort's success depends on user trust, safe file operations, clear validation gates, TVDB ambiguity handling, and feasible Java/local LLM packaging.

## Technique Execution Results

**Question Storming - Trust and File Operation Safety**

**[Trust #1]: TVDB Completeness Cross-Check**  
_Concept_: The local AI should first identify each likely series, then compare available local files against TVDB season and episode counts. It should use this as a grounding pass before proposing any renaming.  
_Novelty_: Matching is not only filename parsing; it is a structured comparison between local inventory and official TVDB series structure.

**[Trust #2]: AI Prefill, Human Editing**  
_Concept_: Episort should prefill the organization proposal with its best guess, then let the user freely modify incorrect matches or names. The UI is not a passive confirmation screen; it is the correction surface.  
_Novelty_: The AI accelerates the task but the user remains the final source of validation.

**[Trust #3]: Confidence Categories**  
_Concept_: Every recommendation should carry a confidence level, with uncertainty made visible rather than hidden. Low-confidence matches must stand out in the plan.  
_Novelty_: Trust is modeled as an interface element, not an internal score only.

**[Trust #4]: Human Review Always Required**  
_Concept_: Even high-confidence suggestions require user review before file operations. The threshold does not bypass the human; it only affects how strongly the app highlights risk.  
_Novelty_: Confidence changes review priority, not permission.

**[Trust #5]: Double Validation Gate**  
_Concept_: No move or rename can happen until the user validates both the detected pattern and the exact source-to-destination file operation plan. The program has no autonomous permission to modify files.  
_Novelty_: Safety is enforced as workflow architecture rather than a warning dialog.

**[Trust #6]: Plan-Only Review Mode**  
_Concept_: The planning UI itself acts as a no-operation preview mode where users can inspect the future folder/file layout without touching files.  
_Novelty_: Planning is treated as the core product experience, not a prelude hidden behind an execute button.

**[UX #7]: Synchronized Original and Target Lists**  
_Concept_: The review UI should show original files on the left and proposed renamed destinations on the right with synchronized scrolling. Target files should be grouped under collapsible English season folders.  
_Novelty_: This gives large libraries a readable before/after map while preserving season context.

**[Safety #8]: No Restore Flow in MVP**  
_Concept_: Episort will not implement operation rollback initially; responsibility rests on the double validation gate. If the user is not confident, they do not execute.  
_Novelty_: The product chooses prevention and explicit consent over complex post-operation recovery.

**[Diagnostics #9]: Local Operation Logs**  
_Concept_: The app should create logs for troubleshooting analysis, matching decisions, TVDB calls, and file operation planning/execution. These logs support later debugging and improvement.  
_Novelty_: Logs are part of developer feedback and user support, not only crash traces.

**[Ambiguity #10]: Duplicate Episode Resolution**  
_Concept_: When two files appear to represent the same episode, Episort should recommend which one to keep but show both full paths so the user can inspect manually. The user must be able to challenge the AI and assign each file to the correct episode/title.  
_Novelty_: Duplicate handling becomes an interactive disambiguation workflow rather than a hidden overwrite or skip rule.

**Question Storming - Local AI Detection and Dialogue**

**[AI #11]: Evidence Priority Ladder**  
_Concept_: The local AI should prioritize filename, parent folder names, and MKV metadata when available. Video duration is kept as a last-resort disambiguation signal when two files remain hard to separate.  
_Novelty_: Matching uses a defined evidence hierarchy instead of treating every signal equally.

**[AI #12]: Series-by-Series Processing**  
_Concept_: The AI should work one series at a time after grouping, rather than trying to solve every file in the working directory at once. This reduces confusion and limits hallucination risk.  
_Novelty_: Scope control becomes part of AI reliability.

**[AI #13]: Fixed Output Pattern With Editable Fields**  
_Concept_: The final output pattern is fixed and cannot be changed, but the user can correct the inferred metadata through text input or classified UI fields before renaming.  
_Novelty_: Users can correct meaning without breaking the canonical naming standard.

**[AI #14]: No Automatic Neighbor Propagation**  
_Concept_: If the user manually changes a file to S02E04, the app should not automatically shift or propagate corrections to adjacent files. If a conflict appears, the app surfaces it for manual resolution.  
_Novelty_: Episort avoids hidden batch edits caused by one correction.

**[AI #15]: Session-Only Correction Memory**  
_Concept_: The AI should remember corrections only inside the current session. It should not persist user corrections as reusable global rules.  
_Novelty_: Each organization session remains independent, avoiding stale or overfit matching rules.

**[AI #16]: Mixed Folder Input, Structured Output**  
_Concept_: The input folder can be messy and contain mixed files. Episort's job is to sort this into correct per-series output, but the AI still processes series groupings separately.  
_Novelty_: The app accepts chaos on input while maintaining controlled reasoning units.

**[AI #17]: Specials Folder and KAI Ignore Rule**  
_Concept_: Specials and OVA files should be placed under a `Specials` folder inside the series folder. KAI files should be ignored because they are not referenced on TVDB.  
_Novelty_: The app explicitly distinguishes TVDB-compatible extras from non-TVDB fan or recut variants.

**[AI #18]: Ambiguity Queue Ordered by Confidence**  
_Concept_: The UI should present all proposed results, with items ordered or highlighted by confidence level so the user fixes the riskiest matches first.  
_Novelty_: The user owns review order, but the app provides triage pressure.

**[AI #19]: Result First, Explanation on Demand**  
_Concept_: The app should show the proposed match by default. If the user asks why a choice was made, the AI explains the reasoning and evidence.  
_Novelty_: Reasoning is available without making the primary UI verbose.

**[AI #20]: TVDB as Source of Truth**  
_Concept_: When TVDB and the local pattern conflict, TVDB wins by default. The user can still manually resolve exceptions in the validation UI.  
_Novelty_: A stable external reference prevents the AI from overfitting to messy local filenames.

**Question Storming - User Workflow and Screens**

**[UX #21]: First-Launch Settings Gate**  
_Concept_: On first launch, Episort should open directly to Settings so the user can set the workspace and TVDB API key. On later launches, if both values are present, the app opens to the main menu.  
_Novelty_: The product prevents unusable scans by making core configuration the first experience.

**[UX #22]: Workspace-Bounded Load Flow**  
_Concept_: The main menu should provide a Load action that lets the user select only subfolders inside the configured workspace. All work remains bounded to that workspace.  
_Novelty_: Folder selection becomes a safety boundary, not only a convenience.

**[Settings #23]: TVDB Key Test**  
_Concept_: Settings must include a way to test the TVDB API key. If the key is invalid or unavailable, the app should not proceed with TVDB-backed organization.  
_Novelty_: External dependency health is validated before the user invests time in review.

**[UX #24]: Detected Series Review Step**  
_Concept_: Before episode-level validation, Episort should show detected series and their proposed season/episode structure. The user can confirm or correct the selected TVDB series.  
_Novelty_: The workflow validates series identity before drilling into episode mapping.

**[UX #25]: TVDB Series Match Popup**  
_Concept_: When validating a detected series, the user should get a popup with alternative TVDB matches including thumbnails, summaries, and release dates.  
_Novelty_: Series disambiguation uses human-recognizable TVDB context instead of names alone.

**[UX #26]: Tree-Based Synchronized Comparison**  
_Concept_: The review screen should show two synchronized tree/list views: original structure on the left, proposed English target structure on the right. Both views include parent folders, `Season XX` folders, `Specials`, and episode rows.  
_Novelty_: Large collections stay inspectable because folder hierarchy and before/after mapping are visible together.

**[UX #27]: Confidence Heat Coloring**  
_Concept_: Confidence should be represented with a red-to-green color scale, where red marks urgent or likely-problematic matches and green marks low-risk matches. No numeric score or icon is needed by default.  
_Novelty_: Confidence becomes visual triage rather than a technical metric.

**[UX #28]: Confidence and Ambiguity Filters**  
_Concept_: The UI should include filters for urgent red items, lower-priority items, green items, and ambiguity-only review.  
_Novelty_: The user can reduce review overload by focusing on the riskiest decisions first.

**[Safety #29]: Duplicate Files Are Never Deleted**  
_Concept_: If a duplicate is detected, the app can move/rename only the user-selected file. The other possible duplicate remains untouched in the source folder and is never deleted automatically.  
_Novelty_: Duplicate resolution is non-destructive by design.

**[AI #30]: Contextual Chat Bubble on Problems**  
_Concept_: Every conflict, ambiguous match, or problematic parent folder should expose a chat bubble button. Opening chat from a row or folder gives the local AI exact context about the selected file, group, or folder.  
_Novelty_: The AI conversation is anchored to a specific UI object, reducing ambiguity in user prompts.

**[Safety #31]: Bottom-Right Execute With Confirmation**  
_Concept_: The final execution button should sit in the bottom-right action area but never execute immediately. It opens a mandatory confirmation flow first.  
_Novelty_: The primary action remains discoverable while still guarded against accidental clicks.

**[Safety #32]: Final Summary Confirmation Modal**  
_Concept_: Before execution, the app must show a recap window with counts of files to move, files ignored, conflicts, ambiguous items, and any unvalidated items. The user must confirm again before changes run.  
_Novelty_: The final checkpoint is quantitative and explicit, not a generic "Are you sure?" prompt.

**Question Storming - Java Packaging and Embedded Local LLM**

**[Runtime #33]: Single Embedded LLM**  
_Concept_: Episort should ship with exactly one local LLM. Users should not choose, download, or plug in external models.  
_Novelty_: Model behavior becomes a controlled product dependency rather than a user-configurable variable.

**[Runtime #34]: Maximum Bundle Budget Around 10 GB**  
_Concept_: The portable application can be large if needed, with an upper bound around 10 GB, but smaller is preferred. Model quality and reduced hallucination matter more than minimizing bundle size.  
_Novelty_: Distribution size is treated as a tradeoff in favor of trustworthy local AI.

**[Runtime #35]: 12 GB VRAM Target**  
_Concept_: The embedded model should be selected with a 12 GB VRAM target in mind.  
_Novelty_: Hardware requirements are explicit and drive model selection rather than being discovered after implementation.

**[Runtime #36]: Online TVDB Requirement**  
_Concept_: Episort is not an offline organizer. TVDB API access is mandatory for scans and organization because TVDB remains the source of truth.  
_Novelty_: "Local AI" does not mean "offline metadata"; the cloud boundary is only around AI reasoning.

**[Runtime #37]: RTX Required**  
_Concept_: The application requires an RTX-class GPU to run the embedded LLM. CPU-only operation is not supported.  
_Novelty_: The product narrows compatibility to preserve AI usefulness and speed.

**[Runtime #38]: Startup Prerequisite Failure Popup**  
_Concept_: If the machine does not meet the required GPU/runtime prerequisites, the app should show a clear startup error explaining why it cannot launch.  
_Novelty_: Unsupported hardware fails early with explanation instead of degraded hidden behavior.

**[Runtime #39]: No Manual Model Management**  
_Concept_: The app should not expose model selection or external model download. The bundled model is the only supported AI runtime.  
_Novelty_: Simplifies support and keeps matching behavior predictable.

**[Config #40]: OS User Settings Storage**  
_Concept_: Settings such as workspace and TVDB API key should be stored in the OS user profile, not next to the executable.  
_Novelty_: Portable distribution does not mean portable personal configuration.

**[Diagnostics #41]: OS User Logs Storage**  
_Concept_: Logs should be written only to the OS user folder.  
_Novelty_: Runtime diagnostics stay separate from media folders and app binaries.

**[Runtime #42]: No Non-AI Mode**  
_Concept_: Episort should not support a manual TVDB-only mode. If local AI cannot run, the application does not start.  
_Novelty_: AI is a core runtime requirement, not an optional assistant.

**[Promise #43]: Zero-Cloud AI Promise**  
_Concept_: The most important product promise is that all AI reasoning runs locally. TVDB calls are allowed for metadata, but no cloud AI service should be involved.  
_Novelty_: The privacy boundary is crisp: cloud metadata yes, cloud intelligence no.

**Question Storming - TVDB Orders and Media Edge Cases**

**[TVDB #44]: Three Episode Orders Supported**  
_Concept_: Episort should support aired order, DVD order, and absolute order. The app should automatically recommend the most intelligent/default order, while allowing the user to override it manually in the matching interface.  
_Novelty_: TVDB order choice becomes an editable part of the validation workflow rather than a hidden setting.

**[TVDB #45]: Order Selection in Matching UI**  
_Concept_: The correspondence/matching interface should expose order selection when TVDB offers multiple valid episode orders.  
_Novelty_: Order is reviewed in the same place where mismatches are visible.

**[TVDB #46]: One Episode Equals One File**  
_Concept_: Episort does not support multi-episode files in the MVP. Each video file maps to exactly one TVDB episode.  
_Novelty_: This avoids ambiguous output naming and keeps the plan aligned with TVDB episode records.

**[Media #47]: Audio and Subtitle Tracks Ignored for Matching**  
_Concept_: Embedded audio languages and subtitle tracks should not influence episode matching.  
_Novelty_: Media stream metadata is not confused with episode identity.

**[TVDB #48]: Fallback to Original TVDB Title**  
_Concept_: If TVDB has no English title for an episode, Episort should use the original title available in TVDB.  
_Novelty_: Missing English metadata does not block organization.

**[TVDB #49]: Series Art Optional**  
_Concept_: Missing thumbnails or summaries should not block matching. Seasons and episodes are the required TVDB data.  
_Novelty_: Visual metadata improves disambiguation but is not core correctness data.

**[TVDB #50]: Homonym Selection With Year Suffix**  
_Concept_: If homonymous series remain ambiguous, Episort should show a popup comparing possible TVDB matches. For homonyms, the year should be preserved in parentheses at the end of the series folder name.  
_Novelty_: Naming keeps enough identity to distinguish remakes and same-name series.

**[Media #51]: Movie Rename Support**  
_Concept_: If the selected folder contains movies, Episort should rename them using `English Movie Name (Release Year).extension`.  
_Novelty_: The product can organize mixed video libraries without treating every non-series video as an error.

**[Media #52]: Unsupported Files Remain Untouched and Visible**  
_Concept_: Files not handled by TVDB or the supported media workflow should not be deleted, moved, or renamed. They should appear as unmatched in the UI so the user knows they exist and can handle them manually.  
_Novelty_: Unknown files are surfaced for awareness without being treated as failures.

**[Media #53]: Sidecar Files Ignored Without Error**  
_Concept_: `.nfo`, subtitle files such as `.srt`, and image files should not be processed, deleted, moved, or shown as errors.  
_Novelty_: Common media-library sidecar files do not pollute the ambiguity queue.

**[Media #54]: Plex-Compatible Video Scope**  
_Concept_: The intended video scope is all common video files usable in Plex libraries, while excluding unsupported disk image formats such as ISO, IMG, VIDEO_TS, and BDMV.  
_Novelty_: The app aligns with real Plex library usage while avoiding formats that do not map cleanly to one episode per file.

**Question Storming - MVP Definition**

**[MVP #55]: Movie Support Included in MVP**  
_Concept_: Movie detection and rename support should be part of the MVP, not deferred to V2.  
_Novelty_: The first release targets realistic mixed video folders rather than series-only ideal cases.

**[MVP #56]: All Three TVDB Orders in MVP**  
_Concept_: Aired order, DVD order, and absolute order should all be available in the MVP.  
_Novelty_: The MVP starts with the ordering flexibility needed for real anime and DVD-library cases.

**[MVP #57]: Contextual AI Chat Everywhere**  
_Concept_: The contextual AI chat should be available throughout the MVP, not only on conflicts.  
_Novelty_: Conversation becomes a universal correction mechanism across files, folders, seasons, and matching decisions.

**[MVP #58]: Two Synchronized Trees Are Mandatory**  
_Concept_: The before/after synchronized tree UI is mandatory for the MVP.  
_Novelty_: The validation surface is treated as core product functionality, not polish.

**[MVP #59]: GPU and VRAM Startup Check in MVP**  
_Concept_: The MVP must include hardware prerequisite validation for the embedded local LLM.  
_Novelty_: Runtime viability is verified from the first release.

**[MVP #60]: Embedded LLM From the Start**  
_Concept_: The MVP must ship with the embedded local LLM from the beginning, not rely on an external local model during early product usage.  
_Novelty_: The MVP validates the real distribution and runtime model, not only the app shell.

**[MVP #61]: Logs Written to Disk Only**  
_Concept_: MVP logs should be written to disk in the OS user folder and do not need an in-app log viewer.  
_Novelty_: Diagnostics are available without adding an extra UI surface.

**[MVP #62]: Settings Editable After Onboarding**  
_Concept_: Users must be able to change the workspace and TVDB API key after first launch.  
_Novelty_: Onboarding values are defaults, not permanent installation choices.

**[MVP #63]: Folder Creation During Execution Only**  
_Concept_: `Season XX`, `Specials`, movie, and series folders should be created only during final execution, with folder creation among the first execution tasks.  
_Novelty_: Planning never mutates the filesystem.

**[MVP #64]: End-to-End Success Definition**  
_Concept_: The V1 succeeds if the app works from configuration through scan, AI-assisted review, easy user correction, final validation, and actual file organization.  
_Novelty_: MVP success is measured by a complete safe workflow, not an isolated matching algorithm.

**Six Thinking Hats - White Hat: Facts and Research Needs**

**[Facts #65]: TVDB API Key Model Must Be Verified**  
_Concept_: TVDB documentation indicates API keys are per-project for developers, not individual end-user keys. Episort must verify the correct API key model before finalizing Settings UX.  
_Novelty_: The product may need developer-provided TVDB integration rather than asking every user for a personal TVDB API key.

**[Research #66]: TVDB Series Endpoint Research**  
_Concept_: Research is required to identify the exact TVDB API endpoints needed for series lookup, seasons, episodes, episode orders, English names, original-title fallback, images, summaries, and release dates.  
_Novelty_: TVDB is the product's source of truth, so endpoint mapping is a first-class architecture dependency.

**[Research #67]: TVDB Movie Endpoint Research**  
_Concept_: Research is required to identify the exact TVDB API endpoints needed for movie lookup and English movie title plus release year retrieval.  
_Novelty_: Movie support being MVP means the TVDB integration cannot be series-only.

**[Research #68]: Java MKV Metadata Library Research**  
_Concept_: Research is required to choose a Java-compatible way to read MKV metadata reliably.  
_Novelty_: MKV metadata is part of the matching evidence ladder, so the parser choice affects matching quality.

**[Research #69]: Embedded LLM Runtime Research**  
_Concept_: Research is required to determine how a local LLM can be embedded or bundled with a Windows Java desktop application.  
_Novelty_: The AI runtime is not optional infrastructure; it is a core product prerequisite.

**[Research #70]: RTX and VRAM Detection Research**  
_Concept_: Research is required to detect RTX GPU availability and VRAM from a Java application on Windows.  
_Novelty_: Hardware checks must happen before the app exposes workflows that require local AI.

**[Platform #71]: Windows-Only V1**  
_Concept_: Because RTX is required and macOS support does not fit the hardware target, Episort V1 should support Windows only.  
_Novelty_: The project drops cross-platform V1 scope to align the runtime promise with realistic hardware support.

**[Research #72]: Portable Java Packaging Research**  
_Concept_: Research is required to determine the packaging strategy for a Windows portable Java app that includes a large embedded model and runtime dependencies.  
_Novelty_: Distribution architecture must handle multi-GB assets from the first release.

**[Research #73]: Plex-Like Video Extension Scope Research**  
_Concept_: Research is required to define the exact video file extensions treated as processable video files in the MVP.  
_Novelty_: The app needs a concrete extension allowlist to avoid touching sidecar or unsupported files.

**Six Thinking Hats - Red Hat: User Emotion and Trust**

**[Emotion #74]: Safety After Full Review**  
_Concept_: The user should feel safe only after reviewing the full list of proposed file modifications.  
_Novelty_: Trust is earned through complete visibility, not through AI confidence alone.

**[Emotion #75]: Trust Breakers**  
_Concept_: Two immediate trust breakers are visible AI hallucination and lack of a double-check popup before final processing.  
_Novelty_: The app must treat confirmation UX as a trust-critical feature.

**[Emotion #76]: Red Means Priority, Not Panic**  
_Concept_: Red confidence coloring should communicate review priority, not catastrophic failure.  
_Novelty_: The heat scale guides attention without making the app feel broken.

**[Emotion #77]: Narrow AI Scope to Reduce Hallucination Feel**  
_Concept_: The AI should be tightly framed and often scoped down to one file or one contextual object at a time.  
_Novelty_: The UI design should constrain the AI's reasoning space to make its behavior feel grounded.

**[Emotion #78]: Corrections Are Applied, Not Debated**  
_Concept_: When the user corrects the AI, the app should simply apply the correction rather than argue or over-explain.  
_Novelty_: User authority is explicit in interaction tone.

**[Emotion #79]: Chat as Discreet Tool**  
_Concept_: The AI chat should feel like a discreet utility, not a verbose personality-driven assistant.  
_Novelty_: The product foregrounds control and clarity rather than conversational spectacle.

**[Emotion #80]: Before/After View Should Produce Control**  
_Concept_: The synchronized before/after view should primarily make the user feel in control.  
_Novelty_: The key emotional outcome is not speed or delight; it is safe command over a risky file operation.

**[Emotion #81]: Avoid Final-Processing Blocking Errors**  
_Concept_: The worst time for a blocking error is during final processing. Prerequisites and plan validity should be checked before execution starts.  
_Novelty_: Failure is pushed earlier in the workflow to protect trust.

**[Emotion #82]: Pre-Execute Confidence From Recap**  
_Concept_: Before clicking Execute, the user should feel safe because the recap is clear and the software appears trustworthy.  
_Novelty_: The final modal is an emotional assurance layer as much as a technical summary.

**[Feedback #83]: Post-Execution Per-File Recap**  
_Concept_: After execution, Episort should show a completion screen listing what happened per file: moved, renamed, ignored, left untouched, or failed.  
_Novelty_: The app closes the loop with auditable outcomes rather than a generic success message.

**Six Thinking Hats - Black Hat: Risks and Failure Modes**

**[Risk #84]: TVDB Unavailable Blocks Scan**  
_Concept_: If TVDB is unavailable at scan time, Episort should not proceed and should not modify anything.  
_Novelty_: Metadata source failure is treated as a hard stop before planning.

**[Risk #85]: TVDB Failure During Workflow Retries Then Errors**  
_Concept_: If TVDB fails after initially responding, Episort should retry and then show an error if recovery fails.  
_Novelty_: The app distinguishes transient API failure from invalid planning input.

**[Risk #86]: TVDB Key Expiry or Keyless Model Warning**  
_Concept_: If a TVDB key expires or authentication fails, the app should inform the user. The project must still verify whether end-user keys are needed at all.  
_Novelty_: The Settings model remains open until TVDB's project-key requirements are confirmed.

**[Risk #87]: LLM Quality Managed by Upfront Testing**  
_Concept_: If the embedded LLM runs but produces incoherent output, the product has limited runtime fallback; model quality must be validated before release.  
_Novelty_: AI reliability is treated as a release gate rather than something to fix during user workflows.

**[Risk #88]: Locked File Retry and Continue Popup**  
_Concept_: If a local file is locked by another process, Episort should show a popup allowing the user to retry or continue when ready.  
_Novelty_: The execution flow can recover interactively without abandoning the whole run.

**[Risk #89]: Existing Destination Folder Reuse**  
_Concept_: If the destination series or season folder already exists, Episort should reuse it rather than recreate it. File conflicts inside the folder require user choice.  
_Novelty_: Existing organized libraries are merged into safely instead of treated as errors.

**[Risk #90]: Same Final Series Folder Merge**  
_Concept_: If two detected series groups resolve to the same final series folder, they should merge into that folder. Similar file conflicts are surfaced for user resolution.  
_Novelty_: Duplicate group detection resolves toward consolidation instead of folder proliferation.

**[Risk #91]: Long Windows Paths Use Title Truncation**  
_Concept_: If a generated Windows path would be too long, Episort should truncate the title portion.  
_Novelty_: Path compatibility is handled through deterministic naming adjustment.

**[Risk #92]: Execution Interruption Detection on Next Launch**  
_Concept_: The app should prevent closing during execution where possible. If a crash or interruption occurs anyway, the next launch should warn the user and provide the log file path.  
_Novelty_: Interrupted operations become diagnosable on restart.

**[Risk #93]: Continue After Per-File Failure**  
_Concept_: If moving/renaming fails for one file during execution, Episort should leave that file untouched, continue with the remaining operations, and show the failed item in the final recap.  
_Novelty_: One file failure does not destroy the entire batch workflow.

**Six Thinking Hats - Yellow Hat: Benefits and Opportunities**

**[Benefit #94]: Clean Plex Library**  
_Concept_: The primary user benefit is making a Plex library clean and consistently organized.  
_Novelty_: The success metric is downstream media-server quality, not just local file tidiness.

**[Benefit #95]: Instant Pattern Detection**  
_Concept_: The local AI should make the product feel valuable by finding naming patterns quickly and reducing manual detective work.  
_Novelty_: AI value is measured by pattern discovery speed.

**[Benefit #96]: Series and Anime High-Impact Use Case**  
_Concept_: Series and anime libraries are the highest-impact targets because they often include multiple seasons, specials, absolute ordering, and inconsistent naming.  
_Novelty_: Anime becomes a first-class driver for TVDB order flexibility.

**[Benefit #97]: Reliable Large-Volume Processing**  
_Concept_: Episort should let users process large volumes of files reliably.  
_Novelty_: The product competes with tedious manual work at scale, not one-off renames.

**[Benefit #98]: Better Than a Script Through Pattern + TVDB Fusion**  
_Concept_: Episort is superior to a simple renaming script because it combines AI pattern detection with TVDB-backed concrete choices and user validation.  
_Novelty_: The app bridges fuzzy local naming and official structured metadata.

**[Benefit #99]: Before/After View Enables Confidence and Manual Repair**  
_Concept_: The synchronized before/after view lets users verify safety and manually correct wrong matches.  
_Novelty_: The review UI is both a trust mechanism and an editing surface.

**[Benefit #100]: Contextual Chat Improves Session Pattern Quality**  
_Concept_: Contextual chat helps users correct and improve the pattern within the current session.  
_Novelty_: The AI assistant becomes a pattern repair tool instead of a general chatbot.

**[Benefit #101]: TVDB Aligns With Plex Matching**  
_Concept_: Using TVDB as source of truth improves compatibility with Plex matching expectations.  
_Novelty_: The product optimizes for the media server's metadata ecosystem.

**[Benefit #102]: No Auto-Delete Fits Existing Manual Workflow**  
_Concept_: Never deleting files automatically aligns with the user's existing habit of manually deciding what to keep.  
_Novelty_: The product augments the user's control instead of replacing it.

**[Benefit #103]: Server Management at Scale**  
_Concept_: Long-term indispensability comes from managing a media server and processing large file volumes with confidence.  
_Novelty_: Episort can become a recurring server maintenance tool rather than a one-time organizer.

## Idea Organization and Prioritization

### Executive Synthesis

Episort is a Windows desktop application for organizing large Plex-oriented video libraries, especially TV series and anime. Its core promise is to make Plex clean at scale by combining TVDB-backed metadata with local AI pattern detection, while keeping the user in full control before any file operation occurs.

The strongest product principle from the session is:

> Episort does not autonomously rename files. Episort builds a readable proof that the proposed organization is correct, lets the user correct it, then executes only after explicit validation.

### Thematic Organization

#### 1. File Safety and Human Validation

**Focus:** Prevent accidental media loss, wrong moves, and hidden destructive behavior.

**Key ideas:** #1-10, #29, #31-32, #74-83, #84-93

- All file operations require explicit human validation.
- Confidence affects review priority, not permission.
- The app should support a plan-only review mode where no filesystem mutation occurs.
- Duplicate candidates are never deleted automatically.
- Folder creation and moves occur only during final execution.
- Per-file failures leave that file untouched and continue with the remaining files.
- A final recap is required before execution, and a post-execution recap shows what happened per file.

**Pattern insight:** Episort's safest design is prevention-first. Rollback is not a V1 requirement because the product prevents unreviewed execution.

#### 2. TVDB as Source of Truth

**Focus:** Use TVDB to anchor official metadata, ordering, and naming.

**Key ideas:** #1, #20, #23-25, #44-50, #65-67, #84-86, #101

- TVDB wins when local patterns conflict with official metadata.
- Aired order, DVD order, and absolute order are all MVP requirements.
- The app should recommend an order automatically but let the user override it.
- Series homonyms require a TVDB comparison popup with thumbnail, summary, release date, and year suffix when needed.
- If no English episode title exists, use the original TVDB title.
- TVDB availability blocks scan; failed API calls should retry and then error.
- The exact TVDB API key model and endpoints require research.

**Pattern insight:** TVDB is not merely a lookup service. It is the product's correctness layer and must be modeled as a core dependency.

#### 3. Local AI, Strictly Scoped

**Focus:** Make AI useful for pattern detection without letting it become an unbounded authority.

**Key ideas:** #11-20, #30, #33-39, #42-43, #57, #60, #69-70, #77-79, #87, #95, #100

- AI is mandatory in V1 and must be embedded locally.
- No external model selection or cloud AI is allowed.
- The AI should process series-by-series and, when possible, file-by-file.
- Corrections are remembered only during the current session.
- The AI shows results first and explains reasoning only on demand.
- Chat should be contextual and discreet, attached to files, folders, seasons, conflicts, or matching decisions.
- AI reliability must be validated before release because runtime fallback is limited.

**Pattern insight:** The AI is a controlled pattern-detection and correction tool, not a general autonomous agent.

#### 4. Main Review UX

**Focus:** Build the interface where trust and correction happen.

**Key ideas:** #7, #18-19, #21-28, #30-32, #57-58, #74-83, #99-100

- First launch opens Settings; later launches open Main Menu if required settings exist.
- Main Menu Load can only select subfolders within the configured workspace.
- The review screen must show two synchronized trees: original structure on the left, proposed target structure on the right.
- The target tree uses English names, `Season XX`, and `Specials`.
- Confidence appears as red-to-green heat coloring, not numeric score.
- Filters should show urgent/red, less urgent, good/green, and ambiguity-only items.
- Chat bubbles appear where contextual correction may be useful.
- Execute is bottom-right but always guarded by a confirmation recap.

**Pattern insight:** The before/after synchronized tree is the core product surface, not a supporting screen.

#### 5. Media Scope and Edge Cases

**Focus:** Decide what files are handled, ignored, or surfaced.

**Key ideas:** #16-17, #46-54, #55-56, #73, #96-98, #102-103

- Input folders may be messy and contain mixed series, anime, movies, sidecars, and unsupported files.
- One episode equals one file; multi-episode files are not supported in MVP.
- Specials and OVA go into `Specials`.
- KAI files are ignored if not referenced by TVDB.
- Movies are MVP and use `English Movie Name (Release Year).extension`.
- Unsupported files are visible as unmatched when useful, but not moved, renamed, deleted, or treated as failures.
- `.nfo`, `.srt`, and image sidecars are ignored without error.
- Plex-like video extension scope requires research.

**Pattern insight:** Episort should accept messy input but produce conservative, TVDB-aligned output.

#### 6. Platform, Runtime, and Packaging

**Focus:** Make the V1 technically coherent.

**Key ideas:** #33-43, #59-62, #68-72

- V1 is Windows-only.
- RTX-class GPU and 12 GB VRAM are required.
- Startup must verify prerequisites and show a clear error if unmet.
- The app ships with one embedded LLM from the beginning.
- Bundle size may be up to about 10 GB, but smaller is preferred.
- Settings and logs live in the OS user folder.
- Logs are disk-only for MVP.
- Research is required for Java packaging with a multi-GB model, embedded LLM runtime, GPU/VRAM detection, and MKV metadata parsing.

**Pattern insight:** Dropping cross-platform support makes the runtime promise clearer and avoids a false MVP.

### Prioritization Results

#### Top Priority Ideas

1. **End-to-end safe workflow**
   - Settings, workspace, TVDB validation, scan, AI matching, user correction, recap, execution, and final report.
   - This defines whether the product works at all.

2. **Synchronized before/after tree UI**
   - Original files on the left, proposed target layout on the right.
   - Collapsible series, seasons, specials, and episode rows.
   - This is the primary validation and correction surface.

3. **TVDB-backed matching for series, anime, and movies**
   - Includes aired, DVD, absolute order.
   - Includes homonym handling and movie rename support.
   - This anchors Plex compatibility.

4. **Embedded local AI with scoped contextual chat**
   - One model, local-only, Windows, RTX required.
   - Chat is contextual and correction-focused.
   - This is the differentiator beyond scripts.

5. **Non-destructive execution and diagnostics**
   - No deletes.
   - Per-file failure continues.
   - Logs on disk.
   - Final per-file recap.

#### Quick Win Opportunities

- Settings screen with workspace and TVDB key/test placeholder.
- Workspace-bounded folder picker.
- File inventory scanner with extension allowlist.
- Plan model that stores source path, proposed destination path, status, confidence, and validation state.
- Static two-tree mock UI using sample data.
- Disk logging infrastructure in OS user folder.

#### Breakthrough Concepts

- **Proof before action:** the app's job is to prove the operation is safe before it mutates files.
- **Confidence as triage, not permission:** red items are priorities, not automatic blockers; green items still require review.
- **Contextual AI chat anchored to UI objects:** the AI knows which file, folder, or conflict the user is discussing.
- **Series-by-series AI scope:** a practical anti-hallucination design principle.
- **Windows-only V1 as honesty:** the product should match its real RTX/LLM requirement instead of pretending cross-platform readiness.

### Action Planning

#### Action Plan 1: Product Brief or PRD

**Why this matters:** The MVP is broad and must be pinned down before implementation.

**Next steps:**

1. Convert this brainstorming output into a Product Brief or PRD.
2. Define the V1 user journey from first launch to post-execution recap.
3. Mark MVP requirements versus explicit non-goals.
4. Define acceptance criteria for no-unvalidated-file-operations.

**Success indicators:**

- Clear V1 scope.
- Named workflows and validation gates.
- Open research questions separated from confirmed requirements.

#### Action Plan 2: Technical Research

**Why this matters:** Several product commitments depend on external or low-level technical feasibility.

**Research topics:**

1. TVDB API key model and endpoints for series, seasons, episodes, orders, and movies.
2. Embedded local LLM runtime compatible with Windows Java distribution.
3. Candidate model that fits 12 GB VRAM and can perform pattern reasoning reliably.
4. RTX and VRAM detection from Java on Windows.
5. MKV metadata parsing from Java.
6. Portable Java packaging with a multi-GB model.
7. Plex-like video extension allowlist.

**Success indicators:**

- Recommended library/runtime choices.
- Known API endpoints and authentication flow.
- Feasible packaging approach.
- Risks documented before architecture.

#### Action Plan 3: UX Architecture

**Why this matters:** The app's trust depends on the review UI.

**Next steps:**

1. Sketch screens for Settings, Main Menu, Load Folder, Detected Series, Review Mapping, Chat Popup, Final Recap, and Completion Recap.
2. Define row states: unmatched, low confidence, medium confidence, high confidence, conflict, duplicate, ignored, validated, failed.
3. Define interactions for manual correction and contextual AI chat.
4. Define filters and confidence heat colors.

**Success indicators:**

- The user can see what will happen before it happens.
- The user can correct any proposed match.
- The user can execute only after complete review and recap.

#### Action Plan 4: Domain Model and Safe File Plan

**Why this matters:** The execution safety rules must be enforceable in code.

**Next steps:**

1. Model source files, detected series groups, TVDB candidates, episode matches, movie matches, ignored files, conflicts, and operation plans.
2. Represent validation state separately from confidence.
3. Require exact source-to-destination operations before execution.
4. Ensure folder creation occurs only during final execution.
5. Ensure file operations are constrained to the configured workspace.

**Success indicators:**

- Tests can verify no mutation happens during scan or planning.
- Tests can verify no operation can execute without validation.
- Tests can verify failed file operations leave source files untouched.

## Session Summary and Insights

### Key Achievements

- Generated 103 ideas across product, UX, AI, TVDB, safety, runtime, MVP, and failure-mode dimensions.
- Reframed Episort from a renamer into a trust-first validation system.
- Changed V1 platform scope from cross-platform to Windows-only based on RTX and embedded LLM constraints.
- Identified TVDB and embedded LLM runtime research as blocking inputs before architecture.
- Established that the before/after synchronized tree is the core product UI.

### Core Product Definition

Episort V1 is a Windows-only Java desktop application for organizing Plex-oriented video libraries. It uses TVDB as the source of truth and a bundled local LLM to detect naming patterns, group files, propose matches, and provide contextual correction support. It never modifies files until the user validates the detected pattern and the exact file operation plan. Its goal is to make large series and anime libraries clean, trustworthy, and Plex-compatible.

### MVP Boundary

**Included in MVP:**

- Windows-only app.
- Java desktop UI.
- Embedded local LLM.
- RTX and 12 GB VRAM prerequisite checks.
- Settings for workspace and TVDB configuration.
- TVDB key/authentication validation, pending final API model research.
- Series, anime, and movie handling.
- Aired, DVD, and absolute TVDB orders.
- English output naming with original-title fallback.
- `Season XX` and `Specials` folder layout.
- Synchronized before/after tree UI.
- Confidence heat coloring and filters.
- Contextual AI chat throughout the review flow.
- Double validation and final recap.
- Disk logs in OS user folder.
- Post-execution per-file result recap.

**Excluded or not supported in MVP:**

- macOS and Linux.
- CPU-only AI mode.
- User-selectable or downloadable external models.
- Cloud AI.
- Manual no-AI mode.
- Multi-episode files.
- Automatic deletion.
- Rollback/restore flow.
- Persistent correction rules across sessions.
- Processing sidecar files such as `.nfo`, `.srt`, and images.
- Unsupported disk image formats such as ISO, IMG, VIDEO_TS, and BDMV.

### Research Backlog

1. TVDB API project-key versus user-key model.
2. TVDB endpoints for series search, seasons, episodes, orders, images, summaries, release dates, and movies.
3. TVDB language/title fallback behavior.
4. Java-compatible MKV metadata extraction.
5. Embedded local LLM runtime strategy for Java on Windows.
6. Candidate model selection for 12 GB VRAM.
7. RTX and VRAM detection from Java.
8. Portable packaging for Java plus model assets up to 10 GB.
9. Plex-like video file extension allowlist.
10. Windows long-path handling and deterministic title truncation rules.

### Recommended Next Step

Create a Product Requirements Document from this session, then run targeted technical research before final architecture. The PRD should preserve the safety rules as non-negotiable acceptance criteria, because they are the product's trust foundation.

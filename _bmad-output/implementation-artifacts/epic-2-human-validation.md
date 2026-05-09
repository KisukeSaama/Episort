# Epic 2 Human Validation Guide

Status: ready-for-human-validation

## Scope

Epic 2 validates local, non-destructive media inventory before any TVDB lookup, pattern validation, operation plan, or file operation exists.

Stories covered:

- 2.1 Supported Media File Detection
- 2.2 Sidecar and Unsupported File Inventory
- 2.3 Mixed Media Group Seed Classification
- 2.4 Large Inventory Progress and Result Summary

## Preconditions

- Worktree contains the Epic 2 implementation changes.
- No real media folder is used for validation.
- All filesystem validation uses JUnit temporary directories or an explicitly disposable scratch folder.

## Automated Baseline

Run:

```powershell
$jdk = Get-ChildItem '.tools\jdk21' -Directory | Select-Object -First 1
$env:JAVA_HOME = $jdk.FullName
$env:Path = "$($jdk.FullName)\bin;$env:Path"
.\gradlew.bat test
```

Expected:

- Build succeeds.
- `MediaInventoryScannerTest` passes.
- `InventoryWorkflowServiceTest` passes.

## Review Stops

### Supported and Non-Executable Classification

Open:

- `src/main/java/com/episort/scanner/MediaInventoryScanner.java`
- `src/test/java/com/episort/scanner/MediaInventoryScannerTest.java`

Validate:

- `.avi`, `.mp4`, and `.mkv` are the only supported video candidates.
- Supported items preserve source path, filename, extension, and parent folder.
- Sidecars, unsupported files, and ignored files remain visible in inventory.
- Sidecars, unsupported files, and ignored files are not operation candidates.

Useful feedback:

- Are the sidecar extensions sufficient for your real media folders?
- Should any common extension be supported or excluded differently?
- Is dotfile handling acceptable for Windows and cross-platform folders?

### Group Seeds Without TVDB Authority

Open:

- `src/main/java/com/episort/scanner/MediaInventoryScanner.java`
- `src/main/java/com/episort/scanner/InventoryGroup.java`
- `src/main/java/com/episort/scanner/InventoryGroupType.java`

Validate:

- `SxxEyy` and `1x02` style files become likely series seeds.
- Filename year patterns become likely movie seeds.
- Loose supported videos stay unknown.
- All inventory groups keep `tvdbIdentityFinal=false`.

Useful feedback:

- Try to think of naming patterns from your library that this seed logic might misread.
- Note examples where a movie could look like a series or a series could look like a movie.
- Check whether the seed names look readable enough for a later review UI.

### Progress and Summary

Open:

- `src/main/java/com/episort/scanner/InventoryScanProgress.java`
- `src/main/java/com/episort/scanner/InventorySummary.java`
- `src/main/java/com/episort/workflow/InventoryWorkflowService.java`
- `src/test/java/com/episort/workflow/InventoryWorkflowServiceTest.java`

Validate:

- Progress reports processed count, total count, and completion.
- The workflow service runs scan work through an injected `Executor`.
- The summary includes supported, sidecar, unsupported, ignored, likely series groups, likely movie groups, and unknown counts.
- Pattern validation and operation-plan approval remain false.

Useful feedback:

- Are these summary counts enough before metadata matching begins?
- Is any missing count important for review confidence?
- Does the progress model expose enough state for a future UI?

### Non-Destructive Guardrail

Open:

- `src/test/java/com/episort/scanner/MediaInventoryScannerTest.java`

Validate:

- Tests create files only in temporary directories.
- Scanner tests snapshot the folder before and after scanning.
- No production code calls `Files.create*`, `Files.move`, `Files.delete`, or rename APIs during scan.

Useful feedback:

- Any concern about following symlinks or hidden directories should be recorded for hardening.
- Any concern about scanning deeply nested folders should be recorded for performance or UX handling.

## Human Validation Notes

Record observations here during review.

### Passed

- Automated baseline passed with `.\gradlew.bat test`.
- Application launch smoke check passed with `.\gradlew.bat run`.
- Sidecar handling validated: sidecars must be detected only for traceability and must remain non-executable.
- Supported video extension scope validated for V1: only `.avi`, `.mp4`, and `.mkv`.
- Unsupported and ignored file behavior validated: non-supported files remain untouched and visible only as non-treated inventory.
- Realistic naming examples reviewed: movie year patterns and `SxxEyy` series patterns match the user's expected V1 naming inputs.
- Ambiguous deterministic `UNKNOWN` classification accepted only with the explicit product expectation that local AI has a central first-scan triage role.
- Current summary counters are sufficient for the deterministic inventory part of Epic 2.

### Questions

- Resolved: future UI should present sidecars together with ignored files because they are not useful as a separate user-facing category for this workflow.

### Requested Changes

- Future epics must not move sidecars with their associated episode files. Sidecars should remain untouched even when a nearby supported video is moved.
- Future review UI should merge sidecars into the ignored-files presentation instead of exposing a prominent separate sidecar category.
- First-scan workflow should allow local AI assistance to suggest grouping for ambiguous supported videos such as absolute-number anime episodes (`One Piece - 001.mkv`, `Detective Conan 001.mkv`) and prose episode formats (`Kaamelott Livre I Episode 01.mkv`).
- Deterministic scanner may keep ambiguous files as `UNKNOWN`, but AI suggestions should be available before user review so these files are not left unexplained.
- Add or reorder an early AI triage story before TVDB matching/review work, because AI is central to first-scan detection quality and should not wait until the end of the project.
- Resolved during validation: added a user-visible scan result surface for the selected folder, inventory counts, and probable groups.
- Preferred local AI direction for early triage validation: Qwen3 8B on the user's NVIDIA RTX 4070 SUPER, using a real local model instead of fake AI data for product-quality tests.

### User Naming Examples

- `Avatar (2009).mkv` -> likely movie seed `Avatar`
- `Avatar The Way of Water (2022).mkv` -> likely movie seed `Avatar The Way of Water`
- `La Tour Montparnasse Infernale (2001).mp4` -> likely movie seed `La Tour Montparnasse Infernale`
- `Black Mirror - S01E01 - The National Anthem.mkv` -> likely series seed `Black Mirror`
- `Loki - S01E02 - The Variant.mkv` -> likely series seed `Loki`
- `Air Gear - S01E12 - She's Finally Here The Savior Rider.mkv` -> likely series seed `Air Gear`
- `Detective Conan - S06E04 - And Then There Were No Mermaids (Part One The Murder).mkv` -> likely series seed `Detective Conan`

### Decision

- Pending manual UI check and AI course correction. Current position: deterministic Epic 2 inventory and scan result display are technically acceptable, but product validation should not close until the team decides where early AI triage belongs in the story order.

# Special-case decisions

Date: 2026-08-06

## Scope

This artifact records the product decisions agreed with the user before implementation.
The two mandatory gates remain unchanged: validate detected series groups, then validate
the exact source-to-destination operation plan.

## Matching and review

1. An ambiguous video must be manually assigned to one episode or explicitly ignored.
   A missing sequence number is never enough to infer its identity.
2. Mixed folders are split into independently confirmed series groups. A file is never
   attached to the majority series automatically.
3. Credible duplicate TVDB series results require explicit selection. A unique result may
   be preselected but remains visible for validation.
4. Aired order remains the fallback. When titles or other reliable filename evidence
   permit it, compare TVDB aired, DVD, and absolute mappings and propose the most coherent
   order. Show source and target numbering; uncertainty remains explicit.
5. One video represents one episode. Multi-episode filenames are unsupported and must be
   assigned to one episode or ignored; Episort never splits video files.
6. Multiple videos assigned to one episode are blocking duplicates. The user chooses one;
   all others are ignored, never deleted.
7. A destination collision never overwrites automatically. Identical content may be
   ignored; different content requires an explicit keep-existing, keep-both, or cancel
   decision. Recheck immediately before execution.
8. Season-zero episodes and recognized specials use a `Specials` directory while keeping
   their `S00EXX` filename number. Unmatched bonus videos remain unresolved or ignored.
9. A number absent from the selected TVDB order is invalid. Alternative-order matches may
   be suggested but never applied automatically.
10. Episode-title language fallback is English, then French, then any available TVDB
    language, then no title only when TVDB supplies none. Show the selected language.
11. For generated filesystem names, replace forbidden separator characters such as `:`
    and `/` with spaces, remove other forbidden characters, collapse whitespace, trim
    trailing spaces/dots, and protect Windows reserved names.
12. No proactive long-path shortening rule is required. A filesystem refusal is reported
    without modifying that file.

## Inventory boundaries

13. Revalidate every source and destination before execution. If preflight fails, start
    nothing. Stop on an unexpected in-run error and never force a locked file.
14. Keep successful operations after a partial failure. Do not roll back automatically;
    rebuild a plan for the remaining operations.
15. A video already at its exact destination is shown as already organized and produces no
    operation. A changed TVDB title is proposed, not silently applied.
16. Ignore every non-video file completely. Only `.avi`, `.mp4`, and `.mkv` enter analysis
    or operations; subtitles and sidecars are untouched.
17. Scan recursively inside the working directory without following symbolic links or
    junctions. Show relative paths, keep every destination inside the workspace, and remove
    source directories after a move only when they and their parents are empty.
    Prefix the parent container of a source directory that remains non-empty
    after a successful run with `[TRI]` so it stays visible for manual sorting.
18. Distinguish TVDB technical failure from no result. Retry temporary failures within API
    limits, preserve local review choices, identify cached data and its date, and create no
    final plan without reliable TVDB data.
19. Freeze validated TVDB data in the exact plan. Refresh only on explicit reanalysis,
    show differences, and require validation again.
20. Generated directory and file names contain no accents. Transliterate letters to their
    unaccented equivalents while retaining the chosen TVDB casing. UI metadata may retain
    accents.
21. Detect crossing renames and execute them through collision-free temporary names in the
    same directory, without treating them as overwrite permission.
22. Cancellation takes effect after the current file operation. Keep completed work, start
    nothing further, show a precise recap, and do not roll back automatically.
23. Journal execution before each operation. After interruption, inspect actual disk state,
    show recovery information, and require a newly validated remaining-work plan; never
    resume automatically.
24. Trailer, sample, and interview videos remain visible as probably unrelated when no
    reliable episode number exists. They must be assigned or explicitly ignored.
25. Missing episodes are informational and do not block present episodes. Never renumber
    later files to fill a gap; an unresolved file may receive a nonautomatic suggestion.
26. No special TVDB migration workflow is required. If a stored reference cannot be used,
    rerun the ordinary series and episode matching flow.
27. Changing the working directory invalidates all previous analysis and planned paths,
    reruns matching, and requires both validation gates again. No operation may escape the
    active working directory.

## Execution guardrails

- Media is never renamed, moved, overwritten, or deleted before both validation gates.
- Ignoring a file never deletes or mutates it.
- The exact operation plan is the immutable execution input.
- Every user-visible status and value comes from real state; missing data displays `—`.

## Implementation notes

- The existing application already covered the core group review, explicit TVDB identity
  selection, specials folder, duplicate and destination conflicts, already-organized
  detection, workspace boundary checks, immutable approved plan, execution journal,
  cancellation/partial recap, and missing-episode correction flow.
- The inventory now admits only visible `.avi`, `.mp4`, and `.mkv` files and excludes
  symbolic links. Non-video sidecars are absent from analysis and operations.
- Multi-episode videos no longer receive an automatic TVDB episode proposal.
- Generated names transliterate accents, replace forbidden separators with spaces, remove
  the remaining forbidden characters, and are not silently shortened.
- Duplicate conflict resolution is non-destructive: duplicates already present in the plan
  or library can be skipped but cannot be replaced or deleted from the conflict UI.
- Execution removes emptied source folders and their empty parents after successful moves.
  Non-empty folders and ignored contents are preserved together under a parent
  container carrying the `[TRI]` prefix. Failed or aborted work is never tagged.
- The default execution path stops at the first error. The UI offers Retry for recoverable
  errors and Stop; it no longer offers Continue.
- Numbered specials whose real title contains `Extra`, `Extras`, or `Bonus` are no longer
  implicitly ignored. Inventory grouping and per-file analysis now apply the same rule, so
  they remain in a named series group, carry a visible review alert, and can be matched
  manually or explicitly ignored by the user.
- Every visible row alert now blocks the first validation gate, so an exact operation plan
  cannot be generated while an alert remains unresolved.

## Verification

- `gradlew test` — passed after implementation.
- `gradlew build` — passed.

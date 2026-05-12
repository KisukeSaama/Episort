---
status: done
date: 2026-05-11
---

# TVDB Automatic Suggestions

Implemented automatic post-scan TVDB suggestions on top of the existing
`TvdbBatchMatchService` and `ScanScreen.applyTvdbBatchResult` path.

## Code Map

- `src/main/java/com/episort/tvdb/TvdbCandidateScorer.java`
  - Normalizes detected titles and TVDB titles.
  - Removes common release/codec tags.
  - Scores candidates and classifies them as automatic or suggestion-only.
- `src/main/java/com/episort/workflow/TvdbBatchMatchService.java`
  - Groups equivalent searches by normalized query + media type for the run.
  - Applies scoring before metadata retrieval.
  - Fetches TVDB metadata only for automatic matches.
- `src/main/java/com/episort/workflow/TvdbBatchMatchResult.java`
  - Carries score and automatic/suggestion state to the UI.
- `src/main/java/com/episort/ui/scan/ScanScreen.java`
  - Applies automatic matches to rows.
  - Leaves ambiguous matches in REVIEW with a TVDB suggestion.
  - Skips rows already selected by the user.
- `src/main/java/com/episort/ui/UiText.java`
  - Adds localized labels for automatic and suggestion states.
- `src/main/resources/i18n/messages.properties`
- `src/main/resources/i18n/messages_fr.properties`
  - Adds English/French TVDB suggestion text.
- `src/test/java/com/episort/workflow/TvdbBatchMatchServiceTest.java`
  - Covers Haikyu normalization, duplicate search suppression, per-file
    episode retention, and ambiguous suggestion behavior.

## Design Notes

- Automatic threshold: score `>= 0.85`.
- Suggestion threshold: score `0.65..0.84`.
- Below `0.65`, no batch match is returned.
- Equivalent queries are cached for the duration of `run(...)` using
  normalized title + inventory group type.
- Manual user selections remain authoritative because batch application returns
  early when `ScanRow.tvdbSelectedByUser()` is true.

## Verification

- `.\gradlew.bat test --tests com.episort.workflow.TvdbBatchMatchServiceTest`
- `.\gradlew.bat test`

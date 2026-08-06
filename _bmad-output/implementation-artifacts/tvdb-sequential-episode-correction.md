# TVDB sequential episode correction

Date: 2026-08-06

## Scope

- Correct one matched series episode from the detail panel.
- Correct checked rows as a consecutive TVDB sequence from a chosen first episode.
- Preserve the existing identity-selection and exact-plan validation gates.

## Implementation

- Added an inline first-episode selector after TVDB series metadata is available.
- Replaced its flat long list with retractable season menus for aired/DVD order
  and 50-episode range menus for absolute order.
- Applies episodes in current table order, including every category exposed by
  the selected TVDB order (notably specials), and stops at the end of available
  metadata instead of fabricating assignments.
- Manual sequence correction replaces the affected TVDB season, episode, title,
  order and proposed filename while leaving filesystem operations untouched.
- The detail-panel reset action clears the TVDB-derived state of every checked
  row when the table contains a multi-selection, with the focused row as fallback.
- Re-synchronizes the table highlight from checked rows after an order change
  refreshes the active filter, preserving the existing functional selection.
- Recomputes the top-bar plan action whenever scan review state changes, including
  after asynchronous batch TVDB matching, so a ready plan no longer requires a
  navigation round trip before `Voir le plan` becomes enabled.
- Added a bilingual Specials category and pure unit tests for ordering, season
  rollover, special sequences, absolute-order grouping and end-of-metadata behavior.

## Verification

- `gradlew test --tests com.episort.ui.scan.ScanRowEditorTest` passed, including
  multi-row TVDB reset coverage.
- `gradlew test --tests com.episort.ui.scan.TvdbEpisodeSequenceTest --tests com.episort.ui.UiTextTest` — passed.
- `gradlew test` — passed.
- `gradlew build` — passed.
- Manual verification: match a series, check several rows, choose S17E01 as the
  first episode, apply the sequence, and inspect each proposed name before opening
  the exact operation plan.
- Manual verification: let automatic TVDB matching finish with no blockers and
  confirm that `Voir le plan` enables without leaving and reopening the Scan view.

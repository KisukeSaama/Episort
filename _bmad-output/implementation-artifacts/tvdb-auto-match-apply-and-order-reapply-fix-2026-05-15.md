# TVDB auto-match apply and order reapply fix - 2026-05-15

## Summary

- Fixed the TVDB detail-panel apply routing so an already-bound match is reapplied as the current match instead of being mistaken for a fresh manual search selection.
- This prevents automatically detected matches from being cleared when the user clicks **Apply** directly.
- Reapplication now preserves and forwards the user-selected TVDB order, so switching from Aired to DVD or Absolute no longer falls back through the manual-candidate path to Aired.
- Automatic series matches now record `AIRED` as their applied source order, allowing later remaps to use the correct source order.
- Context-menu reset now clears the candidate object and applied order together with the visible match label.

## Root cause

`RowDetailPanel` always treated a non-empty ComboBox value as a newly selected candidate. For automatically detected matches, the visible label existed but the candidate was not present in the manual-search label map, so `applyTvdbCandidate(...)` replaced the row candidate with `Optional.empty()`. The same branch also reapplied using the default Aired overload, which ignored the order selected in the UI.

## Verification

- `.\gradlew.bat test --tests com.episort.ui.scan.RowDetailPanelTest --tests com.episort.matching.TvdbEpisodeOrderMapperTest --tests com.episort.workflow.TvdbBatchMatchServiceTest`
- `.\gradlew.bat test`

## Manual verification still recommended in JavaFX

1. Scan a folder where TVDB auto-detects a series, click **Apply** without using search, and confirm the match remains visible and metadata stays applied.
2. On the same detected series, switch Aired → DVD → Absolute → Aired and confirm season/episode/title/proposed filename update where TVDB data exists and the selected order remains visible after each apply.

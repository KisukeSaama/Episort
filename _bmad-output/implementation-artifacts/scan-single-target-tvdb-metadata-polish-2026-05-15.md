# Scan single-target / TVDB metadata polish - 2026-05-15

## Scope

- Preserved checkbox-driven business multi-selection while restoring a clear path back to single-file work:
  ordinary primary clicks on non-checkbox table areas now clear checked rows, keep the clicked row as the current target, and retarget the detail / AI panels to that one row.
- Kept JavaFX visual row focus separate from `ScanRow.selected`; the single-file fallback intentionally leaves all checkboxes unchecked.
- Removed the TVDB manual-search field’s inner border by making shared search-wrapper text fields visually flat after the generic input rules are applied, while retaining the existing orange wrapper focus treatment.
- Fixed manual TVDB metadata application so movie/series matches write structured `ScanInputParse` data with `TVDB` source and then recompute the proposed filename from those fields.
- Preserved user-owned structured edits when later TVDB metadata is reapplied, so `USER` stays above `TVDB` in the existing source-priority model.

## Verification

- `.\gradlew.bat test --tests com.episort.ui.scan.ScanRowFactoryTest --tests com.episort.ui.scan.ScanRowTableSupportTest`
- `.\gradlew.bat test`

## Manual verification to perform in JavaFX

1. Check 3+ rows, then click a non-checkbox cell on another row; verify old checkboxes clear, the AI/detail target becomes the clicked row, and editable cells still enter edit mode.
2. Open the TVDB manual-match dialog and verify the search control shows one orange wrapper border only, with the existing stronger focused state.
3. Apply a TVDB episode match, verify series/season/episode/title columns update, then edit the title manually and confirm the proposed name recomputes from the edited title without falling back to `Untitled`.

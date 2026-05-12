# TVDB Manual Match Dialog

Implemented a manual TVDB match flow for scan rows.

## Changes

- Added poster URL retention on `TvdbCandidate` and mapped `image_url` from TVDB search responses.
- Added `TvdbManualMatchDialog` with editable query, async search, scrollable visual result cards, poster placeholders, loading state, error text, select, and ignore.
- Reworked the row detail TVDB area to show the selected match as a visual card with poster/title/type/year/TVDB ID/overview and a search button.
- Added a non-spamming unresolved-match CTA in the scan filter bar. It shows a manual-match count in the workflow help area and opens the resolver for the first unresolved row.
- Marked manually chosen matches on `ScanRow` with `tvdbSelectedByUser` and retained the selected `TvdbCandidate` for UI display.
- Added EN/FR i18n keys and design-system/CSS entries for the new dialog and match cards.

## Verification

- `.\gradlew.bat classes`
- `.\gradlew.bat test --tests com.episort.tvdb.HttpTvdbClientTest --tests com.episort.workflow.TvdbBatchMatchServiceTest --tests com.episort.tvdb.AiTvdbCandidateRankerTest --tests com.episort.tvdb.cache.TvdbResponseCacheTest`

## Follow-up fixes

- Made the manual TVDB dialog undecorated with a custom draggable header, close button, Escape-to-close behavior, constrained dimensions, compact overview text, and TVDB-specific scrollbar styling.
- Reworked manual match application so it reads the real `TableView` selection via `getSelectionModel().getSelectedItems()`. If no table selection exists, it falls back to the row shown in the detail panel.
- Added a visible apply action in the TVDB detail area and target-count copy so the user can see how many selected files will be updated.
- Applying one series candidate to multiple selected rows fetches the TVDB details once, then recalculates each row independently so detected episode numbers remain distinct.

## Follow-up verification

- `.\gradlew.bat classes`
- `.\gradlew.bat test --tests com.episort.tvdb.HttpTvdbClientTest --tests com.episort.workflow.TvdbBatchMatchServiceTest --tests com.episort.tvdb.AiTvdbCandidateRankerTest --tests com.episort.tvdb.cache.TvdbResponseCacheTest --tests com.episort.ui.scan.ScanRowFactoryTest --tests com.episort.ui.scan.ScanRowTableSupportTest`

## Compact Result Cards and Range Selection Fix

- Constrained TVDB result cards to a fixed poster area, flexible center text area, and fixed-width action area so the Select button remains visible without horizontal scrolling.
- Shortened result descriptions to 180 characters and two-line visual height.
- Added localized overview fallback by retaining TVDB `overviews.eng` and `overviews.fra` on `TvdbCandidate`; dialog display chooses FR -> EN -> default or EN -> FR -> default depending on the app language.
- Added no-description, searching, and loading-results localized messages.
- Reworked detail-panel TVDB buttons into a vertical layout: Apply match spans full width; Search and Reset share a second row.
- Added busy feedback during manual TVDB search and fade-in for result updates.
- Added custom Shift-click range selection for checkbox-column clicks using visible sorted table indexes, while preserving Ctrl/meta toggle behavior.

## Compact Card Verification

- `.\gradlew.bat classes`
- `.\gradlew.bat test --tests com.episort.tvdb.HttpTvdbClientTest --tests com.episort.workflow.TvdbBatchMatchServiceTest --tests com.episort.tvdb.AiTvdbCandidateRankerTest --tests com.episort.tvdb.cache.TvdbResponseCacheTest --tests com.episort.ui.scan.ScanRowFactoryTest --tests com.episort.ui.scan.ScanRowTableSupportTest`

## Search Polish Follow-up

- Shortened right-panel secondary TVDB button labels to Search / Reset while keeping Apply match explicit.
- Added a TVDB search-box shell with search glyph, clear button, dark field styling, orange focus border, and localized series/movie placeholder.
- Added padding inside the TVDB results list so hover/selection borders are not clipped on the first card.
- The clear button only appears when the query has text and clears/focuses the field without changing existing results.

## Search Polish Verification

- `.\gradlew.bat classes`
- `.\gradlew.bat test --tests com.episort.ui.scan.ScanRowFactoryTest --tests com.episort.ui.scan.ScanRowTableSupportTest --tests com.episort.tvdb.HttpTvdbClientTest`

---
status: done
date: 2026-05-11
---

# TVDB Functional Regression Fixes

## Fixes

- Kept post-scan TVDB batch matching alive even when AI refinement is unavailable or fails.
- Search results now enrich missing FR/EN overviews via TVDB translation endpoints and keep the default overview as fallback.
- Relative poster paths are normalized against the TVDB artwork host and spaces are URL-escaped before JavaFX image loading.
- Series details now retrieve aired, DVD, and absolute episode lists when available.
- The right-panel primary TVDB button is now the generic Apply action and applies the currently selected order to the current table selection.
- Applying a different order recalculates season, episode, episode title, and proposed filename without a rescan.
- When a file is labeled in another TVDB order, order application can remap by TVDB episode id into the selected target order.
- Table row context menu is shown explicitly from `onContextMenuRequested`, preserving multi-selection on selected rows.

## Verification

- `.\gradlew.bat classes`
- `.\gradlew.bat test --tests com.episort.tvdb.HttpTvdbClientTest`
- `.\gradlew.bat test --tests com.episort.workflow.TvdbBatchMatchServiceTest --tests com.episort.tvdb.HttpTvdbClientTest --tests com.episort.ui.scan.ScanRowTableSupportTest`
- `.\gradlew.bat test`

## Manual Follow-up

- Confirm live TVDB behavior with Haikyu and Detective Conan using real credentials.
- Confirm the exact DVD/Aired/Absolute endpoint availability against TVDB production data for Detective Conan.
- Confirm poster rendering for relative and absolute artwork URLs in the running JavaFX app.

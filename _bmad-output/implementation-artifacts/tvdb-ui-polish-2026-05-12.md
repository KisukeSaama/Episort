# TVDB UI Polish - 2026-05-12

Implemented targeted UI/UX polish for the scan topbar, manual TVDB search dialog, and right-side TVDB detail panel.

- Changed the topbar load dropdown to `.menu-button.load-primary`: dark background, orange border/accent, visible chevron, and subtler hover/pressed states.
- Changed `Validate preview` / `Valider l'aperçu` to `.button.validate-action`, with a distinct disabled state and restrained green validation styling when enabled.
- Aligned the manual TVDB search box with the main top search component: same dark shell, height, padding, external focus glow, and no inner orange text-field border.
- Added `TvdbSearchQueryCleaner` for manual TVDB query prefill cleanup. It strips extensions, season/episode markers, years, release tags, codecs, language tags, resolution tags, and other technical suffixes before opening/searching TVDB.
- Reworked TVDB dialog empty states so initial search and no-results are distinct and shown once through the `ListView` placeholder.
- Compact right-panel TVDB match card: smaller poster frame, side-by-side poster/meta text, truncated overview, and poster load failure debug logging with placeholder fallback.

Verification:

- `.\gradlew.bat test --tests com.episort.ui.scan.TvdbSearchQueryCleanerTest --tests com.episort.ui.scan.ScanRowFactoryTest`
- `.\gradlew.bat classes`

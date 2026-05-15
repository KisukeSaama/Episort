# TVDB order switching and load-menu expansion - 2026-05-15

## Scope

- Preserved the previously applied TVDB episode order on each scan row so repeated Aired/DVD/Absolute remaps use the correct source order instead of guessing from the newly selected target order.
- Kept the selected order visible in the detail panel and added an inline busy state that disables TVDB controls while metadata is being reapplied.
- Added `Ajouter un dossier` / `Add folder` to the Load menu, separated load-vs-add actions visually, and wired folder append through the existing workspace-validated scan path.
- Prevented duplicate rows when appending files or folders by de-duplicating normalized absolute paths before merging scan rows.

## Verification

- `.\gradlew.bat test --tests com.episort.matching.TvdbEpisodeOrderMapperTest --tests com.episort.ui.scan.ScanRowFactoryTest --tests com.episort.ui.scan.ScanRowTableSupportTest`
- `.\gradlew.bat test`

## Manual verification to perform in JavaFX

1. Apply a TVDB series match, switch Aired -> DVD -> Absolute -> Aired without reopening the search dialog, and verify season/episode/title/proposed filename update each time where TVDB data exists.
2. While switching orders, verify the inline TVDB loader appears, controls are disabled, and they recover after success or failure.
3. Use `Charger un dossier`, `Charger des fichiers`, `Ajouter un dossier`, and `Ajouter des fichiers`; confirm load replaces, add appends, and re-adding the same file does not duplicate it.

# UI state, progress, and history dialog polish

Date: 2026-05-09

## Scope

- Added context-aware Scan top-bar button states.
- Replaced the Scan safety text bar with a horizontal workflow stepper while keeping the safety copy as secondary helper text.
- Corrected remaining French UI labels found in the active UI text paths.
- Restyled the History "Vider l'historique" confirmation dialog with Episort dark theme classes.
- Increased sidebar logo and Episort brand title sizing.
- Updated `docs/design-system.md` with the workflow stepper and top-bar state rules.

## Verification

- `.\gradlew.bat test` passed.
- `.\gradlew.bat build` passed.
- Searched active source/resource files for `Reanalyser`, `Reinitialiser`, `Resultat`, and mojibake markers; none remain in `src/main/java`, `src/main/resources`, or `docs/design-system.md`.

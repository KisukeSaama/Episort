# Load menu append state and alert note guardrails — 2026-05-15

## Scope
- Kept scan alert counting aligned with the existing shared alert predicate while adding regression coverage for ignored rows and informational TVDB notes.
- Prevented append actions from being usable before any scan content exists.
- Stabilized the visual footprint of the top-bar load menu so the `Charger` label and menu arrow are not clipped.

## Changes
- Reused `ScanRowTableSupport.hasAlert(...)` as the single business definition for the `Alertes` filter and warnings metric, and added regression coverage proving:
  - informational notes such as `TVDB candidates loaded...` are not alerts;
  - rows moved to `IGNORED` are excluded from the alert bucket even if they previously carried an alert.
- Added explicit append-menu state control in `TopBar` via `setAppendActionsEnabled(...)`.
- Bound append-menu availability to actual scan presence from `AppShell`:
  - disabled on startup / after reset;
  - enabled after a loaded scan exists;
  - preserved after append flows complete.
- Introduced a shared `.header-action` CSS hook for top-bar actions and gave `.load-primary` a safe minimum width plus label spacing so `Charger` remains fully visible with its arrow.

## Verification
- `.\gradlew.bat test --tests com.episort.ui.scan.ScanRowTableSupportTest` ✅
- `.\gradlew.bat test` ✅

## Notes
- I did not alter TVDB matching behavior itself; only the UI interpretation of notes vs. alerts was hardened through tests.
- I avoided a direct JavaFX unit test for `TopBar` because constructing controls outside an initialized JavaFX toolkit is unstable in the current headless test setup.

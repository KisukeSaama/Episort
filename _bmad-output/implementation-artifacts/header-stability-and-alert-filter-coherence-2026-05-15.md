# Header stability and alert filter coherence — 2026-05-15

## Scope
- Stabilized the shared top bar height across Scan, History, and Settings without reintroducing Settings search or adding unused controls.
- Unified the definition of a scan alert so the warning metric and the Alertes filter share the same predicate.

## Changes
- Centralized the shell header height in `TopBar.SHELL_HEIGHT` and mirrored it in `.top-bar` CSS with a fixed min/pref height.
- Added `ScanRowTableSupport.hasAlert(...)` and reused it from both the Alertes status filter and the warnings metric.
- When the user selects the Alertes status filter, the media-type chip filter is reset to `ALL` so a global alert count cannot silently collapse to zero because another chip remained active.
- Recompute warning metrics and visible rows after TVDB lookup failures, TVDB metadata application, and TVDB reset actions.
- Clear stale TVDB movie alerts once a later metadata application resolves them.

## Verification
- `./gradlew.bat test` ?
- Added focused coverage in `ScanRowTableSupportTest` for the shared alert predicate, including clean and ignored rows.

## Notes
- No search bar was restored in Settings.
- No extra actions were added to History or Settings.
- TVDB business behavior was left intact except where needed to prevent resolved alert state from remaining visible/countable.

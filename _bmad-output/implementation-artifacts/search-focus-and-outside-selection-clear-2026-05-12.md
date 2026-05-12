# Search Focus and Outside Selection Clear - 2026-05-12

## Summary

- Added a Scan screen mouse filter that clears file checkbox selection when the user clicks outside the result table and outside selection-aware controls.
- Added `clearFileSelection()` to clear the business selection model across all scan rows, not only visible rows, then reset the JavaFX table selection, detail panel, select-all state, and AI context.
- Kept checkbox clicks and table editing paths independent from outside-click clearing.
- Strengthened shared search-field styling with a subtle inactive orange border and a brighter focused orange border plus glow.

## Verification

- Passed: `.\gradlew.bat test`

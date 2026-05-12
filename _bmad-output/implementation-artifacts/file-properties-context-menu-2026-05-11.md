# File Properties Context Menu

Implemented a scan-table row context-menu action for file properties.

## Changes

- Added a `Properties` / `Propriétés` menu item to scan-table rows.
- Preserved existing multi-selection when right-clicking an already selected row.
- Selected the right-clicked row when it was outside the current selection.
- Added a compact dark file-properties dialog with sections for file metadata, detection data, TVDB match data, and media information availability.
- Added copy-full-path behavior with localized feedback.

## Verification

- `.\gradlew.bat classes`
- `.\gradlew.bat test --tests com.episort.ui.scan.ScanRowTableSupportTest --tests com.episort.ui.scan.ScanRowFactoryTest`

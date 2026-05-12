# TVDB Order Remap and Mixed Sources - 2026-05-12

Status: implemented

## Summary

- Added scan source support for folders and explicit file lists.
- Added workspace-bounded validation for file or folder sources.
- Added `TvdbEpisodeOrderMapper` to map a detected/source episode identity to a selected target TVDB order.
- Wired the manual TVDB Apply flow to use the selected target order instead of falling back to aired order.
- Added tests for Detective Conan-style DVD to aired overflow, reverse aired to DVD mapping, unavailable order errors, selected-file scanning, and numeric S/E sorting.

## Verification

- `.\gradlew.bat test --tests com.episort.matching.TvdbEpisodeOrderMapperTest --tests com.episort.scanner.MediaInventoryScannerSourceTest --tests com.episort.ui.scan.ScanRowTableSupportTest`
- `.\gradlew.bat build`

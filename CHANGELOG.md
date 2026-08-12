# Changelog

All notable changes to Episort are documented in this file.

## [Unreleased]

## [0.1.1] - 2026-08-12

### Fixed

- A manually confirmed TMDB movie identity no longer remains blocked by the
  automatic filename-similarity warning after its metadata is loaded.
- An active video recognized as a Series or Movie now overrides an obsolete
  non-media scan group and can be matched against TMDB.
- Double-clicking an editable scan-table cell once again opens its inline
  editor instead of being intercepted by row selection refreshes.
- Files restored from the ignored state immediately regain their TMDB matching
  controls and consistent row metadata.
- `Ctrl+A` selects all scan rows while preserving the native shortcut inside
  text editors.

## [0.1.0] - 2026-08-11

First public release.

### Highlights

- Scan mixed working directories containing `.avi`, `.mp4`, and `.mkv` media.
- Parse and group series and movie releases before any filesystem operation.
- Resolve metadata through TMDB using aired, DVD, and absolute episode orders.
- Access TMDB through the Janus gateway without requiring users to create a
  TMDB account, API key, Janus account, or `.env` file.
- Review detected series groups and ambiguous files before generating a plan.
- Review every source and destination path before confirming file operations.
- Preserve original media extensions and target Plex/Jellyfin-friendly season
  folders and filenames.
- Journal completed operations and provide a reviewed rollback workflow.
- Use the complete French or English desktop interface.
- Distribute one self-contained executable per operating system. It embeds Java
  21 and extracts its verified runtime only into the user application-data
  directory.

### Safety

- Filesystem operations remain constrained to the selected workspace.
- No rename or move occurs before the two explicit validation gates.
- Upstream TMDB credentials remain exclusively in the Janus vault.
- Janus caller access is restricted, monitored, rotated, and revocable by the
  Episort operator.

### Validation

- Windows x64 single-file executable built, extraction-smoke-tested, and
  launched successfully without creating files beside itself.
- Janus/TMDB connectivity validated from the embedded release configuration.
- 472 automated tests completed with no failures or errors (10 skipped).

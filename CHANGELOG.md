# Changelog

All notable changes to Episort are documented in this file.

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
- Distribute self-contained Windows and Linux portable archives with Java 21.

### Safety

- Filesystem operations remain constrained to the selected workspace.
- No rename or move occurs before the two explicit validation gates.
- Upstream TMDB credentials remain exclusively in the Janus vault.
- Janus caller access is restricted, monitored, rotated, and revocable by the
  Episort operator.

### Validation

- Windows x64 portable archive manually launched and validated.
- Janus/TMDB connectivity validated from the embedded release configuration.
- 472 automated tests completed with no failures or errors (10 skipped).

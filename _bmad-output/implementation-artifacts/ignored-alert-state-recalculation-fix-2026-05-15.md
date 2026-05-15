# Ignored alert state recalculation fix — 2026-05-15

## Cause
- The 14 visible scan alerts come from non-automatic TVDB batch suggestions: `applyBatchGroupMatchToRow(...)` assigns a manual-validation `alertText` for rows that received candidates but still need user confirmation.
- `applyTvdbBatchResultInternal(...)` updated those rows but did not immediately refresh metrics or the active filter predicate, so the screen could show stale counters until an unrelated later action such as `Mark as ignored`.
- The context-menu unignore flow reconstructed status heuristically (`TVDB` if a match existed, otherwise `REVIEW`) instead of restoring the real pre-ignore status, leaving stale row state attached after the ignore round-trip.
- The informational message `TVDB candidates loaded; select one...` is stored as `noteText`, not `alertText`, and is not itself counted as an alert.

## Changes
- Added explicit ignore round-trip state to `ScanRow` via `markIgnored()` / `stopIgnoring()` so the real prior media type and status are restored idempotently.
- Replaced the direct context-menu status mutation with those helpers.
- Refreshed scan metrics and filter predicates immediately after batch TVDB application.
- Aligned `À traiter` with the existing `TO_PROCESS` predicate instead of `total - ignored`, keeping badges consistent with the status filters.
- Added TVDB debug-window business traces for alert recalculation/clearing and ignore-state transitions (`ALERT`, `ROW_STATE`) including filename, status, ignored state, alert presence, source, and reason.

## Counting rules retained
- `Alertes` counts non-ignored rows with an active `alertText` or `ERROR` status.
- Ignored rows are excluded from `Alertes` and `À traiter`.
- Informational TVDB notes remain visible in the detail panel but do not enter the alert bucket.
- The `Alertes` filter and the warning metric continue to share `ScanRowTableSupport.hasAlert(...)`.

## Verification
- Added regression coverage in `ScanRowTableSupportTest` for:
  - ignore/unignore idempotence and restoration of the real prior row state;
  - preserved exclusion of ignored rows from active alerts;
  - informational TVDB notes not being alerts;
  - shared `TO_PROCESS` semantics for review / ignored / OK / TVDB rows.
- Ran:
  - `.\gradlew.bat test --tests com.episort.ui.scan.ScanRowTableSupportTest`
  - `.\gradlew.bat test`


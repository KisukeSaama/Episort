# TRI source-folder tagging

Date: 2026-08-06

## Scope

- After a non-aborted plan execution, an emptied source folder is still removed.
- When a source folder that is safe to clean up remains non-empty, its parent
  container is renamed in place from `Name` to `[TRI]Name` for manual follow-up.
- A folder already carrying `[TRI]` is not prefixed again.
- A failed or skipped source protects its folder from both deletion and tagging.
- An occupied `[TRI]Name` destination leaves the original folder untouched.
- The workspace root and paths outside the workspace are never renamed.

## UI and audit

- The exact-plan notice discloses the post-execution `[TRI]` rule before approval.
- The execution recap and run history record the number of source folders that
  were actually renamed.

## Verification

- `MediaFileMoverTest` covers a safe folder rename and destination collision.
- `ExecutionServiceTest` covers sidecar tagging, failed-source preservation,
  collision preservation, empty-folder deletion, and aborted execution.

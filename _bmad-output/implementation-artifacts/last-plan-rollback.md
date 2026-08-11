# Retained validated plan rollback

## Scope

- History exposes one visible rollback action for the selected executed plan.
- The action is enabled only while a retained inverse manifest matches that run.
- A second validation dialog lists every exact current → original path before disk mutation.
- The validation is a full-frame plan review using the same workflow stepper,
  table, banner, notice, and actions as rename-plan validation, with one inverse
  move per row and separate current/original columns.
- Confirmation transitions in place to a progress readout with the exact move in
  flight, then to a persistent restoration recap. The shell is locked only while
  the inverse moves are running.
- All validation and mutations remain inside the recorded workspace.

## Implementation notes

- `FileRollbackPlanStore` keeps up to 20 inverse manifests in the Episort application-data directory.
  Writes replace the journal atomically and retention removes only the oldest manifests.
- Every move stores file size, modification time, and a SHA-256 fingerprint sampled from at most
  three 64 KiB regions. Rollback validation rejects a size or sampled-content mismatch without
  reading the whole media file.
- Only successful moves and renames are reversible. Runs containing a successful deletion are
  deliberately not offered because the prior bytes cannot be reconstructed safely.
- `LastPlanRollbackService` preflights the complete inverse plan before moving any file: every
  current file must exist as a regular file, every original path must be free, and both paths must
  resolve inside the workspace.
- Inverse moves run in reverse order. Destination folders are removed only when empty; unrelated
  content is never deleted.
- A completed or refused rollback is appended to the audit history. A successful rollback consumes
  only its own manifest, so independent older plans remain available. Dependent plans naturally
  remain blocked until their expected current paths and fingerprints are restored.

## Verification

- `FileRollbackPlanStoreTest`: multi-plan persistence, selected-plan consumption, and bounded
  retention.
- `LastPlanRollbackServiceTest`: path restoration, empty-folder cleanup, collision and fingerprint
  refusal before mutation, multi-plan restoration, and one-shot behavior, all under JUnit temporary
  directories.
- The full-frame flow keeps a visible success or refusal recap until the user closes it.
- `LastPlanRollbackServiceTest` verifies the progress stream exposes the exact
  current/original pair before and after the move.

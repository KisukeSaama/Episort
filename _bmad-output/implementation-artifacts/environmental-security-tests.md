# Environmental security tests

Date: 2026-08-06

## Scope

- Exercise filesystem boundary behavior only inside JUnit temporary directories.
- Verify that relative traversal, symbolic links, and Windows NTFS junctions cannot
  make scanning or media mutations cross or bypass the configured workspace.
- Preserve the existing rule that a deliberately selected linked workspace root is
  canonicalized, while every linked descendant is rejected.
- Keep all existing user media and unrelated in-progress UI changes untouched.

## Threat matrix

- A symbolic link used as a move source is rejected without moving its target.
- A symbolic link used as a destination is rejected even when replacement was
  explicitly approved; the source and target stay unchanged.
- Deleting an approved file through a symbolic link is rejected without deleting
  the target.
- Recursive source-folder cleanup unlinks a contained external symbolic link but
  never follows it or deletes its target.
- Planned destinations cannot traverse either external or internal symbolic links.
- Recursive inventory scanning does not traverse a linked directory, including a
  link whose target remains inside the input folder.
- A real Windows NTFS junction and a planned child beneath it are both rejected.

## Implementation

- `WorkspaceBoundary` now examines every descendant path component before
  canonicalization.
- Ordinary symbolic links are detected with `Files.isSymbolicLink`.
- Windows junctions and other link-like filesystem entries are detected through
  `BasicFileAttributes.isOther()` with `NOFOLLOW_LINKS`.
- Attribute access failures fail closed; genuinely missing planned path segments
  remain valid candidates for the existing canonical planned-path validation.
- Because `MediaFileMover`, input selection, and the workspace explorer all route
  through `WorkspaceBoundary`, the stronger check applies consistently at the
  mutation and selection boundaries.

## Verification

- TDD red phase: the native Windows junction test failed because the junction was
  accepted by the symbolic-link-only implementation.
- TDD green phase: the targeted `WorkspaceBoundaryTest` suite passed after adding
  link-like entry detection.
- `gradlew clean build` passed: 492 tests, 0 failures, 0 errors, 11 skipped.
- The Windows junction scenario executed successfully without elevated symbolic-link
  privileges. Symbolic-link scenarios are assumption-gated and were skipped on this
  machine because its current account cannot create symbolic links; they remain active
  on environments that provide that capability.
- `git diff --check` passed.

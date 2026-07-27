# Workspace sidebar explorer

Date: 2026-07-28

## Scope

- Use the free space below the sidebar navigation to show the configured
  workspace's files and folders.
- Keep the explorer read-only and strictly bounded to the configured workspace.
- Preserve responsiveness when the workspace is hosted on a NAS.

## Implementation

- `WorkspaceDirectoryReader` lists one directory at a time, revalidating it
  through `WorkspaceBoundary` before every read.
- Direct children sort with directories first, followed by case-insensitive
  names. `.avi`, `.mp4` and `.mkv` files receive the supported-media signal.
- Symbolic links remain visible but are never classified as expandable
  directories.
- `WorkspaceExplorer` adds a localized JavaFX `TreeView` below the three
  navigation entries. Children load asynchronously only when a directory is
  expanded, and stale results are discarded when the workspace changes.
- The workspace section is keyboard-repliable. Its tree is constrained to the
  fixed sidebar viewport, uses ellipses plus full-path tooltips, suppresses
  horizontal scrolling and keeps only a compact neutral vertical scrollbar.
- The shell refreshes the tree after a completed plan changes the filesystem.
- The new anatomy, states and style classes are documented in
  `docs/design-system.md`.

## Verification

- Unit coverage uses temporary directories for ordering, media
  classification, outside-workspace refusal and symbolic-link behavior.
- Manual UI verification: load a local workspace and a network workspace;
  expand nested directories; confirm unsupported files are muted, full paths
  appear in tooltips, language changes update transient states, and no file
  operation is exposed.

## Security boundary

The explorer performs directory reads only. It exposes no rename, move,
delete, drag-and-drop or parent-navigation action. Every listing target must
resolve inside the canonical configured workspace.

# Sidebar storage usage

Date: 2026-08-04

## Scope

- Remove the workspace tree from the sidebar without changing workspace setup.
- Correct the overlong navigation marker.
- Show real storage usage at the bottom-left of the application.

## Implementation

- `Sidebar` now ends with a compact logical-volume readout instead of the
  read-only workspace explorer.
- The navigation heading uses a dedicated 1 × 14 px marker centered inside the
  same 36 px row height as the navigation actions.
- `VolumeSpaceService` resolves the `FileStore` containing the configured
  workspace and reads its total, unallocated and usable capacities. A RAID
  presented as one logical volume therefore reports the complete RAID volume.
- The read runs asynchronously so network volumes and NAS workspaces do not
  block the JavaFX application thread.
- The indicator shows percentage used, used / total, and usable capacity. It
  renders `—` and hides the progress bar when no truthful value is available.
- Typography now follows the application roles: Inter for readable percentage
  and availability phrases, JetBrains Mono for the capacity measurement and
  section caption.
- English and French labels are provided through `UiText`.

## Security boundary

- The storage service performs metadata reads only.
- It never creates, renames, moves or deletes filesystem content.

## Verification

- Unit tests cover volume invariants, localized byte formatting, honest empty
  state and confirmation that capacity comes from the containing logical
  volume rather than directory contents.

# Portable release bundles

Date: 2026-08-06

## Scope

- Produce one self-contained portable executable for Windows and one for Linux.
- Bundle Java 21, JavaFX, application dependencies, resources, and a native
  platform launcher so users do not install Java or Gradle.
- Keep the deliverables installation-free: download one file and launch it.

## Implementation

- Gradle `portableApp` invokes the Java 21 `jpackage` tool with `app-image`.
- `verifyPortableApp` requires both the native launcher and bundled runtime.
- A small native Go launcher embeds the internal app image archive, verifies its
  SHA-256, and extracts it atomically under `%LOCALAPPDATA%\Episort` on Windows
  or `${XDG_DATA_HOME:-~/.local/share}/Episort` on Linux.
- `portableArchive` produces one native executable and its `.sha256`; the ZIP or
  `tar.gz` is only an internal build payload and is never published.
- Icons reuse the existing ICO and PNG assets.
- The downloaded executable creates no files beside itself. Subsequent launches
  reuse the versioned, content-addressed runtime in user application data.
- `.github/workflows/portable-build.yml` builds and tests on native Windows and
  Linux runners for release pull requests, release branches, manual runs and
  version tags, then uploads both executables.
- A successful version-tag matrix downloads the Windows and Linux artifacts and
  publishes a GitHub Release with both executables, both SHA-256 files, and the
  matching versioned release notes.
- Linux uses an Ubuntu 22.04 x64 runner for a conservative glibc baseline and is
  documented as requiring the ordinary GTK 3 desktop libraries.
- README instructions cover extraction, direct launch, local packaging, and the
  Janus-backed TMDB integration.

## Security boundary

- No upstream TMDB key or read token is embedded in either portable image. The
  revocable Janus caller configuration is intentionally bundled for end users.
- Portable bundles use the same workspace containment, validation gates, and
  credential resolution as development runs.
- Generated images, internal archives, and launcher work files remain under the
  ignored `build/` directory.

## Verification

- Windows `clean build portableArchive` passed and produced one native `.exe`
  containing the complete `jpackage` image.
- The first native Linux run exposed an operating-system difference when a
  regular file occupied a planned folder name. `OperationPlanner` now reports
  that blocking folder conflict before canonicalizing the not-yet-created
  destination, preserving both the workspace boundary check and the expected
  conflict classification on Windows and Linux.
- The targeted `OperationPlannerTest` suite and the complete Windows
  `clean build portableArchive` cycle pass after this correction.
- The native launcher tests reject ZIP/TAR path traversal, validate Windows and
  Linux application-data paths, and preserve Linux executable permissions.
- The packaging smoke test verifies the Windows `MZ` signature, runs extraction
  against an isolated fake `%LOCALAPPDATA%`, and finds the embedded JavaFX
  launcher under that directory.
- A real single-file Windows startup produced the normal two `jpackage`
  processes from the isolated application-data runtime, created 448 entries
  there, and created no sibling beside the downloaded executable.
- The final Windows public output contains exactly the 67.73 MiB `.exe` and its
  96-byte SHA-256 file; the internal ZIP remains outside the distribution
  directory.
- Linux output is verified by the native CI matrix because `jpackage` launchers
  must be created on the target operating system.

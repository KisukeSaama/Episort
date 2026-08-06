# Portable release bundles

Date: 2026-08-06

## Scope

- Produce self-contained portable application images for Windows and Linux.
- Bundle Java 21, JavaFX, application dependencies, resources, and a native
  platform launcher so users do not install Java or Gradle.
- Keep the deliverables installation-free: extract the archive and launch.

## Implementation

- Gradle `portableApp` invokes the Java 21 `jpackage` tool with `app-image`.
- `verifyPortableApp` requires both the native launcher and bundled runtime.
- `portableArchive` produces a ZIP on Windows and a `tar.gz` on Linux. The Linux
  archive uses native `tar` so executable permissions survive extraction.
- A matching `.sha256` file is generated beside every archive and uploaded with
  it by the CI workflow.
- Icons reuse the existing ICO and PNG assets.
- Every archive includes a short `README.txt` beside the launcher with direct
  Windows/Linux launch and TVDB environment instructions.
- `.github/workflows/portable-build.yml` builds and tests on native Windows and
  Linux runners for release pull requests, release branches, manual runs and
  version tags, then uploads both archives.
- Linux uses an Ubuntu 22.04 x64 runner for a conservative glibc baseline and is
  documented as requiring the ordinary GTK 3 desktop libraries.
- README instructions cover extraction, direct launch, local packaging, and the
  external `TVDB_API_KEY` requirement.

## Security boundary

- No TVDB key is embedded in either portable image.
- Portable bundles use the same workspace containment, validation gates, and
  credential resolution as development runs.
- Generated images and archives remain under the ignored `build/` directory.

## Verification

- Windows `clean build portableArchive` passed and produced a self-contained
  ZIP containing `Episort.exe`, the application libraries, and the reduced Java
  runtime.
- The packaged `Episort.exe` remained healthy during a five-second startup smoke
  test using only the bundled image, then the test process was stopped.
- The final Windows archive contains 440 entries, including the launcher,
  portable README and runtime marker; its current size is 65.33 MiB.
- Linux output is verified by the native CI matrix because `jpackage` launchers
  must be created on the target operating system.

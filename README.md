# Episort

Episort is a Windows-first JavaFX desktop app that organizes TV series and movies for a Plex-style library. It scans a working directory, parses file names with deterministic rules, resolves titles against TMDB, and prepares a file operation plan that must be validated before anything is moved.

The pipeline is fully deterministic: the same folder always produces the same plan. No model and no guesswork you cannot inspect. Metadata requests are routed through the Janus gateway to TMDB.

## How it works

1. **Scan** — walks the input folder and groups files into likely series, likely movies, and unknowns.
2. **Analyse** — a rule-based parser extracts series, season, episode, title, year, and release noise from each file name (`SxxExx`, `NxNN`, absolute numbering, date-based episodes, folder-derived season and series).
3. **TMDB matches** — TMDB is the source of truth for canonical titles, years, and episode names, across aired, DVD, and absolute orders.
4. **Identity review** — one line per detected group, with the identity that will name its files. Correcting a group of twenty-five files costs one click, the same as correcting one.
5. **Plan review** — every source → destination is shown before anything happens. Conflicts, duplicates, and unsafe Windows paths block the run.
6. **Apply** — file operations stay inside the configured workspace, and every run is journalled so an interrupted execution can be recovered.

## Features

- Scans `.avi`, `.mp4`, and `.mkv` files.
- Handles mixed folders that may contain several series: a file name that contradicts its release folder keeps its own title, so two shows in one folder stay two groups.
- Uses TMDB search and aired, DVD, and absolute episode orders.
- Requires two validations: detected groups/patterns, then the exact source → destination plan.
- Keeps file operations inside the configured working directory.
- Manual correction at every step: reclassify a row, edit the structured fields, or pick a TMDB match by hand.

Target layout:

```text
Series Name in English/
  Season XX/
    Series Name in English - SXXEXX - Episode Title in English.original-extension
```

## Stack

- Java 25
- JavaFX 25
- Gradle
- JUnit 5
- Go 1.26 (Windows single-file release launcher only)

## Commands

```bash
./gradlew run
./gradlew test
./gradlew build
./gradlew portableArchive # Linux also requires dpkg-deb and rpmbuild
```

On Windows PowerShell:

```powershell
.\gradlew.bat run
.\gradlew.bat test
.\gradlew.bat build
```

## Portable application

Each portable build includes Java 25, JavaFX, and the complete application. Users
do not need to install Java separately.

Windows:

1. Download `Episort-0.2.0-windows-x64.exe`.
2. Double-click the downloaded executable.

Linux Debian/Ubuntu:

```bash
sudo apt install ./episort_0.2.0-1_amd64.deb
episort
```

Linux Fedora/RHEL:

```bash
sudo dnf install ./episort-0.2.0-1.x86_64.rpm
episort
```

On first launch, the embedded runtime is verified and extracted to
`%LOCALAPPDATA%\Episort` on Windows or
`${XDG_DATA_HOME:-~/.local/share}/Episort` on Linux. Later launches reuse that
private application-data copy. Nothing is written beside the downloaded
executable.

The Linux bundle targets x64 glibc-based desktop distributions and expects the
usual GTK 3 graphical libraries supplied by mainstream Ubuntu, Debian, Fedora,
and similar desktop installations.

Build the portable package for the current operating system with:

```bash
./gradlew clean build portableArchive
```

The Windows executable, or the Linux `.deb` and `.rpm` packages, are written to
`build/portable/distributions/`. Native packages must be built on Linux, so the
repository workflow builds Windows and Linux artifacts independently.

## Workspace on a server (SFTP)

The workspace can live on the machine that runs Plex instead of on a local
disk. In Settings, choose "A folder on a server (SFTP)", enter the host, port,
user and either a password or a private key file, pick the folder from the
server's own tree, and connect. Episort then scans, renames and moves the
files there directly over SFTP: nothing is copied to this computer, and a
rename on the server is instant whatever the file size.

- One server is remembered, credentials included, so reconnecting after a
  restart is one click. On Windows the secret is protected with DPAPI, tied to
  the Windows user; elsewhere it sits in the user's private configuration
  directory. Disconnect keeps the profile and closes the session.
- The server's host key is trusted on first use and recorded in `known_hosts`
  next to the settings file. A key that later differs is refused.
- Every safety rule holds unchanged: the boundary is the chosen folder on the
  server, symbolic links are never followed out of it, and the two validations
  happen before anything moves. Deleting a duplicate on a server is final,
  since no recycle bin can take it.
- The SSH account needs write access to the library. A read-only account can
  scan and plan, and the run stops at the first move.

## Opening a folder from Umbra

Episort answers `episort://` links. On Windows the portable launcher registers
the scheme for the current user every time it starts, so the "Open in
Episort" button on Umbra's storage page opens Episort positioned on that
folder, or on the files selected there. The link names a volume and the
folders below it, never an absolute path; Episort resolves it under its own
workspace root, so set the workspace to the same folder as the Umbra volume
(`/mnt/plex` by default). With no workspace, or a server that is not
connected, the settings screen opens instead and the link can be used again
once it is. A folder that is not under the workspace is reported, not guessed.

```text
episort://open?volume=Media&path=Series%2FSome+Show&file=ep1.mkv&file=ep2.mkv
```

Linux packages are installed by the system package manager, which owns
desktop integration there; the scheme is not registered by the packages yet.

## Configuration

Episort calls TMDB through the Janus gateway. The Janus client configuration is
bundled in official distributions so TMDB works without setup for end users.
Upstream TMDB credentials remain exclusively in the Janus vault.

Developers and CI can override the bundled values with process variables or a
local `.env`. To start from the committed template:

```powershell
Copy-Item .env.example .env
```

Process variables take priority over `.env`, which takes priority over the
bundled release configuration. End users do not need a TMDB or Janus account.
Janus injects TMDB credentials server-side and owns caching, retries, rate
limiting, key rotation, revocation, monitoring, and audit trails.

## Documentation

- Design system : `docs/design-system.md`
- TMDB and Janus integration: `docs/tmdb-api-integration.md`
- Portable packaging: `docs/portable-readme.txt`
- Release notes: `docs/releases/v0.2.0.md`
- Release notes: `docs/releases/v0.1.0.md`

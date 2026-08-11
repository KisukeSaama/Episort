# Episort

Episort is a Windows-first JavaFX desktop app that organizes TV series and movies for a Plex-style library. It scans a working directory, parses file names with deterministic rules, resolves titles against TMDB, and prepares a file operation plan that must be validated before anything is moved.

The pipeline is fully deterministic: the same folder always produces the same plan. No model, no network beyond TMDB, no guesswork you cannot inspect.

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

- Java 21
- JavaFX
- Gradle
- JUnit 5
- Go 1.26 (single-file release launcher only)

## Commands

```bash
./gradlew run
./gradlew test
./gradlew build
./gradlew portableArchive # also requires Go 1.26
```

On Windows PowerShell:

```powershell
.\gradlew.bat run
.\gradlew.bat test
.\gradlew.bat build
```

## Portable application

Each portable build is distributed as one executable containing Java 21,
JavaFX, and the complete application. Users do not install Java and do not keep
an application folder beside the downloaded file.

Windows:

1. Download `Episort-0.1.0-windows-x64.exe`.
2. Double-click the downloaded executable.

Linux:

```bash
chmod +x Episort-0.1.0-linux-x64
./Episort-0.1.0-linux-x64
```

On first launch, the embedded runtime is verified and extracted to
`%LOCALAPPDATA%\Episort` on Windows or
`${XDG_DATA_HOME:-~/.local/share}/Episort` on Linux. Later launches reuse that
private application-data copy. Nothing is written beside the downloaded
executable.

The Linux bundle targets x64 glibc-based desktop distributions and expects the
usual GTK 3 graphical libraries supplied by mainstream Ubuntu, Debian, Fedora,
and similar desktop installations.

Build the single-file executable for the current operating system with:

```bash
./gradlew clean build portableArchive
```

The executable and its SHA-256 checksum are written to
`build/portable/distributions/`. Native launchers must be built on their target
operating system, so the repository workflow builds the Windows and Linux
executables independently.

## Configuration

Episort calls TMDB through the Janus gateway. The Janus client configuration is
bundled in official distributions so TMDB works without setup for end users.
Upstream TMDB credentials remain exclusively in the Janus vault.

Developers and CI can override the bundled values with process variables or a
local `.env`. To start from the committed template:

```powershell
Copy-Item .env.example .env
```

```dotenv
JANUS_URL=https://janus.kisukesaama.com
JANUS_APPLICATION_ID=be061c51-1947-4ec5-9ac7-86e917168e41
JANUS_API_KEY=the-release-caller-key
```

Process variables take priority over `.env`, which takes priority over the
bundled release configuration. End users do not need a TMDB or Janus account.
Janus injects TMDB credentials server-side and owns caching, retries, rate
limiting, key rotation, revocation, monitoring, and audit trails.

## Documentation

- Design system : `docs/design-system.md`
- BMAD artifacts: `_bmad-output/`

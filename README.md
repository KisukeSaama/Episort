# Episort

Episort is a Windows-first JavaFX desktop app that organizes TV series and movies for a Plex-style library. It scans a working directory, parses file names with deterministic rules, resolves titles against TVDB, and prepares a file operation plan that must be validated before anything is moved.

The pipeline is fully deterministic: the same folder always produces the same plan. No model, no network beyond TVDB, no guesswork you cannot inspect.

## How it works

1. **Scan** — walks the input folder and groups files into likely series, likely movies, and unknowns.
2. **Analyse** — a rule-based parser extracts series, season, episode, title, year, and release noise from each file name (`SxxExx`, `NxNN`, absolute numbering, date-based episodes, folder-derived season and series).
3. **TVDB matches** — TVDB is the source of truth for canonical titles, years, and episode names, across aired, DVD, and absolute orders.
4. **Identity review** — one line per detected group, with the identity that will name its files. Correcting a group of twenty-five files costs one click, the same as correcting one.
5. **Plan review** — every source → destination is shown before anything happens. Conflicts, duplicates, and unsafe Windows paths block the run.
6. **Apply** — file operations stay inside the configured workspace, and every run is journalled so an interrupted execution can be recovered.

## Features

- Scans `.avi`, `.mp4`, and `.mkv` files.
- Handles mixed folders that may contain several series: a file name that contradicts its release folder keeps its own title, so two shows in one folder stay two groups.
- Uses TVDB search and aired, DVD, and absolute episode orders.
- Requires two validations: detected groups/patterns, then the exact source → destination plan.
- Keeps file operations inside the configured working directory.
- Manual correction at every step: reclassify a row, edit the structured fields, or pick a TVDB match by hand.

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

## Commands

```bash
./gradlew run
./gradlew test
./gradlew build
```

On Windows PowerShell:

```powershell
.\gradlew.bat run
.\gradlew.bat test
.\gradlew.bat build
```

## Portable application

Portable bundles include Java 21 and JavaFX. Users do not need to install Java.

Windows:

1. Extract `Episort-0.1.0-windows-x64.zip`.
2. Open the extracted `Episort` folder.
3. Double-click `Episort.exe`.

Linux:

```bash
tar -xzf Episort-0.1.0-linux-x64.tar.gz
./Episort/bin/Episort
```

The Linux bundle targets x64 glibc-based desktop distributions and expects the
usual GTK 3 graphical libraries supplied by mainstream Ubuntu, Debian, Fedora,
and similar desktop installations.

Build the portable archive for the current operating system with:

```bash
./gradlew clean build portableArchive
```

The archive and its SHA-256 checksum are written to
`build/portable/distributions/`. Native launchers must be built on their target
operating system, so the repository workflow builds the Windows and Linux
archives independently.

## Configuration

TVDB credentials must stay out of source control. Set `TVDB_API_KEY` in the
environment before launching Episort. For example:

```powershell
$env:TVDB_API_KEY = "your-key"
.\Episort\Episort.exe
```

```bash
TVDB_API_KEY="your-key" ./Episort/bin/Episort
```

## Documentation

- Design system : `docs/design-system.md`
- BMAD artifacts: `_bmad-output/`

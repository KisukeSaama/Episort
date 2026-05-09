# Episort

Episort is a Windows-first JavaFX desktop app for safely organizing TV series episodes from a working directory. It scans media files, detects likely groups, uses TVDB metadata, and prepares a file operation plan that must be validated before anything is moved.

## Features

- Scans `.avi`, `.mp4`, and `.mkv` files.
- Handles mixed folders that may contain several series.
- Uses TVDB search and aired, DVD, and absolute episode orders.
- Requires two validations: detected groups/patterns, then exact source -> destination plan.
- Keeps file operations inside the configured working directory.
- Includes an optional local AI assistant for pattern suggestions and explanations only.

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

## Configuration

TVDB credentials must stay out of source control. Use an environment variable or an ignored config file.

Local AI uses Qwen3 8B through an embedded llama.cpp runtime. The model is downloaded once to `%LOCALAPPDATA%\Episort\models\`. If the runtime or model is unavailable, the app remains usable without AI features.

To prepare a distribution with the embedded runtime:

```powershell
.\gradlew.bat fetchLlamaRuntime
.\gradlew.bat installDist
```

Real LLM tests are optional smoke tests:

```powershell
.\gradlew.bat test -PrunLocalLlm=true
```

## Documentation

- Design system : `docs/design-system.md`
- BMAD artifacts: `_bmad-output/`

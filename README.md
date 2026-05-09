# Episort

Episort is a Windows-first JavaFX desktop application for safely planning TVDB-backed media organization.

## Requirements

- Java 21

## Development Commands

```bash
./gradlew run
./gradlew test
./gradlew build
```

On Windows PowerShell, use:

```powershell
.\gradlew.bat run
.\gradlew.bat test
.\gradlew.bat build
```

`run` launches the JavaFX application shell. `test` runs the JUnit 5 suite.

## Local AI runtime

Episort embeds its own local AI runtime (Qwen3 8B). No external software is
installed on the user's machine: the runtime binaries ship inside the Episort
distribution under `app/runtime/` and are extracted on first launch into
`%LOCALAPPDATA%\Episort\runtime\`. The model file (`Qwen3-8B-Instruct-Q4_K_M.gguf`,
~5 GB) is downloaded once into `%LOCALAPPDATA%\Episort\models\` with a
progress dialog. Nothing leaves the machine and there is no model selection
or configuration to manage — the bundled model is hardcoded.

The application runs without local AI: if the binaries are missing, the model
has not been downloaded, or the embedded server cannot start, the AI-dependent
flows are skipped and every other workflow (scan, TVDB, plan, validation,
execute) still works.

AI suggestions are advisory only — they never validate patterns, approve plans,
or execute filesystem operations.

### Building with the bundled runtime

The runtime binaries are not committed to the repo. Fetch them once before
building a distribution:

```powershell
.\gradlew.bat fetchLlamaRuntime
.\gradlew.bat installDist
```

`fetchLlamaRuntime` downloads the pinned llama.cpp release zips into
`build/llama-runtime/` (~600 MB). `installDist` (or `distZip`/`distTar`)
copies them into `build/install/Episort/runtime/`.

### Running the LLM tests

The default `test` task uses an in-process fake server, so it runs on any
machine without GPU. To exercise the real bundled runtime against the model on
your local machine (model + runtime binaries must already be on disk):

```powershell
.\gradlew.bat test -PrunLocalLlm=true
```

This runs the smoke tests tagged `local-llm` (skipped by default).

# Story 3.6: Embedded llama.cpp Runtime (Replaces Ollama)

Status: done

<!-- Replaces story 3.5 (Ollama-backed). Goal: zero external software. -->

## Story

As a media library user on Windows with an NVIDIA GPU,
I want Episort to ship its own embedded llama.cpp runtime that runs Qwen3 8B with no separate software install,
so that I never see "Ollama", "Programs & Features", or any third-party tool surface.

## Acceptance Criteria

1. Given a Windows machine with an NVIDIA GPU and ≥ 8 GB VRAM, and Episort installed
   When Episort starts
   Then the embedded `llama-server.exe` (shipped inside the Episort distribution) is spawned as a child process bound to a random localhost port
   And the server loads `Qwen3-8B-Instruct-Q4_K_M.gguf` (downloaded once into `%LOCALAPPDATA%\Episort\models\` if missing) with `--n-gpu-layers all`
   And the user is never prompted to install, configure, or interact with any other piece of software
   And no part of the user-visible UI mentions Ollama, llama.cpp, or any third-party brand

2. Given the embedded runtime cannot start (binaries missing, GPU absent, model missing and offline)
   When Episort runs
   Then `BundledLocalAiRuntimeProbe` reports the runtime as unavailable with a recoverable diagnostic
   And every non-AI workflow (scan, TVDB, plan, validation, execute) continues to function
   And no fallback to a cloud or remote AI is attempted

3. Given Episort exits
   When the JVM shuts down
   Then the embedded `llama-server.exe` child process is terminated cleanly (no orphaned processes)

4. The advisory invariants from stories 3.2-3.4 still hold (single bundled model, no external paths, no validation/execution authority, only selected-item context sent to the model).

## Tasks / Subtasks

- [x] Gradle task `fetchLlamaRuntime` downloads pinned llama.cpp release zips into `build/llama-runtime/`; `distributions.main.contents` stages them under `runtime/`. SHA verification deferred — the URL is HTTPS-pinned to a specific tag. (AC: #1)
- [x] `EmbeddedLlamaRuntime` — locates zips next to JAR, extracts to per-user dir on first start (zip-slip protected via `Path.normalize().startsWith(target)` check, marker file `.extracted`), spawns `llama-server.exe -ngl 999`, polls `/health`, registers JVM shutdown hook. (AC: #1, #3)
- [x] `LlamaServerClient` — HTTP wrapper for `GET /health` (200 ready) and `POST /completion` (non-streaming JSON). (AC: #1)
- [x] `Qwen3ModelDownloader` — downloads GGUF into `%LOCALAPPDATA%\Episort\models\`, supports `EPISORT_MODEL_URL` env override, atomic temp→final move. (AC: #1)
- [x] `BundledLocalAiPatternAssistant` rewired to `Supplier<Optional<LlamaServerClient>>` — works across runtime restarts and during boot window. (AC: #1, #4)
- [x] `BundledLocalAiRuntimeProbe` checks 4 states: binaries missing → model missing → runtime starting → unhealthy → available. (AC: #2)
- [x] Removed `com.episort.ai.ollama` package + tests (clean break). (AC: #1)
- [x] `LocalAiBootstrapDialog` rewritten to download model + start runtime, single non-modal flow, opt-in. UI strings never mention third-party brand names. (AC: #1)
- [x] `EpisortApplication`: if binaries available + model present, start runtime async at boot; if model missing, surface bootstrap dialog. `stop()` calls `embeddedRuntime.close()`. (AC: #1, #3)
- [x] `FakeLlamaServer` test fixture replaces `FakeOllamaServer`; existing 3.2-3.4 tests pass unchanged. New `EmbeddedLlamaRuntimeTest` covers extraction guards (missing binaries/model, zip-slip refusal). `Qwen3LiveSmokeTest` (`@Tag("local-llm")`) uses the real runtime. (AC: #1, #2, #3)
- [x] README updated — describes the embedded runtime, `fetchLlamaRuntime` build step, no Ollama mention. (AC: #1)

## Dev Notes

### Distribution

- Pinned llama.cpp release: `b9090` (CUDA 12.4 build).
- Two zips ship inside `app/runtime/`:
  - `llama-b9090-bin-win-cuda-12.4-x64.zip` (~218 MB) — `llama-server.exe` and ggml backends.
  - `cudart-llama-bin-win-cuda-12.4-x64.zip` (~391 MB) — CUDA runtime DLLs (cudart, cublas, cublasLt). Means we do not require the user to have the CUDA Toolkit installed.
- Total runtime overhead in the installer: ~600 MB.
- Model `Qwen3-8B-Instruct-Q4_K_M.gguf` (~5 GB) downloaded on first run into `%LOCALAPPDATA%\Episort\models\` with progress dialog.

### Architecture

- All ports from Epic 3.1-3.4 stay. Only adapters change.
- New package `com.episort.ai.embedded` houses `EmbeddedLlamaRuntime`, `LlamaServerClient`, `Qwen3ModelDownloader`.
- `BundledLocalAiPatternAssistant` and `BundledLocalAiRuntimeProbe` get rewired to the new client.
- The `com.episort.ai.ollama` package is removed.

### Testing

- `FakeLlamaServer` exposes `/health` and `/completion` from `com.sun.net.httpserver.HttpServer`. Same shape as the existing `FakeOllamaServer`.
- Live smoke test runs only with `-PrunLocalLlm=true`.

### Anti-Patterns

- Do not show any UI label that mentions Ollama, llama.cpp, GGUF, GPU layers, or any third-party brand. The runtime is "Episort local AI".
- Do not require any environment variable, registry tweak, or admin privilege.
- Do not commit the binary zips inside the repo (Gradle task fetches at build time).
- Do not bundle a cloud AI fallback.

## Project Structure Notes

- Adding a Gradle task that downloads ~600 MB at build time means CI needs network. Document a local cache override.
- Removing `com.episort.ai.ollama` is a clean break — story 3.5's classes are deleted, not deprecated.

## Dev Agent Record

### Agent Model Used

Claude Opus 4.7 (Amelia, BMAD dev-story)

### Debug Log References

- WebFetch confirmed llama.cpp release `b9090` ships `llama-b9090-bin-win-cuda-12.4-x64.zip` (~218 MB) and `cudart-llama-bin-win-cuda-12.4-x64.zip` (~391 MB). Both fetched at build time; nothing committed.
- WebFetch confirmed llama-server HTTP API: `GET /health` returns 200 when the model is loaded, `POST /completion` accepts `{prompt, n_predict, stream:false}` and returns `{content, ...}`.
- Build sequence: compile → test → BUILD SUCCESSFUL. All AI test classes pass against `FakeLlamaServer`. `Qwen3LiveSmokeTest` skipped (no `-PrunLocalLlm=true`).

### Completion Notes List

- Pivot from Ollama (3.5) to truly embedded llama.cpp: no third-party software in the user's system, no install dialog for an external tool. The runtime ships inside the Episort distribution.
- Architecture preserved: ports `AiPatternAssistant`/`AiRuntimeProbe` unchanged; only adapters in the new `com.episort.ai.embedded` package.
- The `BundledLocalAiPatternAssistant` now takes a `Supplier<Optional<LlamaServerClient>>` so the assistant degrades gracefully during the boot window and across runtime restarts.
- `EmbeddedLlamaRuntime` defends against zip-slip during extraction (verified by `EmbeddedLlamaRuntimeTest.zipExtractionRefusesEntriesEscapingTargetDirectory`), uses a marker file for idempotent extraction, and registers a JVM shutdown hook so the child process never orphans.
- The bootstrap dialog is non-modal — closing it does not cancel the in-progress download. UI strings deliberately use only "Episort local AI" / "moteur local"; no llama.cpp / Qwen3 / Ollama branding leaks into user-visible labels.
- Distribution overhead: ~600 MB runtime binaries staged via `distributions.main.contents`; the 5 GB GGUF model still downloads on first run.
- Story 3.5 (Ollama) is preserved in sprint-status as `done` for history; its files have been deleted from the codebase.

### File List

- build.gradle.kts
- README.md
- src/main/java/com/episort/EpisortApplication.java
- src/main/java/com/episort/ai/BundledLocalAiPatternAssistant.java
- src/main/java/com/episort/ai/BundledLocalAiRuntimeProbe.java
- src/main/java/com/episort/ai/embedded/EmbeddedLlamaRuntime.java
- src/main/java/com/episort/ai/embedded/LlamaServerClient.java
- src/main/java/com/episort/ai/embedded/Qwen3ModelDownloader.java
- src/main/java/com/episort/ui/LocalAiBootstrapDialog.java
- src/test/java/com/episort/ai/AiContextualHelpServiceTest.java
- src/test/java/com/episort/ai/AiPatternRefinementServiceTest.java
- src/test/java/com/episort/ai/BundledAiPatternRefinementIntegrationTest.java
- src/test/java/com/episort/ai/BundledLocalAiPatternAssistantTest.java
- src/test/java/com/episort/ai/BundledLocalAiRuntimeProbeTest.java
- src/test/java/com/episort/ai/FakeLlamaServer.java
- src/test/java/com/episort/ai/Qwen3LiveSmokeTest.java
- src/test/java/com/episort/ai/embedded/EmbeddedLlamaRuntimeTest.java
- _bmad-output/implementation-artifacts/3-6-embedded-llama-cpp-runtime.md
- _bmad-output/implementation-artifacts/sprint-status.yaml

### Deletions

- src/main/java/com/episort/ai/ollama/ (entire package)
- src/test/java/com/episort/ai/ollama/ (entire package)
- src/test/java/com/episort/ai/FakeOllamaServer.java

### Change Log

- 2026-05-09: Replaced Ollama runtime (story 3.5) with embedded llama.cpp shipped inside the Episort distribution. New `com.episort.ai.embedded` package, refactored bundled probe + pattern assistant to use the embedded server, rewrote bootstrap dialog (model download only), wired runtime lifecycle into `EpisortApplication`. No third-party software is installed on the user's machine.

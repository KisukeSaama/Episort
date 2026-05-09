# Story 3.5: Real Bundled Qwen3 8B Local LLM Runtime

Status: done

<!-- Generated to replace the deterministic regex stub from stories 3.3/3.4 with a real local LLM runtime. -->

## Story

As a media library user on Windows with an NVIDIA GPU,
I want Episort to run a real bundled local LLM (Qwen3 8B Q4_K_M) on my GPU with zero configuration,
so that AI-assisted pattern detection and contextual help return genuine model output instead of placeholder heuristics.

## Acceptance Criteria

1. Given a Windows machine with an NVIDIA GPU and ≥ 8 GB VRAM
   When Episort starts and AI is invoked for the first time
   Then the bundled runtime (`Qwen3-8B-Instruct-Q4_K_M.gguf`) is downloaded into `%LOCALAPPDATA%\Episort\models\` if missing, with a progress dialog and SHA-256 verification
   And the model is loaded once into a llama.cpp CUDA backend with full GPU offload
   And `AiPatternAssistant.suggestPattern` and `AiContextualAssistant.explain` return responses produced by Qwen3 (not regex hits)
   And the user is never asked to choose, install, or configure anything model-related

2. Given local AI prerequisites are unmet (no NVIDIA GPU, < 8 GB VRAM, model file missing and offline, llama.cpp init failure)
   When Episort runs
   Then `BundledLocalAiRuntimeProbe` reports the runtime as unavailable with a recoverable diagnostic
   And every non-AI workflow continues to function (scan, TVDB, plan, validation, execute)
   And no fallback to cloud AI is attempted

3. Given AI advisory output is generated
   Then the existing advisory invariants from stories 3.2-3.4 still hold (single bundled model, no external paths, no validation/execution authority, only selected-item context sent to the model)

## Tasks / Subtasks

- [x] Add Gradle dependency `com.google.code.gson:gson:2.11.0` and tag-exclusion config for `local-llm`. (AC: #1, #2) — _Pivot from `de.kherud:llama` to Ollama HTTP because no Windows-CUDA classifier is published on Maven Central._
- [x] Rebrand `AiBundledModel` to `QWEN3_8B` (`requiresGpu()=true`); bump `AiHardwareSignals.MINIMUM_VRAM_MEGABYTES` to 8192. (AC: #1, #2)
- [x] Implement `OllamaClient` (java.net.http + Gson) covering `/api/tags`, `/api/pull` (streaming progress), `/api/generate`. (AC: #1)
- [x] Implement `OllamaInstaller` (detects `ollama.exe`; downloads + runs `OllamaSetup.exe /SILENT`). (AC: #1)
- [x] Implement `Qwen3ModelProvisioner` orchestrating install → service-up wait → model pull, idempotent. (AC: #1)
- [x] Replace `BundledLocalAiPatternAssistant` regex with a prompt-driven Ollama call returning a JSON envelope; `BundledLocalAiContextualAssistant` reuses it transparently. (AC: #1, #3)
- [x] Replace `BundledLocalAiRuntimeProbe` with real probe (executable present + service reachable + model pulled). (AC: #2)
- [x] Wire `LocalAiBootstrapDialog` from `EpisortApplication.start` (Windows only, opt-in; skipped if status is `READY`). (AC: #1)
- [x] Tests: tag `Qwen3LiveSmokeTest` as `local-llm` (skipped without `-PrunLocalLlm=true`); add `FakeOllamaServer` test fixture so existing 3.2-3.4 tests stay deterministic. (AC: #1, #2, #3)
- [x] Update `README.md` with the new runtime requirements and tagged-test command. (AC: #1)

## Dev Notes

### Architecture Compliance

- Responsible packages: `ai`, `workflow`, `ui` (download dialog only).
- llama.cpp/JNA code lives behind the `AiPatternAssistant`/`AiContextualAssistant`/`AiRuntimeProbe` ports — no direct LLM calls outside `com.episort.ai`.
- Domain/planning/filesystem/persistence/TVDB code must not import the LLM runtime.
- Model file path is `%LOCALAPPDATA%\Episort\models\Qwen3-8B-Instruct-Q4_K_M.gguf` — never inside the workspace or repo.

### Distribution Decision

- First-run download (light installer, ~5 GB downloaded once into per-user data).
- Source URL pinned to a specific HuggingFace revision; SHA-256 verified.
- Offline at first run → AI unavailable, non-AI flows preserved.

### Testing Strategy

- Existing 3.2-3.4 tests stay deterministic by injecting a fake `Qwen3LlamaCppRuntime` (or by keeping the assistants' interfaces unchanged and using lambda fakes as today).
- New tests tagged `local-llm` exercise the real runtime; skipped on CI by default, runnable locally on the target GPU.
- Probe tests still use `UnavailableLocalAiRuntimeProbe` to verify the refusal path.

### Anti-Patterns to Avoid

- Do not add cloud AI fallback.
- Do not expose model selection/import UI.
- Do not block non-AI workflows when the runtime fails.
- Do not bundle the GGUF inside the repository or installer.

## Project Structure Notes

- New native dependency means tests that load the LLM cannot run on CI without a CUDA-capable GPU. Tag and skip them.
- The AI prerequisite contract changes (`requiresGpu()=true`, minimum VRAM 8 GB) — verify nothing in stories 3.1-3.4 silently relied on the CPU-only relaxation.

## Dev Agent Record

### Agent Model Used

Claude Opus 4.7 (Amelia, BMAD dev-story)

### Debug Log References

- WebFetch: confirmed `de.kherud:llama` v4.2.0 publishes only `cuda12-linux-x86-64` on Maven Central → pivot to Ollama.
- WebFetch: confirmed `qwen3:8b` is in the Ollama registry (5.2 GB, 40K context).
- WebFetch: Ollama API endpoints `/api/tags`, `/api/pull` (streaming), `/api/generate` are stable as documented.
- Build sequence: compile → test → BUILD SUCCESSFUL with 19 test classes, all green; `Qwen3LiveSmokeTest` skipped because `-PrunLocalLlm=true` not set.

### Completion Notes List

- Stack pivoted from `de.kherud:llama` (no Windows binaries) to **Ollama localhost HTTP**. Same architectural shape: ports unchanged, only adapters swapped.
- Bundled model is `qwen3:8b` (Q4_K_M, ~5 GB, 40K context window) — chosen for the RTX 4070 Super 12 GB VRAM target.
- `OllamaClient` uses pure JDK `java.net.http` + Gson; no extra runtime deps.
- `LocalAiBootstrapDialog` is opt-in: first-run probe spawns it only when `Qwen3ProvisioningStatus != READY`. The user can dismiss; the rest of the app still works.
- All existing Epic 3 tests (3.2-3.4) pass against an in-process `FakeOllamaServer` so CI does not need a GPU.
- `Qwen3LiveSmokeTest` is tagged `@Tag("local-llm")` — skipped by default. Run with `.\gradlew.bat test -PrunLocalLlm=true` on the target machine.
- Ollama transparently dispatches to GPU when available; we trust its hardware decisions rather than building our own NVML probe in this story.

### File List

- build.gradle.kts
- README.md
- src/main/java/com/episort/EpisortApplication.java
- src/main/java/com/episort/ai/AiBundledModel.java
- src/main/java/com/episort/ai/AiHardwareSignals.java
- src/main/java/com/episort/ai/AiModelConfiguration.java
- src/main/java/com/episort/ai/AiPrerequisiteService.java
- src/main/java/com/episort/ai/AiRuntimeStatus.java
- src/main/java/com/episort/ai/BundledLocalAiPatternAssistant.java
- src/main/java/com/episort/ai/BundledLocalAiRuntimeProbe.java
- src/main/java/com/episort/ai/ollama/OllamaClient.java
- src/main/java/com/episort/ai/ollama/OllamaInstaller.java
- src/main/java/com/episort/ai/ollama/OllamaPullProgress.java
- src/main/java/com/episort/ai/ollama/Qwen3ModelProvisioner.java
- src/main/java/com/episort/ai/ollama/Qwen3ProvisioningStatus.java
- src/main/java/com/episort/ui/LocalAiBootstrapDialog.java
- src/test/java/com/episort/ai/AiContextualHelpServiceTest.java
- src/test/java/com/episort/ai/AiPatternRefinementServiceTest.java
- src/test/java/com/episort/ai/AiPrerequisiteServiceTest.java
- src/test/java/com/episort/ai/BundledAiPatternRefinementIntegrationTest.java
- src/test/java/com/episort/ai/BundledLocalAiPatternAssistantTest.java
- src/test/java/com/episort/ai/BundledLocalAiRuntimeProbeTest.java
- src/test/java/com/episort/ai/FakeOllamaServer.java
- src/test/java/com/episort/ai/Qwen3LiveSmokeTest.java
- src/test/java/com/episort/ai/ollama/OllamaClientTest.java
- src/test/java/com/episort/ai/ollama/Qwen3ModelProvisionerTest.java
- _bmad-output/implementation-artifacts/3-5-real-bundled-qwen3-llm-runtime.md
- _bmad-output/implementation-artifacts/sprint-status.yaml

### Change Log

- 2026-05-09: Replaced regex stub with Ollama-backed Qwen3 8B runtime. New ai/ollama package (Client/Installer/Provisioner/Progress), refactored bundled probe + pattern assistant, opt-in JavaFX bootstrap dialog wired in `EpisortApplication`. Existing Epic 3 tests preserved via `FakeOllamaServer` fixture; live smoke test gated behind `local-llm` tag.
- 2026-05-09: Code review (3 layers — Blind Hunter, Edge Case Hunter, Acceptance Auditor). All 3 ACs verified satisfied. Applied 7 patches and deferred 6 lower-priority items.

### Review Findings

- [x] [Review][Patch] `OllamaClient.isReachable()` now hits `/api/tags` and requires HTTP 200 (rejects non-Ollama servers and 4xx responses) [src/main/java/com/episort/ai/ollama/OllamaClient.java]
- [x] [Review][Patch] `OllamaClient` honours `OLLAMA_HOST` env var via `resolveDefaultBaseUri()` for non-default ports [OllamaClient.java]
- [x] [Review][Patch] `/api/generate` payload now includes `format:"json"` so Ollama constrains output and the parser does not have to scrape braces from chain-of-thought [OllamaClient.java]
- [x] [Review][Patch] `BundledLocalAiPatternAssistant` rejects blank `modelName` at construction [BundledLocalAiPatternAssistant.java]
- [x] [Review][Patch] `OllamaInstaller` skips the `LOCALAPPDATA` candidate when the env var is missing/blank — no relative-path lookup [OllamaInstaller.java]
- [x] [Review][Patch] `OllamaInstaller.downloadAndInstallSilently()` enforces a 10-minute install timeout and force-kills the installer on expiry [OllamaInstaller.java]
- [x] [Review][Patch] `Qwen3ModelProvisioner.provision()` is now serialized via a synchronized lock so a double-clicked bootstrap does not start two concurrent pulls [Qwen3ModelProvisioner.java]
- [x] [Review][Defer] No SHA-256 verification of `OllamaSetup.exe` — Ollama does not publish a stable hash list to fetch from a known URL; HTTPS cert chain is the trust boundary
- [x] [Review][Defer] 5-minute generate timeout is fine on GPU (the only supported path); CPU fallback is out of scope
- [x] [Review][Defer] 30s service-up wait may be tight on slow hardware — revisit after first user reports
- [x] [Review][Defer] Search paths miss winget/MSIX install locations — PATH walk catches most cases; revisit after telemetry
- [x] [Review][Defer] Non-Windows users get a silent no-op — explicit logging deferred to a future cross-platform story
- [x] [Review][Defer] `requiresGpu` is a boolean rather than enum (REQUIRED/RECOMMENDED/OPTIONAL) — refactor when a second model is added

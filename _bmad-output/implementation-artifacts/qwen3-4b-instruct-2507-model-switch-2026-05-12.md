# Qwen3 4B Instruct 2507 model switch

## Summary

- The embedded local-AI downloader now targets `Qwen_Qwen3-4B-Instruct-2507-Q4_K_M.gguf`.
- The default mirror is `bartowski/Qwen_Qwen3-4B-Instruct-2507-GGUF`.
- Settings/bootstrap labels and README now name `Qwen3 4B Instruct 2507 Q4_K_M`.
- Runtime comments and the downloader constant test were updated from the previous Qwen3 1.7B Q8 model.

## Verification

- `.\gradlew.bat test --tests com.episort.ai.embedded.EmbeddedLlamaRuntimeTest --tests com.episort.ai.BundledLocalAiRuntimeProbeTest`

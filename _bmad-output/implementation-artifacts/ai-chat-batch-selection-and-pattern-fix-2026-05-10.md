# AI Chat Batch Selection and Pattern Fix

Date: 2026-05-10

## Summary

Fixed scan-table AI chat behavior so batch selections are represented as a complete selected-file context instead of collapsing to the table's single active row. The chat backend is now called without prior chat history to avoid stale proposed rename state influencing the next request.

## Changes

- `ScanScreen` now rebuilds AI context from all selected `ScanRow` items and labels multi-selection targets as a batch.
- `applyPatternToGroup` now applies to the selected batch when rows are selected, falling back to the active row's group when no batch exists.
- `AiChatPanel` no longer sends prior per-row chat history to the model and clears visible messages when the target context changes.
- `AiChatPanel` now exposes a one-click rename preprompt while preserving free-form chat input.
- Applying a confirmed rename proposal now preserves the visible chat conversation while refreshing the target context.
- The proposed-name table column is editable so users can manually correct generated names before validation.
- `ScanRow` now carries a separate input-pattern field. The table pattern column displays the detected input pattern (`SxxExx`, `NxNN`, etc.) instead of the canonical output rename template.
- Selection labels and AI context strings now use proper French accents.
- `ScanScreen.applyAiRefinement` no longer applies the first AI pattern automatically during scan load; AI pattern hints stay advisory until the user asks for/apply-confirms a rename.
- `ScanRowToolbox` normalizes short AI hints such as `SxxExx` into the canonical rename template:
  `{series} - S{season}E{episode} - {title}`
- `AiChatService` English prompt now states that batch selections must account for every selected file and that the final rename pattern preserves the original extension.
- `LlamaServerClient` disables prompt-cache reuse for streaming chat requests so selected-file context cannot bleed between chat turns.

## Verification

- `.\gradlew.bat test --tests com.episort.ui.scan.ScanRowFactoryTest`
- `.\gradlew.bat test`

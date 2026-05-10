# Qwen3 1.7B model switch

## Implémentation

- Remplacement du modèle unique embarqué `QWEN3_8B` par `QWEN3_1_7B`.
- Le downloader cible maintenant `Qwen_Qwen3-1.7B-Q8_0.gguf` depuis `bartowski/Qwen_Qwen3-1.7B-GGUF`.
- Correction du 404 initial : les fichiers distants `bartowski` contiennent le préfixe `Qwen_` dans leur nom.
- Le runtime démarre avec un contexte `32768` et les prompts imposent `/no_think` / pas de blocs `<think>`.
- Le nom affiché dans les paramètres passe à `Qwen3 1.7B`.
- La documentation README mentionne Qwen3 1.7B.
- Le seuil VRAM local passe de 8192 Mo à 4096 Mo.
- Les tests de prérequis, probe runtime et downloader ont été mis à jour.

## Vérification

- `.\gradlew.bat test --tests com.episort.ai.AiPrerequisiteServiceTest --tests com.episort.ai.BundledLocalAiRuntimeProbeTest --tests com.episort.ai.embedded.EmbeddedLlamaRuntimeTest`
- `.\gradlew.bat build`

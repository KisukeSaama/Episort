# Analysis pipeline pre-TVDB

## Implementation

- Added `com.episort.analysis` model/services:
  - `AnalyzedVideoFile`, `AnalysisField`, `FieldSource`, `VideoMediaType`, `TvdbOrder`, `AnalysisStatus`.
  - `HeuristicAnalysisService` for local filename/extension/type extraction.
  - `AiResultMapper` for AI-to-domain mapping without TVDB writes.
  - `RenameProposalService` for stable application-side proposed names.
  - `AnalysisValidationService` for pre-TVDB status and status reasons.
- Updated `ScanRowFactory` to build rows from the analysis pipeline instead of carrying validation logic directly in the table mapping.
- Removed the main scan table TVDB column while keeping `Ordre`.
- Kept `Ordre` as TVDB/configuration state:
  - movie pre-TVDB: `N/A`
  - series pre-TVDB: localized `To define` / `À définir`
  - AI/heuristic episode markers remain in structured input parse, not in the order column.
- Expanded row statuses to the target short codes and surfaced status reasons through status badge tooltips.
- Updated EN/FR i18n for new statuses and order placeholders.
- Added JUnit coverage for pre-TVDB validation, order behavior, duplicate detection, AI mapper source priority and USER override protection.

## Verification

- `.\gradlew.bat test --tests com.episort.analysis.AnalysisPipelineServiceTest --tests com.episort.ui.scan.ScanRowFactoryTest`
- `.\gradlew.bat test`

# UI table selection/editing polish - 2026-05-12

## Scope

- Restored scan table multi-selection synchronization around `ScanRow.selected`.
- Preserved Ctrl-click toggle, Shift-click range selection, checkbox toggles, and ignored-row exclusion.
- Allowed editable table cells to receive double-click edit gestures again.
- Restyled the final validation action as the orange `Validate plan` / `Valider le plan` button.
- Moved `Resolve now` / `Résoudre maintenant` above the right detail panel.
- Reused the AI chat focus glow for search wrappers and recolored the TVDB search loader orange.
- Added shared `.episort-search-box` / `.episort-search-field` classes and applied them to the top search and TVDB manual-match search fields.
- Changed checkbox-cell clicks to toggle rows additively without clearing earlier checked rows; Shift-click range still selects visible ranges and ignored rows remain excluded.
- Forced editable text cells to clear the active table edit after Enter/focus-loss commit or Escape cancel so they return to normal table rendering.
- Separated JavaFX row focus/visual selection from the business checkbox state: primary row clicks no longer toggle `ScanRow.selected`, while checkbox-cell clicks still toggle additively and keep the selected-file counter/AI context driven by checkbox state.
- Made scan-table editable columns explicit: proposed name, role token columns, and order stay editable; selection/original/arrow/extension/type/confidence/status stay non-editable.
- Centralized search icon/clear-button styling through `.episort-search-icon` and `.episort-search-clear`, and kept focused search wrappers orange even while hovered.

## Verification

- Ran `.\gradlew.bat test --tests com.episort.ui.scan.ScanRowTableSupportTest --tests com.episort.ui.scan.ScanRowFactoryTest` with Java 21.
- Ran `.\gradlew.bat test --tests com.episort.ui.scan.ScanRowTableSupportTest` with Java 21.
- Ran `.\gradlew.bat test` with Java 21.

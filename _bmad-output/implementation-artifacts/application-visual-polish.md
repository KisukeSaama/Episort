# Application visual polish

Date: 2026-08-04

## Scope

- Preserve every screen, control, label, workflow and interaction.
- Keep the incumbent dark workstation identity and single orange accent.
- Remove small visual inconsistencies against the documented type and spacing scales.

## Implementation

- Mirrored Reanalyze and Reset as ghost actions immediately left of Load in
  the scan top bar; the original Analysis menu entries and shared state remain.

- Normalized status pills, row statuses, section headings and menu accelerator hints to the shared 10 px caption step.
- Returned the main screen rhythm to the documented 14 px spacing token.
- Replaced the identity-card one-off 11 px vertical padding with the existing 10 px spacing token.
- Preserved the in-progress section-heading refinement and its orange keyline treatment.
- Replaced terminal-like typography on interface labels with Inter: section
  headings, card titles, table headers, row statuses and field captions.
- Reduced repeated 700/800 weights to 600/700; 800 is now reserved for the
  Episort wordmark, while monospace remains limited to genuine machine values.
- Moved Scan and History searches out of the global top bar and into each
  table's filter row, with scope-specific bilingual placeholders.
- Changed the `Charger` / `Load` menu button to an outline-only treatment in
  every interactive state; its border and chevron retain the orange accent,
  its label is white, and the interior stays transparent.
- Unified primary row clicks across read-only and editable scan cells: moving
  focus to another row now clears the previous batch checkboxes before JavaFX
  continues with the requested cell edit.
- Enforced a single active scan-row context menu: a new right-click closes the
  previous popup before opening the menu for the newly targeted row.

## Verification

- Manually verify that clicking an editable scan cell moves the orange focus,
  clears prior row checkboxes, and still permits the cell to enter edit mode.
- Manually verify that repeated right-clicks on scan rows replace the existing
  context menu instead of stacking multiple popups.
- Context-menu fix verification: `compileJava` completed during `gradlew test`;
  the run then stopped at `processResources` because Gradle could not clean
  stale locked outputs. No resource or process was removed automatically.

- `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew test` — passed.
- `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew build` — passed.
- Impeccable detector — no structural findings; it flags the incumbent Inter
  family, which is deliberately preserved because changing typography would
  alter the documented visual foundation and exceed this polish scope.
- Manual inspection remains recommended for Scan, History, Settings, About and
  plan review at the 1180 × 760 minimum window size; no graphical display was
  available in the validation environment.

# Application visual polish

Date: 2026-08-04

## Scope

- Preserve every screen, control, label, workflow and interaction.
- Keep the incumbent dark workstation identity and single orange accent.
- Remove small visual inconsistencies against the documented type and spacing scales.

## Implementation

- Normalized status pills, row statuses, section headings and menu accelerator hints to the shared 10 px caption step.
- Returned the main screen rhythm to the documented 14 px spacing token.
- Replaced the identity-card one-off 11 px vertical padding with the existing 10 px spacing token.
- Preserved the in-progress section-heading refinement and its orange keyline treatment.
- Replaced terminal-like typography on interface labels with Inter: section
  headings, card titles, table headers, row statuses and field captions.
- Reduced repeated 700/800 weights to 600/700; 800 is now reserved for the
  Episort wordmark, while monospace remains limited to genuine machine values.

## Verification

- `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew test` — passed.
- `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew build` — passed.
- Impeccable detector — no structural findings; it flags the incumbent Inter
  family, which is deliberately preserved because changing typography would
  alter the documented visual foundation and exceed this polish scope.
- Manual inspection remains recommended for Scan, History, Settings, About and
  plan review at the 1180 × 760 minimum window size; no graphical display was
  available in the validation environment.

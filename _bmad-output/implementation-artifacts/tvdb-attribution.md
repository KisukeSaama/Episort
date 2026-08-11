# TheTVDB attribution

Date: 2026-08-06

## Scope

- Display TheTVDB attribution in the application settings.
- Use the official logo supplied for dark backgrounds.
- Include the provider's recommended attribution message and a direct link.
- Keep the attribution available in French and English.

## Implementation

- Added the official `logo1.png` brand asset from TheTVDB's API information page.
- Added a compact attribution row to the existing TVDB settings section.
- The link opens `https://thetvdb.com/subscribe` through JavaFX host services and
  remains keyboard focusable with a visible focus state.
- Extended `UiText`, both resource bundles, `app.css`, and the design system.

## Verification

- `gradlew test --rerun-tasks` — 484 tests, 0 failures (4 environment-gated tests skipped).
- `gradlew build` — passed.
- `git diff --check` — passed.
- Manual acceptance is recorded by the user for the current application workflow.

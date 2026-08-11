# TMDB attribution

Date: 2026-08-10

## Scope

- Replace the former TheTVDB credit in Settings with TMDB attribution.
- Follow TMDB's official logo, notice, naming, and linking requirements.
- Keep the attribution available in French and English.

## Implementation

- Settings displays the bundled approved TMDB logo on the existing dark surface.
- The English notice uses TMDB's required wording: “This product uses the TMDB
  API but is not endorsed or certified by TMDB.”
- The French bundle carries the equivalent disclaimer.
- The keyboard-focusable attribution link opens `https://www.themoviedb.org`,
  as required by TMDB's brand guidance.
- Attribution tests cover the official domain, bundled logo, and required notice.

## Verification

- `gradlew test --tests com.episort.ui.settings.SettingsPaneAttributionTest
  --tests com.episort.ui.UiTextTest` — passed.
- `gradlew build` — passed.
- Manual UI verification remains: open Settings in French and English, confirm
  the TMDB logo and disclaimer are legible, then activate the link.

## Reference

- <https://www.themoviedb.org/about/logos-attribution>
- <https://developer.themoviedb.org/docs/faq>

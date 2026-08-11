# TMDB provider migration

Date: 2026-08-10

## Scope

- Replace TheTVDB with The Movie Database (TMDB) throughout Episort.
- Preserve series/movie matching, manual identity selection, aired/DVD/absolute
  ordering, caching, diagnostics, and both filesystem validation gates.
- Keep API credentials outside source control and comply with TMDB attribution.

## Implementation

- Renamed the provider domain from `tvdb`/`Tvdb*` to `tmdb`/`Tmdb*` across the
  analysis, matching, workflow, UI, persistence, resources, and test layers.
- Replaced TVDB v4 login and payload DTOs with TMDB v3 endpoints and DTOs:
  `/search/tv`, `/search/movie`, `/tv/{id}`, `/movie/{id}`, TV seasons, episode
  groups, and `/authentication`.
- Added application authentication through `TMDB_API_READ_ACCESS_TOKEN`
  (preferred Bearer header) with `TMDB_API_KEY` query authentication as fallback.
- Added local `.env` loading and a committed `.env.example` matching the Janus
  caller configuration intentionally bundled in official Episort releases.
  Process environment values take priority, keeping CI secret injection intact;
  a missing `.env` is valid and does not affect builds or tests.
- Loads aired episodes per TMDB season. DVD and absolute orders use TMDB episode
  groups of types 3 and 2; stable TMDB episode IDs drive cross-order remapping.
  Missing episode groups remain unavailable instead of being fabricated.
- Preserved disk caching and bounded transport retries. Physical requests are
  serialized and respect provider `Retry-After` responses.
- Replaced provider-specific filenames for the cache and optional DPAPI-protected
  credential store. Secret redaction now recognizes TMDB read access tokens.
- Migrated all bilingual labels, CSS class names, status codes, diagnostic names,
  manual matching controls, and provider references to TMDB.
- Bundled an approved TMDB logo and added the required endorsement disclaimer to
  Settings and About, with a direct link to `https://www.themoviedb.org`.
- Replaced the obsolete provider investigation with `docs/tmdb-api-integration.md`
  and updated the README, portable instructions, product context, repository
  guidelines, and design system.

## Security

- No real TMDB credential was written to the workspace or test fixtures.
- The credential shown in the user-provided screenshot was treated as disclosed
  and was not used for live requests.
- API-key query parameters and Authorization headers are excluded from request
  traces; application errors omit response bodies and secrets.
- Existing media-operation confirmation gates and workspace boundaries are
  unchanged.

## Verification

- `gradlew test --rerun-tasks` — 490 tests, 0 failures, 0 errors, 11 skipped
  environment-gated tests.
- `gradlew build` — passed.
- `git diff --check` — passed.
- Workspace scan for the disclosed key and token prefix — no match.

Manual UI verification remains: start Episort with a newly rotated TMDB read
access token, confirm the startup status, search one TV series and one movie,
inspect an aired order and an available episode group, and verify the Settings
and About attribution in both languages.

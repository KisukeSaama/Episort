# Janus TMDB gateway migration

Date: 2026-08-11

## Scope

- Route all Episort TMDB API v3 traffic through the registered Janus `tmdb` slug.
- Keep upstream TMDB credentials exclusively in Janus while bundling the Janus
  caller configuration required by the public desktop release.

## Implementation

- Added `JanusConfiguration` and a provider with priority: process environment,
  local `.env`, bundled release configuration (URL, application ID and caller
  key).
- Added `X-Janus-Application-Id` and `X-Janus-Api-Key` centrally to every TMDB
  request; removed TMDB Authorization headers and `api_key` query parameters.
- Routed validation through Janus `/3/authentication` with a 40-second timeout.
- Removed the active local TMDB credential vault, response cache, retry scheduler,
  and rate-limit guard because Janus owns those responsibilities.
- Request traces retain Janus correlation IDs and gateway cache-hit state for
  operator diagnostics without logging the caller key.
- Removed the local-cache controls and cache path from Settings/About.
- Updated portable and developer documentation for zero-configuration Janus
  access in official builds and optional local/CI overrides.

## Contract revision (2026-08-11)

- Updated the gateway prefix from `/kisukesaama/gateway` to `/gateway`.
- The Episort operator explicitly accepts distributing the Janus caller key and
  manages its restrictions, monitoring, rotation and revocation in Janus.
- The complete caller configuration is embedded in official builds; portable
  users do not provision credentials.
- The upstream TMDB key/read token remains in the Janus vault and is never
  distributed.

## Verification

- Unit tests assert Janus routing headers and absence of upstream credentials.
- `gradlew clean build portableArchive --no-daemon` succeeds.
- 472 tests executed: 0 failures, 0 errors, 10 skipped.
- The bundled caller configuration returns HTTP 200 from the Janus TMDB
  authentication route.
- The Windows portable ZIP contains the complete Janus configuration in the
  application JAR and contains no `.env` file.

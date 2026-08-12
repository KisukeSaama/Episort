# TMDB through Janus

Episort uses TMDB API v3 through the Janus gateway for series, movies, seasons,
episodes, posters, and alternative episode orders.

## Client authentication

The desktop client sends every metadata request to:

`https://janus.kisukesaama.com/gateway/tmdb-v3/...`

The release caller configuration is bundled in `janus-client.properties` so an
installed application works without user configuration:

- `JANUS_URL`
- `JANUS_APPLICATION_ID`
- `JANUS_API_KEY`

The Janus caller key is intentionally distributed with Episort. Its exposure is
accepted: access restrictions, monitoring, rotation and revocation are managed
by the Episort operator in Janus. It is distinct from the upstream TMDB
credential, which remains exclusively in the Janus vault and must never enter
the desktop application.

Process variables override `.env`, and `.env` overrides the bundled values.
These overrides are intended for development, CI and key rotation testing; end
users do not need to create a `.env` file or a TMDB account.

Episort adds `X-Janus-Application-Id` and `X-Janus-Api-Key` in one HTTP client.
It never sends an upstream `Authorization` header, cookie, TMDB API key, or TMDB
read access token.

## Responsibilities

Janus owns upstream secret storage, response caching, retries/backoff, circuit
breaking, rate limiting, OAuth token renewal, and audit correlation. Episort does
not duplicate those layers. Its request timeout is 40 seconds, above Janus's
30-second upstream wait.

The Janus TMDB connection already targets TMDB API v3, so forwarded paths are
`/search/tv`, `/search/movie`, `/tv/{id}`, `/movie/{id}`, seasons, episode groups,
and `/authentication`. TMDB episode
IDs remain stable identities for aired/DVD/absolute remapping.

## Diagnostics and attribution

Janus problem responses are surfaced without logging the caller key or response
body. Request diagnostics record `X-Janus-Correlation-Id` and gateway cache state.

Settings and About display the approved TMDB logo and required notice:

> This product uses the TMDB API but is not endorsed or certified by TMDB.

The attribution links to <https://www.themoviedb.org>.

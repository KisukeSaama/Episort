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

## Request volume

A full media library reaches the batch matcher in one go, so the cost per
folder is kept down first and the remaining requests are then sent several at a
time.

- **Seasons travel together.** `tv/{id}` is called with
  `append_to_response=episode_groups`, then once more with up to twenty
  `season/{n}` sub-resources, which is the TMDB ceiling. A show costs two
  requests whatever its length. Any season the gateway does not append is
  fetched on its own, so a change on that side costs speed and never episodes.
- **A film costs no detail request.** `search/movie` already returns the title
  and release date, which is everything a movie proposal reads.
- **One index per folder.** A group the scan reads as a series only queries
  `search/tv`, and falls back to `search/movie` when that comes back empty.
- **Identical work happens once per run.** Groups whose titles normalize the
  same share one search, and groups landing on the same show share one episode
  load, even when they are resolved concurrently.

## Pacing

`TmdbRequestPacer` holds one application-wide budget: at most six requests in
flight, departures spaced by an interval derived from
`X-Janus-RateLimit-Remaining` and `X-Janus-RateLimit-Reset`, which spreads what
is left of the quota over what is left of the window. With a comfortable quota
the interval stays at its 20 ms floor and nothing is slowed down.

This is not a second retry layer, which stays with Janus. The only status the
pacer acts on is 429: the caller quota being enforced, which the gateway
contract asks the caller to honour by waiting out `Retry-After`. Replaying is
safe because every TMDB call Episort makes is a GET.

## Diagnostics and attribution

Janus problem responses are surfaced without logging the caller key or response
body. Request diagnostics record `X-Janus-Correlation-Id` and gateway cache state.

Settings and About display the approved TMDB logo and required notice:

> This product uses the TMDB API but is not endorsed or certified by TMDB.

The attribution links to <https://www.themoviedb.org>.

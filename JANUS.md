# Janus gateway

This project calls third-party APIs through Janus, which holds each API's own secret and adds it on
the way out. Never hold, request, or hardcode an upstream API secret here.

## Environment

    JANUS_URL=https://janus.kisukesaama.com
    JANUS_APPLICATION_ID=be061c51-1947-4ec5-9ac7-86e917168e41   # this service (Episort); not a secret
    JANUS_API_KEY=...   # caller credential embedded in official Episort releases

For Episort releases, the Janus caller key is intentionally bundled so end
users need no Janus or TMDB account. The operator accepts that this caller
credential is publicly extractable and manages its scope, monitoring, rotation
and revocation in Janus. This exception never applies to TMDB credentials or
other upstream API secrets, which remain exclusively in the Janus vault.

## Calling

Send the request you would have sent to the API, with its address replaced by
`$JANUS_URL/gateway/<slug>` and two headers added:

    X-Janus-Application-Id: $JANUS_APPLICATION_ID
    X-Janus-Api-Key: $JANUS_API_KEY

- The path after the slug is forwarded as is: `/gateway/spotify/v1/me` reaches the API at `/v1/me`.
- Method, query, body and response are unchanged. No SDK: use the stock HTTP client.
- Never send `Authorization` or cookies (Janus strips them), and never the API's own key.
- Body limit 10 MiB. Janus waits 30 s upstream, so set the client timeout above 35 s.
- One client per service, both headers set there and never at a call site. Do not retry POST or
  PATCH; Janus does not either.

## Already handled — do not build it

    response cache    Janus reuses upstream responses; `X-Janus-Cache` reports HIT, MISS, STALE…
    retries, backoff  GET, HEAD, PUT, DELETE are retried; a failing API is paused for everyone
    rate limiting     per-caller quota, answered as 429 with `Retry-After`
    secret storage    the API's secret lives in the vault, never in this project
    OAuth2 tokens     client-credentials tokens are fetched, cached and renewed by Janus
    audit trail       every call is recorded with its correlation id

So: no cache layer, no retry or backoff wrapper, no circuit breaker, no token store, no entry in
`.env` for the API's own credentials. Read the response headers instead of reimplementing any of it.

## APIs this service may call

- TMDB — `/gateway/tmdb/…`

Any path and any method under a slug above is forwarded. What the API itself allows for the secret
Janus presents is the only limit; an API at a slug not listed is not reachable at all.

## Errors

`Content-Type: application/problem+json` means Janus refused and its `detail` says why; any other
media type means the API itself answered.

    400  dot segment, // or encoded separator in the path
    401  headers missing, malformed, or wrong
    403  this service is not connected to that API, or the connection is paused
    404  no API at that slug, or its record is disabled
    405  a method the gateway does not forward
    413  body over the limit
    429  a quota was reached; honour Retry-After
    502  the API failed, or its address is no longer permitted

Log `X-Janus-Correlation-Id`, present on every response, beside your own errors. Also returned:
`X-Janus-Cache`, `X-Janus-RateLimit-Limit/-Remaining/-Reset`, `X-Janus-Upstream-Attempts`,
and `Retry-After` on a 429.

## If the API you need is not listed

Stop and ask the operator to register it. Do not call the API directly, and never ask anyone for its
key. In the Janus console at https://janus.kisukesaama.com, two records are needed:

1. **Connections → Register an API**: its name and base address, e.g. `https://api.spotify.com` —
   the gateway slug is derived from the name — then how that API expects its secret (bearer, custom
   header, query parameter, basic, OAuth2 client credentials, or nothing at all for an open API) and
   its value, which goes to the vault and not into this repository.
2. **Registry → Applications**: on `Episort`, add the new API under
   **Subscribed APIs**. Registering an API does not authorise any caller; without that subscription
   the gateway answers 403.

`JANUS_APPLICATION_ID` is on that service's page. `JANUS_API_KEY` appears **once**, on the screen
that issues it: a lost key is rotated from the connection or from the service, and the previous one
stops working immediately, tokens included.

Then add the new slug to this file.

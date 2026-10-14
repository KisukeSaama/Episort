# Janus gateway

This project calls third-party APIs through Janus, which holds each API's own secret and adds it on
the way out. Never hold, request, or hardcode an API secret here.

Written 2026-10-14, and true that day. This file goes stale rather than wrong: an API listed below that is
refused with 403 `grant_missing` lost its grant since, and the fix is a fresh copy of this file — ask
the operator for one, or fetch it yourself with the `get_janus_md` MCP tool below. Do not work around
the refusal, and do not edit the list by hand.

## Environment

    JANUS_URL=https://janus.kisukesaama.com
    JANUS_APPLICATION_ID=b446464a-fcfe-46fb-928a-8275a9543811   # this service (Episort); not a secret
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
    two identities    an API's own token or a connected person's; say which with X-Janus-Identity
    audit trail       every call is recorded with its correlation id

So: no cache layer, no retry or backoff wrapper, no circuit breaker, no token store, no entry in
`.env` for the API's own credentials. Read the response headers instead of reimplementing any of it.

## APIs this service may call

- **Fankai Metadata**: `/gateway/fankai-metadata/…` (only GET, HEAD)
- **TMDB v3**: `/gateway/tmdb-v3/…`

Unless a line above narrows it, any path and any method under a slug is forwarded, and what the API
itself allows for the secret Janus presents is the only limit. A line that names a path or a set of
methods is a ceiling this service was given: anything outside it is refused with 403 by Janus, before
the API is called. An API at a slug not listed is not reachable at all.

## Errors

`Content-Type: application/problem+json` means Janus refused and its `detail` says why; any other
media type means the API itself answered.

    400  dot segment, // or encoded separator in the path; a GraphQL document Janus cannot read
         (graphql_invalid) or one deeper than the API accepts (graphql_too_complex)
    401  headers missing, malformed, or wrong
    403  not connected to that API, connection paused, or a path, method, GraphQL operation or root
         field outside what it was given
    404  no API at that slug, or its record is disabled
    405  a method the gateway does not forward, or a GraphQL mutation sent as GET
    413  body over the limit
    429  a quota was reached, or too many open subscriptions (stream_limit); honour Retry-After
    502  the API failed, or its address is no longer permitted

Log `X-Janus-Correlation-Id`, present on every response, beside your own errors. Also returned:
`X-Janus-Cache`, `X-Janus-Identity`, `X-Janus-RateLimit-Limit/-Remaining/-Reset`,
`X-Janus-Upstream-Attempts`, and `Retry-After` on a 429.

## Two identities, where an API has both

An API marked **app and account** above answers both for this service and for the person who
connected their account. Nothing in a URL says which an endpoint wants, so **state it**: send

    X-Janus-Identity: app        # the API's own data: a catalogue, a search, a public resource
    X-Janus-Identity: account    # data belonging to the person who connected: their library, their profile

on every call to those APIs, decided from what the endpoint returns rather than guessed at call time.
It never reaches the API, and `X-Janus-Identity` on the response repeats what was used.

Leaving it off is not an error: Janus presents the application, and on a refusal tries the account and
remembers the answer. But it costs an extra round trip against the API's quota, it makes the first
call to a new endpoint behave differently from every call after it, and it does not happen at all
where a second attempt could repeat a write: a `POST` refused with 403 is returned as it came.
Send the header.

A grant may refuse the account identity, in which case `X-Janus-Identity: account` is answered 403 with
code `identity_not_granted` and every call goes as the service. That is an operator's decision, not a
fault to work around: ask them, do not retry as `app` and call it equivalent.

## If the API you need is not listed

Stop. Do not call the API directly, and never ask anyone for its key.

If you hold Janus's own MCP tools — Janus serves one at `https://janus.kisukesaama.com/mcp`, and a connected assistant has
`list_apis`, `create_api`, `create_grant`, `get_janus_md` and the rest — you may do it yourself once
the operator agrees, and no secret passes through you: they set the credential in the console. You act
as the account that authorised you, with its role, so an ordinary account cannot register an API.
Register the API, grant it to this service, then fetch this file again.

Without those tools, ask the operator. In the Janus console at https://janus.kisukesaama.com, two records are needed:

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

Then fetch this file again, with the new slug on it.

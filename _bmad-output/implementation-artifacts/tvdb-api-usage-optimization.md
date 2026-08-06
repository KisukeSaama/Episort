# TVDB API usage optimization

Date: 2026-08-06

## Scope

- Bound and space every physical TVDB request made by an Episort process.
- Replace immediate transient retries with provider-aware cooldowns and bounded backoff.
- Remove translation request fan-out from text search results.
- Fetch and cache episode orders independently, only when the selected workflow needs them.
- Follow TVDB episode pagination and coalesce identical concurrent cache misses.

## Implementation

- Added a process-wide `TvdbRequestScheduler` shared by the operational client and startup
  connection test. It allows one in-flight request, spaces dispatches by 500 ms, and caps the
  rolling physical rate at 30 requests per minute.
- HTTP 429 responses honour `Retry-After` when present and otherwise start at a 60-second
  jittered cooldown. Network, timeout, and 5xx retries use bounded exponential backoff. A 401
  still performs at most one token refresh.
- Text search now uses overviews and translations included in `/search`; it no longer performs
  up to two translation requests for every returned candidate. Exact ID lookup may still fetch
  translations because the user explicitly selected one record.
- `TvdbClient` now accepts a requested `TvdbEpisodeOrder`. Aired, DVD, and absolute payloads
  have separate cache keys. Non-aired results are merged with the cached aired payload so
  cross-order episode remapping keeps working.
- Episode retrieval follows TVDB `links.next` pages, with a defensive page ceiling.
- Empty searches are cached for six hours; positive searches remain seven days. Concurrent
  requests for the same cache key share one upstream lookup.

## Verification

- `gradlew test` — passed.
- `gradlew build` — passed.
- `git diff --check` — passed.
- Added coverage for request spacing, global cooldown, rolling-window capacity, search
  translation fan-out, lazy episode orders, pagination, per-order caching, and concurrent
  request coalescing.

## Deferred architecture

- A shared Episort caching proxy and `/updates` ingestion require hosted infrastructure and a
  confirmed TVDB licensing model, so they are not part of this desktop-client change.
- Stale-on-error responses were not enabled silently because rename proposals must clearly
  expose stale provider data in the UI before it can be considered safe.

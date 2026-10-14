# Fankai catalog

Fankai publishes fan edits of long anime (Kaï, Henshū, Yabai, Fan-Cut…). Plex
reads them through the Fankai metadata agent (`https://metadata.fankai.fr/plex`),
which matches a library folder by the kai title and each file by Fankai's own
season and episode numbers. Episort offers those kai next to TMDB series, the
same way FanKarr reads them, so the plan names the files the way that agent
expects:

```text
Naruto Shippuden Yabai [Kaï]/
  Season 02/
    Naruto Shippuden Yabai - S02E08 - Rituel.mkv
```

The series folder of a kai is tagged `[Kaï]` so it stands apart from the show
it was cut from; the files keep the Fankai title. Like every generated path,
the title loses its accents (`Black Lagoon Henshu [Kaï]`); the tag alone keeps
its diaeresis. A later scan reads that folder back as the kai title, since the
TMDB query drops bracketed tags. Match lists show the title with a trailing
"Kaï" (`Naruto Shippuden Yabai Kaï`), unless it already ends that way
(`Black Clover Kaï`).

## Source

`HttpFankaiClient` reads the public API at `https://metadata.fankai.fr`:

- `/series`: the catalog (title, alternate titles, TMDB id, poster, plot);
- `/series/{id}/seasons`, then `/seasons/{id}/episodes`: a kai's episodes.

The API needs no key, yet it is reached through Janus like TMDB, at
`$JANUS_URL/gateway/fankai-metadata/…` with the Episort caller headers
(`JanusConfiguration.fankaiBaseUri()`). Janus caches the answers for an hour,
retries, and pauses the API for every caller when it fails, so the client keeps
no cache or backoff of its own. A refusal reports Janus's correlation id.

In Janus, the `fankai-metadata` API (authentication `NONE`) is granted to the
Episort application for `GET` and `HEAD` only. Should that grant go, the
gateway answers 403 and searches fall back to TMDB alone.

## Matching

`FankaiTmdbClient` wraps the TMDB client:

- a series search also returns the kai whose title resembles the query
  (scorer ≥ 0.75), ahead of TMDB's series. An exact kai title wins the batch
  match automatically; a plain show title still resolves to TMDB, with its
  kai offered as alternatives in the manual search;
- a search by TMDB id also returns the kai built on that series;
- a kai's alternate titles are its Plex and show titles. Its original title
  names the source show ("BLACK LAGOON") and is left out: as an alias it tied
  the kai with TMDB's series, and a plain "Black Lagoon" folder lost its
  automatic match;
- a kai identity has a `fankai-{id}` id, never numeric, so no TMDB page link is
  offered for it. Loading it returns Fankai's episodes as a single order.

Fankai keeps counting episodes across seasons (season 2 may start at
episode 8); that numbering is kept as is. Release files number episodes from
the first one onwards ("Naruto Shippuden Yabaï 08"), so each regular episode
also carries its rank across seasons as absolute number. Season 0 holds
specials.

The catalog is additive: when it is unreachable, searches return what TMDB
returned, and loading a kai's episodes fails as a recoverable TMDB error.

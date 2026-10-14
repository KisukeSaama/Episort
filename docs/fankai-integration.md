# Fankai catalog

Fankai publishes fan edits of long anime (Kaï, Henshū, Yabai, Fan-Cut…). Plex
reads them through the Fankai metadata agent (`https://metadata.fankai.fr/plex`),
which matches a library folder by the kai title and each file by Fankai's own
season and episode numbers. Episort offers those kai next to TMDB series, the
same way FanKarr reads them, so the plan names the files the way that agent
expects:

```text
Naruto Shippuden Yabai/
  Season 02/
    Naruto Shippuden Yabai - S02E08 - Rituel.mkv
```

## Source

`HttpFankaiClient` reads the public, unauthenticated API at
`https://metadata.fankai.fr`:

- `/series`: the catalog (title, alternate titles, TMDB id, poster, plot);
- `/series/{id}/seasons`, then `/seasons/{id}/episodes`: a kai's episodes.

No key is involved, so these requests do not go through Janus. Answers are kept
in memory for an hour. When the catalog cannot be reached, the failure is
replayed for five minutes instead of every scanned group waiting on its own
timeout.

## Matching

`FankaiTmdbClient` wraps the TMDB client:

- a series search also returns the kai whose title resembles the query
  (scorer ≥ 0.75), ahead of TMDB's series. An exact kai title wins the batch
  match automatically; a plain show title still resolves to TMDB, with its
  kai offered as alternatives in the manual search;
- a search by TMDB id also returns the kai built on that series;
- a kai identity has a `fankai-{id}` id, never numeric, so no TMDB page link is
  offered for it. Loading it returns Fankai's episodes as a single order.

Fankai keeps counting episodes across seasons (season 2 may start at
episode 8); that numbering is kept as is. Release files number episodes from
the first one onwards ("Naruto Shippuden Yabaï 08"), so each regular episode
also carries its rank across seasons as absolute number. Season 0 holds
specials.

The catalog is additive: when it is unreachable, searches return what TMDB
returned, and loading a kai's episodes fails as a recoverable TMDB error.

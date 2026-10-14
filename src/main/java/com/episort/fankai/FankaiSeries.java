package com.episort.fankai;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One kai listed by the Fankai catalog. */
public record FankaiSeries(
        int id,
        String title,
        List<String> alternateTitles,
        Optional<String> tmdbId,
        Optional<String> posterUrl,
        Optional<String> plot,
        Optional<Integer> year,
        Optional<String> country) {
    public FankaiSeries {
        title = Objects.requireNonNull(title, "title").trim();
        if (title.isEmpty()) throw new IllegalArgumentException("title is required");
        alternateTitles = alternateTitles == null ? List.of() : List.copyOf(alternateTitles);
        tmdbId = tmdbId == null ? Optional.empty() : tmdbId;
        posterUrl = posterUrl == null ? Optional.empty() : posterUrl;
        plot = plot == null ? Optional.empty() : plot;
        year = year == null ? Optional.empty() : year;
        country = country == null ? Optional.empty() : country;
    }
}

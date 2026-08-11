package com.episort.ui.scan;

import com.episort.tmdb.TmdbEpisode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Selects a consecutive TMDB episode range from a user-chosen first episode. */
final class TmdbEpisodeSequence {
    private TmdbEpisodeSequence() {
    }

    static List<TmdbEpisode> startingAt(List<TmdbEpisode> episodes, String firstEpisodeId, int count) {
        Objects.requireNonNull(episodes, "episodes");
        if (firstEpisodeId == null || firstEpisodeId.isBlank() || count <= 0) {
            return List.of();
        }
        List<TmdbEpisode> ordered = episodes.stream()
                .sorted(Comparator.comparingInt(TmdbEpisode::seasonNumber)
                        .thenComparingInt(TmdbEpisode::episodeNumber))
                .toList();
        int start = -1;
        for (int index = 0; index < ordered.size(); index++) {
            if (ordered.get(index).id().equals(firstEpisodeId)) {
                start = index;
                break;
            }
        }
        if (start < 0) {
            return List.of();
        }
        return ordered.subList(start, Math.min(ordered.size(), start + count));
    }
}

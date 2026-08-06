package com.episort.ui.scan;

import com.episort.tvdb.TvdbEpisode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Selects a consecutive TVDB episode range from a user-chosen first episode. */
final class TvdbEpisodeSequence {
    private TvdbEpisodeSequence() {
    }

    static List<TvdbEpisode> startingAt(List<TvdbEpisode> episodes, String firstEpisodeId, int count) {
        Objects.requireNonNull(episodes, "episodes");
        if (firstEpisodeId == null || firstEpisodeId.isBlank() || count <= 0) {
            return List.of();
        }
        List<TvdbEpisode> ordered = episodes.stream()
                .sorted(Comparator.comparingInt(TvdbEpisode::seasonNumber)
                        .thenComparingInt(TvdbEpisode::episodeNumber))
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

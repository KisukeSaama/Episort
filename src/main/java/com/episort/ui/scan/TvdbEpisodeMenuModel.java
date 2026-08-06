package com.episort.ui.scan;

import com.episort.tvdb.TvdbEpisode;
import com.episort.tvdb.TvdbEpisodeOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the compact hierarchy used by the first-episode picker. */
final class TvdbEpisodeMenuModel {

    private static final int ABSOLUTE_GROUP_SIZE = 50;

    private TvdbEpisodeMenuModel() {
    }

    static List<Group> groups(List<TvdbEpisode> episodes, TvdbEpisodeOrder order) {
        Map<GroupKey, List<TvdbEpisode>> grouped = new LinkedHashMap<>();
        for (TvdbEpisode episode : episodes == null ? List.<TvdbEpisode>of() : episodes) {
            GroupKey key = order == TvdbEpisodeOrder.ABSOLUTE
                    && !episode.special()
                    && episode.absoluteNumber().isPresent()
                    ? absoluteGroup(episode)
                    : new GroupKey(GroupKind.SEASON, episode.seasonNumber(), episode.seasonNumber());
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(episode);
        }
        return grouped.entrySet().stream()
                .map(entry -> new Group(entry.getKey().kind(), entry.getKey().start(),
                        entry.getKey().end(), List.copyOf(entry.getValue())))
                .toList();
    }

    static String episodeCode(TvdbEpisode episode, TvdbEpisodeOrder order) {
        if (order == TvdbEpisodeOrder.ABSOLUTE && episode.absoluteNumber().isPresent()) {
            return "A%03d".formatted(episode.absoluteNumber().orElseThrow());
        }
        return "S%02dE%02d".formatted(episode.seasonNumber(), episode.episodeNumber());
    }

    private static GroupKey absoluteGroup(TvdbEpisode episode) {
        int absolute = episode.absoluteNumber().orElse(episode.episodeNumber());
        int start = ((Math.max(1, absolute) - 1) / ABSOLUTE_GROUP_SIZE) * ABSOLUTE_GROUP_SIZE + 1;
        return new GroupKey(GroupKind.ABSOLUTE_RANGE, start, start + ABSOLUTE_GROUP_SIZE - 1);
    }

    enum GroupKind { SEASON, ABSOLUTE_RANGE }

    record Group(GroupKind kind, int start, int end, List<TvdbEpisode> episodes) {
    }

    private record GroupKey(GroupKind kind, int start, int end) {
    }
}

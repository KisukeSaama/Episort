package com.episort.tmdb;

import java.util.Objects;

/** One concrete episode order advertised on a TMDB series page. */
public record TmdbEpisodeGroup(
        String id,
        String name,
        TmdbEpisodeOrder order,
        int groupCount,
        int episodeCount) {
    private static final String AIRED_ID = "__tmdb_aired__";
    private static final TmdbEpisodeGroup AIRED = new TmdbEpisodeGroup(
            AIRED_ID, "TMDB seasons", TmdbEpisodeOrder.AIRED, 0, 0);

    public TmdbEpisodeGroup {
        id = Objects.requireNonNull(id, "id").trim();
        name = Objects.requireNonNull(name, "name").trim();
        order = Objects.requireNonNull(order, "order");
        if (id.isEmpty()) throw new IllegalArgumentException("id must not be blank");
        if (name.isEmpty()) throw new IllegalArgumentException("name must not be blank");
        groupCount = Math.max(0, groupCount);
        episodeCount = Math.max(0, episodeCount);
    }

    public static TmdbEpisodeGroup aired() {
        return AIRED;
    }

    public static TmdbEpisodeGroup legacy(TmdbEpisodeOrder order) {
        TmdbEpisodeOrder safeOrder = order == null ? TmdbEpisodeOrder.AIRED : order;
        return safeOrder == TmdbEpisodeOrder.AIRED
                ? aired()
                : new TmdbEpisodeGroup("__tmdb_type_" + safeOrder.groupType() + "__",
                        safeOrder.name(), safeOrder, 0, 0);
    }

    public boolean isAired() {
        return AIRED_ID.equals(id);
    }

    /** TMDB group ids are the stable identity; mutable labels and counts are descriptive. */
    @Override
    public boolean equals(Object other) {
        return other instanceof TmdbEpisodeGroup group && id.equals(group.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}

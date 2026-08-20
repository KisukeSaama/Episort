package com.episort.tmdb;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Series metadata plus every concrete TMDB episode group loaded so far. */
public final class TmdbSeriesDetails {
    private final TmdbIdentity identity;
    private final Set<TmdbEpisodeOrder> supportedOrders;
    private final Map<TmdbEpisodeOrder, List<TmdbEpisode>> episodesByOrder;
    private final List<TmdbEpisodeGroup> availableGroups;
    private final Map<String, List<TmdbEpisode>> episodesByGroupId;

    public TmdbSeriesDetails(
            TmdbIdentity identity,
            Set<TmdbEpisodeOrder> supportedOrders,
            List<TmdbEpisode> airedEpisodes,
            List<TmdbEpisode> dvdEpisodes,
            List<TmdbEpisode> absoluteEpisodes) {
        this(identity,
                safeOrders(supportedOrders),
                legacyOrders(airedEpisodes, dvdEpisodes, absoluteEpisodes),
                legacyGroups(supportedOrders),
                legacyGroupEpisodes(airedEpisodes, dvdEpisodes, absoluteEpisodes));
    }

    private TmdbSeriesDetails(
            TmdbIdentity identity,
            Set<TmdbEpisodeOrder> supportedOrders,
            Map<TmdbEpisodeOrder, List<TmdbEpisode>> episodesByOrder,
            List<TmdbEpisodeGroup> availableGroups,
            Map<String, List<TmdbEpisode>> episodesByGroupId) {
        this.identity = Objects.requireNonNull(identity, "identity");
        if (identity.mediaType() != TmdbMediaType.SERIES) {
            throw new IllegalArgumentException("identity must be a series");
        }
        this.supportedOrders = Set.copyOf(supportedOrders);
        this.episodesByOrder = copyOrderEpisodes(episodesByOrder);
        this.availableGroups = List.copyOf(availableGroups);
        this.episodesByGroupId = copyGroupEpisodes(episodesByGroupId);
    }

    public static TmdbSeriesDetails forOrder(
            TmdbIdentity identity,
            Set<TmdbEpisodeOrder> supportedOrders,
            TmdbEpisodeOrder order,
            List<TmdbEpisode> episodes) {
        TmdbEpisodeGroup group = TmdbEpisodeGroup.legacy(order);
        List<TmdbEpisodeGroup> groups = legacyGroups(supportedOrders);
        if (!groups.contains(group)) {
            groups = new ArrayList<>(groups);
            groups.add(group);
        }
        return forGroup(identity, groups, group, episodes);
    }

    public static TmdbSeriesDetails forGroup(
            TmdbIdentity identity,
            List<TmdbEpisodeGroup> availableGroups,
            TmdbEpisodeGroup loadedGroup,
            List<TmdbEpisode> episodes) {
        Objects.requireNonNull(loadedGroup, "loadedGroup");
        List<TmdbEpisodeGroup> safeGroups = availableGroups == null
                ? List.of(TmdbEpisodeGroup.aired())
                : distinctGroups(availableGroups);
        if (!safeGroups.contains(loadedGroup)) {
            List<TmdbEpisodeGroup> expanded = new ArrayList<>(safeGroups);
            expanded.add(loadedGroup);
            safeGroups = List.copyOf(expanded);
        }
        EnumSet<TmdbEpisodeOrder> orders = EnumSet.of(TmdbEpisodeOrder.AIRED);
        safeGroups.forEach(group -> orders.add(group.order()));
        List<TmdbEpisode> safeEpisodes = episodes == null ? List.of() : List.copyOf(episodes);
        return new TmdbSeriesDetails(
                identity,
                orders,
                Map.of(loadedGroup.order(), safeEpisodes),
                safeGroups,
                Map.of(loadedGroup.id(), safeEpisodes));
    }

    public TmdbSeriesDetails merge(TmdbSeriesDetails other) {
        Objects.requireNonNull(other, "other");
        if (!identity.id().equals(other.identity.id())) {
            throw new IllegalArgumentException("series identities must match");
        }
        EnumSet<TmdbEpisodeOrder> orders = EnumSet.noneOf(TmdbEpisodeOrder.class);
        orders.addAll(supportedOrders);
        orders.addAll(other.supportedOrders);
        EnumMap<TmdbEpisodeOrder, List<TmdbEpisode>> byOrder = new EnumMap<>(TmdbEpisodeOrder.class);
        byOrder.putAll(episodesByOrder);
        byOrder.putAll(other.episodesByOrder);
        LinkedHashMap<String, TmdbEpisodeGroup> groups = new LinkedHashMap<>();
        availableGroups.forEach(group -> groups.put(group.id(), group));
        other.availableGroups.forEach(group -> groups.put(group.id(), group));
        LinkedHashMap<String, List<TmdbEpisode>> byGroup = new LinkedHashMap<>(episodesByGroupId);
        byGroup.putAll(other.episodesByGroupId);
        return new TmdbSeriesDetails(other.identity, orders, byOrder, List.copyOf(groups.values()), byGroup);
    }

    public TmdbIdentity identity() {
        return identity;
    }

    public Set<TmdbEpisodeOrder> supportedOrders() {
        return supportedOrders;
    }

    public Map<TmdbEpisodeOrder, List<TmdbEpisode>> episodesByOrder() {
        return episodesByOrder;
    }

    public List<TmdbEpisodeGroup> availableGroups() {
        return availableGroups;
    }

    public List<TmdbEpisode> episodesFor(TmdbEpisodeGroup group) {
        if (group == null) return episodesFor(TmdbEpisodeOrder.AIRED);
        return episodesByGroupId.getOrDefault(group.id(), List.of());
    }

    public List<TmdbEpisode> episodesFor(TmdbEpisodeOrder order) {
        return episodesByOrder.getOrDefault(order == null ? TmdbEpisodeOrder.AIRED : order, List.of());
    }

    public Optional<TmdbEpisodeGroup> group(String id) {
        return availableGroups.stream().filter(group -> group.id().equals(id)).findFirst();
    }

    public TmdbEpisodeGroup firstGroupFor(TmdbEpisodeOrder order) {
        TmdbEpisodeOrder safeOrder = order == null ? TmdbEpisodeOrder.AIRED : order;
        return availableGroups.stream()
                .filter(group -> group.order() == safeOrder)
                .findFirst()
                .orElseGet(() -> TmdbEpisodeGroup.legacy(safeOrder));
    }

    public List<TmdbEpisode> airedEpisodes() {
        return episodesFor(TmdbEpisodeOrder.AIRED);
    }

    public List<TmdbEpisode> dvdEpisodes() {
        return episodesFor(TmdbEpisodeOrder.DVD);
    }

    public List<TmdbEpisode> absoluteEpisodes() {
        return episodesFor(TmdbEpisodeOrder.ABSOLUTE);
    }

    private static Set<TmdbEpisodeOrder> safeOrders(Set<TmdbEpisodeOrder> orders) {
        return orders == null ? Set.of() : Set.copyOf(orders);
    }

    private static Map<TmdbEpisodeOrder, List<TmdbEpisode>> legacyOrders(
            List<TmdbEpisode> aired, List<TmdbEpisode> dvd, List<TmdbEpisode> absolute) {
        EnumMap<TmdbEpisodeOrder, List<TmdbEpisode>> values = new EnumMap<>(TmdbEpisodeOrder.class);
        values.put(TmdbEpisodeOrder.AIRED, safeEpisodes(aired));
        values.put(TmdbEpisodeOrder.DVD, safeEpisodes(dvd));
        values.put(TmdbEpisodeOrder.ABSOLUTE, safeEpisodes(absolute));
        return values;
    }

    private static List<TmdbEpisodeGroup> legacyGroups(Set<TmdbEpisodeOrder> supportedOrders) {
        EnumSet<TmdbEpisodeOrder> orders = EnumSet.of(TmdbEpisodeOrder.AIRED);
        if (supportedOrders != null) orders.addAll(supportedOrders);
        return orders.stream().map(TmdbEpisodeGroup::legacy).toList();
    }

    private static Map<String, List<TmdbEpisode>> legacyGroupEpisodes(
            List<TmdbEpisode> aired, List<TmdbEpisode> dvd, List<TmdbEpisode> absolute) {
        return Map.of(
                TmdbEpisodeGroup.aired().id(), safeEpisodes(aired),
                TmdbEpisodeGroup.legacy(TmdbEpisodeOrder.DVD).id(), safeEpisodes(dvd),
                TmdbEpisodeGroup.legacy(TmdbEpisodeOrder.ABSOLUTE).id(), safeEpisodes(absolute));
    }

    private static List<TmdbEpisode> safeEpisodes(List<TmdbEpisode> episodes) {
        return episodes == null ? List.of() : List.copyOf(episodes);
    }

    private static Map<TmdbEpisodeOrder, List<TmdbEpisode>> copyOrderEpisodes(
            Map<TmdbEpisodeOrder, List<TmdbEpisode>> values) {
        EnumMap<TmdbEpisodeOrder, List<TmdbEpisode>> copy = new EnumMap<>(TmdbEpisodeOrder.class);
        if (values != null) values.forEach((order, episodes) -> {
            if (order != null) copy.put(order, safeEpisodes(episodes));
        });
        return Map.copyOf(copy);
    }

    private static Map<String, List<TmdbEpisode>> copyGroupEpisodes(Map<String, List<TmdbEpisode>> values) {
        LinkedHashMap<String, List<TmdbEpisode>> copy = new LinkedHashMap<>();
        if (values != null) values.forEach((id, episodes) -> {
            if (id != null && !id.isBlank()) copy.put(id, safeEpisodes(episodes));
        });
        return Map.copyOf(copy);
    }

    private static List<TmdbEpisodeGroup> distinctGroups(List<TmdbEpisodeGroup> groups) {
        LinkedHashMap<String, TmdbEpisodeGroup> distinct = new LinkedHashMap<>();
        distinct.put(TmdbEpisodeGroup.aired().id(), TmdbEpisodeGroup.aired());
        groups.stream().filter(Objects::nonNull).forEach(group -> distinct.put(group.id(), group));
        return List.copyOf(distinct.values());
    }
}

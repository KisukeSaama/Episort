package com.episort.matching;

import com.episort.tmdb.TmdbEpisode;
import com.episort.tmdb.TmdbEpisodeGroup;
import com.episort.tmdb.TmdbEpisodeOrder;
import com.episort.tmdb.TmdbSeriesDetails;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public final class TmdbEpisodeOrderMapper {
    public EpisodeOrderMappingResult map(
            TmdbSeriesDetails series,
            TmdbEpisodeGroup sourceGroup,
            TmdbEpisodeGroup targetGroup,
            Path sourcePath,
            int sourceSeason,
            int sourceEpisode,
            Optional<Integer> absoluteNumber) {
        if (series == null || targetGroup == null) {
            return EpisodeOrderMappingResult.unmapped("No TMDB series metadata.");
        }
        List<TmdbEpisode> targetEpisodes = series.episodesFor(targetGroup);
        if (targetEpisodes.isEmpty() || !series.availableGroups().contains(targetGroup)) {
            return EpisodeOrderMappingResult.unmapped("TMDB order unavailable for this series");
        }
        Optional<TmdbEpisode> logical = sourceGroup == null
                ? Optional.empty()
                : findIn(series.episodesFor(sourceGroup), sourceGroup.order(),
                        sourceSeason, sourceEpisode, absoluteNumber);
        if (logical.isEmpty()) {
            logical = series.availableGroups().stream()
                    .filter(group -> !group.equals(targetGroup))
                    .map(group -> findIn(series.episodesFor(group), group.order(),
                            sourceSeason, sourceEpisode, absoluteNumber))
                    .flatMap(Optional::stream)
                    .findFirst();
        }
        return mapLogicalEpisode(logical, targetEpisodes, sourcePath);
    }

    public EpisodeOrderMappingResult map(
            TmdbSeriesDetails series,
            TmdbEpisodeOrder sourceOrder,
            TmdbEpisodeOrder targetOrder,
            Path sourcePath,
            int sourceSeason,
            int sourceEpisode,
            Optional<Integer> absoluteNumber) {
        if (series == null) {
            return EpisodeOrderMappingResult.unmapped("No TMDB series metadata.");
        }
        TmdbEpisodeOrder target = targetOrder == null ? TmdbEpisodeOrder.AIRED : targetOrder;
        List<TmdbEpisode> targetEpisodes = series.episodesFor(target);
        if (targetEpisodes.isEmpty() || !series.supportedOrders().contains(target)) {
            return EpisodeOrderMappingResult.unmapped("TMDB order unavailable for this series");
        }
        Optional<TmdbEpisode> logical = findSourceEpisode(series, sourceOrder, target, sourceSeason, sourceEpisode, absoluteNumber);
        return mapLogicalEpisode(logical, targetEpisodes, sourcePath);
    }

    private EpisodeOrderMappingResult mapLogicalEpisode(
            Optional<TmdbEpisode> logical, List<TmdbEpisode> targetEpisodes, Path sourcePath) {
        if (logical.isEmpty()) {
            return EpisodeOrderMappingResult.unmapped("Episode not found in selected order");
        }
        return targetEpisodes.stream()
                .filter(candidate -> candidate.id().equals(logical.orElseThrow().id()))
                .findFirst()
                .map(targetEpisode -> EpisodeOrderMappingResult.mapped(new MediaMatchProposal(
                        sourcePath,
                        targetEpisode.special() ? MediaMatchType.SERIES_SPECIAL : MediaMatchType.SERIES_EPISODE,
                        Optional.of(targetEpisode.id()),
                        Optional.of(targetEpisode.title()),
                        Optional.of(targetEpisode.seasonNumber()),
                        Optional.of(targetEpisode.episodeNumber()),
                        targetEpisode.absoluteNumber(),
                        OptionalDouble.of(0.92),
                        "Remapped across TMDB episode orders.")))
                .orElseGet(() -> EpisodeOrderMappingResult.unmapped("Episode not found in selected order"));
    }

    private Optional<TmdbEpisode> findSourceEpisode(
            TmdbSeriesDetails series,
            TmdbEpisodeOrder sourceOrder,
            TmdbEpisodeOrder targetOrder,
            int sourceSeason,
            int sourceEpisode,
            Optional<Integer> absoluteNumber) {
        if (sourceOrder != null) {
            Optional<TmdbEpisode> exact = findIn(series.episodesFor(sourceOrder), sourceOrder, sourceSeason, sourceEpisode, absoluteNumber);
            if (exact.isPresent()) {
                return exact;
            }
        }
        return List.of(
                        TmdbEpisodeOrder.AIRED,
                        TmdbEpisodeOrder.DVD,
                        TmdbEpisodeOrder.ABSOLUTE,
                        TmdbEpisodeOrder.DIGITAL,
                        TmdbEpisodeOrder.STORY_ARC,
                        TmdbEpisodeOrder.PRODUCTION,
                        TmdbEpisodeOrder.TV).stream()
                .filter(order -> order != targetOrder)
                .map(series::episodesFor)
                .flatMap(Collection::stream)
                .filter(candidate -> matches(candidate, null, sourceSeason, sourceEpisode, absoluteNumber))
                .min(Comparator.comparingInt(TmdbEpisode::seasonNumber).thenComparingInt(TmdbEpisode::episodeNumber));
    }

    private Optional<TmdbEpisode> findIn(
            List<TmdbEpisode> episodes,
            TmdbEpisodeOrder order,
            int sourceSeason,
            int sourceEpisode,
            Optional<Integer> absoluteNumber) {
        return episodes.stream()
                .filter(candidate -> matches(candidate, order, sourceSeason, sourceEpisode, absoluteNumber))
                .findFirst();
    }

    private boolean matches(
            TmdbEpisode episode,
            TmdbEpisodeOrder order,
            int sourceSeason,
            int sourceEpisode,
            Optional<Integer> absoluteNumber) {
        if (order == TmdbEpisodeOrder.ABSOLUTE) {
            return episode.absoluteNumber().filter(number -> number == sourceEpisode).isPresent()
                    || absoluteNumber.filter(number -> episode.absoluteNumber().filter(number::equals).isPresent()).isPresent();
        }
        if (absoluteNumber.isPresent()
                && episode.absoluteNumber().filter(number -> number.equals(absoluteNumber.orElseThrow())).isPresent()) {
            return true;
        }
        return episode.seasonNumber() == sourceSeason && episode.episodeNumber() == sourceEpisode;
    }

    public record EpisodeOrderMappingResult(Optional<MediaMatchProposal> proposal, Optional<String> error) {
        public EpisodeOrderMappingResult {
            proposal = proposal == null ? Optional.empty() : proposal;
            error = error == null ? Optional.empty() : error;
        }

        static EpisodeOrderMappingResult mapped(MediaMatchProposal proposal) {
            return new EpisodeOrderMappingResult(Optional.of(proposal), Optional.empty());
        }

        static EpisodeOrderMappingResult unmapped(String error) {
            return new EpisodeOrderMappingResult(Optional.empty(), Optional.of(error));
        }
    }
}

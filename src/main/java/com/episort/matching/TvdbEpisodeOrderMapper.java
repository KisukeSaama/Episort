package com.episort.matching;

import com.episort.tvdb.TvdbEpisode;
import com.episort.tvdb.TvdbEpisodeOrder;
import com.episort.tvdb.TvdbSeriesDetails;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public final class TvdbEpisodeOrderMapper {
    public EpisodeOrderMappingResult map(
            TvdbSeriesDetails series,
            TvdbEpisodeOrder sourceOrder,
            TvdbEpisodeOrder targetOrder,
            Path sourcePath,
            int sourceSeason,
            int sourceEpisode,
            Optional<Integer> absoluteNumber) {
        if (series == null) {
            return EpisodeOrderMappingResult.unmapped("No TVDB series metadata.");
        }
        TvdbEpisodeOrder target = targetOrder == null ? TvdbEpisodeOrder.AIRED : targetOrder;
        List<TvdbEpisode> targetEpisodes = series.episodesFor(target);
        if (targetEpisodes.isEmpty() || !series.supportedOrders().contains(target)) {
            return EpisodeOrderMappingResult.unmapped("TVDB order unavailable for this series");
        }
        Optional<TvdbEpisode> logical = findSourceEpisode(series, sourceOrder, target, sourceSeason, sourceEpisode, absoluteNumber);
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
                        "Remapped across TVDB episode orders.")))
                .orElseGet(() -> EpisodeOrderMappingResult.unmapped("Episode not found in selected order"));
    }

    private Optional<TvdbEpisode> findSourceEpisode(
            TvdbSeriesDetails series,
            TvdbEpisodeOrder sourceOrder,
            TvdbEpisodeOrder targetOrder,
            int sourceSeason,
            int sourceEpisode,
            Optional<Integer> absoluteNumber) {
        if (sourceOrder != null) {
            Optional<TvdbEpisode> exact = findIn(series.episodesFor(sourceOrder), sourceOrder, sourceSeason, sourceEpisode, absoluteNumber);
            if (exact.isPresent()) {
                return exact;
            }
        }
        return List.of(TvdbEpisodeOrder.AIRED, TvdbEpisodeOrder.DVD, TvdbEpisodeOrder.ABSOLUTE).stream()
                .filter(order -> order != targetOrder)
                .map(series::episodesFor)
                .flatMap(Collection::stream)
                .filter(candidate -> matches(candidate, null, sourceSeason, sourceEpisode, absoluteNumber))
                .min(Comparator.comparingInt(TvdbEpisode::seasonNumber).thenComparingInt(TvdbEpisode::episodeNumber));
    }

    private Optional<TvdbEpisode> findIn(
            List<TvdbEpisode> episodes,
            TvdbEpisodeOrder order,
            int sourceSeason,
            int sourceEpisode,
            Optional<Integer> absoluteNumber) {
        return episodes.stream()
                .filter(candidate -> matches(candidate, order, sourceSeason, sourceEpisode, absoluteNumber))
                .findFirst();
    }

    private boolean matches(
            TvdbEpisode episode,
            TvdbEpisodeOrder order,
            int sourceSeason,
            int sourceEpisode,
            Optional<Integer> absoluteNumber) {
        if (order == TvdbEpisodeOrder.ABSOLUTE) {
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

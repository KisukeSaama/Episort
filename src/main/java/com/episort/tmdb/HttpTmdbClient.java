package com.episort.tmdb;

import com.episort.config.JanusConfiguration;
import com.episort.tmdb.debug.TmdbRequestBus;
import com.episort.tmdb.debug.TmdbRequestTrace;
import com.episort.tmdb.dto.TmdbEpisodeGroupDetailsDto;
import com.episort.tmdb.dto.TmdbEpisodeGroupsDto;
import com.episort.tmdb.dto.TmdbMovieResponseDto;
import com.episort.tmdb.dto.TmdbSearchResponseDto;
import com.episort.tmdb.dto.TmdbSeriesResponseDto;
import com.google.gson.Gson;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** HTTP implementation of the application-level TMDB v3 API integration. */
public final class HttpTmdbClient implements TmdbClient {
    private static final URI IMAGE_BASE_URI = URI.create("https://image.tmdb.org/t/p/w342/");
    private static final String LANGUAGE = "en-US";
    private static final int ABSOLUTE_GROUP_TYPE = 2;
    private static final int DVD_GROUP_TYPE = 3;
    private final HttpClient httpClient;
    private final URI baseUriOverride;
    private final Gson gson = new Gson();

    public HttpTmdbClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    private HttpTmdbClient(HttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.baseUriOverride = null;
    }

    HttpTmdbClient(HttpClient httpClient, URI baseUri) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        String value = Objects.requireNonNull(baseUri, "baseUri").toString();
        this.baseUriOverride = URI.create(value.endsWith("/") ? value : value + "/");
    }

    @Override
    public TmdbSearchResult search(String query, JanusConfiguration credentials) {
        return search(TmdbSearchCriteria.title(query), credentials);
    }

    @Override
    public TmdbSearchResult search(TmdbSearchCriteria criteria, JanusConfiguration credentials) {
        Objects.requireNonNull(criteria, "criteria");
        Objects.requireNonNull(credentials, "credentials");
        if (criteria.tmdbId().isPresent()) {
            return searchById(criteria.tmdbId().orElseThrow(), credentials);
        }

        String encoded = encode(criteria.query());
        String common = "query=" + encoded + "&include_adult=false&language=" + LANGUAGE;
        String tvPath = "search/tv?" + common
                + criteria.year().map(year -> "&first_air_date_year=" + year).orElse("");
        String moviePath = "search/movie?" + common
                + criteria.year().map(year -> "&primary_release_year=" + year).orElse("");
        TmdbSearchResponseDto tv = send(tvPath, credentials, TmdbSearchResponseDto.class);
        TmdbSearchResponseDto movies = send(moviePath, credentials, TmdbSearchResponseDto.class);
        return new TmdbSearchResult(mapSearch(tv, TmdbMediaType.SERIES), mapSearch(movies, TmdbMediaType.MOVIE));
    }

    private TmdbSearchResult searchById(String id, JanusConfiguration credentials) {
        List<TmdbCandidate> series = sendIfFound(
                        "tv/" + encode(id) + "?language=" + LANGUAGE,
                        credentials,
                        TmdbSeriesResponseDto.class)
                .flatMap(value -> mapSeriesCandidate(value, 0))
                .stream().toList();
        List<TmdbCandidate> movies = sendIfFound(
                        "movie/" + encode(id) + "?language=" + LANGUAGE,
                        credentials,
                        TmdbMovieResponseDto.class)
                .flatMap(value -> mapMovieCandidate(value, 0))
                .stream().toList();
        return new TmdbSearchResult(series, movies);
    }

    @Override
    public TmdbSeriesDetails seriesDetails(TmdbIdentity identity, JanusConfiguration credentials) {
        return seriesDetails(identity, TmdbEpisodeOrder.AIRED, credentials);
    }

    @Override
    public TmdbSeriesDetails seriesDetails(
            TmdbIdentity identity,
            TmdbEpisodeOrder requestedOrder,
            JanusConfiguration credentials) {
        Objects.requireNonNull(identity, "identity");
        TmdbEpisodeOrder safeOrder = requestedOrder == null ? TmdbEpisodeOrder.AIRED : requestedOrder;
        String id = encode(identity.id());
        TmdbSeriesResponseDto series = send(
                "tv/" + id + "?language=" + LANGUAGE, credentials, TmdbSeriesResponseDto.class);
        TmdbEpisodeGroupsDto groupIndex = sendIfFound(
                        "tv/" + id + "/episode_groups", credentials, TmdbEpisodeGroupsDto.class)
                .orElseGet(TmdbEpisodeGroupsDto::new);
        EnumSet<TmdbEpisodeOrder> supported = supportedOrders(groupIndex);
        TmdbIdentity resolvedIdentity = new TmdbIdentity(
                series.id == null ? identity.id() : String.valueOf(series.id),
                TmdbMediaType.SERIES,
                fallback(series.name, series.original_name, identity.displayName()));

        if (safeOrder == TmdbEpisodeOrder.AIRED) {
            List<TmdbEpisode> aired = loadAiredEpisodes(id, series.seasons, credentials);
            return new TmdbSeriesDetails(resolvedIdentity, supported, aired, List.of(), List.of());
        }

        int type = safeOrder == TmdbEpisodeOrder.DVD ? DVD_GROUP_TYPE : ABSOLUTE_GROUP_TYPE;
        Optional<TmdbEpisodeGroupsDto.GroupSummary> selected = summaries(groupIndex).stream()
                .filter(group -> group.type != null && group.type == type)
                .filter(group -> group.id != null && !group.id.isBlank())
                .min(Comparator.comparingInt(group -> group.order == null ? Integer.MAX_VALUE : group.order));
        if (selected.isEmpty()) {
            return new TmdbSeriesDetails(resolvedIdentity, supported, List.of(), List.of(), List.of());
        }
        TmdbEpisodeGroupDetailsDto group = send(
                "tv/episode_group/" + encode(selected.orElseThrow().id),
                credentials,
                TmdbEpisodeGroupDetailsDto.class);
        List<TmdbEpisode> ordered = safeOrder == TmdbEpisodeOrder.DVD
                ? mapDvdEpisodes(group)
                : mapAbsoluteEpisodes(group);
        return safeOrder == TmdbEpisodeOrder.DVD
                ? new TmdbSeriesDetails(resolvedIdentity, supported, List.of(), ordered, List.of())
                : new TmdbSeriesDetails(resolvedIdentity, supported, List.of(), List.of(), ordered);
    }

    @Override
    public TmdbMovieDetails movieDetails(TmdbIdentity identity, JanusConfiguration credentials) {
        Objects.requireNonNull(identity, "identity");
        TmdbMovieResponseDto movie = send(
                "movie/" + encode(identity.id()) + "?language=" + LANGUAGE,
                credentials,
                TmdbMovieResponseDto.class);
        TmdbIdentity resolved = new TmdbIdentity(
                movie.id == null ? identity.id() : String.valueOf(movie.id),
                TmdbMediaType.MOVIE,
                fallback(movie.title, movie.original_title, identity.displayName()));
        return new TmdbMovieDetails(resolved, parseYear(movie.release_date));
    }

    private List<TmdbCandidate> mapSearch(TmdbSearchResponseDto response, TmdbMediaType mediaType) {
        List<TmdbCandidate> candidates = new ArrayList<>();
        int rank = 0;
        for (TmdbSearchResponseDto.Result result : response == null || response.results == null
                ? List.<TmdbSearchResponseDto.Result>of() : response.results) {
            Optional<TmdbCandidate> candidate = mapSearchCandidate(result, mediaType, rank++);
            candidate.ifPresent(candidates::add);
        }
        return List.copyOf(candidates);
    }

    private Optional<TmdbCandidate> mapSearchCandidate(
            TmdbSearchResponseDto.Result item, TmdbMediaType mediaType, int rank) {
        if (item == null || item.id == null) return Optional.empty();
        String name = mediaType == TmdbMediaType.SERIES
                ? fallback(item.name, item.original_name, "")
                : fallback(item.title, item.original_title, "");
        if (name.isBlank()) return Optional.empty();
        String original = mediaType == TmdbMediaType.SERIES ? item.original_name : item.original_title;
        String date = mediaType == TmdbMediaType.SERIES ? item.first_air_date : item.release_date;
        return Optional.of(candidate(
                String.valueOf(item.id), mediaType, name, original, item.poster_path,
                item.overview, parseYear(date), first(item.origin_country), Optional.empty(), rank));
    }

    private Optional<TmdbCandidate> mapSeriesCandidate(TmdbSeriesResponseDto series, int rank) {
        if (series == null || series.id == null) return Optional.empty();
        String name = fallback(series.name, series.original_name, "");
        if (name.isBlank()) return Optional.empty();
        Optional<String> network = series.networks == null || series.networks.isEmpty()
                ? Optional.empty() : optional(series.networks.getFirst().name);
        return Optional.of(candidate(
                String.valueOf(series.id), TmdbMediaType.SERIES, name, series.original_name,
                series.poster_path, series.overview, parseYear(series.first_air_date),
                first(series.origin_country), network, rank));
    }

    private Optional<TmdbCandidate> mapMovieCandidate(TmdbMovieResponseDto movie, int rank) {
        if (movie == null || movie.id == null) return Optional.empty();
        String name = fallback(movie.title, movie.original_title, "");
        if (name.isBlank()) return Optional.empty();
        Optional<String> country = movie.production_countries == null || movie.production_countries.isEmpty()
                ? Optional.empty() : optional(movie.production_countries.getFirst().iso_3166_1);
        return Optional.of(candidate(
                String.valueOf(movie.id), TmdbMediaType.MOVIE, name, movie.original_title,
                movie.poster_path, movie.overview, parseYear(movie.release_date), country,
                Optional.empty(), rank));
    }

    private static TmdbCandidate candidate(
            String id,
            TmdbMediaType mediaType,
            String name,
            String originalName,
            String posterPath,
            String overview,
            Optional<Integer> year,
            Optional<String> country,
            Optional<String> network,
            int rank) {
        Optional<String> englishOverview = optional(overview);
        List<String> aliases = originalName == null || originalName.isBlank() || originalName.equals(name)
                ? List.of() : List.of(originalName);
        return new TmdbCandidate(
                new TmdbIdentity(id, mediaType, name),
                posterUrl(posterPath),
                englishOverview,
                englishOverview,
                Optional.empty(),
                year,
                country,
                network,
                OptionalDoubleScore.empty(),
                rank,
                aliases);
    }

    private List<TmdbEpisode> loadAiredEpisodes(
            String seriesId,
            List<TmdbSeriesResponseDto.SeasonSummary> seasons,
            JanusConfiguration credentials) {
        List<TmdbEpisode> episodes = new ArrayList<>();
        List<TmdbSeriesResponseDto.SeasonSummary> orderedSeasons = seasons == null
                ? List.of()
                : seasons.stream()
                        .filter(season -> season != null && season.season_number != null)
                        .sorted(Comparator.comparingInt(season -> season.season_number))
                        .toList();
        for (TmdbSeriesResponseDto.SeasonSummary season : orderedSeasons) {
            TmdbSeriesResponseDto.SeasonDetails details = send(
                    "tv/" + seriesId + "/season/" + season.season_number + "?language=" + LANGUAGE,
                    credentials,
                    TmdbSeriesResponseDto.SeasonDetails.class);
            if (details.episodes == null) continue;
            details.episodes.stream().map(HttpTmdbClient::mapAiredEpisode).flatMap(Optional::stream)
                    .forEach(episodes::add);
        }
        return List.copyOf(episodes);
    }

    private static Optional<TmdbEpisode> mapAiredEpisode(TmdbSeriesResponseDto.Episode episode) {
        if (episode == null || episode.id == null
                || episode.season_number == null || episode.episode_number == null) {
            return Optional.empty();
        }
        return Optional.of(new TmdbEpisode(
                String.valueOf(episode.id), episode.season_number, episode.episode_number,
                Optional.empty(), fallback(episode.name, "Untitled episode"), episode.season_number == 0));
    }

    private static List<TmdbEpisode> mapDvdEpisodes(TmdbEpisodeGroupDetailsDto details) {
        List<TmdbEpisode> episodes = new ArrayList<>();
        for (TmdbEpisodeGroupDetailsDto.Group group : orderedGroups(details)) {
            int dvdSeason = safeOrder(group.order) + 1;
            for (TmdbEpisodeGroupDetailsDto.Episode episode : orderedEpisodes(group)) {
                if (episode.id == null) continue;
                boolean special = episode.season_number != null && episode.season_number == 0;
                int season = special ? 0 : dvdSeason;
                int number = special && episode.episode_number != null
                        ? episode.episode_number : safeOrder(episode.order) + 1;
                episodes.add(new TmdbEpisode(
                        String.valueOf(episode.id), season, number, Optional.empty(),
                        fallback(episode.name, "Untitled episode"), special));
            }
        }
        return List.copyOf(episodes);
    }

    private static List<TmdbEpisode> mapAbsoluteEpisodes(TmdbEpisodeGroupDetailsDto details) {
        List<TmdbEpisode> episodes = new ArrayList<>();
        int absolute = 0;
        for (TmdbEpisodeGroupDetailsDto.Group group : orderedGroups(details)) {
            for (TmdbEpisodeGroupDetailsDto.Episode episode : orderedEpisodes(group)) {
                if (episode.id == null) continue;
                boolean special = episode.season_number != null && episode.season_number == 0;
                Optional<Integer> absoluteNumber = special ? Optional.empty() : Optional.of(++absolute);
                int season = special ? 0 : 1;
                int number = special && episode.episode_number != null ? episode.episode_number : absolute;
                episodes.add(new TmdbEpisode(
                        String.valueOf(episode.id), season, number, absoluteNumber,
                        fallback(episode.name, "Untitled episode"), special));
            }
        }
        return List.copyOf(episodes);
    }

    private static List<TmdbEpisodeGroupDetailsDto.Group> orderedGroups(TmdbEpisodeGroupDetailsDto details) {
        return details == null || details.groups == null ? List.of() : details.groups.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(group -> safeOrder(group.order)))
                .toList();
    }

    private static List<TmdbEpisodeGroupDetailsDto.Episode> orderedEpisodes(
            TmdbEpisodeGroupDetailsDto.Group group) {
        return group.episodes == null ? List.of() : group.episodes.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(episode -> safeOrder(episode.order)))
                .toList();
    }

    private static EnumSet<TmdbEpisodeOrder> supportedOrders(TmdbEpisodeGroupsDto dto) {
        EnumSet<TmdbEpisodeOrder> orders = EnumSet.of(TmdbEpisodeOrder.AIRED);
        for (TmdbEpisodeGroupsDto.GroupSummary group : summaries(dto)) {
            if (group.type == null) continue;
            if (group.type == DVD_GROUP_TYPE) orders.add(TmdbEpisodeOrder.DVD);
            if (group.type == ABSOLUTE_GROUP_TYPE) orders.add(TmdbEpisodeOrder.ABSOLUTE);
        }
        return orders;
    }

    private static List<TmdbEpisodeGroupsDto.GroupSummary> summaries(TmdbEpisodeGroupsDto dto) {
        return dto == null || dto.results == null ? List.of() : dto.results.stream()
                .filter(Objects::nonNull).toList();
    }

    private <T> T send(String path, JanusConfiguration credentials, Class<T> type) {
        HttpResponse<String> response = sendResponse(path, credentials);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw requestFailed(response.statusCode());
        }
        return gson.fromJson(response.body(), type);
    }

    private <T> Optional<T> sendIfFound(String path, JanusConfiguration credentials, Class<T> type) {
        HttpResponse<String> response = sendResponse(path, credentials);
        if (response.statusCode() == 404) return Optional.empty();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw requestFailed(response.statusCode());
        }
        return Optional.ofNullable(gson.fromJson(response.body(), type));
    }

    private HttpResponse<String> sendResponse(String path, JanusConfiguration credentials) {
        Instant start = Instant.now();
        try {
            HttpResponse<String> response = sendOnce(request(path, credentials));
            publishTrace("GET", path, response, Duration.between(start, Instant.now()).toMillis(), null);
            return response;
        } catch (TmdbException exception) {
            publishFailure("GET", path, Duration.between(start, Instant.now()).toMillis(), exception.getMessage());
            throw exception;
        }
    }

    private HttpRequest request(String path, JanusConfiguration credentials) {
        URI endpoint = baseUriOverride == null ? credentials.tmdbBaseUri().resolve(path) : baseUriOverride.resolve(path);
        return HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(40))
                .header("Accept", "application/json")
                .header("X-Janus-Application-Id", credentials.applicationId())
                .header("X-Janus-Api-Key", credentials.apiKey())
                .GET()
                .build();
    }

    private HttpResponse<String> sendOnce(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw TmdbException.recoverable("TMDB_INTERRUPTED", "TMDB lookup was interrupted.",
                    "Interrupted while calling Janus.");
        } catch (IOException exception) {
            throw TmdbException.recoverable("TMDB_UNAVAILABLE", "TMDB is unavailable.",
                    "Network error while calling Janus.");
        }
    }

    private static TmdbException requestFailed(int statusCode) {
        if (statusCode == 401) {
            return TmdbException.recoverable("TMDB_AUTH_FAILED", "TMDB authentication failed.",
                    "Janus rejected the Episort caller credentials (HTTP 401).");
        }
        if (statusCode == 429) {
            return TmdbException.recoverable("TMDB_RATE_LIMITED", "TMDB is temporarily limiting requests.",
                    "Janus returned HTTP 429. Retry-After is managed by the gateway and caller workflow.");
        }
        return TmdbException.recoverable("TMDB_REQUEST_FAILED",
                "TMDB lookup failed (HTTP " + statusCode + ").",
                "Janus/TMDB returned HTTP " + statusCode + ". Response body omitted.");
    }

    private static void publishTrace(
            String method, String path, HttpResponse<String> response, long milliseconds, String error) {
        try {
            String body = response == null || response.body() == null ? "" : response.body();
            if (body.length() > 4000) body = body.substring(0, 4000) + "…";
            int status = response == null ? 0 : response.statusCode();
            String correlationId = response == null ? "" : response.headers()
                    .firstValue("X-Janus-Correlation-Id").orElse("");
            String diagnostic = correlationId.isBlank()
                    ? error
                    : "janusCorrelationId=" + correlationId + (error == null ? "" : "; " + error);
            boolean gatewayCacheHit = response != null && response.headers()
                    .firstValue("X-Janus-Cache")
                    .map(value -> !value.equalsIgnoreCase("MISS"))
                    .orElse(false);
            TmdbRequestBus.get().publish(new TmdbRequestTrace(
                    Instant.now(), method, path, status, milliseconds, body, diagnostic, gatewayCacheHit));
        } catch (RuntimeException ignored) {
            // Diagnostics must never break metadata retrieval.
        }
    }

    private static void publishFailure(String method, String path, long milliseconds, String error) {
        try {
            TmdbRequestBus.get().publish(new TmdbRequestTrace(
                    Instant.now(), method, path, 0, milliseconds, "", error, false));
        } catch (RuntimeException ignored) {
            // Diagnostics must never break metadata retrieval.
        }
    }

    private static Optional<String> posterUrl(String path) {
        if (path == null || path.isBlank()) return Optional.empty();
        String relative = path.trim();
        while (relative.startsWith("/")) relative = relative.substring(1);
        return Optional.of(IMAGE_BASE_URI.resolve(relative.replace(" ", "%20")).toString());
    }

    private static Optional<Integer> parseYear(String date) {
        if (date == null || date.length() < 4) return Optional.empty();
        try {
            return Optional.of(Integer.parseInt(date.substring(0, 4)));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static Optional<String> first(List<String> values) {
        return values == null || values.isEmpty() ? Optional.empty() : optional(values.getFirst());
    }

    private static Optional<String> optional(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }

    private static String fallback(String primary, String fallback) {
        return fallback(primary, fallback, "");
    }

    private static String fallback(String primary, String secondary, String tertiary) {
        if (primary != null && !primary.isBlank()) return primary.trim();
        if (secondary != null && !secondary.isBlank()) return secondary.trim();
        return tertiary == null ? "" : tertiary.trim();
    }

    private static int safeOrder(Integer value) {
        return value == null || value < 0 ? 0 : value;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

}

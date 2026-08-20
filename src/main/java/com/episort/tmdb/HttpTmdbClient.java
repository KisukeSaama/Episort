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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** HTTP implementation of the application-level TMDB v3 API integration. */
public final class HttpTmdbClient implements TmdbClient {
    private static final URI IMAGE_BASE_URI = URI.create("https://image.tmdb.org/t/p/w342/");
    private static final String LANGUAGE = "en-US";
    /** TMDB serves at most twenty appended sub-resources in one response. */
    private static final int MAX_APPENDED_RESOURCES = 20;
    private final HttpClient httpClient;
    private final URI baseUriOverride;
    private final TmdbRequestPacer pacer;
    private final Gson gson = new Gson();

    public HttpTmdbClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    private HttpTmdbClient(HttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.baseUriOverride = null;
        this.pacer = new TmdbRequestPacer();
    }

    HttpTmdbClient(HttpClient httpClient, URI baseUri) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        String value = Objects.requireNonNull(baseUri, "baseUri").toString();
        this.baseUriOverride = URI.create(value.endsWith("/") ? value : value + "/");
        this.pacer = new TmdbRequestPacer();
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
        // A caller that already knows which index it needs only pays for that
        // one. Over a full library that halves the search traffic, and the
        // caller is free to ask for the other index when this one comes back
        // empty.
        boolean wantsSeries = criteria.mediaType().map(TmdbMediaType.SERIES::equals).orElse(true);
        boolean wantsMovies = criteria.mediaType().map(TmdbMediaType.MOVIE::equals).orElse(true);
        List<TmdbCandidate> series = List.of();
        List<TmdbCandidate> movies = List.of();
        if (wantsSeries) {
            String tvPath = "search/tv?" + common
                    + criteria.year().map(year -> "&first_air_date_year=" + year).orElse("");
            series = mapSearch(send(tvPath, credentials, TmdbSearchResponseDto.class), TmdbMediaType.SERIES);
        }
        if (wantsMovies) {
            String moviePath = "search/movie?" + common
                    + criteria.year().map(year -> "&primary_release_year=" + year).orElse("");
            movies = mapSearch(send(moviePath, credentials, TmdbSearchResponseDto.class), TmdbMediaType.MOVIE);
        }
        return new TmdbSearchResult(series, movies);
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
        return seriesDetails(identity, requestedOrder, null, credentials);
    }

    @Override
    public TmdbSeriesDetails seriesDetails(
            TmdbIdentity identity,
            TmdbEpisodeGroup requestedGroup,
            JanusConfiguration credentials) {
        TmdbEpisodeGroup safeGroup = requestedGroup == null ? TmdbEpisodeGroup.aired() : requestedGroup;
        return seriesDetails(identity, safeGroup.order(), safeGroup, credentials);
    }

    private TmdbSeriesDetails seriesDetails(
            TmdbIdentity identity,
            TmdbEpisodeOrder requestedOrder,
            TmdbEpisodeGroup requestedGroup,
            JanusConfiguration credentials) {
        Objects.requireNonNull(identity, "identity");
        TmdbEpisodeOrder safeOrder = requestedOrder == null ? TmdbEpisodeOrder.AIRED : requestedOrder;
        String id = encode(identity.id());
        // The series record and its episode group index travel together:
        // append_to_response is what stops a show from costing one round trip
        // per thing we need to know about it.
        JsonObject payload = sendObject(
                "tv/" + id + "?language=" + LANGUAGE + "&append_to_response=episode_groups", credentials);
        TmdbSeriesResponseDto series = gson.fromJson(payload, TmdbSeriesResponseDto.class);
        TmdbEpisodeGroupsDto groupIndex = appended(payload, "episode_groups", TmdbEpisodeGroupsDto.class)
                .or(() -> sendIfFound("tv/" + id + "/episode_groups", credentials, TmdbEpisodeGroupsDto.class))
                .orElseGet(TmdbEpisodeGroupsDto::new);
        List<TmdbEpisodeGroup> availableGroups = episodeGroups(groupIndex);
        TmdbIdentity resolvedIdentity = new TmdbIdentity(
                series.id == null ? identity.id() : String.valueOf(series.id),
                TmdbMediaType.SERIES,
                fallback(series.name, series.original_name, identity.displayName()));

        if (safeOrder == TmdbEpisodeOrder.AIRED && (requestedGroup == null || requestedGroup.isAired())) {
            List<TmdbEpisode> aired = loadAiredEpisodes(id, series.seasons, credentials);
            return TmdbSeriesDetails.forGroup(
                    resolvedIdentity, availableGroups, TmdbEpisodeGroup.aired(), aired);
        }

        Optional<TmdbEpisodeGroupsDto.GroupSummary> selected = summaries(groupIndex).stream()
                .filter(group -> requestedGroup == null
                        ? group.type != null && group.type == safeOrder.groupType()
                        : requestedGroup.id().equals(group.id))
                .filter(group -> group.id != null && !group.id.isBlank())
                .min(Comparator.comparingInt(group -> group.order == null ? Integer.MAX_VALUE : group.order));
        if (selected.isEmpty()) {
            TmdbEpisodeGroup missing = requestedGroup == null
                    ? TmdbEpisodeGroup.legacy(safeOrder)
                    : requestedGroup;
            return TmdbSeriesDetails.forGroup(resolvedIdentity, availableGroups, missing, List.of());
        }
        TmdbEpisodeGroup selectedGroup = mapEpisodeGroup(selected.orElseThrow()).orElseThrow();
        TmdbEpisodeGroupDetailsDto group = send(
                "tv/episode_group/" + encode(selected.orElseThrow().id),
                credentials,
                TmdbEpisodeGroupDetailsDto.class);
        List<TmdbEpisode> ordered = safeOrder == TmdbEpisodeOrder.ABSOLUTE
                ? mapAbsoluteEpisodes(group)
                : mapGroupedEpisodes(group);
        return TmdbSeriesDetails.forGroup(resolvedIdentity, availableGroups, selectedGroup, ordered);
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

    /**
     * Loads every aired season of a show. Seasons are asked for twenty at a
     * time through {@code append_to_response}, so a long-running series costs
     * one request instead of one per season. Whatever the gateway does not
     * append is still fetched the long way, so a change on that side costs
     * speed and never episodes.
     */
    private List<TmdbEpisode> loadAiredEpisodes(
            String seriesId,
            List<TmdbSeriesResponseDto.SeasonSummary> seasons,
            JanusConfiguration credentials) {
        List<Integer> numbers = seasons == null
                ? List.of()
                : seasons.stream()
                        .filter(season -> season != null && season.season_number != null)
                        .map(season -> season.season_number)
                        .distinct()
                        .sorted()
                        .toList();
        Map<Integer, TmdbSeriesResponseDto.SeasonDetails> loaded = new LinkedHashMap<>();
        for (int from = 0; from < numbers.size(); from += MAX_APPENDED_RESOURCES) {
            List<Integer> batch = numbers.subList(from, Math.min(numbers.size(), from + MAX_APPENDED_RESOURCES));
            String append = batch.stream().map(number -> "season/" + number).collect(Collectors.joining(","));
            JsonObject payload = sendObject(
                    "tv/" + seriesId + "?language=" + LANGUAGE + "&append_to_response=" + append, credentials);
            for (Integer number : batch) {
                appended(payload, "season/" + number, TmdbSeriesResponseDto.SeasonDetails.class)
                        .ifPresent(details -> loaded.put(number, details));
            }
        }
        List<TmdbEpisode> episodes = new ArrayList<>();
        for (Integer number : numbers) {
            TmdbSeriesResponseDto.SeasonDetails details = loaded.containsKey(number)
                    ? loaded.get(number)
                    : send("tv/" + seriesId + "/season/" + number + "?language=" + LANGUAGE,
                            credentials, TmdbSeriesResponseDto.SeasonDetails.class);
            if (details == null || details.episodes == null) continue;
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

    private static List<TmdbEpisode> mapGroupedEpisodes(TmdbEpisodeGroupDetailsDto details) {
        List<TmdbEpisode> episodes = new ArrayList<>();
        for (TmdbEpisodeGroupDetailsDto.Group group : orderedGroups(details)) {
            int orderedSeason = safeOrder(group.order) + 1;
            for (TmdbEpisodeGroupDetailsDto.Episode episode : orderedEpisodes(group)) {
                if (episode.id == null) continue;
                boolean special = episode.season_number != null && episode.season_number == 0;
                int season = special ? 0 : orderedSeason;
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

    private static List<TmdbEpisodeGroup> episodeGroups(TmdbEpisodeGroupsDto dto) {
        List<TmdbEpisodeGroup> groups = new ArrayList<>();
        groups.add(TmdbEpisodeGroup.aired());
        summaries(dto).stream().map(HttpTmdbClient::mapEpisodeGroup).flatMap(Optional::stream)
                .forEach(groups::add);
        return List.copyOf(groups);
    }

    private static Optional<TmdbEpisodeGroup> mapEpisodeGroup(TmdbEpisodeGroupsDto.GroupSummary group) {
        if (group == null || group.id == null || group.id.isBlank() || group.type == null) {
            return Optional.empty();
        }
        return TmdbEpisodeOrder.fromGroupType(group.type).map(order -> new TmdbEpisodeGroup(
                group.id,
                fallback(group.name, order.name()),
                order,
                safeOrder(group.group_count),
                safeOrder(group.episode_count)));
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

    /** Reads a payload that carries appended sub-resources under composite keys. */
    private JsonObject sendObject(String path, JanusConfiguration credentials) {
        JsonObject payload = send(path, credentials, JsonObject.class);
        return payload == null ? new JsonObject() : payload;
    }

    private <T> Optional<T> appended(JsonObject payload, String field, Class<T> type) {
        if (payload == null) return Optional.empty();
        JsonElement element = payload.get(field);
        if (element == null || !element.isJsonObject()) return Optional.empty();
        return Optional.ofNullable(gson.fromJson(element, type));
    }

    private <T> Optional<T> sendIfFound(String path, JanusConfiguration credentials, Class<T> type) {
        HttpResponse<String> response = sendResponse(path, credentials);
        if (response.statusCode() == 404) return Optional.empty();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw requestFailed(response.statusCode());
        }
        return Optional.ofNullable(gson.fromJson(response.body(), type));
    }

    /**
     * Every call leaves through the pacer, which holds the application-wide
     * request budget: a library-sized scan and a single manual search share the
     * same allowance rather than competing for it.
     */
    private HttpResponse<String> sendResponse(String path, JanusConfiguration credentials) {
        Instant queued = Instant.now();
        try {
            return pacer.send(() -> {
                Instant start = Instant.now();
                HttpResponse<String> sent = sendOnce(request(path, credentials));
                publishTrace("GET", path, sent, Duration.between(start, Instant.now()).toMillis(), null);
                return sent;
            });
        } catch (TmdbException exception) {
            publishFailure("GET", path, Duration.between(queued, Instant.now()).toMillis(), exception.getMessage());
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
        // User-facing messages stay free of transport detail; the status code and
        // gateway wording belong to the diagnostic field, which never reaches the UI.
        if (statusCode == 401) {
            return TmdbException.recoverable("TMDB_AUTH_FAILED", "TMDB is unavailable right now.",
                    "Janus rejected the Episort caller credentials (HTTP 401).");
        }
        if (statusCode == 429) {
            return TmdbException.recoverable("TMDB_RATE_LIMITED", "TMDB is temporarily limiting requests.",
                    "Janus returned HTTP 429. Retry-After is managed by the gateway and caller workflow.");
        }
        return TmdbException.recoverable("TMDB_REQUEST_FAILED",
                "TMDB did not answer this request.",
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

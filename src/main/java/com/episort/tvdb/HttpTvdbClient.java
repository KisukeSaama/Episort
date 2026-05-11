package com.episort.tvdb;

import com.episort.config.TvdbCredentials;
import com.episort.tvdb.dto.TvdbLoginResponseDto;
import com.episort.tvdb.dto.TvdbMovieResponseDto;
import com.episort.tvdb.dto.TvdbSearchResponseDto;
import com.episort.tvdb.dto.TvdbSeriesResponseDto;
import com.google.gson.Gson;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class HttpTvdbClient implements TvdbClient {
    private static final URI DEFAULT_BASE_URI = URI.create("https://api4.thetvdb.com/v4/");
    private static final Set<Integer> TRANSIENT_STATUS_CODES = Set.of(408, 429, 500, 502, 503, 504);

    private final HttpClient httpClient;
    private final URI baseUri;
    private final Gson gson;
    private String bearerToken;

    public HttpTvdbClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), DEFAULT_BASE_URI);
    }

    HttpTvdbClient(HttpClient httpClient, URI baseUri) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.baseUri = normalizeBaseUri(baseUri);
        this.gson = new Gson();
    }

    @Override
    public TvdbSearchResult search(String query, TvdbCredentials credentials) {
        String encoded = URLEncoder.encode(query == null ? "" : query, StandardCharsets.UTF_8);
        TvdbSearchResponseDto dto = send(
                "search?query=" + encoded,
                credentials,
                TvdbSearchResponseDto.class);
        List<TvdbCandidate> series = new ArrayList<>();
        List<TvdbCandidate> movies = new ArrayList<>();
        int rank = 0;
        List<TvdbSearchResponseDto.Item> items = dto.data == null || dto.data.items == null
                ? List.of()
                : dto.data.items;
        for (TvdbSearchResponseDto.Item item : items) {
            Optional<TvdbCandidate> candidate = mapCandidate(item, rank++);
            candidate.ifPresent(value -> {
                if (value.identity().mediaType() == TvdbMediaType.SERIES) {
                    series.add(value);
                } else {
                    movies.add(value);
                }
            });
        }
        return new TvdbSearchResult(series, movies);
    }

    @Override
    public TvdbSeriesDetails seriesDetails(TvdbIdentity identity, TvdbCredentials credentials) {
        Objects.requireNonNull(identity, "identity");
        TvdbSeriesResponseDto dto = send("series/" + encodePath(identity.id()) + "/extended?meta=episodes", credentials,
                TvdbSeriesResponseDto.class);
        if (dto.data == null) {
            throw TvdbException.recoverable("TVDB_SERIES_EMPTY", "TVDB returned no series details.",
                    "Empty data object from TVDB series details response.");
        }
        String name = fallback(dto.data.name, dto.data.translationsName, identity.displayName());
        List<TvdbEpisode> episodes = (dto.data.episodes == null ? List.<TvdbSeriesResponseDto.Episode>of() : dto.data.episodes)
                .stream()
                .map(HttpTvdbClient::mapEpisode)
                .flatMap(Optional::stream)
                .toList();
        Set<TvdbEpisodeOrder> orders = detectOrders(dto.data.airsOrder, episodes);
        return new TvdbSeriesDetails(
                new TvdbIdentity(fallback(dto.data.id, identity.id()), TvdbMediaType.SERIES, name),
                orders,
                episodes,
                episodes,
                episodes.stream().filter(episode -> episode.absoluteNumber().isPresent()).toList());
    }

    @Override
    public TvdbMovieDetails movieDetails(TvdbIdentity identity, TvdbCredentials credentials) {
        Objects.requireNonNull(identity, "identity");
        TvdbMovieResponseDto dto = send("movies/" + encodePath(identity.id()) + "/extended", credentials,
                TvdbMovieResponseDto.class);
        if (dto.data == null) {
            throw TvdbException.recoverable("TVDB_MOVIE_EMPTY", "TVDB returned no movie details.",
                    "Empty data object from TVDB movie details response.");
        }
        String name = fallback(dto.data.name, dto.data.translationsName, identity.displayName());
        return new TvdbMovieDetails(
                new TvdbIdentity(fallback(dto.data.id, identity.id()), TvdbMediaType.MOVIE, name),
                parseYear(fallback(dto.data.year, firstFour(dto.data.releaseDate), "")));
    }

    private <T> T send(String path, TvdbCredentials credentials, Class<T> type) {
        ensureToken(credentials);
        HttpResponse<String> response = sendWithRetry(request(path, bearerToken));
        if (response.statusCode() == 401) {
            bearerToken = null;
            ensureToken(credentials);
            response = sendWithRetry(request(path, bearerToken));
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw TvdbException.recoverable("TVDB_REQUEST_FAILED", "TVDB lookup failed.",
                    "TVDB returned HTTP " + response.statusCode() + ". Response body omitted.");
        }
        return gson.fromJson(response.body(), type);
    }

    private void ensureToken(TvdbCredentials credentials) {
        if (bearerToken != null && !bearerToken.isBlank()) {
            return;
        }
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("login"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(loginBody(credentials)))
                .build();
        HttpResponse<String> response = sendWithRetry(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw TvdbException.recoverable("TVDB_AUTH_FAILED", "TVDB authentication failed.",
                    "TVDB login failed with HTTP " + response.statusCode() + ". Credentials and body omitted.");
        }
        TvdbLoginResponseDto dto = gson.fromJson(response.body(), TvdbLoginResponseDto.class);
        if (dto == null || dto.data == null || dto.data.token == null || dto.data.token.isBlank()) {
            throw TvdbException.recoverable("TVDB_AUTH_TOKEN_MISSING", "TVDB authentication did not return a token.",
                    "Login response did not include data.token.");
        }
        bearerToken = dto.data.token;
    }

    private HttpResponse<String> sendWithRetry(HttpRequest request) {
        IOException lastIo = null;
        InterruptedException lastInterrupted = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (!TRANSIENT_STATUS_CODES.contains(response.statusCode()) || attempt == 2) {
                    return response;
                }
            } catch (IOException exception) {
                lastIo = exception;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                lastInterrupted = exception;
                break;
            }
        }
        if (lastInterrupted != null) {
            throw TvdbException.recoverable("TVDB_INTERRUPTED", "TVDB lookup was interrupted.",
                    "Interrupted while calling TVDB.");
        }
        throw TvdbException.recoverable("TVDB_UNAVAILABLE", "TVDB is unavailable.",
                lastIo == null ? "TVDB did not return a usable response." : "Network error while calling TVDB.");
    }

    private HttpRequest request(String path, String token) {
        return HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .GET()
                .build();
    }

    private String loginBody(TvdbCredentials credentials) {
        StringBuilder builder = new StringBuilder("{\"apikey\":\"")
                .append(escapeJson(credentials.apiKey()))
                .append("\"");
        credentials.subscriberPin().ifPresent(pin -> builder.append(",\"pin\":\"").append(escapeJson(pin)).append("\""));
        return builder.append("}").toString();
    }

    private static Optional<TvdbCandidate> mapCandidate(TvdbSearchResponseDto.Item item, int rank) {
        if (item == null) {
            return Optional.empty();
        }
        Optional<TvdbMediaType> mediaType = mediaType(fallback(item.type, item.recordType));
        if (mediaType.isEmpty()) {
            return Optional.empty();
        }
        String id = fallback(item.tvdb_id, item.id, "");
        String name = fallback(item.name, item.translationsName, "");
        if (id.isBlank() || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new TvdbCandidate(
                new TvdbIdentity(id, mediaType.orElseThrow(), name),
                optional(item.overview),
                parseYear(item.year),
                optional(item.country),
                optional(item.network),
                OptionalDoubleScore.empty(),
                rank));
    }

    private static Optional<TvdbEpisode> mapEpisode(TvdbSeriesResponseDto.Episode episode) {
        if (episode == null || episode.id == null || episode.seasonNumber == null || episode.number == null) {
            return Optional.empty();
        }
        return Optional.of(new TvdbEpisode(
                episode.id,
                episode.seasonNumber,
                episode.number,
                Optional.ofNullable(episode.absoluteNumber),
                fallback(episode.name, episode.translationsName, "Untitled episode"),
                episode.seasonNumber == 0));
    }

    private static Set<TvdbEpisodeOrder> detectOrders(List<String> providerOrders, List<TvdbEpisode> episodes) {
        EnumSet<TvdbEpisodeOrder> orders = EnumSet.of(TvdbEpisodeOrder.AIRED);
        if (episodes.stream().anyMatch(episode -> episode.absoluteNumber().isPresent())) {
            orders.add(TvdbEpisodeOrder.ABSOLUTE);
        }
        if (providerOrders != null) {
            for (String order : providerOrders) {
                String normalized = order == null ? "" : order.toLowerCase(java.util.Locale.ROOT);
                if (normalized.contains("dvd")) {
                    orders.add(TvdbEpisodeOrder.DVD);
                }
                if (normalized.contains("absolute")) {
                    orders.add(TvdbEpisodeOrder.ABSOLUTE);
                }
            }
        }
        return orders;
    }

    private static Optional<TvdbMediaType> mediaType(String type) {
        String normalized = type == null ? "" : type.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.equals("series") || normalized.equals("tvseries")) {
            return Optional.of(TvdbMediaType.SERIES);
        }
        if (normalized.equals("movie") || normalized.equals("movies")) {
            return Optional.of(TvdbMediaType.MOVIE);
        }
        return Optional.empty();
    }

    private static Optional<Integer> parseYear(String value) {
        try {
            String year = firstFour(value);
            return year.isBlank() ? Optional.empty() : Optional.of(Integer.parseInt(year));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static String firstFour(String value) {
        return value == null || value.length() < 4 ? "" : value.substring(0, 4);
    }

    private static Optional<String> optional(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }

    private static String fallback(String primary, String fallback) {
        return fallback(primary, fallback, "");
    }

    private static String fallback(String primary, String secondary, String tertiary) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        if (secondary != null && !secondary.isBlank()) {
            return secondary.trim();
        }
        return tertiary == null ? "" : tertiary.trim();
    }

    private static String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static URI normalizeBaseUri(URI uri) {
        String value = Objects.requireNonNull(uri, "uri").toString();
        return URI.create(value.endsWith("/") ? value : value + "/");
    }
}

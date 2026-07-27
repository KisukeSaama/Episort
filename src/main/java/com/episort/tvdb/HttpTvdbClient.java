package com.episort.tvdb;

import com.episort.config.TvdbCredentials;
import com.episort.tvdb.debug.TvdbRequestBus;
import com.episort.tvdb.debug.TvdbRequestTrace;
import com.episort.tvdb.dto.TvdbLoginResponseDto;
import com.episort.tvdb.dto.TvdbMovieResponseDto;
import com.episort.tvdb.dto.TvdbSearchResponseDto;
import com.episort.tvdb.dto.TvdbSeriesResponseDto;
import com.episort.tvdb.dto.TvdbTranslationResponseDto;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class HttpTvdbClient implements TvdbClient {
    private static final URI DEFAULT_BASE_URI = URI.create("https://api4.thetvdb.com/v4/");
    private static final URI TVDB_IMAGE_BASE_URI = URI.create("https://artworks.thetvdb.com/");
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
        List<TvdbSearchResponseDto.Item> items = dto.data == null ? List.of() : dto.data;
        for (TvdbSearchResponseDto.Item item : items) {
            Optional<TvdbCandidate> candidate = mapCandidate(item, rank++, credentials);
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
        // The localized episodes endpoint returns the series name and every
        // episode title in the requested language, with automatic fallback to
        // the original when no translation exists. One call, English-first.
        TvdbSeriesResponseDto dto = fetchSeriesEpisodes(identity.id(), "default", credentials)
                .orElseThrow(() -> TvdbException.recoverable("TVDB_SERIES_EMPTY", "TVDB returned no series details.",
                        "Empty data object from TVDB series details response."));
        if (dto.data == null) {
            throw TvdbException.recoverable("TVDB_SERIES_EMPTY", "TVDB returned no series details.",
                    "Empty data object from TVDB series details response.");
        }
        TvdbSeriesResponseDto.Series series = dto.data.series;
        String seriesId = series == null ? identity.id() : fallback(series.id, identity.id());
        // The episodes endpoint returns series.name in TVDB's canonical
        // language (often Japanese for anime, etc.). Fetch the English
        // translation explicitly to override it when available.
        String englishSeriesName = fetchEnglishSeriesName(identity.id(), credentials);
        String name = fallback(
                englishSeriesName,
                series == null ? null : series.name,
                identity.displayName());
        List<TvdbSeriesResponseDto.Episode> raw = dto.data.episodes == null ? List.of() : dto.data.episodes;
        List<TvdbEpisode> episodes = raw.stream()
                .map(HttpTvdbClient::mapEpisode)
                .flatMap(Optional::stream)
                .toList();
        List<TvdbEpisode> dvdEpisodes = fetchSeriesEpisodes(identity.id(), "dvd", credentials)
                .map(HttpTvdbClient::mapEpisodes)
                .orElse(List.of());
        List<TvdbEpisode> absoluteEpisodes = fetchSeriesEpisodes(identity.id(), "absolute", credentials)
                .map(HttpTvdbClient::mapEpisodes)
                .orElseGet(() -> episodes.stream().filter(episode -> episode.absoluteNumber().isPresent()).toList());
        Set<TvdbEpisodeOrder> orders = detectOrders(series == null ? null : series.airsOrder,
                episodes, dvdEpisodes, absoluteEpisodes);
        return new TvdbSeriesDetails(
                new TvdbIdentity(seriesId, TvdbMediaType.SERIES, name),
                orders,
                episodes,
                dvdEpisodes.isEmpty() ? episodes : dvdEpisodes,
                absoluteEpisodes);
    }

    @Override
    public TvdbMovieDetails movieDetails(TvdbIdentity identity, TvdbCredentials credentials) {
        Objects.requireNonNull(identity, "identity");
        TvdbMovieResponseDto dto = send(
                "movies/" + encodePath(identity.id()) + "/extended?meta=translations&short=false",
                credentials,
                TvdbMovieResponseDto.class);
        if (dto.data == null) {
            throw TvdbException.recoverable("TVDB_MOVIE_EMPTY", "TVDB returned no movie details.",
                    "Empty data object from TVDB movie details response.");
        }
        String englishName = pickEnglishTranslation(dto.data.translations);
        String name = fallback(englishName, dto.data.name, dto.data.translationsName, identity.displayName());
        return new TvdbMovieDetails(
                new TvdbIdentity(fallback(dto.data.id, identity.id()), TvdbMediaType.MOVIE, name),
                parseYear(fallback(dto.data.year, firstFour(dto.data.releaseDate), "")));
    }

    private String fetchEnglishSeriesName(String id, TvdbCredentials credentials) {
        try {
            TvdbTranslationResponseDto dto = send(
                    "series/" + encodePath(id) + "/translations/eng",
                    credentials,
                    TvdbTranslationResponseDto.class);
            if (dto != null && dto.data != null && dto.data.name != null && !dto.data.name.isBlank()) {
                return dto.data.name.trim();
            }
        } catch (TvdbException ignored) {
            // English translation may not exist; fall back to original.
        }
        return null;
    }

    private Optional<TvdbSeriesResponseDto> fetchSeriesEpisodes(
            String id, String order, TvdbCredentials credentials) {
        try {
            return Optional.of(send(
                    "series/" + encodePath(id) + "/episodes/" + encodePath(order) + "/eng?page=0",
                    credentials,
                    TvdbSeriesResponseDto.class));
        } catch (TvdbException ex) {
            return Optional.empty();
        }
    }

    private static String pickEnglishTranslation(TvdbMovieResponseDto.Translations translations) {
        if (translations == null || translations.nameTranslations == null) return null;
        for (TvdbMovieResponseDto.NameTranslation entry : translations.nameTranslations) {
            if (entry != null && "eng".equalsIgnoreCase(entry.language) && entry.name != null && !entry.name.isBlank()) {
                return entry.name;
            }
        }
        return null;
    }

    private static String fallback(String primary, String secondary, String tertiary, String quaternary) {
        if (primary != null && !primary.isBlank()) return primary.trim();
        if (secondary != null && !secondary.isBlank()) return secondary.trim();
        if (tertiary != null && !tertiary.isBlank()) return tertiary.trim();
        return quaternary == null ? "" : quaternary.trim();
    }

    private <T> T send(String path, TvdbCredentials credentials, Class<T> type) {
        ensureToken(credentials);
        Instant start = Instant.now();
        HttpResponse<String> response;
        try {
            response = sendWithRetry(request(path, bearerToken));
        } catch (TvdbException ex) {
            publishFailure("GET", path, Duration.between(start, Instant.now()).toMillis(), ex.getMessage());
            throw ex;
        }
        if (response.statusCode() == 401) {
            bearerToken = null;
            ensureToken(credentials);
            start = Instant.now();
            response = sendWithRetry(request(path, bearerToken));
        }
        long ms = Duration.between(start, Instant.now()).toMillis();
        publishTrace("GET", path, response, ms, null);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw TvdbException.recoverable("TVDB_REQUEST_FAILED",
                    "TVDB lookup failed (HTTP " + response.statusCode() + ").",
                    "TVDB returned HTTP " + response.statusCode() + ". Response body omitted.");
        }
        return gson.fromJson(response.body(), type);
    }

    private static void publishTrace(String method, String path, HttpResponse<String> response, long ms, String error) {
        try {
            String body = response == null ? "" : response.body();
            if (body == null) body = "";
            if (body.length() > 4000) body = body.substring(0, 4000) + "…";
            int status = response == null ? 0 : response.statusCode();
            TvdbRequestBus.get().publish(new TvdbRequestTrace(
                    Instant.now(), method, path, status, ms, body, error, false));
        } catch (RuntimeException ignored) {
            // Instrumentation must never break the request path.
        }
    }

    private static void publishFailure(String method, String path, long ms, String error) {
        try {
            TvdbRequestBus.get().publish(new TvdbRequestTrace(
                    Instant.now(), method, path, 0, ms, "", error, false));
        } catch (RuntimeException ignored) {
            // ignore
        }
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
            throw TvdbException.recoverable("TVDB_AUTH_FAILED",
                    "TVDB authentication failed (HTTP " + response.statusCode() + ").",
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
                .header("Accept-Language", "eng, fra;q=0.9")
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

    private Optional<TvdbCandidate> mapCandidate(TvdbSearchResponseDto.Item item, int rank, TvdbCredentials credentials) {
        if (item == null) {
            return Optional.empty();
        }
        Optional<TvdbMediaType> mediaType = mediaType(fallback(item.type, item.recordType));
        if (mediaType.isEmpty()) {
            return Optional.empty();
        }
        String id = fallback(item.tvdb_id, item.id, "");
        String englishName = item.translations == null ? null : item.translations.get("eng");
        String name = fallback(englishName, item.name, item.translationsName);
        if (id.isBlank() || name.isBlank()) {
            return Optional.empty();
        }
        Optional<String> englishOverview = optional(item.overviews == null ? null : item.overviews.get("eng"));
        Optional<String> frenchOverview = optional(item.overviews == null ? null : item.overviews.get("fra"));
        if (englishOverview.isEmpty() || frenchOverview.isEmpty()) {
            TranslationPair translations = fetchCandidateTranslations(id, mediaType.orElseThrow(), credentials);
            englishOverview = englishOverview.or(translations::englishOverview);
            frenchOverview = frenchOverview.or(translations::frenchOverview);
        }
        return Optional.of(new TvdbCandidate(
                new TvdbIdentity(id, mediaType.orElseThrow(), name),
                optional(normalizeImageUrl(item.image_url)),
                optional(item.overview),
                englishOverview,
                frenchOverview,
                parseYear(item.year),
                optional(item.country),
                optional(item.network),
                OptionalDoubleScore.empty(),
                rank,
                alternateTitles(item, name)));
    }

    /**
     * Every other title TVDB knows for this record: the raw name, the localized
     * translations and the declared aliases. The scorer compares the parsed
     * folder name against all of them, so a French or original-language folder
     * still matches the English record we display.
     */
    private static List<String> alternateTitles(TvdbSearchResponseDto.Item item, String primaryName) {
        List<String> titles = new ArrayList<>();
        titles.add(item.name);
        titles.add(item.translationsName);
        if (item.translations != null) {
            titles.addAll(item.translations.values());
        }
        if (item.aliases != null) {
            titles.addAll(item.aliases);
        }
        return titles.stream()
                .filter(title -> title != null && !title.isBlank() && !title.equals(primaryName))
                .distinct()
                .toList();
    }

    private TranslationPair fetchCandidateTranslations(String id, TvdbMediaType mediaType, TvdbCredentials credentials) {
        return new TranslationPair(
                fetchOverviewTranslation(id, mediaType, "eng", credentials),
                fetchOverviewTranslation(id, mediaType, "fra", credentials));
    }

    private Optional<String> fetchOverviewTranslation(
            String id, TvdbMediaType mediaType, String language, TvdbCredentials credentials) {
        String prefix = mediaType == TvdbMediaType.MOVIE ? "movies/" : "series/";
        try {
            TvdbTranslationResponseDto dto = send(
                    prefix + encodePath(id) + "/translations/" + encodePath(language),
                    credentials,
                    TvdbTranslationResponseDto.class);
            return dto == null || dto.data == null ? Optional.empty() : optional(dto.data.overview);
        } catch (TvdbException ex) {
            return Optional.empty();
        }
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
                fallback(episode.name, "Untitled episode"),
                episode.seasonNumber == 0));
    }

    private static List<TvdbEpisode> mapEpisodes(TvdbSeriesResponseDto dto) {
        if (dto == null || dto.data == null || dto.data.episodes == null) {
            return List.of();
        }
        return dto.data.episodes.stream()
                .map(HttpTvdbClient::mapEpisode)
                .flatMap(Optional::stream)
                .toList();
    }

    private static Set<TvdbEpisodeOrder> detectOrders(
            List<String> providerOrders,
            List<TvdbEpisode> airedEpisodes,
            List<TvdbEpisode> dvdEpisodes,
            List<TvdbEpisode> absoluteEpisodes) {
        EnumSet<TvdbEpisodeOrder> orders = EnumSet.of(TvdbEpisodeOrder.AIRED);
        if (!dvdEpisodes.isEmpty()) {
            orders.add(TvdbEpisodeOrder.DVD);
        }
        if (!absoluteEpisodes.isEmpty()
                || airedEpisodes.stream().anyMatch(episode -> episode.absoluteNumber().isPresent())) {
            orders.add(TvdbEpisodeOrder.ABSOLUTE);
        }
        if (providerOrders != null) {
            for (String order : providerOrders) {
                String normalized = order == null ? "" : order.toLowerCase(Locale.ROOT);
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

    private static String normalizeImageUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim().replace(" ", "%20");
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        String relative = trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
        return TVDB_IMAGE_BASE_URI.resolve(relative).toString();
    }

    private record TranslationPair(Optional<String> englishOverview, Optional<String> frenchOverview) {
    }

    private static Optional<TvdbMediaType> mediaType(String type) {
        String normalized = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
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

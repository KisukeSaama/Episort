package com.episort.fankai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Public, unauthenticated metadata.fankai.fr API, the same one FanKarr reads:
 * {@code /series}, then {@code /series/{id}/seasons} and
 * {@code /seasons/{id}/episodes}. No key is involved, so it does not go
 * through Janus.
 *
 * <p>The catalog changes a few times a month and a library scan asks for it
 * once per group, so answers are kept in memory for an hour.
 */
public final class HttpFankaiClient implements FankaiClient {
    private static final URI DEFAULT_BASE_URI = URI.create("https://metadata.fankai.fr/");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    /**
     * A scan asks once per group. When the catalog is down, the first failure
     * is replayed for a while instead of every group waiting on its own
     * timeout.
     */
    private static final Duration FAILURE_BACKOFF = Duration.ofMinutes(5);

    private final HttpClient httpClient;
    private final URI baseUri;
    private final Clock clock;
    private final Object seriesLock = new Object();
    private Cached<List<FankaiSeries>> seriesCache;
    private FankaiException lastSeriesFailure;
    private Instant retrySeriesAfter = Instant.MIN;
    private final Map<Integer, Cached<List<FankaiEpisode>>> episodesCache = new ConcurrentHashMap<>();

    public HttpFankaiClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), DEFAULT_BASE_URI, Clock.systemUTC());
    }

    HttpFankaiClient(HttpClient httpClient, URI baseUri, Clock clock) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        String value = Objects.requireNonNull(baseUri, "baseUri").toString();
        this.baseUri = URI.create(value.endsWith("/") ? value : value + "/");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public List<FankaiSeries> series() {
        synchronized (seriesLock) {
            Instant now = clock.instant();
            if (seriesCache != null && !seriesCache.expired(now)) return seriesCache.value();
            if (lastSeriesFailure != null && now.isBefore(retrySeriesAfter)) {
                throw new FankaiException(lastSeriesFailure.getMessage(), lastSeriesFailure);
            }
            try {
                seriesCache = new Cached<>(loadSeries(), now.plus(CACHE_TTL));
                lastSeriesFailure = null;
                return seriesCache.value();
            } catch (FankaiException failure) {
                lastSeriesFailure = failure;
                retrySeriesAfter = now.plus(FAILURE_BACKOFF);
                throw failure;
            }
        }
    }

    @Override
    public List<FankaiEpisode> episodes(int seriesId) {
        return episodesCache.compute(seriesId, (id, cached) ->
                cached == null || cached.expired(clock.instant())
                        ? new Cached<>(loadEpisodes(id), clock.instant().plus(CACHE_TTL))
                        : cached).value();
    }

    private List<FankaiSeries> loadSeries() {
        List<FankaiSeries> series = new ArrayList<>();
        for (JsonElement element : list(get("series"), "series")) {
            if (!element.isJsonObject()) continue;
            JsonObject json = element.getAsJsonObject();
            Optional<Integer> id = integer(json, "id");
            Optional<String> title = text(json, "title");
            if (id.isEmpty() || title.isEmpty()) continue;
            List<String> alternates = new ArrayList<>();
            text(json, "title_for_plex").ifPresent(alternates::add);
            text(json, "show_title").ifPresent(alternates::add);
            text(json, "original_title").ifPresent(alternates::add);
            series.add(new FankaiSeries(
                    id.get(),
                    title.get(),
                    alternates.stream().filter(alternate -> !alternate.equals(title.get())).distinct().toList(),
                    object(json, "ids").flatMap(ids -> text(ids, "tmdb")),
                    object(json, "images").flatMap(images -> text(images, "poster"))
                            .or(() -> text(json, "poster_image")),
                    text(json, "plot"),
                    integer(json, "year").or(() -> text(json, "premiered").flatMap(HttpFankaiClient::leadingYear)),
                    text(json, "country")));
        }
        return List.copyOf(series);
    }

    private List<FankaiEpisode> loadEpisodes(int seriesId) {
        List<FankaiEpisode> episodes = new ArrayList<>();
        for (JsonElement element : list(get("series/" + seriesId + "/seasons"), "seasons")) {
            if (!element.isJsonObject()) continue;
            JsonObject season = element.getAsJsonObject();
            Optional<Integer> seasonId = integer(season, "id");
            Optional<Integer> seasonNumber = integer(season, "season_number");
            if (seasonId.isEmpty() || seasonNumber.isEmpty()) continue;
            for (JsonElement episodeElement : list(get("seasons/" + seasonId.get() + "/episodes"), "episodes")) {
                if (!episodeElement.isJsonObject()) continue;
                JsonObject episode = episodeElement.getAsJsonObject();
                Optional<Integer> id = integer(episode, "id");
                Optional<Integer> number = integer(episode, "episode_number");
                if (id.isEmpty() || number.isEmpty()) continue;
                episodes.add(new FankaiEpisode(
                        id.get(), seasonNumber.get(), number.get(), text(episode, "title").orElse("")));
            }
        }
        episodes.sort(Comparator.comparingInt(FankaiEpisode::seasonNumber)
                .thenComparingInt(FankaiEpisode::episodeNumber));
        return List.copyOf(episodes);
    }

    private JsonElement get(String path) {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new FankaiException("Fankai catalog unreachable on /" + path + ".", failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new FankaiException("Fankai request interrupted on /" + path + ".", interrupted);
        }
        if (response.statusCode() != 200) {
            throw new FankaiException("Fankai catalog answered HTTP " + response.statusCode() + " on /" + path + ".");
        }
        try {
            return JsonParser.parseString(response.body());
        } catch (JsonParseException failure) {
            throw new FankaiException("Fankai catalog returned unreadable JSON on /" + path + ".", failure);
        }
    }

    /** The API answers with a bare array; an object wrapping it under {@code key} is read too. */
    private static JsonArray list(JsonElement body, String key) {
        if (body != null && body.isJsonArray()) return body.getAsJsonArray();
        if (body != null && body.isJsonObject()
                && body.getAsJsonObject().get(key) instanceof JsonArray wrapped) return wrapped;
        return new JsonArray();
    }

    private static Optional<JsonObject> object(JsonObject json, String key) {
        return json.get(key) instanceof JsonObject value ? Optional.of(value) : Optional.empty();
    }

    private static Optional<String> text(JsonObject json, String key) {
        JsonElement value = json.get(key);
        if (value == null || !value.isJsonPrimitive()) return Optional.empty();
        String text = value.getAsString().trim();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    private static Optional<Integer> integer(JsonObject json, String key) {
        JsonElement value = json.get(key);
        if (value == null || !value.isJsonPrimitive()) return Optional.empty();
        try {
            return Optional.of(value.getAsInt());
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    private static Optional<Integer> leadingYear(String date) {
        return date.matches("\\d{4}.*") ? Optional.of(Integer.parseInt(date.substring(0, 4))) : Optional.empty();
    }

    private record Cached<T>(T value, Instant expiresAt) {
        boolean expired(Instant now) {
            return !now.isBefore(expiresAt);
        }
    }
}

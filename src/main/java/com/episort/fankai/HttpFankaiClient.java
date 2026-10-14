package com.episort.fankai;

import com.episort.config.JanusConfiguration;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The metadata.fankai.fr API, the same one FanKarr reads: {@code /series},
 * then {@code /series/{id}/seasons} and {@code /seasons/{id}/episodes}. It
 * needs no key, yet goes through Janus like TMDB under the
 * {@code fankai-metadata} slug, which caches the answers, retries and pauses
 * the API when it fails, so nothing of the kind is rebuilt here.
 */
public final class HttpFankaiClient implements FankaiClient {
    /** Janus waits 30 s upstream; the client gives it room to answer. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(40);

    private final HttpClient httpClient;

    public HttpFankaiClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    HttpFankaiClient(HttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    @Override
    public List<FankaiSeries> series(JanusConfiguration credentials) {
        List<FankaiSeries> series = new ArrayList<>();
        for (JsonElement element : list(get("series", credentials), "series")) {
            if (!element.isJsonObject()) continue;
            JsonObject json = element.getAsJsonObject();
            Optional<Integer> id = integer(json, "id");
            Optional<String> title = text(json, "title");
            if (id.isEmpty() || title.isEmpty()) continue;
            // original_title names the source show ("BLACK LAGOON"), not the
            // kai: as an alias it ties the kai with TMDB's own series.
            List<String> alternates = new ArrayList<>();
            text(json, "title_for_plex").ifPresent(alternates::add);
            text(json, "show_title").ifPresent(alternates::add);
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

    @Override
    public List<FankaiEpisode> episodes(int seriesId, JanusConfiguration credentials) {
        List<FankaiEpisode> episodes = new ArrayList<>();
        for (JsonElement element : list(get("series/" + seriesId + "/seasons", credentials), "seasons")) {
            if (!element.isJsonObject()) continue;
            JsonObject season = element.getAsJsonObject();
            Optional<Integer> seasonId = integer(season, "id");
            Optional<Integer> seasonNumber = integer(season, "season_number");
            if (seasonId.isEmpty() || seasonNumber.isEmpty()) continue;
            for (JsonElement episodeElement : list(get("seasons/" + seasonId.get() + "/episodes", credentials), "episodes")) {
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

    private JsonElement get(String path, JanusConfiguration credentials) {
        HttpRequest request = HttpRequest.newBuilder(credentials.fankaiBaseUri().resolve(path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("X-Janus-Application-Id", credentials.applicationId())
                .header("X-Janus-Api-Key", credentials.apiKey())
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
            String correlationId = response.headers().firstValue("X-Janus-Correlation-Id").orElse("none");
            throw new FankaiException("Fankai catalog answered HTTP " + response.statusCode() + " on /" + path
                    + " (janusCorrelationId=" + correlationId + ").");
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

}

package com.episort.tvdb.cache;

import com.episort.config.FileSettingsStore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Disk-backed JSON cache for TVDB responses. All entries carry an absolute
 * expiry timestamp so callers can store different categories with different
 * TTLs. Entries are kept in-memory in insertion order and the cache eagerly
 * evicts the oldest entries beyond {@link #MAX_ENTRIES}. The file is rewritten
 * atomically on each put so an unexpected shutdown never leaves the cache in
 * an inconsistent state.
 *
 * <p>This cache is process-local; concurrent processes will race on the file.
 * Episort is a single-process desktop app so that is acceptable.
 */
public final class TvdbResponseCache {
    private static final int MAX_ENTRIES = 10_000;
    private static final String SCHEMA_VERSION = "1";

    private final Path cacheFile;
    private final Clock clock;
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapterFactory(new OptionalTypeAdapterFactory())
            .create();
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
    private boolean loaded;

    public TvdbResponseCache(Path cacheFile) {
        this(cacheFile, Clock.systemUTC());
    }

    TvdbResponseCache(Path cacheFile, Clock clock) {
        this.cacheFile = Objects.requireNonNull(cacheFile, "cacheFile").toAbsolutePath().normalize();
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static TvdbResponseCache userProfileCache() {
        Path settings = FileSettingsStore.userProfileStore().settingsFile().orElseThrow();
        return new TvdbResponseCache(settings.resolveSibling("tvdb-cache.json"));
    }

    public synchronized <T> Optional<T> get(String key, Class<T> type) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
        ensureLoaded();
        Entry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (Instant.now(clock).isAfter(entry.expiresAt)) {
            entries.remove(key);
            persistQuietly();
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(gson.fromJson(entry.json, type));
        } catch (JsonSyntaxException ex) {
            entries.remove(key);
            persistQuietly();
            return Optional.empty();
        }
    }

    public synchronized void put(String key, Object value, Duration ttl) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(ttl, "ttl");
        ensureLoaded();
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }
        Instant expiresAt = Instant.now(clock).plus(ttl);
        entries.remove(key);
        entries.put(key, new Entry(gson.toJson(value), expiresAt));
        evictOverflow();
        persistQuietly();
    }

    public synchronized void invalidate(String key) {
        ensureLoaded();
        if (entries.remove(key) != null) {
            persistQuietly();
        }
    }

    public synchronized int size() {
        ensureLoaded();
        return entries.size();
    }

    public synchronized int clear() {
        ensureLoaded();
        int removed = entries.size();
        if (removed == 0) {
            return 0;
        }
        entries.clear();
        persistQuietly();
        return removed;
    }

    public synchronized int purgeExpired() {
        ensureLoaded();
        Instant now = Instant.now(clock);
        int removed = 0;
        Iterator<Map.Entry<String, Entry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            if (now.isAfter(it.next().getValue().expiresAt)) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            persistQuietly();
        }
        return removed;
    }

    private void evictOverflow() {
        Iterator<Map.Entry<String, Entry>> it = entries.entrySet().iterator();
        while (entries.size() > MAX_ENTRIES && it.hasNext()) {
            it.next();
            it.remove();
        }
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        if (!Files.exists(cacheFile)) {
            return;
        }
        try (InputStream in = Files.newInputStream(cacheFile)) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (body.isBlank()) return;
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonObject items = root.has("entries") ? root.getAsJsonObject("entries") : new JsonObject();
            Instant now = Instant.now(clock);
            for (Map.Entry<String, com.google.gson.JsonElement> field : items.entrySet()) {
                JsonObject entryObj = field.getValue().getAsJsonObject();
                Instant expiresAt = Instant.parse(entryObj.get("expiresAt").getAsString());
                if (now.isAfter(expiresAt)) continue;
                String json = entryObj.get("json").getAsString();
                entries.put(field.getKey(), new Entry(json, expiresAt));
            }
        } catch (IOException | RuntimeException ignored) {
            // Corrupted cache: start fresh. Persisted on next put.
            entries.clear();
        }
    }

    private void persistQuietly() {
        try {
            Files.createDirectories(cacheFile.getParent());
            Path tmp = Files.createTempFile(cacheFile.getParent(), "tvdb-cache", ".tmp");
            try {
                JsonObject root = new JsonObject();
                root.addProperty("schema", SCHEMA_VERSION);
                JsonObject items = new JsonObject();
                for (Map.Entry<String, Entry> e : entries.entrySet()) {
                    JsonObject entryObj = new JsonObject();
                    entryObj.addProperty("json", e.getValue().json);
                    entryObj.addProperty("expiresAt", e.getValue().expiresAt.toString());
                    items.add(e.getKey(), entryObj);
                }
                root.add("entries", items);
                try (OutputStream out = Files.newOutputStream(tmp)) {
                    out.write(gson.toJson(root).getBytes(StandardCharsets.UTF_8));
                }
                try {
                    Files.move(tmp, cacheFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException atomicFailure) {
                    Files.move(tmp, cacheFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ex) {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException ignored) {
            // Best-effort: the next put will retry.
        }
    }

    private record Entry(String json, Instant expiresAt) {}

    /**
     * Bridges {@link Optional} fields on records (TvdbSearchResult, TvdbSeriesDetails,
     * TvdbMovieDetails, TvdbCandidate, …) since vanilla Gson cannot reflect on
     * Optional under the JDK module system.
     */
    private static final class OptionalTypeAdapterFactory implements TypeAdapterFactory {
        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            if (type.getRawType() != Optional.class) {
                return null;
            }
            Type elementType;
            if (type.getType() instanceof ParameterizedType parameterized) {
                elementType = parameterized.getActualTypeArguments()[0];
            } else {
                elementType = Object.class;
            }
            TypeAdapter<Object> elementAdapter = (TypeAdapter<Object>) gson.getAdapter(TypeToken.get(elementType));
            return (TypeAdapter<T>) new OptionalTypeAdapter(elementAdapter);
        }
    }

    private static final class OptionalTypeAdapter extends TypeAdapter<Optional<Object>> {
        private final TypeAdapter<Object> elementAdapter;

        OptionalTypeAdapter(TypeAdapter<Object> elementAdapter) {
            this.elementAdapter = elementAdapter;
        }

        @Override
        public void write(JsonWriter out, Optional<Object> value) throws java.io.IOException {
            if (value == null || value.isEmpty()) {
                boolean previous = out.getSerializeNulls();
                out.setSerializeNulls(true);
                out.nullValue();
                out.setSerializeNulls(previous);
                return;
            }
            elementAdapter.write(out, value.orElseThrow());
        }

        @Override
        public Optional<Object> read(JsonReader in) throws java.io.IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return Optional.empty();
            }
            return Optional.ofNullable(elementAdapter.read(in));
        }
    }
}

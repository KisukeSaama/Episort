package com.episort.ai;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

/**
 * Service abstraction over the local AI model files: list, download, delete,
 * and selection persistence. All entries share the per-user models directory
 * already used by {@code Qwen3ModelDownloader}, so existing installs are
 * picked up automatically.
 *
 * <p>Selection is persisted via the {@link Supplier}/{@link Consumer}
 * callbacks supplied by the host (typically backed by {@code FileSettingsStore}).
 * If a previously-selected model has been deleted, {@link #selectedId()}
 * falls back to the first locally-available entry, then to the catalog
 * default.
 */
public final class AiModelLibrary {

    private final Path modelsDirectory;
    private final HttpClient httpClient;
    private final Supplier<Optional<String>> selectedReader;
    private final Consumer<String> selectedWriter;

    public AiModelLibrary(
            Path modelsDirectory,
            Supplier<Optional<String>> selectedReader,
            Consumer<String> selectedWriter) {
        this(modelsDirectory, defaultClient(), selectedReader, selectedWriter);
    }

    public AiModelLibrary(
            Path modelsDirectory,
            HttpClient httpClient,
            Supplier<Optional<String>> selectedReader,
            Consumer<String> selectedWriter) {
        this.modelsDirectory = Objects.requireNonNull(modelsDirectory, "modelsDirectory");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.selectedReader = Objects.requireNonNull(selectedReader, "selectedReader");
        this.selectedWriter = Objects.requireNonNull(selectedWriter, "selectedWriter");
    }

    public Path pathOf(AiModelEntry entry) {
        return modelsDirectory.resolve(entry.fileName());
    }

    /**
     * Path the embedded runtime should load. Resolves the active selection
     * (if its file is present), otherwise the first locally-present model,
     * otherwise the catalog default Qwen3 path even if absent (callers must
     * still check {@link Files#isRegularFile} before launching).
     */
    public Path activeModelPath() {
        return selectedId()
                .flatMap(AiModelCatalog::findById)
                .map(this::pathOf)
                .orElseGet(() -> modelsDirectory.resolve(
                        com.episort.ai.embedded.Qwen3ModelDownloader.MODEL_FILENAME));
    }

    public boolean isPresent(AiModelEntry entry) {
        Path target = pathOf(entry);
        return Files.isRegularFile(target) && target.toFile().length() > 0;
    }

    /**
     * Active model id, restricted to models actually present locally:
     * stored → if present; otherwise first locally-present entry; otherwise
     * empty (signals "no model installed"). Selecting a missing model is
     * disallowed by {@link #setSelectedId(String)}, so this stays consistent.
     */
    public Optional<String> selectedId() {
        Optional<String> stored = selectedReader.get();
        if (stored.isPresent()) {
            Optional<AiModelEntry> entry = AiModelCatalog.findById(stored.get());
            if (entry.isPresent() && isPresent(entry.get())) {
                return Optional.of(entry.get().id());
            }
        }
        return AiModelCatalog.entries().stream()
                .filter(this::isPresent)
                .map(AiModelEntry::id)
                .findFirst();
    }

    /** Selects a model. Refuses ids that are not present locally. */
    public void setSelectedId(String id) {
        AiModelEntry entry = AiModelCatalog.findById(id).orElseThrow(() ->
                new IllegalArgumentException("Unknown model id: " + id));
        if (!isPresent(entry)) {
            throw new IllegalStateException("Model is not downloaded: " + id);
        }
        selectedWriter.accept(id);
    }

    /** Clears the persisted selection (no model active). */
    public void clearSelection() {
        selectedWriter.accept(null);
    }

    /**
     * Downloads the GGUF for {@code entry} if not already present. Throws
     * {@link UnsupportedOperationException} when no mirror has been pinned
     * for the entry yet (see {@code AiModelCatalog} TODOs).
     */
    public DownloadResult download(AiModelEntry entry, LongConsumer progress)
            throws IOException, InterruptedException {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(progress, "progress");
        if (isPresent(entry)) {
            Path target = pathOf(entry);
            return new DownloadResult(target, target.toFile().length(), false);
        }
        URI source = entry.sourceUrl().orElseThrow(() -> new UnsupportedOperationException(
                "No download mirror is configured for " + entry.id()));
        Files.createDirectories(modelsDirectory);
        HttpRequest request = HttpRequest.newBuilder(source)
                .timeout(Duration.ofHours(2))
                .GET()
                .build();
        HttpResponse<InputStream> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("Model download returned status " + response.statusCode());
        }
        long totalBytes = response.headers().firstValueAsLong("content-length").orElse(-1);
        Path tempFile = Files.createTempFile(modelsDirectory, "model-", ".part");
        try (InputStream in = response.body();
             var out = Files.newOutputStream(tempFile)) {
            byte[] buffer = new byte[1 << 20];
            long written = 0;
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
                written += n;
                progress.accept(written);
            }
        }
        Path target = pathOf(entry);
        Files.move(tempFile, target,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        return new DownloadResult(target, totalBytes, true);
    }

    /** Deletes the local file for {@code entry}. Returns true if a file was removed. */
    public boolean delete(AiModelEntry entry) throws IOException {
        Objects.requireNonNull(entry, "entry");
        return Files.deleteIfExists(pathOf(entry));
    }

    public Path modelsDirectory() {
        return modelsDirectory;
    }

    public record DownloadResult(Path path, long totalBytes, boolean downloaded) {}

    private static HttpClient defaultClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
}

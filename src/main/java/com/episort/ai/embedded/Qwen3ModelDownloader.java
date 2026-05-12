package com.episort.ai.embedded;

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
import java.util.function.LongConsumer;

/**
 * Downloads the bundled Qwen3 4B Instruct 2507 GGUF model into Episort's per-user cache and
 * exposes its absolute path. Idempotent: subsequent calls return the cached
 * path without re-downloading.
 *
 * <p>Used at first run from {@link EmbeddedLlamaRuntime}; the model file is
 * never bundled inside the installer (~2.5 GB) — only the runtime binaries are.
 */
public class Qwen3ModelDownloader {
    public static final String MODEL_FILENAME = "Qwen_Qwen3-4B-Instruct-2507-Q4_K_M.gguf";
    // Default mirror — pinned community build. Override via EPISORT_MODEL_URL.
    public static final URI DEFAULT_MODEL_URL = URI.create(
            "https://huggingface.co/bartowski/Qwen_Qwen3-4B-Instruct-2507-GGUF/resolve/main/"
                    + "Qwen_Qwen3-4B-Instruct-2507-Q4_K_M.gguf");

    private final Path modelDirectory;
    private final URI sourceUrl;
    private final HttpClient httpClient;

    public Qwen3ModelDownloader() {
        this(defaultModelDirectory(), resolveSourceUrl(), defaultClient());
    }

    public Qwen3ModelDownloader(Path modelDirectory, URI sourceUrl, HttpClient httpClient) {
        this.modelDirectory = Objects.requireNonNull(modelDirectory, "modelDirectory");
        this.sourceUrl = Objects.requireNonNull(sourceUrl, "sourceUrl");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    public Path modelPath() {
        return modelDirectory.resolve(MODEL_FILENAME);
    }

    public boolean isPresent() {
        Path target = modelPath();
        return Files.isRegularFile(target) && target.toFile().length() > 0;
    }

    /**
     * Downloads the GGUF if absent. {@code progress} receives cumulative bytes
     * written; the total size comes from the HTTP {@code Content-Length} header
     * and is reported via the returned {@link DownloadResult}.
     */
    public DownloadResult downloadIfMissing(LongConsumer progress) throws IOException, InterruptedException {
        Objects.requireNonNull(progress, "progress");
        Path target = modelPath();
        if (isPresent()) {
            return new DownloadResult(target, target.toFile().length(), false);
        }
        Files.createDirectories(modelDirectory);
        HttpRequest request = HttpRequest.newBuilder(sourceUrl)
                .timeout(Duration.ofHours(2))
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("Model download returned status " + response.statusCode());
        }
        long totalBytes = response.headers().firstValueAsLong("content-length").orElse(-1);
        Path tempFile = Files.createTempFile(modelDirectory, "model-", ".part");
        try (InputStream in = response.body()) {
            try (var out = Files.newOutputStream(tempFile)) {
                byte[] buffer = new byte[1 << 20];
                long written = 0;
                int n;
                while ((n = in.read(buffer)) > 0) {
                    out.write(buffer, 0, n);
                    written += n;
                    progress.accept(written);
                }
            }
        }
        Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        return new DownloadResult(target, totalBytes, true);
    }

    public record DownloadResult(Path path, long totalBytes, boolean downloaded) {
    }

    private static Path defaultModelDirectory() {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path base = (localAppData != null && !localAppData.isBlank())
                ? Path.of(localAppData, "Episort", "models")
                : Path.of(System.getProperty("user.home", "."), ".episort", "models");
        return base;
    }

    private static URI resolveSourceUrl() {
        String override = System.getenv("EPISORT_MODEL_URL");
        if (override != null && !override.isBlank()) {
            return URI.create(override);
        }
        return DEFAULT_MODEL_URL;
    }

    private static HttpClient defaultClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
}

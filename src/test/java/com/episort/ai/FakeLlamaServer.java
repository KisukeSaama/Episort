package com.episort.ai;

import com.episort.ai.embedded.EmbeddedLlamaRuntime;
import com.episort.ai.embedded.LlamaServerClient;
import com.episort.ai.embedded.Qwen3ModelDownloader;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Minimal in-process fake of the embedded llama-server. Auto-handles
 * {@code /health} (200 OK) and {@code /completion} (returns the canned JSON
 * envelope set via {@link #setNextContent}). Backed by a temp directory that
 * mimics the runtime+model layout so {@link BundledLocalAiRuntimeProbe} can
 * be exercised end-to-end.
 */
final class FakeLlamaServer implements AutoCloseable {
    private final HttpServer server;
    private final Path runtimeZipDir;
    private final Path extractionDir;
    private final Path modelPath;
    private volatile String nextContent =
            "{\"patterns\":[\"SxxExx\"],\"explanation\":\"detected pattern hint\"}";

    static FakeLlamaServer start() throws IOException {
        return new FakeLlamaServer();
    }

    private FakeLlamaServer() throws IOException {
        Path workspace = Files.createTempDirectory("episort-fake-llama-");
        this.runtimeZipDir = Files.createDirectories(workspace.resolve("runtime"));
        Files.writeString(runtimeZipDir.resolve("placeholder.zip"), ""); // satisfies binariesAvailable()
        this.extractionDir = Files.createDirectories(workspace.resolve("extracted"));
        this.modelPath = workspace.resolve("model.gguf");
        Files.writeString(modelPath, "fake");

        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> respond(exchange, 200, "{\"status\":\"ok\"}"));
        server.createContext("/completion", exchange -> {
            String body = "{\"content\":" + jsonString(nextContent) + "}";
            respond(exchange, 200, body);
        });
        server.start();
    }

    void setNextContent(String envelopeJson) {
        this.nextContent = envelopeJson;
    }

    URI baseUri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    LlamaServerClient client() {
        return new LlamaServerClient(baseUri());
    }

    Qwen3ModelDownloader downloader() {
        return new Qwen3ModelDownloader(modelPath.getParent(), URI.create("http://localhost/unused"),
                HttpClient.newHttpClient()) {
            @Override
            public Path modelPath() {
                return modelPath;
            }

            @Override
            public boolean isPresent() {
                return true;
            }
        };
    }

    EmbeddedLlamaRuntime runtime() {
        URI uri = baseUri();
        return new EmbeddedLlamaRuntime(runtimeZipDir, extractionDir, modelPath) {
            @Override
            public Optional<URI> baseUri() {
                return Optional.of(uri);
            }
        };
    }

    BundledLocalAiPatternAssistant patternAssistant() {
        URI uri = baseUri();
        return new BundledLocalAiPatternAssistant(() -> Optional.of(new LlamaServerClient(uri)));
    }

    BundledLocalAiRuntimeProbe probe() {
        return new BundledLocalAiRuntimeProbe(runtime(), downloader());
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private static String jsonString(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                default -> out.append(c);
            }
        }
        return out.append('"').toString();
    }
}

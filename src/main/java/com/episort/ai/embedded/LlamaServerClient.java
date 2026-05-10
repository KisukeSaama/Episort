package com.episort.ai.embedded;

import com.episort.ai.debug.AiTrace;
import com.episort.ai.debug.AiTraceBus;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Instant;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Minimal HTTP client for the embedded llama-server bound to a localhost port.
 * Wraps {@code GET /health} (200 once the model is loaded) and
 * {@code POST /completion} (non-streaming, JSON envelope output).
 *
 * <p>The base URI is set by {@link EmbeddedLlamaRuntime} once the server has
 * bound a free port. Tests inject a stub URI.
 */
public final class LlamaServerClient {
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration COMPLETE_TIMEOUT = Duration.ofMinutes(5);

    private final URI baseUri;
    private final HttpClient httpClient;

    public LlamaServerClient(URI baseUri) {
        this(baseUri, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
    }

    public LlamaServerClient(URI baseUri, HttpClient httpClient) {
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    public boolean isHealthy() {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/health"))
                .timeout(HEALTH_TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<Void> response = httpClient.send(request, BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    /**
     * Single-shot completion. {@code prompt} is sent as-is; we ask the server
     * to constrain output to JSON via the {@code response_format} hint that
     * llama-server accepts when the underlying model supports JSON mode.
     */
    public String complete(String prompt, int maxTokens) {
        return complete("completion", prompt, maxTokens);
    }

    public String complete(String source, String prompt, int maxTokens) {
        Objects.requireNonNull(prompt, "prompt");
        JsonObject body = new JsonObject();
        body.addProperty("prompt", prompt);
        body.addProperty("n_predict", maxTokens);
        // Near-greedy sampling: every Episort task either wants strict JSON or
        // a one-word answer, so we deliberately suppress creativity.
        body.addProperty("temperature", 0.1);
        body.addProperty("top_p", 0.9);
        body.addProperty("top_k", 40);
        body.addProperty("repeat_penalty", 1.05);
        // Reuse the KV cache for the system-prompt prefix across calls — major
        // speed win since the same SYSTEM_PROMPT is repeated for every group.
        body.addProperty("cache_prompt", true);
        // Stop as soon as a Qwen3 turn ends or a blank line follows JSON, so
        // we don't burn tokens on prose past the JSON envelope.
        com.google.gson.JsonArray stop = new com.google.gson.JsonArray();
        stop.add("<|im_end|>");
        stop.add("</s>");
        stop.add("\n\n\n");
        body.add("stop", stop);
        body.addProperty("stream", false);
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/completion"))
                .timeout(COMPLETE_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        Instant start = Instant.now();
        long startNanos = System.nanoTime();
        try {
            HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IOException("llama-server /completion returned status " + response.statusCode());
            }
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            String content = root.has("content") ? root.get("content").getAsString() : "";
            long ms = (System.nanoTime() - startNanos) / 1_000_000L;
            AiTraceBus.get().publish(new AiTrace(start, source, prompt, content, ms, null));
            return content;
        } catch (IOException | InterruptedException | JsonSyntaxException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            long ms = (System.nanoTime() - startNanos) / 1_000_000L;
            AiTraceBus.get().publish(new AiTrace(start, source, prompt, "", ms, ex.getMessage()));
            throw new UncheckedIOException(new IOException("llama-server completion failed", ex));
        }
    }

    /**
     * Streaming completion. Each token chunk emitted by the server is forwarded to {@code onToken}.
     * The returned String is the full concatenated response. Caller handles JavaFX threading.
     */
    public String completeStream(String source, String prompt, int maxTokens, Consumer<String> onToken) {
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(onToken, "onToken");
        JsonObject body = new JsonObject();
        body.addProperty("prompt", prompt);
        body.addProperty("n_predict", maxTokens);
        body.addProperty("temperature", 0.3);
        body.addProperty("top_p", 0.9);
        body.addProperty("top_k", 40);
        body.addProperty("repeat_penalty", 1.05);
        // Chat prompts carry changing selected-file context. Disable KV prompt
        // reuse here so one selection cannot bleed into the next turn.
        body.addProperty("cache_prompt", false);
        com.google.gson.JsonArray stop = new com.google.gson.JsonArray();
        stop.add("<|im_end|>");
        stop.add("</s>");
        body.add("stop", stop);
        body.addProperty("stream", true);
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/completion"))
                .timeout(COMPLETE_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        Instant start = Instant.now();
        long startNanos = System.nanoTime();
        StringBuilder full = new StringBuilder();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new IOException("llama-server /completion stream status " + response.statusCode());
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        continue;
                    }
                    String payload = line.startsWith("data:") ? line.substring(5).trim() : line.trim();
                    if (payload.isEmpty() || "[DONE]".equals(payload)) {
                        continue;
                    }
                    try {
                        JsonObject obj = JsonParser.parseString(payload).getAsJsonObject();
                        if (obj.has("content")) {
                            String chunk = obj.get("content").getAsString();
                            if (!chunk.isEmpty()) {
                                full.append(chunk);
                                onToken.accept(chunk);
                            }
                        }
                        if (obj.has("stop") && obj.get("stop").getAsBoolean()) {
                            break;
                        }
                    } catch (JsonSyntaxException ignored) {
                        // skip malformed SSE payloads
                    }
                }
            }
            long ms = (System.nanoTime() - startNanos) / 1_000_000L;
            AiTraceBus.get().publish(new AiTrace(start, source, prompt, full.toString(), ms, null));
            return full.toString();
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            long ms = (System.nanoTime() - startNanos) / 1_000_000L;
            AiTraceBus.get().publish(new AiTrace(start, source, prompt, full.toString(), ms, ex.getMessage()));
            throw new UncheckedIOException(new IOException("llama-server stream failed", ex));
        }
    }
}

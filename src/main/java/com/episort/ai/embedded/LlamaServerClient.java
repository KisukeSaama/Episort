package com.episort.ai.embedded;

import com.episort.ai.debug.AiTrace;
import com.episort.ai.debug.AiTraceBus;
import com.google.gson.JsonArray;
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
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Minimal HTTP client for the embedded llama-server bound to a localhost port.
 * Wraps {@code GET /health} (200 once the model is loaded) and
 * {@code POST /v1/chat/completions} (non-streaming JSON envelope or SSE
 * streaming, depending on the call site). The server is launched with
 * {@code --jinja} so the GGUF's embedded chat template formats turns.
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

    /** One turn in a chat-completions request. */
    public record ChatMessage(String role, String content) {
        public static ChatMessage system(String content) { return new ChatMessage("system", content); }
        public static ChatMessage user(String content)   { return new ChatMessage("user", content); }
        public static ChatMessage assistant(String c)    { return new ChatMessage("assistant", c); }
    }

    /**
     * Non-streaming chat completion via {@code POST /v1/chat/completions}. Uses
     * the GGUF's embedded Jinja chat template (server must run with
     * {@code --jinja}) so ChatML is formatted by the model's own template
     * rather than hand-rolled in Java.
     *
     * <p>When {@code jsonSchema} is non-null, output is constrained at the
     * token level via {@code response_format: {"type":"json_schema", ...}} —
     * llama-server compiles it to GBNF, eliminating "// similar entries"
     * placeholders.
     *
     * <p>Sampling is near-greedy because every structured call is JSON-schema
     * constrained; we keep {@code min_p=0} per Qwen3's official recommendation
     * and avoid {@code repeat_penalty} which the Qwen team explicitly warns
     * against on this model family.
     */
    public String complete(String source, List<ChatMessage> messages, int maxTokens, JsonObject jsonSchema) {
        Objects.requireNonNull(messages, "messages");
        JsonObject body = baseChatBody(messages, maxTokens, false);
        // Structured profile: near-greedy for deterministic JSON values.
        body.addProperty("temperature", 0.2);
        body.addProperty("top_p", 0.8);
        body.addProperty("top_k", 20);
        body.addProperty("min_p", 0.0);
        // Reuse the KV cache for the system-prompt prefix across calls.
        body.addProperty("cache_prompt", true);
        if (jsonSchema != null) {
            JsonObject responseFormat = new JsonObject();
            responseFormat.addProperty("type", "json_schema");
            JsonObject schemaWrap = new JsonObject();
            schemaWrap.addProperty("name", "episort_response");
            schemaWrap.addProperty("strict", true);
            schemaWrap.add("schema", jsonSchema);
            responseFormat.add("json_schema", schemaWrap);
            body.add("response_format", responseFormat);
        }
        return sendChat(source, body, messages);
    }

    /**
     * Streaming chat completion. Each delta content chunk is forwarded to
     * {@code onToken}; returns the full concatenated reply. Uses the Qwen3
     * non-thinking sampling profile recommended by the Qwen team:
     * {@code temperature=0.7, top_p=0.8, top_k=20, min_p=0, presence_penalty=1.5}.
     */
    public String completeStream(String source, List<ChatMessage> messages, int maxTokens, Consumer<String> onToken) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(onToken, "onToken");
        JsonObject body = baseChatBody(messages, maxTokens, true);
        // Qwen3 non-thinking conversational profile.
        body.addProperty("temperature", 0.7);
        body.addProperty("top_p", 0.8);
        body.addProperty("top_k", 20);
        body.addProperty("min_p", 0.0);
        body.addProperty("presence_penalty", 1.5);
        // llama-server matches the longest common prefix between requests, so
        // the static system prompt is reused across chat turns without any
        // risk of one selection's context bleeding into the next.
        body.addProperty("cache_prompt", true);

        String prompt = renderMessagesForTrace(messages);
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/v1/chat/completions"))
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
                throw new IOException("llama-server /v1/chat/completions stream status " + response.statusCode());
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
                        String chunk = extractDeltaContent(obj);
                        if (!chunk.isEmpty()) {
                            full.append(chunk);
                            onToken.accept(chunk);
                        }
                        if (isStopChunk(obj)) {
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
            throw new UncheckedIOException(new IOException("llama-server chat stream failed", ex));
        }
    }

    private JsonObject baseChatBody(List<ChatMessage> messages, int maxTokens, boolean stream) {
        JsonObject body = new JsonObject();
        JsonArray arr = new JsonArray();
        for (ChatMessage msg : messages) {
            JsonObject m = new JsonObject();
            m.addProperty("role", msg.role());
            m.addProperty("content", msg.content());
            arr.add(m);
        }
        body.add("messages", arr);
        body.addProperty("max_tokens", maxTokens);
        body.addProperty("stream", stream);
        // Stop as soon as a Qwen3 turn ends. The Jinja template already closes
        // assistant turns with <|im_end|>; <|endoftext|> is Qwen3's secondary
        // EOS. (</s> is a Llama-family token and is not used by Qwen3 — leaving
        // it in the stop list was harmless but incorrect.)
        JsonArray stop = new JsonArray();
        stop.add("<|im_end|>");
        stop.add("<|endoftext|>");
        body.add("stop", stop);
        return body;
    }

    private String sendChat(String source, JsonObject body, List<ChatMessage> messages) {
        String prompt = renderMessagesForTrace(messages);
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/v1/chat/completions"))
                .timeout(COMPLETE_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        Instant start = Instant.now();
        long startNanos = System.nanoTime();
        try {
            HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IOException("llama-server /v1/chat/completions returned status " + response.statusCode());
            }
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            String content = extractMessageContent(root);
            long ms = (System.nanoTime() - startNanos) / 1_000_000L;
            AiTraceBus.get().publish(new AiTrace(start, source, prompt, content, ms, null));
            return content;
        } catch (IOException | InterruptedException | JsonSyntaxException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            long ms = (System.nanoTime() - startNanos) / 1_000_000L;
            AiTraceBus.get().publish(new AiTrace(start, source, prompt, "", ms, ex.getMessage()));
            throw new UncheckedIOException(new IOException("llama-server chat completion failed", ex));
        }
    }

    private static String extractMessageContent(JsonObject root) {
        if (!root.has("choices") || !root.get("choices").isJsonArray()) return "";
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices.isEmpty()) return "";
        JsonObject first = choices.get(0).getAsJsonObject();
        if (!first.has("message")) return "";
        JsonObject msg = first.getAsJsonObject("message");
        return msg.has("content") && !msg.get("content").isJsonNull()
                ? msg.get("content").getAsString()
                : "";
    }

    private static String extractDeltaContent(JsonObject chunk) {
        if (!chunk.has("choices") || !chunk.get("choices").isJsonArray()) return "";
        JsonArray choices = chunk.getAsJsonArray("choices");
        if (choices.isEmpty()) return "";
        JsonObject first = choices.get(0).getAsJsonObject();
        if (!first.has("delta")) return "";
        JsonObject delta = first.getAsJsonObject("delta");
        return delta.has("content") && !delta.get("content").isJsonNull()
                ? delta.get("content").getAsString()
                : "";
    }

    private static boolean isStopChunk(JsonObject chunk) {
        if (!chunk.has("choices") || !chunk.get("choices").isJsonArray()) return false;
        JsonArray choices = chunk.getAsJsonArray("choices");
        if (choices.isEmpty()) return false;
        JsonObject first = choices.get(0).getAsJsonObject();
        return first.has("finish_reason") && !first.get("finish_reason").isJsonNull();
    }

    private static String renderMessagesForTrace(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : messages) {
            sb.append('[').append(m.role()).append("]\n").append(m.content()).append("\n\n");
        }
        return sb.toString();
    }
}

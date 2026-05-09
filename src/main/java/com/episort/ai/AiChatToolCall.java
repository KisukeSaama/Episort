package com.episort.ai;

import com.google.gson.JsonObject;
import java.util.Objects;

/** A tool invocation parsed from the AI stream, awaiting user confirmation. */
public record AiChatToolCall(String name, JsonObject args, String rawJson) {
    public AiChatToolCall {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(rawJson, "rawJson");
    }

    public String stringArg(String key, String fallback) {
        return args.has(key) && !args.get(key).isJsonNull() ? args.get(key).getAsString() : fallback;
    }
}

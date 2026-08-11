package com.episort.persistence;

import java.util.LinkedHashMap;
import java.util.Map;

final class JsonWriter {
    private final StringBuilder builder = new StringBuilder();
    private final Map<String, String> entries = new LinkedHashMap<>();

    JsonWriter put(String key, String value) {
        if (value != null) {
            entries.put(key, quote(value));
        } else {
            entries.put(key, "null");
        }
        return this;
    }

    JsonWriter putRaw(String key, String rawJson) {
        entries.put(key, rawJson);
        return this;
    }

    JsonWriter putObject(String key, Map<String, String> object) {
        StringBuilder objectBuilder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : object.entrySet()) {
            if (!first) {
                objectBuilder.append(',');
            }
            objectBuilder.append(quote(entry.getKey())).append(':').append(quote(entry.getValue()));
            first = false;
        }
        objectBuilder.append('}');
        entries.put(key, objectBuilder.toString());
        return this;
    }

    String build() {
        builder.setLength(0);
        builder.append('{');
        boolean first = true;
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            builder.append(quote(entry.getKey())).append(':').append(entry.getValue());
            first = false;
        }
        builder.append('}');
        return builder.toString();
    }

    static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (character < 0x20) {
                        out.append(String.format("\\u%04x", (int) character));
                    } else {
                        out.append(character);
                    }
                }
            }
        }
        out.append('"');
        return out.toString();
    }
}

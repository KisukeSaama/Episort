package com.episort.persistence;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal JSON reader for the run-event log.
 * Accepts a flat object whose values are strings or one-level string maps.
 * Throws {@link IllegalArgumentException} on malformed input — the caller is
 * expected to catch and skip malformed lines.
 */
final class JsonReader {
    private final String source;
    private int cursor;

    private JsonReader(String source) {
        this.source = source;
        this.cursor = 0;
    }

    static Map<String, Object> parseObject(String source) {
        JsonReader reader = new JsonReader(source);
        reader.skipWhitespace();
        Map<String, Object> result = reader.readObject();
        reader.skipWhitespace();
        if (reader.cursor != reader.source.length()) {
            throw new IllegalArgumentException("Trailing content after JSON object at " + reader.cursor);
        }
        return result;
    }

    private Map<String, Object> readObject() {
        expect('{');
        Map<String, Object> result = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') {
            cursor++;
            return result;
        }
        while (true) {
            skipWhitespace();
            String key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            Object value = readValue();
            result.put(key, value);
            skipWhitespace();
            char next = next();
            if (next == ',') {
                continue;
            }
            if (next == '}') {
                return result;
            }
            throw new IllegalArgumentException("Unexpected character '" + next + "' at " + (cursor - 1));
        }
    }

    private Object readValue() {
        char first = peek();
        if (first == '"') {
            return readString();
        }
        if (first == '{') {
            Map<String, Object> nested = readObject();
            Map<String, String> stringMap = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : nested.entrySet()) {
                if (!(entry.getValue() instanceof String stringValue)) {
                    throw new IllegalArgumentException("Nested object only supports string values at " + cursor);
                }
                stringMap.put(entry.getKey(), stringValue);
            }
            return stringMap;
        }
        if (first == 'n' && source.startsWith("null", cursor)) {
            cursor += 4;
            return null;
        }
        throw new IllegalArgumentException("Unsupported JSON value at " + cursor);
    }

    private String readString() {
        expect('"');
        StringBuilder builder = new StringBuilder();
        while (true) {
            if (cursor >= source.length()) {
                throw new IllegalArgumentException("Unterminated string");
            }
            char character = source.charAt(cursor++);
            if (character == '"') {
                return builder.toString();
            }
            if (character == '\\') {
                if (cursor >= source.length()) {
                    throw new IllegalArgumentException("Unterminated escape");
                }
                char escaped = source.charAt(cursor++);
                switch (escaped) {
                    case '"' -> builder.append('"');
                    case '\\' -> builder.append('\\');
                    case '/' -> builder.append('/');
                    case 'b' -> builder.append('\b');
                    case 'f' -> builder.append('\f');
                    case 'n' -> builder.append('\n');
                    case 'r' -> builder.append('\r');
                    case 't' -> builder.append('\t');
                    case 'u' -> {
                        if (cursor + 4 > source.length()) {
                            throw new IllegalArgumentException("Truncated unicode escape");
                        }
                        int codePoint = Integer.parseInt(source.substring(cursor, cursor + 4), 16);
                        builder.append((char) codePoint);
                        cursor += 4;
                    }
                    default -> throw new IllegalArgumentException("Unknown escape \\" + escaped);
                }
            } else {
                builder.append(character);
            }
        }
    }

    private void expect(char expected) {
        char actual = next();
        if (actual != expected) {
            throw new IllegalArgumentException("Expected '" + expected + "' but found '" + actual + "' at " + (cursor - 1));
        }
    }

    private char next() {
        if (cursor >= source.length()) {
            throw new IllegalArgumentException("Unexpected end of input");
        }
        return source.charAt(cursor++);
    }

    private char peek() {
        if (cursor >= source.length()) {
            throw new IllegalArgumentException("Unexpected end of input");
        }
        return source.charAt(cursor);
    }

    private void skipWhitespace() {
        while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) {
            cursor++;
        }
    }
}

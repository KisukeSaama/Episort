package com.episort.ui.scan;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

public record ScanInputParse(
        String label,
        List<ScanInputToken> tokens,
        Optional<String> normalizedOrder,
        OptionalDouble confidence,
        ScanInputParseSource source) {
    public ScanInputParse {
        label = label == null ? "" : label;
        tokens = tokens == null ? List.of() : List.copyOf(tokens);
        normalizedOrder = normalizedOrder == null ? Optional.empty() : normalizedOrder;
        confidence = confidence == null ? OptionalDouble.empty() : confidence;
        Objects.requireNonNull(source, "source");
    }

    public Optional<String> tokenValue(ScanInputRole role) {
        return tokens.stream()
                .filter(token -> token.role() == role)
                .map(ScanInputToken::normalizedValue)
                .filter(value -> !value.isBlank())
                .findFirst();
    }

    public String summary() {
        return tokens.stream()
                .filter(token -> token.role() != ScanInputRole.NOISE)
                .map(ScanInputParse::summarizeToken)
                .collect(Collectors.joining(" | "));
    }

    public String positionsSummary() {
        return tokens.stream()
                .map(token -> token.role().name().toLowerCase(Locale.ROOT)
                        + "[" + token.start() + ".." + token.end() + "]")
                .collect(Collectors.joining(" "));
    }

    public ScanInputParse withSource(ScanInputParseSource source) {
        return new ScanInputParse(label, tokens, normalizedOrder, confidence, source);
    }

    public ScanInputParse withLabel(String label) {
        return new ScanInputParse(label, tokens, normalizedOrder, confidence, source);
    }

    private static String summarizeToken(ScanInputToken token) {
        return switch (token.role()) {
            case SERIES -> "Series: " + token.normalizedValue();
            case SEASON -> "S:" + token.normalizedValue();
            case EPISODE -> "E:" + token.normalizedValue();
            case TITLE -> "Title: " + token.normalizedValue();
            case YEAR -> "Year: " + token.normalizedValue();
            case EXTENSION -> "Ext:" + token.normalizedValue();
            case NOISE -> token.normalizedValue();
        };
    }
}

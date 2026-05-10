package com.episort.ai;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public record AiFilePatternParse(
        String filename,
        String pattern,
        List<AiPatternToken> tokens,
        Optional<String> normalizedOrder,
        OptionalDouble confidence) {
    public AiFilePatternParse {
        filename = filename == null ? "" : filename;
        pattern = pattern == null ? "" : pattern;
        tokens = tokens == null ? List.of() : List.copyOf(tokens);
        normalizedOrder = normalizedOrder == null ? Optional.empty() : normalizedOrder;
        confidence = confidence == null ? OptionalDouble.empty() : confidence;
    }
}

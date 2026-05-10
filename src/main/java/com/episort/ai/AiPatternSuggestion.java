package com.episort.ai;

import com.episort.scanner.InventoryGroupType;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

public record AiPatternSuggestion(
        String explanation,
        List<String> suggestedPatterns,
        List<AiFilePatternParse> fileParses,
        List<String> selectedItemContext,
        Optional<InventoryGroupType> classifiedType,
        OptionalDouble classificationConfidence,
        boolean advisoryOnly,
        boolean validationAuthority,
        boolean executionAuthority) {
    public AiPatternSuggestion {
        explanation = explanation == null ? "" : explanation;
        suggestedPatterns = suggestedPatterns == null ? List.of() : List.copyOf(suggestedPatterns);
        fileParses = fileParses == null ? List.of() : List.copyOf(fileParses);
        selectedItemContext = selectedItemContext == null ? List.of() : List.copyOf(selectedItemContext);
        classifiedType = classifiedType == null ? Optional.empty() : classifiedType;
        classificationConfidence = classificationConfidence == null ? OptionalDouble.empty() : classificationConfidence;
        if (!advisoryOnly || validationAuthority || executionAuthority) {
            throw new IllegalArgumentException("AI suggestions must remain advisory and cannot validate or execute.");
        }
        Objects.requireNonNull(classifiedType, "classifiedType");
        Objects.requireNonNull(classificationConfidence, "classificationConfidence");
    }

    /** Backwards-compatible 6-arg constructor used by existing tests. */
    public AiPatternSuggestion(
            String explanation,
            List<String> suggestedPatterns,
            List<String> selectedItemContext,
            boolean advisoryOnly,
            boolean validationAuthority,
            boolean executionAuthority) {
        this(explanation, suggestedPatterns, selectedItemContext,
                Optional.empty(), OptionalDouble.empty(),
                advisoryOnly, validationAuthority, executionAuthority);
    }

    public AiPatternSuggestion(
            String explanation,
            List<String> suggestedPatterns,
            List<String> selectedItemContext,
            Optional<InventoryGroupType> classifiedType,
            OptionalDouble classificationConfidence,
            boolean advisoryOnly,
            boolean validationAuthority,
            boolean executionAuthority) {
        this(explanation, suggestedPatterns, List.of(), selectedItemContext,
                classifiedType, classificationConfidence,
                advisoryOnly, validationAuthority, executionAuthority);
    }

    public static AiPatternSuggestion advisory(
            String explanation,
            List<String> suggestedPatterns,
            List<String> selectedItemContext) {
        return new AiPatternSuggestion(explanation, suggestedPatterns, List.of(), selectedItemContext,
                Optional.empty(), OptionalDouble.empty(), true, false, false);
    }

    public static AiPatternSuggestion advisory(
            String explanation,
            List<String> suggestedPatterns,
            List<String> selectedItemContext,
            Optional<InventoryGroupType> classifiedType,
            OptionalDouble classificationConfidence) {
        return new AiPatternSuggestion(explanation, suggestedPatterns, List.of(), selectedItemContext,
                classifiedType, classificationConfidence, true, false, false);
    }

    public static AiPatternSuggestion advisory(
            String explanation,
            List<String> suggestedPatterns,
            List<AiFilePatternParse> fileParses,
            List<String> selectedItemContext,
            Optional<InventoryGroupType> classifiedType,
            OptionalDouble classificationConfidence) {
        return new AiPatternSuggestion(explanation, suggestedPatterns, fileParses, selectedItemContext,
                classifiedType, classificationConfidence, true, false, false);
    }
}

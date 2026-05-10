package com.episort.ai;

import java.nio.file.Path;
import java.util.List;

public record AiPatternSuggestionRequest(
        List<String> selectedItemNames,
        String selectedItemPath,
        List<String> parentFolderChain) {
    public AiPatternSuggestionRequest {
        selectedItemNames = selectedItemNames == null ? List.of() : List.copyOf(selectedItemNames);
        selectedItemPath = selectedItemPath == null ? "" : selectedItemPath;
        parentFolderChain = parentFolderChain == null ? List.of() : List.copyOf(parentFolderChain);
    }

    public AiPatternSuggestionRequest(List<String> selectedItemNames, String selectedItemPath) {
        this(selectedItemNames, selectedItemPath, List.of());
    }

    public List<String> minimizedSelectedItemContext() {
        if (!selectedItemNames.isEmpty()) {
            return selectedItemNames;
        }
        if (selectedItemPath.isBlank()) {
            return List.of();
        }
        Path fileName = Path.of(selectedItemPath).getFileName();
        return fileName == null ? List.of() : List.of(fileName.toString());
    }
}

package com.episort.ai;

import java.util.List;

public record AiPatternSuggestionRequest(
        List<String> selectedItemNames,
        String selectedItemPath,
        List<String> parentFolderChain,
        List<List<String>> perFileParentFolderChains) {
    public AiPatternSuggestionRequest {
        selectedItemNames = selectedItemNames == null ? List.of() : List.copyOf(selectedItemNames);
        selectedItemPath = selectedItemPath == null ? "" : selectedItemPath;
        parentFolderChain = parentFolderChain == null ? List.of() : List.copyOf(parentFolderChain);
        if (perFileParentFolderChains == null) {
            perFileParentFolderChains = List.of();
        } else {
            java.util.List<List<String>> copy = new java.util.ArrayList<>(perFileParentFolderChains.size());
            for (List<String> chain : perFileParentFolderChains) {
                copy.add(chain == null ? List.of() : List.copyOf(chain));
            }
            perFileParentFolderChains = List.copyOf(copy);
        }
    }

    public AiPatternSuggestionRequest(List<String> selectedItemNames, String selectedItemPath) {
        this(selectedItemNames, selectedItemPath, List.of(), List.of());
    }

    public AiPatternSuggestionRequest(
            List<String> selectedItemNames,
            String selectedItemPath,
            List<String> parentFolderChain) {
        this(selectedItemNames, selectedItemPath, parentFolderChain, List.of());
    }

    public List<String> minimizedSelectedItemContext() {
        if (!selectedItemNames.isEmpty()) {
            return selectedItemNames;
        }
        if (selectedItemPath.isBlank()) {
            return List.of();
        }
        String normalized = selectedItemPath.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String fileName = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        return fileName.isBlank() ? List.of() : List.of(fileName);
    }
}

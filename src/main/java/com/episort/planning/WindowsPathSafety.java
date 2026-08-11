package com.episort.planning;

import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic Windows path hygiene for generated destinations (Story 6.5).
 *
 * <p>Every method is a pure function of its inputs: the same title always yields
 * the same folder and file name, so a plan reviewed by the user cannot change
 * shape between review and execution.
 */
public final class WindowsPathSafety {
    /** Classic Windows limit, including the drive prefix and the terminating NUL. */
    public static final int MAX_PATH = 260;

    /** NTFS per-component limit. */
    public static final int MAX_COMPONENT_LENGTH = 255;

    /** Never truncate a base name below this, so files stay recognizable. */
    static final int MIN_BASE_NAME_LENGTH = 12;

    /** Never truncate a folder name below this. */
    static final int MIN_FOLDER_LENGTH = 8;

    /** Suffix appended to reserved device names, the only case needing an extra character. */
    private static final String DEVICE_NAME_SUFFIX = "_";

    private static final String FALLBACK = "untitled";

    private static final Set<String> RESERVED_DEVICE_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private WindowsPathSafety() {
    }

    /**
     * Makes a single path component safe: drops characters Windows forbids,
     * collapses whitespace, removes trailing dots and spaces, escapes reserved
     * device names, and caps the length.
     */
    public static String sanitizeComponent(String raw) {
        if (raw == null) {
            return FALLBACK;
        }
        String decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        StringBuilder builder = new StringBuilder(decomposed.length());
        for (int index = 0; index < decomposed.length(); index++) {
            char character = decomposed.charAt(index);
            if (character == ':' || character == '/' || character == '\\') {
                builder.append(' ');
            } else if (!isForbidden(character)) {
                builder.append(character);
            }
        }
        String collapsed = builder.toString().replaceAll("\\s+", " ").trim();
        collapsed = stripTrailingDotsAndSpaces(collapsed);
        if (collapsed.isEmpty()) {
            return FALLBACK;
        }
        if (isReservedDeviceName(collapsed)) {
            collapsed = collapsed + DEVICE_NAME_SUFFIX;
        }
        return collapsed;
    }

    /**
     * Truncates deterministically and re-strips the trailing dots or spaces the
     * cut may have exposed. Never returns an empty string.
     */
    public static String truncate(String value, int maxLength) {
        Objects.requireNonNull(value, "value");
        if (maxLength <= 0) {
            return FALLBACK;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        String cut = stripTrailingDotsAndSpaces(value.substring(0, maxLength));
        return cut.isEmpty() ? FALLBACK : cut;
    }

    /**
     * Builds a destination path that fits within {@link #MAX_PATH}.
     *
     * <p>Overflow is absorbed in a fixed order — first the file base name, then
     * the deepest folder, then shallower folders — so two runs over the same
     * inputs always produce byte-identical paths. The extension is never
     * touched: the original one is preserved exactly.
     */
    public static Path fitWithinMaxPath(
            Path root, List<String> folderComponents, String baseName, String extension) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(folderComponents, "folderComponents");
        Objects.requireNonNull(baseName, "baseName");
        Objects.requireNonNull(extension, "extension");

        List<String> folders = folderComponents.stream().map(WindowsPathSafety::sanitizeComponent).toList();
        String base = sanitizeComponent(baseName);
        String safeExtension = sanitizeExtension(extension);

        Path result = root;
        for (String folder : folders) {
            result = result.resolve(folder);
        }
        return result.resolve(base + safeExtension);
    }

    /** True when the path still exceeds the classic Windows limit. */
    public static boolean exceedsMaxPath(Path path) {
        return path.toString().length() > MAX_PATH - 1;
    }

    /**
     * Normalizes an extension to a leading-dot form without altering its case:
     * Story 6.1 requires the original extension to be preserved exactly.
     */
    static String sanitizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return "";
        }
        String trimmed = extension.trim();
        String withoutDot = trimmed.startsWith(".") ? trimmed.substring(1) : trimmed;
        StringBuilder builder = new StringBuilder(withoutDot.length());
        for (int index = 0; index < withoutDot.length(); index++) {
            char character = withoutDot.charAt(index);
            if (!isForbidden(character) && character != '.') {
                builder.append(character);
            }
        }
        return builder.isEmpty() ? "" : "." + builder;
    }

    private static List<String> shrinkFolders(List<String> folders, int budget) {
        List<String> working = new ArrayList<>(folders);
        for (int index = working.size() - 1; index >= 0; index--) {
            int current = separatorCount(working) + totalLength(working);
            if (current <= budget) {
                break;
            }
            int overflow = current - budget;
            String folder = working.get(index);
            int target = Math.max(MIN_FOLDER_LENGTH, folder.length() - overflow);
            working.set(index, truncate(folder, target));
        }
        return List.copyOf(working);
    }

    private static int lengthOf(Path root) {
        String text = root.toString();
        return text.endsWith("\\") || text.endsWith("/") ? text.length() - 1 : text.length();
    }

    private static int totalLength(List<String> components) {
        return components.stream().mapToInt(String::length).sum();
    }

    private static int separatorCount(List<String> components) {
        return components.size();
    }

    private static String stripTrailingDotsAndSpaces(String value) {
        int end = value.length();
        while (end > 0 && (value.charAt(end - 1) == '.' || value.charAt(end - 1) == ' ')) {
            end--;
        }
        return value.substring(0, end).trim();
    }

    private static boolean isReservedDeviceName(String value) {
        int dot = value.indexOf('.');
        String stem = dot >= 0 ? value.substring(0, dot) : value;
        return RESERVED_DEVICE_NAMES.contains(stem.toUpperCase(Locale.ROOT));
    }

    private static boolean isForbidden(char character) {
        return character < 32
                || character == '<'
                || character == '>'
                || character == ':'
                || character == '"'
                || character == '/'
                || character == '\\'
                || character == '|'
                || character == '?'
                || character == '*';
    }
}

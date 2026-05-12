package com.episort.scanner;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record ScanSource(ScanSourceType type, List<Path> paths) {
    public ScanSource {
        type = Objects.requireNonNull(type, "type");
        paths = paths == null ? List.of() : paths.stream()
                .filter(Objects::nonNull)
                .map(path -> path.toAbsolutePath().normalize())
                .distinct()
                .toList();
        if (paths.isEmpty()) {
            throw new IllegalArgumentException("paths must not be empty");
        }
    }

    public static ScanSource folder(Path folder) {
        return new ScanSource(ScanSourceType.FOLDER, List.of(folder));
    }

    public static ScanSource files(List<Path> files) {
        return new ScanSource(ScanSourceType.FILES, files);
    }
}

package com.episort.planning;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * What the library already holds, keyed by media identity.
 *
 * <p>Read-only and built once per plan: for every folder the plan is about to
 * write into, the files already there are parsed and indexed. That is what lets
 * the planner see that an incoming episode is already on disk under a different
 * name, instead of letting a second copy land beside the first.
 *
 * <p>Only the destination folders themselves are read, never the whole
 * workspace — a duplicate that lives somewhere else is not a duplicate the plan
 * is about to create.
 */
final class LibraryIndex {
    /** Same set the parser recognizes; anything else is not a media duplicate. */
    private static final Set<String> MEDIA_EXTENSIONS = Set.of(
            "mkv", "mp4", "avi", "m4v", "mov", "wmv", "ts", "m2ts", "mpg", "mpeg", "flv", "webm");

    private final Map<String, List<Path>> byIdentity;

    private LibraryIndex(Map<String, List<Path>> byIdentity) {
        this.byIdentity = byIdentity;
    }

    static LibraryIndex empty() {
        return new LibraryIndex(Map.of());
    }

    /**
     * Indexes every media file already present in the given folders.
     *
     * <p>An unreadable folder is skipped rather than failing the plan: not being
     * able to look is a reason to report no duplicate, never a reason to refuse
     * to plan.
     */
    static LibraryIndex over(Iterable<Path> folders) {
        Map<String, List<Path>> index = new HashMap<>();
        Set<Path> visited = new LinkedHashSet<>();
        for (Path folder : folders) {
            Path canonical = folder.toAbsolutePath().normalize();
            if (!visited.add(canonical) || !Files.isDirectory(canonical)) {
                continue;
            }
            try (Stream<Path> entries = Files.list(canonical)) {
                entries.filter(Files::isRegularFile)
                        .filter(LibraryIndex::isMediaFile)
                        .forEach(file -> MediaIdentityKey.ofExistingFile(file).ifPresent(key ->
                                index.computeIfAbsent(key.value(), ignored -> new ArrayList<>())
                                        .add(file.toAbsolutePath().normalize())));
            } catch (IOException | RuntimeException exception) {
                System.getLogger(LibraryIndex.class.getName()).log(
                        System.Logger.Level.DEBUG,
                        "Destination folder could not be indexed for duplicates: " + canonical, exception);
            }
        }
        return new LibraryIndex(Map.copyOf(index));
    }

    /**
     * @param excluded keys, from {@link #pathKey(Path)}, of files that must not
     *                 count as a match: the file being planned, the destination it
     *                 is heading for, and every other file the plan already holds a
     *                 row for
     * @return an existing file holding the same episode or movie under a different
     *         name, if there is one
     */
    Optional<Path> existingCopy(MediaIdentityKey key, Set<String> excluded) {
        List<Path> matches = byIdentity.get(key.value());
        if (matches == null) {
            return Optional.empty();
        }
        return matches.stream().filter(candidate -> !excluded.contains(pathKey(candidate))).findFirst();
    }

    /** Windows filesystems are case-insensitive, so paths are compared that way. */
    static String pathKey(Path path) {
        return path.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
    }

    private static boolean isMediaFile(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 && MEDIA_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}

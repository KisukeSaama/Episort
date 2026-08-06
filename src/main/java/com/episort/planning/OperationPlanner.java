package com.episort.planning;

import com.episort.filesystem.WorkspaceBoundary;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Turns a validated pattern into a complete operation plan (Stories 6.3 and 6.4).
 *
 * <p>The planner only ever reads the filesystem — it probes for existing files
 * and folders to detect conflicts and reusable folders. It never creates,
 * renames, moves, or deletes anything.
 */
public final class OperationPlanner {
    private final PlexDestinationNamer namer;

    public OperationPlanner() {
        this(new PlexDestinationNamer());
    }

    public OperationPlanner(PlexDestinationNamer namer) {
        this.namer = Objects.requireNonNull(namer, "namer");
    }

    /**
     * @param workspaceRoot the configured workspace; must exist
     * @param items         the reviewed items captured when the pattern was validated
     */
    public OperationPlan plan(Path workspaceRoot, List<PlanSourceItem> items) throws IOException {
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        Objects.requireNonNull(items, "items");
        WorkspaceBoundary boundary = new WorkspaceBoundary(workspaceRoot);
        Path root = boundary.root();

        List<PlannedOperation> operations = new ArrayList<>(items.size());
        List<Optional<MediaIdentityKey>> identities = new ArrayList<>(items.size());
        for (PlanSourceItem item : items) {
            PlannedOperation operation = planOne(boundary, root, item);
            operations.add(operation);
            identities.add(operation.destinationPath().flatMap(destination -> MediaIdentityKey.of(item, destination)));
        }
        operations = markDuplicateDestinations(operations);
        operations = markDuplicateMedia(operations, identities);
        operations = markLibraryDuplicates(operations, identities);
        return new OperationPlan(root, operations, reusableFolders(root, operations));
    }

    private PlannedOperation planOne(WorkspaceBoundary boundary, Path root, PlanSourceItem item)
            throws IOException {
        // Canonicalize the source the same way destinations are canonicalized, so
        // "same folder" comparisons at execution time cannot be fooled by short
        // names, drive aliases, or an un-normalized workspace path.
        Optional<Path> canonicalSource = boundary.resolvePlannedInside(item.sourcePath());
        Path source = canonicalSource.orElse(item.sourcePath());
        if (item.excluded()) {
            return PlannedOperation.excluded(source, item.kind(), item.exclusionReason());
        }
        if (!item.hasCompleteIdentity()) {
            return PlannedOperation.excluded(source, item.kind(), PlanExclusionReason.UNASSIGNED);
        }
        if (canonicalSource.isEmpty()) {
            return PlannedOperation.conflicting(source, Optional.empty(), item.kind(),
                    PlanConflict.of(PlanConflictType.SOURCE_OUTSIDE_WORKSPACE,
                            "Source is outside the configured workspace: " + source));
        }

        Path destination;
        try {
            destination = namer.destinationFor(root, item);
        } catch (IllegalArgumentException exception) {
            return PlannedOperation.excluded(source, item.kind(), PlanExclusionReason.UNASSIGNED);
        }

        Optional<PlanConflict> folderConflict = blockedFolder(root, destination);
        if (folderConflict.isPresent()) {
            return PlannedOperation.conflicting(source, Optional.of(destination), item.kind(), folderConflict.get());
        }

        Optional<Path> insideWorkspace = boundary.resolvePlannedInside(destination);
        if (insideWorkspace.isEmpty()) {
            return PlannedOperation.conflicting(source, Optional.of(destination), item.kind(),
                    PlanConflict.of(PlanConflictType.DESTINATION_OUTSIDE_WORKSPACE,
                            "Destination would leave the configured workspace: " + destination));
        }
        Path resolved = insideWorkspace.orElseThrow();

        if (samePath(source, resolved)) {
            return PlannedOperation.alreadyInPlace(source, resolved, item.kind());
        }
        if (Files.exists(resolved)) {
            return PlannedOperation.conflicting(source, Optional.of(resolved), item.kind(),
                    PlanConflict.of(PlanConflictType.DESTINATION_FILE_EXISTS,
                            "A file already occupies the destination: " + resolved));
        }
        return PlannedOperation.executable(source, resolved, item.kind());
    }

    /**
     * A destination folder cannot be created when an existing regular file
     * already owns that name.
     */
    private Optional<PlanConflict> blockedFolder(Path root, Path destination) {
        Path parent = destination.getParent();
        while (parent != null && parent.startsWith(root) && !parent.equals(root)) {
            if (Files.exists(parent) && !Files.isDirectory(parent)) {
                return Optional.of(PlanConflict.of(PlanConflictType.DESTINATION_FOLDER_BLOCKED,
                        "A file blocks the destination folder: " + parent));
            }
            parent = parent.getParent();
        }
        return Optional.empty();
    }

    /**
     * Two items resolving to the same destination are both blocked: picking a
     * winner would silently discard the user's other file.
     */
    static List<PlannedOperation> markDuplicateDestinations(List<PlannedOperation> operations) {
        Map<String, List<Integer>> byDestination = new LinkedHashMap<>();
        for (int index = 0; index < operations.size(); index++) {
            PlannedOperation operation = operations.get(index);
            if (operation.status() != PlanOperationStatus.EXECUTABLE
                    && operation.status() != PlanOperationStatus.ALREADY_IN_PLACE) {
                continue;
            }
            String key = destinationKey(operation.destinationPath().orElseThrow());
            byDestination.computeIfAbsent(key, ignored -> new ArrayList<>()).add(index);
        }

        List<PlannedOperation> result = new ArrayList<>(operations);
        for (Map.Entry<String, List<Integer>> entry : byDestination.entrySet()) {
            List<Integer> indexes = entry.getValue();
            if (indexes.size() < 2) {
                continue;
            }
            for (int index : indexes) {
                PlannedOperation operation = result.get(index);
                result.set(index, operation.withConflict(PlanConflict.of(
                        PlanConflictType.DUPLICATE_DESTINATION,
                        indexes.size() + " files target the same destination: "
                                + operation.destinationPath().orElseThrow())));
            }
        }
        return List.copyOf(result);
    }

    /**
     * Two files that are the same episode or the same movie are both blocked,
     * even when their names — and therefore their destinations — differ.
     *
     * <p>Without this, a copy that TVDB matched to an episode title and a copy
     * that did not each get a valid destination of their own, and the library
     * quietly ends up storing the same episode twice.
     */
    static List<PlannedOperation> markDuplicateMedia(
            List<PlannedOperation> operations, List<Optional<MediaIdentityKey>> identities) {
        Map<String, List<Integer>> byIdentity = new LinkedHashMap<>();
        for (int index = 0; index < operations.size(); index++) {
            if (!considerForDuplicates(operations.get(index)) || identities.get(index).isEmpty()) {
                continue;
            }
            byIdentity.computeIfAbsent(identities.get(index).orElseThrow().value(), ignored -> new ArrayList<>())
                    .add(index);
        }

        List<PlannedOperation> result = new ArrayList<>(operations);
        for (List<Integer> indexes : byIdentity.values()) {
            if (indexes.size() < 2) {
                continue;
            }
            // One copy of the group keeps its place and asks nothing: the question
            // is about the extra copies, not about the file that is already named
            // the way the library names it. Blocking every row of the pair would
            // make the user answer twice for one duplicate — and would turn a
            // select-all-then-delete into the loss of both copies.
            int keeper = keeperOf(result, indexes);
            Path kept = result.get(keeper).sourcePath();
            for (int index : indexes) {
                if (index == keeper) {
                    continue;
                }
                PlannedOperation operation = result.get(index);
                result.set(index, operation.withConflict(PlanConflict.duplicateOf(
                        PlanConflictType.DUPLICATE_MEDIA,
                        "Same media as " + kept + ", under a different name",
                        kept)));
            }
        }
        return List.copyOf(result);
    }

    /**
     * Which copy of a duplicate group is the one to keep.
     *
     * <p>The richest name wins: for one identity the namer only ever differs by
     * how much it knows — the copy carrying an episode title produces a strictly
     * longer name than the one that does not. Ties fall back to the most recent
     * file, then to path order, so the same group always elects the same keeper.
     */
    private static int keeperOf(List<PlannedOperation> operations, List<Integer> indexes) {
        int keeper = indexes.get(0);
        for (int index : indexes) {
            if (index != keeper && richerName(operations.get(index), operations.get(keeper))) {
                keeper = index;
            }
        }
        return keeper;
    }

    private static boolean richerName(PlannedOperation candidate, PlannedOperation incumbent) {
        String left = candidate.destinationPath().orElseThrow().getFileName().toString();
        String right = incumbent.destinationPath().orElseThrow().getFileName().toString();
        if (left.length() != right.length()) {
            return left.length() > right.length();
        }
        int byDate = PlanConflictResolver.lastModified(candidate.sourcePath()).orElse(Instant.EPOCH)
                .compareTo(PlanConflictResolver.lastModified(incumbent.sourcePath()).orElse(Instant.EPOCH));
        return byDate != 0
                ? byDate > 0
                : destinationKey(candidate.sourcePath()).compareTo(destinationKey(incumbent.sourcePath())) < 0;
    }

    /**
     * Blocks every file the library already holds under a different name.
     *
     * <p>The exact-path case is {@link PlanConflictType#DESTINATION_FILE_EXISTS}
     * and is caught while planning; this is the case that used to slip through —
     * same episode, different name, so nothing stood at the destination and a
     * second copy landed beside the first.
     *
     * <p>Files the plan already has a row for are never reported here: that row
     * carries its own decision, and raising a second conflict on the copy sitting
     * at the destination would make the user answer twice for one pair.
     */
    private static List<PlannedOperation> markLibraryDuplicates(
            List<PlannedOperation> operations, List<Optional<MediaIdentityKey>> identities) {
        List<Path> destinationFolders = new ArrayList<>();
        for (int index = 0; index < operations.size(); index++) {
            if (!considerForDuplicates(operations.get(index)) || identities.get(index).isEmpty()) {
                continue;
            }
            operations.get(index).destinationPath()
                    .map(Path::getParent)
                    .filter(Objects::nonNull)
                    .ifPresent(destinationFolders::add);
        }
        if (destinationFolders.isEmpty()) {
            return operations;
        }

        Set<String> plannedFiles = new LinkedHashSet<>();
        for (PlannedOperation operation : operations) {
            plannedFiles.add(LibraryIndex.pathKey(operation.sourcePath()));
        }

        LibraryIndex library = LibraryIndex.over(destinationFolders);
        List<PlannedOperation> result = new ArrayList<>(operations);
        for (int index = 0; index < operations.size(); index++) {
            PlannedOperation operation = operations.get(index);
            if (!considerForDuplicates(operation) || identities.get(index).isEmpty()) {
                continue;
            }
            Path destination = operation.destinationPath().orElseThrow();
            Set<String> excluded = new LinkedHashSet<>(plannedFiles);
            excluded.add(LibraryIndex.pathKey(destination));
            Optional<Path> existing = library.existingCopy(identities.get(index).orElseThrow(), excluded);
            if (existing.isEmpty()) {
                continue;
            }
            result.set(index, operation.withConflict(PlanConflict.duplicateOf(
                    PlanConflictType.MEDIA_ALREADY_IN_LIBRARY,
                    "The library already holds this media under another name: " + existing.orElseThrow(),
                    existing.orElseThrow())));
        }
        return List.copyOf(result);
    }

    /** Only rows that would still put a file in the library can create a duplicate. */
    private static boolean considerForDuplicates(PlannedOperation operation) {
        return (operation.status() == PlanOperationStatus.EXECUTABLE
                        || operation.status() == PlanOperationStatus.ALREADY_IN_PLACE)
                && operation.destinationPath().isPresent();
    }

    /**
     * Existing series, season, specials, and movie folders that the plan will
     * reuse instead of creating (Story 6.4).
     */
    private List<Path> reusableFolders(Path root, List<PlannedOperation> operations) {
        Set<Path> reused = new LinkedHashSet<>();
        for (PlannedOperation operation : operations) {
            if (operation.destinationPath().isEmpty()) {
                continue;
            }
            Path parent = operation.destinationPath().orElseThrow().getParent();
            while (parent != null && parent.startsWith(root) && !parent.equals(root)) {
                if (Files.isDirectory(parent)) {
                    reused.add(parent);
                }
                parent = parent.getParent();
            }
        }
        return List.copyOf(reused);
    }

    private static boolean samePath(Path left, Path right) {
        return destinationKey(left).equals(destinationKey(right));
    }

    /** Windows filesystems are case-insensitive, so destinations are compared that way. */
    private static String destinationKey(Path path) {
        return path.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
    }
}

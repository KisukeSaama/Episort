package com.episort.filesystem;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * The only place in the application allowed to create folders and move media
 * files (Story 7.2).
 *
 * <p>Every path is revalidated against the workspace boundary immediately before
 * the mutation, not just at planning time — a boundary check that ran minutes
 * earlier proves nothing about the filesystem as it is now. An occupied
 * destination is an error by default: overwriting happens only when the caller
 * passes {@code replaceExisting}, which carries a conflict resolution the user
 * made explicitly in front of the exact plan — never a silent replacement.
 *
 * <p>Deletion exists for emptied-out source folders, through {@link
 * #deleteFolderTree(Path)} / {@link #deleteFolderIfEmpty(Path)}, and for a single
 * approved file through {@link #deleteFile(Path)} — the duplicate the user chose
 * to throw away in front of the exact plan. All three refuse the workspace root
 * and anything resolving outside the workspace, and none ever follows a symlink
 * out of it. Deciding <em>what</em> deserves to go is the caller's job, not this
 * class's.
 */
public final class MediaFileMover {
    /**
     * Whether a deleted file is offered to the system recycle bin first. Only
     * ever turned off by {@code -Depisort.delete.recycleBin=false}, which the test
     * run sets so deletion never depends on a desktop session.
     */
    private static final boolean RECYCLE_BIN =
            !"false".equalsIgnoreCase(System.getProperty("episort.delete.recycleBin"));

    private final WorkspaceBoundary boundary;

    public MediaFileMover(WorkspaceBoundary boundary) {
        this.boundary = Objects.requireNonNull(boundary, "boundary");
    }

    /**
     * Creates the destination folder chain, reusing every folder that already
     * exists.
     *
     * @return the folders actually created, parents first
     */
    public List<Path> createFolders(Path folder) throws IOException {
        Objects.requireNonNull(folder, "folder");
        Path target = requireInsideWorkspace(folder);
        List<Path> missing = new ArrayList<>();
        Path cursor = target;
        while (cursor != null && !Files.isDirectory(cursor) && cursor.startsWith(boundary.root())) {
            missing.add(cursor);
            cursor = cursor.getParent();
        }
        Collections.reverse(missing);
        List<Path> created = new ArrayList<>(missing.size());
        for (Path directory : missing) {
            requireInsideWorkspace(directory);
            Files.createDirectory(directory);
            created.add(directory);
        }
        return List.copyOf(created);
    }

    /**
     * Moves or renames one approved file.
     *
     * @throws FileAlreadyExistsException when the destination is occupied; the
     *         caller decides what to do, this class never overwrites
     * @throws IOException on any other filesystem failure, including a source or
     *         destination that no longer resolves inside the workspace
     */
    public void move(Path source, Path destination) throws IOException {
        move(source, destination, false);
    }

    /**
     * Moves or renames one approved file, overwriting the destination only when
     * the caller explicitly asked for it.
     *
     * <p>{@code replaceExisting} is not a convenience: it exists solely to carry
     * out a conflict resolution the user made in front of the exact plan. Even
     * then, only a regular file may be replaced — a folder standing at the
     * destination is still an error.
     *
     * @throws FileAlreadyExistsException when the destination is occupied and
     *         replacement was not requested
     * @throws IOException on any other filesystem failure, including a source or
     *         destination that no longer resolves inside the workspace
     */
    public void move(Path source, Path destination, boolean replaceExisting) throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        Path safeSource = requireExistingInsideWorkspace(source);
        Path safeDestination = requireInsideWorkspace(destination);
        boolean occupied = Files.exists(safeDestination);
        if (occupied && !replaceExisting) {
            throw new FileAlreadyExistsException(safeDestination.toString());
        }
        if (occupied && Files.isDirectory(safeDestination, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Refusing to replace a folder: " + safeDestination);
        }
        Path parent = safeDestination.getParent();
        if (parent != null) {
            createFolders(parent);
        }
        StandardCopyOption[] options = replaceExisting
                ? new StandardCopyOption[] {StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE}
                : new StandardCopyOption[] {StandardCopyOption.ATOMIC_MOVE};
        try {
            Files.move(safeSource, safeDestination, options);
        } catch (AtomicMoveNotSupportedException exception) {
            // Cross-volume moves inside the same workspace cannot be atomic.
            if (replaceExisting) {
                Files.move(safeSource, safeDestination, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(safeSource, safeDestination);
            }
        }
    }

    /**
     * Removes one approved file, because the user decided in front of the exact
     * plan that this copy was the one too many.
     *
     * <p>The recycle bin is tried first, so a decision taken in a hurry stays
     * recoverable; only where the system offers no bin does the file go for good.
     * A folder standing at that path is never touched — this method deletes files
     * and nothing else.
     *
     * @throws NoSuchFileException when the file is already gone
     * @throws IOException when the path resolves outside the workspace, is the
     *         workspace root, is a folder, or cannot be removed
     */
    public void deleteFile(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        Path target = requireExistingInsideWorkspace(file);
        if (target.equals(boundary.root())) {
            throw new IOException("Refusing to delete the workspace root: " + target);
        }
        if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Refusing to delete a folder: " + target);
        }
        if (!RECYCLE_BIN || !moveToTrash(target)) {
            Files.delete(target);
        }
    }

    /**
     * @return true when the system took the file into its recycle bin; false when
     *         there is no bin to take it, leaving the caller to delete outright
     */
    private static boolean moveToTrash(Path target) {
        try {
            if (!Desktop.isDesktopSupported()) {
                return false;
            }
            Desktop desktop = Desktop.getDesktop();
            return desktop.isSupported(Desktop.Action.MOVE_TO_TRASH) && desktop.moveToTrash(target.toFile());
        } catch (RuntimeException exception) {
            // Headless sessions and desktops without a bin are ordinary here, not
            // failures: the permanent delete below is the answer either way.
            return false;
        }
    }

    /**
     * Deletes a source folder and everything still inside it, after every media
     * file the plan listed for that folder has been moved out.
     *
     * <p>Destructive by design: leftovers such as .nfo files, artwork, or sample
     * clips go with it. Symlinked entries are unlinked, never followed, so a link
     * pointing outside the workspace cannot drag its target along.
     *
     * @return true when the folder existed and was removed
     * @throws IOException when the folder is the workspace root, resolves outside
     *         the workspace, or cannot be removed
     */
    public boolean deleteFolderTree(Path folder) throws IOException {
        Objects.requireNonNull(folder, "folder");
        Path target = requireDeletableFolder(folder);
        if (target == null) {
            return false;
        }
        Files.walkFileTree(target, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
        return true;
    }

    /**
     * Removes a folder only if nothing is left in it — used to walk back up the
     * source tree once a leaf folder is gone, without ever destroying content.
     *
     * @return true when the folder existed, was empty, and was removed
     */
    public boolean deleteFolderIfEmpty(Path folder) throws IOException {
        Objects.requireNonNull(folder, "folder");
        Path target = requireDeletableFolder(folder);
        if (target == null) {
            return false;
        }
        try (Stream<Path> entries = Files.list(target)) {
            if (entries.findAny().isPresent()) {
                return false;
            }
        }
        Files.delete(target);
        return true;
    }

    /**
     * @return the canonical folder, or null when there is nothing to delete
     * @throws IOException when deleting it would be unsafe
     */
    private Path requireDeletableFolder(Path folder) throws IOException {
        Path target = requireInsideWorkspace(folder);
        if (target.equals(boundary.root())) {
            throw new IOException("Refusing to delete the workspace root: " + target);
        }
        if (!Files.exists(target)) {
            return null;
        }
        if (!Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Not a folder: " + target);
        }
        return target;
    }

    private Path requireInsideWorkspace(Path candidate) throws IOException {
        return boundary.resolvePlannedInside(candidate)
                .orElseThrow(() -> new IOException("Path is outside the configured workspace: " + candidate));
    }

    private Path requireExistingInsideWorkspace(Path candidate) throws IOException {
        Path resolved = requireInsideWorkspace(candidate);
        if (!Files.exists(resolved)) {
            throw new NoSuchFileException(resolved.toString());
        }
        return resolved;
    }
}

package com.episort.filesystem;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lists one directory for the read-only workspace explorer.
 *
 * <p>Every listing request is checked against the canonical workspace
 * boundary. Symbolic links are visible so the explorer reflects what is on
 * disk, but they are never classified as expandable directories.
 */
public final class WorkspaceDirectoryReader {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("avi", "mp4", "mkv");
    private static final Comparator<WorkspaceDirectoryEntry> DISPLAY_ORDER =
            Comparator.comparing(WorkspaceDirectoryEntry::directory).reversed()
                    .thenComparing(WorkspaceDirectoryEntry::name, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(WorkspaceDirectoryEntry::name);

    public List<WorkspaceDirectoryEntry> list(Path workspaceRoot, Path directory) throws IOException {
        WorkspaceBoundary boundary = new WorkspaceBoundary(workspaceRoot);
        Path realDirectory = boundary.resolveInside(directory)
                .filter(Files::isDirectory)
                .orElseThrow(() -> new IOException(
                        "Directory is outside the configured workspace or is unavailable: " + directory));

        List<WorkspaceDirectoryEntry> entries = new ArrayList<>();
        try (DirectoryStream<Path> children = Files.newDirectoryStream(realDirectory)) {
            for (Path child : children) {
                boolean symbolicLink = Files.isSymbolicLink(child);
                boolean directoryChild = !symbolicLink
                        && Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS);
                String name = child.getFileName() == null
                        ? child.toString()
                        : child.getFileName().toString();
                entries.add(new WorkspaceDirectoryEntry(
                        child,
                        name,
                        directoryChild,
                        !directoryChild && !symbolicLink && isSupportedMedia(name),
                        symbolicLink));
            }
        }
        entries.sort(DISPLAY_ORDER);
        return List.copyOf(entries);
    }

    static boolean isSupportedMedia(String filename) {
        int separator = filename.lastIndexOf('.');
        if (separator < 0 || separator == filename.length() - 1) {
            return false;
        }
        return SUPPORTED_EXTENSIONS.contains(
                filename.substring(separator + 1).toLowerCase(Locale.ROOT));
    }
}

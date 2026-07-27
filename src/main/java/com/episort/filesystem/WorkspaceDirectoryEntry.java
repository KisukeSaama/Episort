package com.episort.filesystem;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One direct child displayed by the read-only workspace explorer.
 *
 * @param path absolute normalized path of the child
 * @param name display name of the child
 * @param directory true for a directory that may be expanded
 * @param supportedMedia true for an Episort-supported video file
 * @param symbolicLink true when the child is a symbolic link; links are shown
 *                     but never offered as expandable directories
 */
public record WorkspaceDirectoryEntry(
        Path path,
        String name,
        boolean directory,
        boolean supportedMedia,
        boolean symbolicLink) {

    public WorkspaceDirectoryEntry {
        path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        name = Objects.requireNonNull(name, "name");
    }
}

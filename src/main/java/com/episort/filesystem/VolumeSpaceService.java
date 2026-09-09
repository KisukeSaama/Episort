package com.episort.filesystem;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Reads the logical filesystem volume containing a workspace. */
public final class VolumeSpaceService {
    public Optional<VolumeSpace> read(Path workspace) {
        Objects.requireNonNull(workspace, "workspace");
        try {
            FileStore store = Files.getFileStore(workspace.toRealPath());
            long total = Math.max(0, store.getTotalSpace());
            long unallocated = Math.clamp(store.getUnallocatedSpace(), 0, total);
            long available = Math.clamp(store.getUsableSpace(), 0, total);
            return Optional.of(new VolumeSpace(total, total - unallocated, available));
        } catch (IOException | RuntimeException exception) {
            // A server that does not report its free space is not an error the
            // user can act on: the gauge shows nothing rather than a guess.
            return Optional.empty();
        }
    }
}

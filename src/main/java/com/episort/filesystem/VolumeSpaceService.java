package com.episort.filesystem;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.extensions.openssh.OpenSSHStatExtensionInfo;
import org.apache.sshd.sftp.client.extensions.openssh.OpenSSHStatPathExtension;
import org.apache.sshd.sftp.client.fs.SftpPath;

/** Reads the logical filesystem volume containing a workspace. */
public final class VolumeSpaceService {
    public Optional<VolumeSpace> read(Path workspace) {
        Objects.requireNonNull(workspace, "workspace");
        try {
            if (workspace instanceof SftpPath remote) {
                // SftpFileStore returns Long.MAX_VALUE, not server statistics.
                // Query the workspace path so nested server mounts are respected.
                try (SftpClient client = remote.getFileSystem().getClient()) {
                    OpenSSHStatPathExtension extension = client.getExtension(OpenSSHStatPathExtension.class);
                    if (extension == null || !extension.isSupported()) {
                        return Optional.empty();
                    }
                    return fromStat(extension.stat(remote.toRealPath().toString()));
                }
            }
            FileStore store = Files.getFileStore(workspace.toRealPath());
            return fromBytes(store.getTotalSpace(), store.getUnallocatedSpace(), store.getUsableSpace());
        } catch (IOException | RuntimeException exception) {
            // A server that does not report its free space is not an error the
            // user can act on: the gauge shows nothing rather than a guess.
            return Optional.empty();
        }
    }

    static Optional<VolumeSpace> fromStat(OpenSSHStatExtensionInfo stat) {
        if (stat.f_frsize <= 0 || stat.f_blocks < 0 || stat.f_bfree < 0 || stat.f_bavail < 0) {
            return Optional.empty();
        }
        try {
            return fromBytes(Math.multiplyExact(stat.f_blocks, stat.f_frsize),
                    Math.multiplyExact(stat.f_bfree, stat.f_frsize),
                    Math.multiplyExact(stat.f_bavail, stat.f_frsize));
        } catch (ArithmeticException exception) {
            return Optional.empty();
        }
    }

    private static Optional<VolumeSpace> fromBytes(long total, long unallocated, long available) {
        if (total <= 0 || total == Long.MAX_VALUE || unallocated < 0 || unallocated > total
                || available < 0 || available > unallocated) {
            return Optional.empty();
        }
        return Optional.of(new VolumeSpace(total, total - unallocated, available));
    }
}

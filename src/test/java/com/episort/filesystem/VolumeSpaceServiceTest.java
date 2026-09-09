package com.episort.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.sftp.client.extensions.openssh.OpenSSHStatExtensionInfo;
import org.apache.sshd.sftp.common.SftpConstants;
import org.apache.sshd.sftp.common.extensions.openssh.AbstractOpenSSHExtensionParser.OpenSSHExtension;
import org.apache.sshd.sftp.server.SftpSubsystem;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VolumeSpaceServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void readsTheContainingLogicalVolumeRatherThanDirectoryContents() throws Exception {
        Files.writeString(temporaryDirectory.resolve("small-file.txt"), "episort");

        VolumeSpace space = new VolumeSpaceService().read(temporaryDirectory).orElseThrow();
        long fileStoreCapacity = Files.getFileStore(temporaryDirectory).getTotalSpace();

        assertEquals(fileStoreCapacity, space.totalBytes());
        assertTrue(space.totalBytes() > Files.size(temporaryDirectory.resolve("small-file.txt")));
        assertTrue(space.availableBytes() > 0);
        assertTrue(space.usedBytes() > 0);
    }

    @Test
    void missingLocalDirectoryHasNoMeasurement() {
        assertTrue(new VolumeSpaceService().read(temporaryDirectory.resolve("missing")).isEmpty());
    }

    @Test
    void sftpWithoutVolumeStatisticsDoesNotDisplayPlaceholderCapacity() throws Exception {
        Path serverRoot = Files.createDirectory(temporaryDirectory.resolve("server"));
        try (EmbeddedSftpServer server = EmbeddedSftpServer.start(serverRoot);
                RemoteWorkspaceSessions sessions = new RemoteWorkspaceSessions(
                        temporaryDirectory.resolve("known_hosts"))) {
            Path workspace = sessions.connect(server.workspace("/"));

            assertTrue(new VolumeSpaceService().read(workspace).isEmpty());
        }
    }

    @Test
    void remoteStatisticsUseFragmentSizeAndKeepReservedSpaceSeparate() {
        OpenSSHStatExtensionInfo stat = statistics();

        assertEquals(new VolumeSpace(409600, 245760, 122880),
                VolumeSpaceService.fromStat(stat).orElseThrow());
    }

    @Test
    void queriesStatisticsForTheSelectedRemoteDirectoryAndKeepsSessionOpen() throws Exception {
        Path serverRoot = Files.createDirectory(temporaryDirectory.resolve("server"));
        Files.createDirectory(serverRoot.resolve("media"));
        AtomicReference<String> requestedPath = new AtomicReference<>();
        SftpSubsystemFactory factory = new SftpSubsystemFactory() {
            @Override
            public Command createSubsystem(ChannelSession channel) {
                return new SftpSubsystem(channel, this) {
                    @Override
                    protected List<OpenSSHExtension> resolveOpenSSHExtensions(ServerSession session) {
                        return List.of(new OpenSSHExtension("statvfs@openssh.com", "2"));
                    }

                    @Override
                    protected void executeExtendedCommand(Buffer buffer, int id, String extension)
                            throws IOException {
                        if (!"statvfs@openssh.com".equals(extension)) {
                            super.executeExtendedCommand(buffer, id, extension);
                            return;
                        }
                        requestedPath.set(buffer.getString());
                        buffer = prepareReply(buffer);
                        buffer.putByte((byte) SftpConstants.SSH_FXP_EXTENDED_REPLY);
                        buffer.putInt(id);
                        statistics().encode(buffer);
                        send(buffer);
                    }
                };
            }
        };
        try (EmbeddedSftpServer server = EmbeddedSftpServer.start(serverRoot, factory);
                RemoteWorkspaceSessions sessions = new RemoteWorkspaceSessions(
                        temporaryDirectory.resolve("known_hosts"))) {
            Path workspace = sessions.connect(server.workspace("/media"));

            assertEquals(new VolumeSpace(409600, 245760, 122880),
                    new VolumeSpaceService().read(workspace).orElseThrow());
            assertEquals("/media", requestedPath.get());
            assertTrue(Files.isDirectory(workspace));
        }
    }

    @Test
    void remoteStatisticsRejectUnknownOverflowingAndInconsistentValues() {
        for (long invalid : new long[] {-1, 0, Long.MAX_VALUE}) {
            OpenSSHStatExtensionInfo stat = statistics();
            stat.f_frsize = invalid;
            assertTrue(VolumeSpaceService.fromStat(stat).isEmpty());
        }
        OpenSSHStatExtensionInfo stat = statistics();
        stat.f_bfree = 101;
        assertTrue(VolumeSpaceService.fromStat(stat).isEmpty());
        stat = statistics();
        stat.f_bavail = -1;
        assertTrue(VolumeSpaceService.fromStat(stat).isEmpty());
        stat = statistics();
        stat.f_bavail = 41;
        assertTrue(VolumeSpaceService.fromStat(stat).isEmpty());
    }

    private static OpenSSHStatExtensionInfo statistics() {
        OpenSSHStatExtensionInfo stat = new OpenSSHStatExtensionInfo();
        stat.f_bsize = 8192;
        stat.f_frsize = 4096;
        stat.f_blocks = 100;
        stat.f_bfree = 40;
        stat.f_bavail = 30;
        return stat;
    }
}

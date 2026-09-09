package com.episort.ui.platform;

import com.episort.filesystem.RemoteWorkspaceSessions;
import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Non-mutating integrations with the operating system's file manager and media player. */
public final class DesktopFileActions {
    private DesktopFileActions() {
    }

    /** False for a file on a server: no local file manager or player can show it. */
    public static boolean isLocal(Path file) {
        return !RemoteWorkspaceSessions.isRemote(file);
    }

    public static void openParent(Path file) throws IOException {
        if (!isLocal(file)) {
            throw new UnsupportedOperationException("Remote files cannot be shown in the local file manager");
        }
        Path safeFile = file.toAbsolutePath().normalize();
        Path parent = safeFile.getParent();
        if (parent == null) return;
        List<String> command = openParentCommand(safeFile, System.getProperty("os.name", ""));
        if (!command.isEmpty()) {
            new ProcessBuilder(command).start();
            return;
        }
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(parent.toFile());
    }

    public static boolean canOpenFile() {
        return Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN);
    }

    public static void openFile(Path file) throws IOException {
        if (!isLocal(file)) {
            throw new UnsupportedOperationException("Remote files cannot be opened with a local player");
        }
        Path safeFile = file.toAbsolutePath().normalize();
        if (!canOpenFile()) return;
        Desktop.getDesktop().open(safeFile.toFile());
    }

    static List<String> openParentCommand(Path file, String operatingSystem) {
        if (operatingSystem != null && operatingSystem.toLowerCase(Locale.ROOT).contains("win")) {
            Path parent = file.toAbsolutePath().normalize().getParent();
            return parent == null ? List.of() : List.of("explorer.exe", parent.toString());
        }
        return List.of();
    }
}

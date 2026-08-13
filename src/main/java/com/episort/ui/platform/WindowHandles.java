package com.episort.ui.platform;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.ptr.IntByReference;
import java.util.Optional;
import javafx.stage.Stage;

/**
 * Finds the native Windows handle behind a JavaFX {@link Stage}.
 *
 * <p>JavaFX does not expose it, so it has to be recovered from the window list.
 * Matching on the title alone is not enough: {@code FindWindow} searches every
 * window on the desktop, and an Explorer window showing a folder named
 * "Episort" answers to the same title. Everything here would then be applied to
 * that window instead — a dark title bar, rounded corners, or worse, a move.
 * Restricting the search to this process removes the ambiguity.
 */
final class WindowHandles {

    private WindowHandles() {
    }

    /** The visible top-level window of this process carrying the stage's title. */
    static Optional<HWND> of(Stage stage) {
        String title = stage == null ? null : stage.getTitle();
        if (title == null || title.isBlank()) {
            return Optional.empty();
        }
        try {
            int currentProcess = Kernel32.INSTANCE.GetCurrentProcessId();
            HWND[] match = new HWND[1];
            User32.INSTANCE.EnumWindows((window, data) -> {
                IntByReference owningProcess = new IntByReference();
                User32.INSTANCE.GetWindowThreadProcessId(window, owningProcess);
                if (owningProcess.getValue() != currentProcess
                        || !User32.INSTANCE.IsWindowVisible(window)) {
                    return true;
                }
                char[] text = new char[title.length() + 2];
                int length = User32.INSTANCE.GetWindowText(window, text, text.length);
                if (length == title.length() && title.contentEquals(new String(text, 0, length))) {
                    match[0] = window;
                    return false;
                }
                return true;
            }, null);
            return Optional.ofNullable(match[0]);
        } catch (RuntimeException | UnsatisfiedLinkError unavailable) {
            return Optional.empty();
        }
    }
}

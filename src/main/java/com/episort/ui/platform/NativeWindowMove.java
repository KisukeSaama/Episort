package com.episort.ui.platform;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import java.util.Locale;
import java.util.Optional;
import javafx.geometry.Point2D;
import javafx.stage.Stage;

/**
 * Hands a window drag to Windows instead of doing it in Java.
 *
 * <p>Episort's window is undecorated, so the title bar is ours and so, until
 * now, was the dragging: every mouse event moved the stage by hand. That can
 * only ever be as smooth as the application's own event loop, which is why the
 * window lagged behind the pointer while Explorer, Brave and Discord did not —
 * they do what this class does. Telling Windows that the press landed on the
 * caption makes the system run its own move loop, so the compositor moves the
 * window at the display's rate.
 *
 * <p>The call blocks until the user drops the window, exactly as it does in a
 * native application, and the window keeps its last painted frame meanwhile.
 *
 * <p>Aero Snap comes with it only once the window carries the system frame —
 * see {@link NativeWindowFrame}. Without that frame the move is still the
 * system's, but the edges do nothing, and {@link WindowManager} falls back to
 * snapping the window itself.
 *
 * <p>Set {@code -Depisort.window.nativeMove=false} to fall back to the Java
 * drag, which stays in place for every non-Windows platform anyway.
 */
final class NativeWindowMove {

    static final String DISABLE_PROPERTY = "episort.window.nativeMove";

    private static final int WM_NCLBUTTONDOWN = 0x00A1;
    private static final int HTCAPTION = 0x0002;

    private NativeWindowMove() {
    }

    static boolean isSupported() {
        return isWindows(System.getProperty("os.name")) && isEnabled(System.getProperty(DISABLE_PROPERTY));
    }

    static boolean isWindows(String osName) {
        return osName != null && osName.toLowerCase(Locale.ROOT).startsWith("windows");
    }

    static boolean isEnabled(String propertyValue) {
        return !"false".equalsIgnoreCase(propertyValue);
    }

    /**
     * Starts the system move loop for the stage's window and returns once the
     * drag is over. Returns {@code false} when the platform cannot do it, in
     * which case the caller keeps its own drag handling.
     */
    static boolean begin(Stage stage) {
        if (!isSupported()) {
            return false;
        }
        return WindowHandles.of(stage).map(window -> {
            try {
                // The application still holds the mouse capture from the press
                // it just received; the system loop cannot start until it is
                // let go.
                User32Extras.INSTANCE.ReleaseCapture();
                User32.INSTANCE.SendMessage(
                        window, WM_NCLBUTTONDOWN, new WPARAM(HTCAPTION), new LPARAM(0));
                return true;
            } catch (RuntimeException | UnsatisfiedLinkError unavailable) {
                return false;
            }
        }).orElse(false);
    }

    /**
     * Where the pointer sits inside the window, in the stage's own units.
     *
     * <p>Needed because the drop point cannot simply be assumed to be the point
     * the window was grabbed at: Windows refuses to push a window past the top
     * of the desktop, so the pointer keeps rising while the window stops, and a
     * drag to the top edge would otherwise never read as one.
     *
     * <p>Measuring the pointer against the window's own native rectangle rather
     * than against the screen keeps this free of any display-scaling arithmetic:
     * the ratio between the two rectangles is the scale, whichever monitor the
     * window is on.
     */
    static Optional<Point2D> pointerOffset(Stage stage) {
        if (!isSupported() || stage.getWidth() <= 0 || stage.getHeight() <= 0) {
            return Optional.empty();
        }
        return WindowHandles.of(stage).flatMap(window -> {
            try {
                POINT pointer = new POINT();
                RECT frame = new RECT();
                if (!User32.INSTANCE.GetCursorPos(pointer) || !User32.INSTANCE.GetWindowRect(window, frame)) {
                    return Optional.empty();
                }
                return offsetInStage(
                        pointer.x - frame.left,
                        pointer.y - frame.top,
                        frame.right - frame.left,
                        frame.bottom - frame.top,
                        stage.getWidth(),
                        stage.getHeight());
            } catch (RuntimeException | UnsatisfiedLinkError unavailable) {
                return Optional.empty();
            }
        });
    }

    /** Converts an offset measured in device pixels into the stage's units. */
    static Optional<Point2D> offsetInStage(
            int pointerX, int pointerY, int nativeWidth, int nativeHeight, double width, double height) {
        if (nativeWidth <= 0 || nativeHeight <= 0) {
            return Optional.empty();
        }
        return Optional.of(new Point2D(pointerX * width / nativeWidth, pointerY * height / nativeHeight));
    }
}

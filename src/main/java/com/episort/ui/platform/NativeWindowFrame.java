package com.episort.ui.platform;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.Structure.FieldOrder;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.MONITORINFO;
import com.sun.jna.platform.win32.WinUser.WindowProc;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.stage.Stage;

/**
 * Gives Episort's window the Windows frame back, without letting it be drawn.
 *
 * <p>Everything the system does with a window — snapping it to a half, the
 * animation into that half, Snap Assist offering the other one, the Windows 11
 * snap layouts, minimizing into the taskbar — it does only for windows that
 * carry {@code WS_THICKFRAME} and {@code WS_MAXIMIZEBOX}. An undecorated stage
 * carries neither, and none of it can be imitated: Snap Assist in particular is
 * a shell feature with no API behind it. Reproducing snapping by hand is what
 * Episort did until now, and it read as an imitation because it was one.
 *
 * <p>So the styles go back on. On their own they would also bring back the
 * sizing border, which would sit on top of the title bar Episort draws itself,
 * so the window procedure is subclassed for the single message that decides how
 * much of the window Windows keeps for its frame: answering
 * {@code WM_NCCALCSIZE} with "none" leaves the whole window as client area. The
 * frame exists for the system, and does not exist on screen.
 *
 * <p>Maximizing needs one more answer. Windows maximizes by setting the window
 * rectangle to the work area <em>plus</em> the frame it expects to be there, so
 * a window with no frame left would spill past the screen edges and under the
 * taskbar. Rather than hand the frame back for that one state — which would
 * make the window wider than the part of it JavaFX can draw into, and cut the
 * close button off the right-hand end — the maximized size is answered directly
 * through {@code WM_GETMINMAXINFO}: exactly the work area, no inflation. The
 * client area is then the whole window in every state, which is the one thing
 * the JavaFX side takes for granted.
 *
 * <p>Set {@code -Depisort.window.systemFrame=false} to leave the window as it
 * was, with Episort's own snapping.
 */
final class NativeWindowFrame {

    static final String DISABLE_PROPERTY = "episort.window.systemFrame";

    private static final int WM_NCCALCSIZE = 0x0083;
    private static final int WM_NCPAINT = 0x0085;
    private static final int WM_NCACTIVATE = 0x0086;
    private static final int WM_GETMINMAXINFO = 0x0024;
    /** {@code WM_NCACTIVATE}'s "leave the frame alone" region. */
    private static final int NO_FRAME_REPAINT = -1;
    private static final int MONITOR_DEFAULTTONEAREST = 2;
    private static final int GWLP_WNDPROC = -4;
    private static final int FRAME_STYLES =
            WinUser.WS_THICKFRAME | WinUser.WS_MAXIMIZEBOX | WinUser.WS_MINIMIZEBOX;
    private static final int REFRESH_FLAGS = WinUser.SWP_FRAMECHANGED
            | WinUser.SWP_NOMOVE | WinUser.SWP_NOSIZE | WinUser.SWP_NOZORDER | WinUser.SWP_NOACTIVATE;

    /**
     * A subclassed procedure has to outlive the window it serves, and JNA
     * collects a callback nothing references — after which Windows calls into
     * freed memory. Holding them here is what keeps that from happening.
     */
    private static final List<NativeWindowFrame> INSTALLED = new ArrayList<>();

    private final HWND window;
    private final Pointer previousProcedure;
    private final WindowProc procedure;

    private NativeWindowFrame(HWND window, Pointer previousProcedure, WindowProc procedure) {
        this.window = window;
        this.previousProcedure = previousProcedure;
        this.procedure = procedure;
    }

    /** Whether the platform can carry a system frame at all. */
    static boolean isSupported() {
        return NativeWindowMove.isWindows(System.getProperty("os.name"))
                && !"false".equalsIgnoreCase(System.getProperty(DISABLE_PROPERTY));
    }

    /**
     * Adopts the system frame for a stage that is already showing.
     *
     * <p>The stage has to be on screen: the window it is asking about does not
     * exist before that.
     */
    static Optional<NativeWindowFrame> install(Stage stage) {
        if (!isSupported()) {
            return Optional.empty();
        }
        Optional<HWND> window = WindowHandles.of(stage);
        if (window.isEmpty()) {
            report("window not found");
            return Optional.empty();
        }
        return window.flatMap(NativeWindowFrame::adopt);
    }

    private static void report(String reason) {
        if (Boolean.getBoolean("episort.debug.window")) {
            System.out.println("[episort.window] " + reason);
        }
    }

    private static Optional<NativeWindowFrame> adopt(HWND window) {
        try {
            // Subclassed before the styles change, because changing them is
            // itself what asks the window how much frame it wants.
            NativeWindowFrame[] frame = new NativeWindowFrame[1];
            WindowProc procedure = (hwnd, message, wParam, lParam) ->
                    frame[0].dispatch(hwnd, message, wParam, lParam);
            Pointer previous = User32Extras.INSTANCE.SetWindowLongPtrW(window, GWLP_WNDPROC, procedure);
            if (previous == null) {
                report("window procedure could not be replaced");
                return Optional.empty();
            }
            frame[0] = new NativeWindowFrame(window, previous, procedure);
            INSTALLED.add(frame[0]);

            int style = User32.INSTANCE.GetWindowLong(window, WinUser.GWL_STYLE);
            User32.INSTANCE.SetWindowLong(window, WinUser.GWL_STYLE, style | FRAME_STYLES);
            User32.INSTANCE.SetWindowPos(window, null, 0, 0, 0, 0, REFRESH_FLAGS);
            return Optional.of(frame[0]);
        } catch (RuntimeException | UnsatisfiedLinkError unavailable) {
            report("frame refused: " + unavailable);
            return Optional.empty();
        }
    }

    /**
     * What the adoption actually produced, for {@code -Depisort.debug.window=true}.
     *
     * <p>The interesting number is the inset: the frame is only invisible if the
     * client rectangle covers the whole window, and nothing on screen says so
     * clearly enough to be judged by eye.
     */
    String describe() {
        try {
            RECT frame = new RECT();
            RECT client = new RECT();
            User32.INSTANCE.GetWindowRect(window, frame);
            User32.INSTANCE.GetClientRect(window, client);
            int style = User32.INSTANCE.GetWindowLong(window, WinUser.GWL_STYLE);
            return "[episort.window] style=0x%08X sizable=%s maximizable=%s window=%dx%d client=%dx%d inset=%d"
                    .formatted(
                            style,
                            (style & WinUser.WS_THICKFRAME) != 0,
                            (style & WinUser.WS_MAXIMIZEBOX) != 0,
                            frame.right - frame.left,
                            frame.bottom - frame.top,
                            client.right - client.left,
                            client.bottom - client.top,
                            (frame.right - frame.left) - (client.right - client.left));
        } catch (RuntimeException | UnsatisfiedLinkError unavailable) {
            return "[episort.window] unavailable";
        }
    }

    boolean isMaximized() {
        try {
            return User32Extras.INSTANCE.IsZoomed(window);
        } catch (RuntimeException | UnsatisfiedLinkError unavailable) {
            return false;
        }
    }

    void setMaximized(boolean maximized) {
        try {
            User32.INSTANCE.ShowWindow(window, maximized ? WinUser.SW_MAXIMIZE : WinUser.SW_RESTORE);
        } catch (RuntimeException | UnsatisfiedLinkError unavailable) {
            // The window stays where it is; the button simply did nothing.
        }
    }

    /**
     * Every message the window receives passes here, so this must stay short and
     * must never throw: what it does not answer itself, it hands back untouched.
     */
    private LRESULT dispatch(HWND hwnd, int message, WPARAM wParam, LPARAM lParam) {
        // wParam tells the two forms of the question apart: only the one asking
        // for the client rectangle may be answered with "all of it".
        if (message == WM_NCCALCSIZE && wParam.intValue() != 0) {
            return new LRESULT(0);
        }
        // Leaving no frame to calculate is not the same as leaving no frame to
        // draw. WS_THICKFRAME still has Windows paint the sizing border, over
        // the client area now that the client area is the whole window, and it
        // paints it in the system's colour: a pale line around a near-black
        // shell. Nothing asked for that line — the border Episort does want a
        // say in is DWM's, which WindowsTitleBar sets and which is a separate
        // thing from this one.
        if (message == WM_NCPAINT) {
            return new LRESULT(0);
        }
        // Same line, drawn down a different path: activation repaints the frame
        // on its own, which is why it appeared on the way back from another
        // application rather than at startup. The region says which part to
        // repaint and -1 means none of it; the message still has to reach the
        // original procedure, which is where the window learns it is active.
        if (message == WM_NCACTIVATE) {
            return User32.INSTANCE.CallWindowProc(
                    previousProcedure, hwnd, message, wParam, new LPARAM(NO_FRAME_REPAINT));
        }
        LRESULT answer = User32.INSTANCE.CallWindowProc(previousProcedure, hwnd, message, wParam, lParam);
        if (message == WM_GETMINMAXINFO) {
            try {
                // After the original, which is where the minimum size the stage
                // declares is filled in; only the maximized figures are ours.
                limitMaximizedToWorkArea(hwnd, lParam);
            } catch (RuntimeException ignored) {
                // The window still maximizes, just to the size Windows chose.
            }
        }
        return answer;
    }

    private static void limitMaximizedToWorkArea(HWND hwnd, LPARAM lParam) {
        MONITORINFO screen = new MONITORINFO();
        screen.cbSize = screen.size();
        if (!User32.INSTANCE.GetMonitorInfo(
                User32.INSTANCE.MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST), screen).booleanValue()) {
            return;
        }
        MinMaxInfo limits = new MinMaxInfo(new Pointer(lParam.longValue()));
        // Expressed relative to the monitor, not to the desktop.
        limits.ptMaxPosition.x = screen.rcWork.left - screen.rcMonitor.left;
        limits.ptMaxPosition.y = screen.rcWork.top - screen.rcMonitor.top;
        limits.ptMaxSize.x = screen.rcWork.right - screen.rcWork.left;
        limits.ptMaxSize.y = screen.rcWork.bottom - screen.rcWork.top;
        limits.write();
    }

    /** {@code MINMAXINFO}, which jna-platform does not declare. */
    @FieldOrder({"ptReserved", "ptMaxSize", "ptMaxPosition", "ptMinTrackSize", "ptMaxTrackSize"})
    public static class MinMaxInfo extends Structure {
        public POINT ptReserved;
        public POINT ptMaxSize;
        public POINT ptMaxPosition;
        public POINT ptMinTrackSize;
        public POINT ptMaxTrackSize;

        public MinMaxInfo(Pointer memory) {
            super(memory);
            read();
        }
    }
}

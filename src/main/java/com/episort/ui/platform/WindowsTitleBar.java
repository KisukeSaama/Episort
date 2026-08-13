package com.episort.ui.platform;

import com.episort.ui.Theme;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.ptr.IntByReference;
import java.util.Locale;
import javafx.stage.Stage;

public final class WindowsTitleBar {
    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1 = 19;
    private static final int DWMWA_WINDOW_CORNER_PREFERENCE = 33;
    private static final int DWMWA_BORDER_COLOR = 34;
    private static final int DWMWCP_DONOTROUND = 1;
    private static final int DWMWCP_ROUND = 2;
    /** Hand the edge back to Windows, which is right for a light window. */
    private static final int DWMWA_COLOR_DEFAULT = 0xFFFFFFFF;
    /** Windows draws no outline: every pixel of the window is the shell's. */
    private static final int DWMWA_COLOR_NONE = 0xFFFFFFFE;

    private WindowsTitleBar() {
    }

    public static void applyTheme(Stage stage, Theme theme) {
        if (!supportsDwmAttributes(System.getProperty("os.name"))) {
            return;
        }

        try {
            HWND window = WindowHandles.of(stage).orElse(null);
            if (window == null) {
                return;
            }
            IntByReference enabled = new IntByReference(theme == Theme.DARK ? 1 : 0);
            int result = DwmApi.INSTANCE.DwmSetWindowAttribute(
                    window,
                    DWMWA_USE_IMMERSIVE_DARK_MODE,
                    enabled,
                    Integer.BYTES);
            if (result != 0) {
                result = DwmApi.INSTANCE.DwmSetWindowAttribute(
                        window,
                        DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1,
                        enabled,
                        Integer.BYTES);
            }
            report("dark mode %s -> hr=0x%08X".formatted(theme, result));
            int border = borderColorFor(theme);
            int borderResult = DwmApi.INSTANCE.DwmSetWindowAttribute(
                    window,
                    DWMWA_BORDER_COLOR,
                    new IntByReference(border),
                    Integer.BYTES);
            report("border 0x%08X -> hr=0x%08X".formatted(border, borderResult));
        } catch (RuntimeException | UnsatisfiedLinkError ignored) {
            // Content dark mode remains active if native Windows title bar customization is unavailable.
        }
    }

    /**
     * The one pixel of the window Episort does not draw.
     *
     * <p>Windows outlines every top-level window and picks the colour itself,
     * to separate the window from what is behind it. Over a near-black shell
     * that outline does not read as a separation: it reads as a pale line along
     * the four edges, brighter still while the window holds focus. Tinting it
     * dark only makes a quieter line — an edge is an edge — so the dark theme
     * asks for none, and the shell ends on its own background. A light window
     * sits on a light desktop, where that line is the only thing telling the
     * two apart, and keeps the system's choice.
     */
    static int borderColorFor(Theme theme) {
        return theme == Theme.DARK ? DWMWA_COLOR_NONE : DWMWA_COLOR_DEFAULT;
    }

    public static void applyCornerPreference(Stage stage, boolean rounded) {
        if (!supportsDwmAttributes(System.getProperty("os.name"))) {
            return;
        }

        try {
            HWND window = WindowHandles.of(stage).orElse(null);
            if (window == null) {
                return;
            }
            int corners = DwmApi.INSTANCE.DwmSetWindowAttribute(
                    window,
                    DWMWA_WINDOW_CORNER_PREFERENCE,
                    new IntByReference(rounded ? DWMWCP_ROUND : DWMWCP_DONOTROUND),
                    Integer.BYTES);
            report("corners rounded=%s -> hr=0x%08X".formatted(rounded, corners));
        } catch (RuntimeException | UnsatisfiedLinkError ignored) {
            // The opaque square window remains usable if DWM corner customization is unavailable.
        }
    }

    /** Same switch as the rest of the window plumbing: {@code -Depisort.debug.window=true}. */
    private static void report(String message) {
        if (Boolean.getBoolean("episort.debug.window")) {
            System.out.println("[episort.window] " + message);
        }
    }

    static boolean supportsDwmAttributes(String osName) {
        return osName != null && osName.toLowerCase(Locale.ROOT).startsWith("windows");
    }

    private interface DwmApi extends Library {
        DwmApi INSTANCE = Native.load("dwmapi", DwmApi.class);

        int DwmSetWindowAttribute(HWND hwnd, int attribute, IntByReference value, int size);
    }
}

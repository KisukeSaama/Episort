package com.episort.ui.platform;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import javafx.stage.Stage;

public final class WindowsTitleBar {
    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1 = 19;

    private WindowsTitleBar() {
    }

    public static void applyDarkMode(Stage stage) {
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            return;
        }

        try {
            Pointer window = User32.INSTANCE.FindWindowW(null, new WString(stage.getTitle()));
            if (window == null) {
                return;
            }
            IntByReference enabled = new IntByReference(1);
            int result = DwmApi.INSTANCE.DwmSetWindowAttribute(
                    window,
                    DWMWA_USE_IMMERSIVE_DARK_MODE,
                    enabled,
                    Integer.BYTES);
            if (result != 0) {
                DwmApi.INSTANCE.DwmSetWindowAttribute(
                        window,
                        DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1,
                        enabled,
                        Integer.BYTES);
            }
        } catch (RuntimeException | UnsatisfiedLinkError ignored) {
            // Content dark mode remains active if native Windows title bar customization is unavailable.
        }
    }

    private interface User32 extends Library {
        User32 INSTANCE = Native.load("user32", User32.class);

        Pointer FindWindowW(String className, WString windowName);
    }

    private interface DwmApi extends Library {
        DwmApi INSTANCE = Native.load("dwmapi", DwmApi.class);

        int DwmSetWindowAttribute(Pointer hwnd, int attribute, IntByReference value, int size);
    }
}

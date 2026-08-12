package com.episort.ui.platform;

import com.episort.ui.Theme;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;
import java.util.Locale;

/** Reads the OS application-theme preference without owning any UI state. */
public final class SystemTheme {
    private static final String PERSONALIZE_KEY =
            "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize";

    private SystemTheme() {}

    public static Theme current() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return Theme.DARK;
        }
        try {
            int light = Advapi32Util.registryGetIntValue(
                    WinReg.HKEY_CURRENT_USER, PERSONALIZE_KEY, "AppsUseLightTheme");
            return light == 0 ? Theme.DARK : Theme.LIGHT;
        } catch (RuntimeException | UnsatisfiedLinkError ignored) {
            return Theme.DARK;
        }
    }
}

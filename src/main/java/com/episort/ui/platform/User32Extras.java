package com.episort.ui.platform;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinUser.WindowProc;

/**
 * The {@code user32} entry points jna-platform's own {@code User32} binding
 * either does not declare, or declares in a shape that cannot be called with a
 * Java callback.
 */
interface User32Extras extends Library {
    User32Extras INSTANCE = Native.load("user32", User32Extras.class);

    /** Lets go of the mouse capture so the system can start a loop of its own. */
    boolean ReleaseCapture();

    /** Whether the window is maximized, as Windows itself understands it. */
    boolean IsZoomed(HWND window);

    /**
     * Replaces the window procedure, returning the previous one.
     *
     * <p>Declared here for two reasons. jna-platform's version takes a
     * {@code Pointer}, which cannot receive a Java callback. And the name is
     * spelled out with its {@code W} suffix because that is what 64-bit
     * {@code user32} actually exports — {@code SetWindowLongPtr} alone is a
     * macro, and looking it up fails.
     */
    Pointer SetWindowLongPtrW(HWND window, int index, WindowProc procedure);
}

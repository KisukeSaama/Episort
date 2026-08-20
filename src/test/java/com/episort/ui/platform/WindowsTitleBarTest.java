package com.episort.ui.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.ui.Theme;
import org.junit.jupiter.api.Test;

class WindowsTitleBarTest {
    @Test
    void enablesDwmAttributesOnlyOnWindows() {
        assertTrue(WindowsTitleBar.supportsDwmAttributes("Windows 11"));
        assertTrue(WindowsTitleBar.supportsDwmAttributes("Windows 10"));
        assertFalse(WindowsTitleBar.supportsDwmAttributes("Linux"));
        assertFalse(WindowsTitleBar.supportsDwmAttributes("Mac OS X"));
        assertFalse(WindowsTitleBar.supportsDwmAttributes("Darwin"));
        assertFalse(WindowsTitleBar.supportsDwmAttributes(""));
        assertFalse(WindowsTitleBar.supportsDwmAttributes(null));
    }

    @Test
    void dropsTheWindowEdgeOnDarkAndKeepsTheSystemEdgeOnLight() {
        // The two sentinels differ by one bit, and picking the wrong one asks
        // Windows for its own pale outline instead of for none at all.
        assertEquals(0xFFFFFFFE, WindowsTitleBar.borderColorFor(Theme.DARK));
        assertEquals(0xFFFFFFFF, WindowsTitleBar.borderColorFor(Theme.LIGHT));
        assertNotEquals(
                WindowsTitleBar.borderColorFor(Theme.DARK), WindowsTitleBar.borderColorFor(Theme.LIGHT));
    }
}

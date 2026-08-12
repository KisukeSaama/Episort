package com.episort.ui.platform;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}

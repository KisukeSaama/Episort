package com.episort.ui.platform;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class SystemThemeTest {
    @Test
    void alwaysResolvesToAUsableTheme() {
        assertNotNull(SystemTheme.current());
    }
}

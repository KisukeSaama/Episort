package com.episort.ui;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ThemeStylesheetTest {
    @Test
    void shipsBothCompleteThemeStylesheets() {
        assertNotNull(ThemeStylesheetTest.class.getResource("/styles/app.css"));
        assertNotNull(ThemeStylesheetTest.class.getResource("/styles/light.css"));
    }

    @Test
    void lightThemeOverridesHighSpecificityDarkComponentRules() throws IOException {
        String css;
        try (var stream = ThemeStylesheetTest.class.getResourceAsStream("/styles/light.css")) {
            assertNotNull(stream);
            css = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(css.contains(".theme-light .metric-grid .card .card-value-mono"));
        assertTrue(css.contains(".theme-light .preview-table .table-row-cell:filled:selected .table-cell"));
        assertTrue(css.contains(".theme-light .menu-button.tmdb-episode-picker"));
        assertTrue(css.contains(".theme-light .button.ghost"));
        assertTrue(css.contains(".theme-light .row-checkbox .box"));
        assertTrue(css.contains(".theme-light .combo-box > .list-cell"));
        assertTrue(css.contains(".combo-box-popup .list-view .list-cell:filled:selected"));
        assertTrue(css.contains(".combo-box-popup .list-view .list-cell:filled:hover"));
    }
}

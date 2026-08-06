package com.episort.ui.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class SettingsPaneAttributionTest {
    @Test
    void attributionUsesTheTvdbDirectSubscriptionLink() {
        assertEquals("https://thetvdb.com/subscribe", SettingsPane.TVDB_ATTRIBUTION_URL);
    }

    @Test
    void officialDarkBackgroundLogoIsBundled() {
        assertNotNull(SettingsPane.class.getResource("/assets/thetvdb-logo-dark.png"));
    }
}

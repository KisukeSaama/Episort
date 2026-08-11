package com.episort.ui.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.episort.ui.AppLanguage;
import com.episort.ui.UiText;
import org.junit.jupiter.api.Test;

class SettingsPaneAttributionTest {
    @Test
    void attributionUsesTheOfficialTmdbHomepageLink() {
        assertEquals("https://www.themoviedb.org", SettingsPane.TMDB_ATTRIBUTION_URL);
    }

    @Test
    void officialTmdbLogoIsBundled() {
        assertNotNull(SettingsPane.class.getResource("/assets/tmdb-logo-dark.png"));
    }

    @Test
    void attributionUsesTmdbRequiredNotice() {
        assertEquals(
                "This product uses the TMDB API but is not endorsed or certified by TMDB.",
                UiText.tmdbSettingsAttribution(AppLanguage.ENGLISH));
        assertEquals(
                "Ce produit utilise l’API TMDB, mais n’est ni approuvé ni certifié par TMDB.",
                UiText.tmdbSettingsAttribution(AppLanguage.FRENCH));
    }
}

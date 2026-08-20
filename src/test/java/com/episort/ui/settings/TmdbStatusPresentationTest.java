package com.episort.ui.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.ui.AppLanguage;
import com.episort.workflow.ApplicationError;
import com.episort.workflow.ErrorSeverity;
import com.episort.workflow.TmdbGatewayStatus;
import org.junit.jupiter.api.Test;

class TmdbStatusPresentationTest {
    @Test
    void successfulStartupCheckIsPresentedAsActive() {
        TmdbStatusPresentation status = TmdbStatusPresentation.from(
                TmdbGatewayStatus.passed(),
                AppLanguage.FRENCH);

        assertTrue(status.active());
        assertEquals("Actif : la connexion TMDB a été vérifiée au démarrage.", status.text());
        assertEquals("dot-good", status.dotStyleClass());
    }

    @Test
    void failedStartupCheckIsPresentedAsInactiveWithoutLeakingErrorDetails() {
        TmdbGatewayStatus result = TmdbGatewayStatus.failure(
                ApplicationError.recoverable(
                        "TMDB_CREDENTIALS_UNAVAILABLE",
                        ErrorSeverity.BLOCKING,
                        "apiKey=secret-key",
                        "readAccessToken=secret-token"));

        TmdbStatusPresentation status = TmdbStatusPresentation.from(result, AppLanguage.ENGLISH);

        assertFalse(status.active());
        assertEquals("Inactive: TMDB cannot be reached right now.", status.text());
        assertEquals("dot-error", status.dotStyleClass());
        assertFalse(status.text().contains("secret-key"));
        assertFalse(status.text().contains("secret-token"));
    }
}

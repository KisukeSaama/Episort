package com.episort.ui.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.ui.AppLanguage;
import com.episort.workflow.ApplicationError;
import com.episort.workflow.ErrorSeverity;
import com.episort.workflow.TvdbCredentialConfigurationResult;
import org.junit.jupiter.api.Test;

class TvdbStatusPresentationTest {
    @Test
    void successfulStartupCheckIsPresentedAsActive() {
        TvdbStatusPresentation status = TvdbStatusPresentation.from(
                TvdbCredentialConfigurationResult.passed(),
                AppLanguage.FRENCH);

        assertTrue(status.active());
        assertEquals("Actif — la connexion TVDB a été vérifiée au démarrage.", status.text());
        assertEquals("dot-good", status.dotStyleClass());
    }

    @Test
    void failedStartupCheckIsPresentedAsInactiveWithoutLeakingErrorDetails() {
        TvdbCredentialConfigurationResult result = TvdbCredentialConfigurationResult.failure(
                ApplicationError.recoverable(
                        "TVDB_CREDENTIALS_UNAVAILABLE",
                        ErrorSeverity.BLOCKING,
                        "apiKey=secret-key",
                        "subscriberPin=secret-pin"));

        TvdbStatusPresentation status = TvdbStatusPresentation.from(result, AppLanguage.ENGLISH);

        assertFalse(status.active());
        assertEquals("Inactive — the TVDB key or service is unavailable.", status.text());
        assertEquals("dot-error", status.dotStyleClass());
        assertFalse(status.text().contains("secret-key"));
        assertFalse(status.text().contains("secret-pin"));
    }
}

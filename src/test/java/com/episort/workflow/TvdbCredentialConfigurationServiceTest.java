package com.episort.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.config.InMemoryTvdbCredentialStore;
import com.episort.config.TvdbCredentials;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TvdbCredentialConfigurationServiceTest {
    @Test
    void validCredentialsAreSavedAfterSuccessfulConnectionTest() {
        InMemoryTvdbCredentialStore store = new InMemoryTvdbCredentialStore();
        TvdbCredentialConfigurationService service = new TvdbCredentialConfigurationService(
                store, credentials -> TvdbConnectionTestResult.passed());

        TvdbCredentialConfigurationResult result = service.configureAndTest(new TvdbCredentials("api-key", Optional.of("pin")));

        assertTrue(result.success());
        assertTrue(result.organizationAllowed());
        assertTrue(store.load().isPresent());
    }

    @Test
    void missingCredentialsBlockOrganization() {
        TvdbCredentialConfigurationService service = new TvdbCredentialConfigurationService(
                new InMemoryTvdbCredentialStore(), credentials -> TvdbConnectionTestResult.passed());

        TvdbCredentialConfigurationResult result = service.currentStatus();

        assertFalse(result.success());
        assertFalse(result.organizationAllowed());
        assertEquals("TVDB_CONFIGURATION_REQUIRED", result.error().orElseThrow().code());
    }

    @Test
    void storedCredentialsAreRetestedBeforeOrganizationIsAllowed() {
        InMemoryTvdbCredentialStore store = new InMemoryTvdbCredentialStore();
        store.save(new TvdbCredentials("old-key", Optional.empty()));
        TvdbCredentialConfigurationService service = new TvdbCredentialConfigurationService(
                store,
                credentials -> TvdbConnectionTestResult.failure(ApplicationError.recoverable(
                        "TVDB_AUTH_FAILED",
                        ErrorSeverity.BLOCKING,
                        "TVDB authentication failed.",
                        "Stored credential test failed.")));

        TvdbCredentialConfigurationResult result = service.currentStatus();

        assertFalse(result.success());
        assertFalse(result.organizationAllowed());
        assertEquals("TVDB_AUTH_FAILED", result.error().orElseThrow().code());
    }

    @Test
    void failedConnectionDoesNotPersistCredentialsAndDoesNotLeakSecrets() {
        InMemoryTvdbCredentialStore store = new InMemoryTvdbCredentialStore();
        TvdbCredentialConfigurationService service = new TvdbCredentialConfigurationService(
                store,
                credentials -> TvdbConnectionTestResult.failure(ApplicationError.recoverable(
                        "TVDB_AUTH_FAILED",
                        ErrorSeverity.BLOCKING,
                        "TVDB rejected apiKey=secret-key",
                        "Authorization: Bearer secret-token")));

        TvdbCredentialConfigurationResult result = service.configureAndTest(new TvdbCredentials("secret-key", Optional.empty()));

        assertFalse(result.success());
        assertFalse(result.organizationAllowed());
        assertTrue(store.load().isEmpty());
        assertFalse(result.error().orElseThrow().safeMessage().contains("secret-key"));
    }
}

package com.episort.workflow;

import com.episort.config.SettingsStoreException;
import com.episort.config.TvdbCredentialStore;
import com.episort.config.TvdbCredentials;

public final class TvdbCredentialConfigurationService {
    private final TvdbCredentialStore credentialStore;
    private final TvdbConnectionTester connectionTester;

    public TvdbCredentialConfigurationService(
            TvdbCredentialStore credentialStore,
            TvdbConnectionTester connectionTester) {
        this.credentialStore = credentialStore;
        this.connectionTester = connectionTester;
    }

    public TvdbCredentialConfigurationResult currentStatus() {
        try {
            return credentialStore.load()
                    .map(this::testStoredCredentials)
                    .orElseGet(() -> TvdbCredentialConfigurationResult.failure(requiredError()));
        } catch (SettingsStoreException exception) {
            return TvdbCredentialConfigurationResult.failure(credentialsUnavailableError());
        }
    }

    private TvdbCredentialConfigurationResult testStoredCredentials(TvdbCredentials credentials) {
        TvdbConnectionTestResult testResult = connectionTester.test(credentials);
        return testResult.success()
                ? TvdbCredentialConfigurationResult.passed()
                : TvdbCredentialConfigurationResult.failure(testResult.error().orElseThrow());
    }

    public TvdbCredentialConfigurationResult configureAndTest(TvdbCredentials credentials) {
        TvdbConnectionTestResult testResult = connectionTester.test(credentials);
        if (!testResult.success()) {
            return TvdbCredentialConfigurationResult.failure(testResult.error().orElseThrow());
        }

        try {
            credentialStore.save(credentials);
        } catch (SettingsStoreException exception) {
            return TvdbCredentialConfigurationResult.failure(credentialsUnavailableError());
        }
        return TvdbCredentialConfigurationResult.passed();
    }

    private ApplicationError requiredError() {
        return ApplicationError.recoverable(
                "TVDB_CONFIGURATION_REQUIRED",
                ErrorSeverity.BLOCKING,
                "Enter and test TVDB access before metadata-backed organization.",
                "No TVDB credentials are configured.");
    }

    private ApplicationError credentialsUnavailableError() {
        return ApplicationError.recoverable(
                "TVDB_CREDENTIALS_UNAVAILABLE",
                ErrorSeverity.BLOCKING,
                "TVDB credentials are unavailable. Check your user profile permissions.",
                "TVDB credential storage could not be read or written.");
    }
}

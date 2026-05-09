package com.episort.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.config.AppSettings;
import com.episort.scanner.InventoryScanResult;
import com.episort.scanner.InventorySummary;
import com.episort.workflow.ApplicationError;
import com.episort.workflow.ErrorSeverity;
import com.episort.workflow.StartupWorkflow;
import com.episort.workflow.TvdbCredentialConfigurationResult;
import com.episort.workflow.WorkspaceConfigurationResult;
import com.episort.workflow.InputFolderSelectionResult;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class AppShellViewModelTest {
    @Test
    void startsWithStableApplicationShellCopy() {
        AppShellViewModel viewModel = AppShellViewModel.initial();

        assertEquals("Episort", viewModel.title());
        assertEquals("Choisis un workspace avant de scanner des fichiers.", viewModel.primaryStatus());
        assertTrue(viewModel.description().contains("dossier racine"));
    }

    @Test
    void exposesRecoverableErrorsWithoutDetails() {
        AppShellViewModel viewModel = AppShellViewModel.fromError(ApplicationError.recoverable(
                "WORKSPACE_REQUIRED",
                ErrorSeverity.BLOCKING,
                "Choose a workspace directory before scanning media.",
                "C:\\Users\\Jonathan\\PrivateMedia"));

        assertEquals("WORKSPACE_REQUIRED", viewModel.errorCode().orElseThrow());
        assertEquals("Choisis un workspace avant de scanner des fichiers.", viewModel.primaryStatus());
        assertTrue(viewModel.errorDetails().isEmpty());
    }

    @Test
    void canBeCreatedFromStartupWorkflowError() {
        AppShellViewModel viewModel = AppShellViewModel.fromError(StartupWorkflow.missingWorkspace());

        assertEquals("WORKSPACE_REQUIRED", viewModel.errorCode().orElseThrow());
        assertEquals("Choisis un workspace avant de scanner des fichiers.", viewModel.primaryStatus());
    }

    @Test
    void showsConfiguredWorkspaceAfterRestart() {
        Path workspace = Path.of("C:", "Media").toAbsolutePath().normalize();

        AppShellViewModel viewModel = AppShellViewModel.fromWorkspace(workspace);

        assertEquals("Choisis un workspace avant de scanner des fichiers.", viewModel.primaryStatus());
        assertFalse(viewModel.description().contains(workspace.toString()));
    }

    @Test
    void handlesInconsistentSuccessResultWithoutThrowing() {
        WorkspaceConfigurationResult result = new WorkspaceConfigurationResult(AppSettings.empty(), java.util.Optional.empty());

        assertDoesNotThrow(() -> AppShellViewModel.fromWorkspaceConfiguration(result));
    }

    @Test
    void showsTvdbConnectionTestSuccess() {
        AppShellViewModel viewModel = AppShellViewModel.fromTvdbConfiguration(TvdbCredentialConfigurationResult.passed());

        assertEquals("Connexion TVDB vérifiée", viewModel.primaryStatus());
        assertTrue(viewModel.description().contains("métadonnées"));
    }

    @Test
    void startupStateDoesNotBlockSettingsPageWhenTvdbIsMissing() {
        Path workspace = Path.of("C:", "Media").toAbsolutePath().normalize();
        WorkspaceConfigurationResult workspaceResult = WorkspaceConfigurationResult.success(new AppSettings(workspace));
        TvdbCredentialConfigurationResult tvdbResult = TvdbCredentialConfigurationResult.failure(ApplicationError.recoverable(
                "TVDB_CONFIGURATION_REQUIRED",
                ErrorSeverity.BLOCKING,
                "Enter and test TVDB access before metadata-backed organization.",
                "No TVDB credentials are configured."));

        AppShellViewModel viewModel = AppShellViewModel.fromStartupPrerequisites(workspaceResult, tvdbResult);

        assertTrue(viewModel.errorCode().isEmpty());
        assertEquals("Choisis un workspace avant de scanner des fichiers.", viewModel.primaryStatus());
    }

    @Test
    void recoverableErrorDoesNotExposeCodeOrDetailsInUserCopy() {
        AppShellViewModel viewModel = AppShellViewModel.fromError(ApplicationError.recoverable(
                "TVDB_CREDENTIALS_UNAVAILABLE",
                ErrorSeverity.BLOCKING,
                "TVDB credentials are unavailable.",
                "apiKey=secret-key could not be read."));

        assertFalse(viewModel.description().contains("TVDB_CREDENTIALS_UNAVAILABLE"));
        assertFalse(viewModel.description().contains("secret-key"));
        assertEquals("", viewModel.description());
    }

    @Test
    void defaultsToDarkTheme() {
        assertEquals(Theme.DARK, AppShellViewModel.initial().theme());
        assertEquals(AppLanguage.FRENCH, AppShellViewModel.initial().language());
    }

    @Test
    void withThemeReturnsNewViewModelInDarkTheme() {
        AppShellViewModel light = AppShellViewModel.initial().withTheme(Theme.LIGHT);

        AppShellViewModel dark = light.withTheme(Theme.DARK);

        assertEquals(Theme.DARK, dark.theme());
        assertEquals(Theme.LIGHT, light.theme());
        assertEquals(light.title(), dark.title());
        assertEquals(light.primaryStatus(), dark.primaryStatus());
    }

    @Test
    void preservingThemeKeepsPreviousThemeOnNewState() {
        AppShellViewModel previous = AppShellViewModel.initial().withTheme(Theme.DARK);
        AppShellViewModel newState = AppShellViewModel.fromError(ApplicationError.recoverable(
                "WORKSPACE_REQUIRED", ErrorSeverity.BLOCKING, "msg", "details"));

        AppShellViewModel merged = AppShellViewModel.preservingTheme(previous, newState);

        assertEquals(Theme.DARK, merged.theme());
        assertEquals(newState.primaryStatus(), merged.primaryStatus());
    }

    @Test
    void withThemeRejectsNull() {
        org.junit.jupiter.api.Assertions.assertThrows(
                NullPointerException.class,
                () -> AppShellViewModel.initial().withTheme(null));
    }

    @Test
    void fromErrorWithNullDetailsDoesNotLeakNullSentinel() {
        AppShellViewModel viewModel = AppShellViewModel.fromError(new ApplicationError(
                "PLAN_BLOCKED",
                ErrorSeverity.BLOCKING,
                "Plan blocked.",
                true,
                null));

        assertFalse(viewModel.description().contains("null"));
    }

    @Test
    void redactsSecretsLeakedThroughFilesystemErrorDetails() {
        AppShellViewModel viewModel = AppShellViewModel.fromError(ApplicationError.recoverable(
                "INPUT_FOLDER_INVALID",
                ErrorSeverity.BLOCKING,
                "Choose an existing readable input folder.",
                "C:\\Users\\Jonathan\\Media token=eyJhbGciOi.bearer-leak ; apiKey=secret-from-path"));

        assertFalse(viewModel.description().contains("secret-from-path"));
        assertFalse(viewModel.description().contains("eyJhbGciOi.bearer-leak"));
        assertFalse(viewModel.description().contains("[REDACTED]"));
    }

    @Test
    void showsAcceptedInputFolder() {
        Path input = Path.of("C:", "Media", "Shows").toAbsolutePath().normalize();

        AppShellViewModel viewModel = AppShellViewModel.fromInputFolderSelection(InputFolderSelectionResult.success(input));

        assertEquals("Dossier à scanner accepté", viewModel.primaryStatus());
        assertTrue(viewModel.description().contains(input.toString()));
    }

    @Test
    void canSwitchInitialCopyToEnglish() {
        AppShellViewModel viewModel = AppShellViewModel.initial().withLanguage(AppLanguage.ENGLISH);

        assertEquals("Choose a workspace before scanning files.", viewModel.primaryStatus());
        assertEquals("First define the authorized root folder.", viewModel.description());
        assertEquals(AppLanguage.ENGLISH, viewModel.language());
    }

    @Test
    void canSwitchInitialCopyBackToFrench() {
        AppShellViewModel viewModel = AppShellViewModel.initial()
                .withLanguage(AppLanguage.ENGLISH)
                .withLanguage(AppLanguage.FRENCH);

        assertEquals("Choisis un workspace avant de scanner des fichiers.", viewModel.primaryStatus());
        assertEquals("Définis d'abord le dossier racine autorisé.", viewModel.description());
        assertEquals(AppLanguage.FRENCH, viewModel.language());
    }

    @Test
    void canSwitchErrorCopyToEnglish() {
        AppShellViewModel viewModel = AppShellViewModel.fromError(ApplicationError.recoverable(
                        "INPUT_OUTSIDE_WORKSPACE",
                        ErrorSeverity.BLOCKING,
                        "x",
                        "private path"))
                .withLanguage(AppLanguage.ENGLISH);

        assertEquals("The selected folder is outside the workspace.", viewModel.primaryStatus());
        assertEquals("Select a folder contained by the configured workspace.", viewModel.description());
    }

    @Test
    void exposesInventoryScanResultForManualReviewSurface() {
        Path input = Path.of("C:", "Media", "Inbox").toAbsolutePath().normalize();
        InventoryScanResult scanResult = new InventoryScanResult(
                List.of(),
                List.of(),
                new InventorySummary(3, 2, 1, 1, 2, 1, 0, false, false));

        AppShellViewModel viewModel = AppShellViewModel.fromInventoryScan(input, scanResult);

        assertEquals("Scan terminé", viewModel.primaryStatus());
        assertTrue(viewModel.description().contains(input.toString()));
        assertTrue(viewModel.inventoryScanResult().isPresent());
        assertEquals(3, viewModel.inventoryScanResult().orElseThrow().summary().supportedVideoCount());
    }
}

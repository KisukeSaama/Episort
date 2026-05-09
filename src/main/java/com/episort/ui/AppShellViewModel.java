package com.episort.ui;

import com.episort.workflow.ApplicationError;
import com.episort.workflow.TvdbCredentialConfigurationResult;
import com.episort.workflow.WorkspaceConfigurationResult;
import com.episort.workflow.InputFolderSelectionResult;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public record AppShellViewModel(
        String title,
        String primaryStatus,
        String description,
        Optional<String> errorCode,
        Optional<String> errorDetails,
        Theme theme,
        AppLanguage language) {
    public AppShellViewModel {
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(language, "language");
    }

    private static AppShellViewModel of(
            String title,
            String primaryStatus,
            String description,
            Optional<String> errorCode,
            Optional<String> errorDetails) {
        return new AppShellViewModel(title, primaryStatus, description, errorCode, errorDetails, Theme.DARK, AppLanguage.FRENCH);
    }

    public AppShellViewModel withTheme(Theme newTheme) {
        Objects.requireNonNull(newTheme, "newTheme");
        return new AppShellViewModel(title, primaryStatus, description, errorCode, errorDetails, newTheme, language);
    }

    public AppShellViewModel withLanguage(AppLanguage newLanguage) {
        Objects.requireNonNull(newLanguage, "newLanguage");
        if (errorCode.isPresent()) {
            return fromErrorCode(errorCode.orElseThrow(), newLanguage).withTheme(theme);
        }
        return fromKnownState(primaryStatus, description, newLanguage).withTheme(theme);
    }

    public static AppShellViewModel preservingTheme(AppShellViewModel previous, AppShellViewModel newState) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(newState, "newState");
        return newState.withTheme(previous.theme()).withLanguage(previous.language());
    }

    public static AppShellViewModel initial() {
        return of(
                "Episort",
                UiText.settingsRequiredStatus(AppLanguage.FRENCH),
                UiText.settingsRequiredDescription(AppLanguage.FRENCH),
                Optional.empty(),
                Optional.empty());
    }

    public static AppShellViewModel fromError(ApplicationError error) {
        return fromErrorCode(error.code(), AppLanguage.FRENCH);
    }

    public static AppShellViewModel fromWorkspace(Path workspaceDirectory) {
        return of(
                "Episort",
                UiText.settingsRequiredStatus(AppLanguage.FRENCH),
                UiText.settingsRequiredDescription(AppLanguage.FRENCH),
                Optional.empty(),
                Optional.empty());
    }

    public static AppShellViewModel fromWorkspaceConfiguration(WorkspaceConfigurationResult result) {
        if (result.success() && result.settings().workspaceDirectory().isEmpty()) {
            return AppShellViewModel.fromError(ApplicationError.recoverable(
                    "WORKSPACE_REQUIRED",
                    com.episort.workflow.ErrorSeverity.BLOCKING,
                    UiText.settingsRequiredStatus(AppLanguage.FRENCH),
                    "Workspace configuration result was successful without a workspace directory."));
        }

        return result.settings()
                .workspaceDirectory()
                .filter(ignored -> result.success())
                .map(AppShellViewModel::fromWorkspace)
                .orElseGet(() -> AppShellViewModel.fromError(result.error().orElseThrow()));
    }

    public static AppShellViewModel fromTvdbConfiguration(TvdbCredentialConfigurationResult result) {
        if (result.success()) {
            return of(
                "Episort",
                "Connexion TVDB vérifiée",
                "L'accès TVDB est prêt pour l'organisation basée sur les métadonnées.",
                Optional.empty(),
                Optional.empty());
        }

        return AppShellViewModel.fromError(result.error().orElseThrow());
    }

    public static AppShellViewModel fromStartupPrerequisites(
            WorkspaceConfigurationResult workspaceResult,
            TvdbCredentialConfigurationResult tvdbResult) {
        if (!workspaceResult.success()) {
            return fromWorkspaceConfiguration(workspaceResult);
        }
        return fromWorkspace(workspaceResult.settings().workspaceDirectory().orElseThrow());
    }

    public static AppShellViewModel fromInputFolderSelection(InputFolderSelectionResult result) {
        if (result.success()) {
            return of(
                "Episort",
                "Dossier à scanner accepté",
                "Dossier à scanner : " + result.inputFolder().orElseThrow(),
                Optional.empty(),
                Optional.empty());
        }

        return fromError(result.error().orElseThrow());
    }

    private static AppShellViewModel fromErrorCode(String code, AppLanguage language) {
        return new AppShellViewModel(
                "Episort",
                UiText.errorStatus(code, language),
                UiText.errorDescription(code, language),
                Optional.of(code),
                Optional.empty(),
                Theme.DARK,
                language);
    }

    private static AppShellViewModel fromKnownState(String status, String description, AppLanguage language) {
        if (language == AppLanguage.ENGLISH) {
            if (status.equals("Configuration requise")
                    || status.equals("Settings required")
                    || status.equals(UiText.settingsRequiredStatus(AppLanguage.FRENCH))
                    || status.equals(UiText.settingsRequiredStatus(AppLanguage.ENGLISH))) {
                return new AppShellViewModel(
                        "Episort",
                        UiText.settingsRequiredStatus(language),
                        UiText.settingsRequiredDescription(language),
                        Optional.empty(),
                        Optional.empty(),
                        Theme.DARK,
                        language);
            }
            if (status.equals("Workspace configuré")
                    || status.equals(UiText.workspaceConfiguredStatus(AppLanguage.FRENCH))
                    || status.equals(UiText.workspaceConfiguredStatus(AppLanguage.ENGLISH))
                    || status.equals("Workspace configured")) {
                return new AppShellViewModel(
                        "Episort",
                        UiText.workspaceConfiguredStatus(language),
                        translateWorkspaceDescription(description, language),
                        Optional.empty(),
                        Optional.empty(),
                        Theme.DARK,
                        language);
            }
        } else if (status.equals("Settings required")
                || status.equals("Configuration requise")
                || status.equals(UiText.settingsRequiredStatus(AppLanguage.FRENCH))
                || status.equals(UiText.settingsRequiredStatus(AppLanguage.ENGLISH))
                || status.equals("Choose a workspace before scanning media.")) {
            return initial();
        } else if (status.equals("Workspace configured")
                || status.equals("Workspace configuré")
                || status.equals(UiText.workspaceConfiguredStatus(AppLanguage.FRENCH))
                || status.equals(UiText.workspaceConfiguredStatus(AppLanguage.ENGLISH))) {
            return new AppShellViewModel(
                    "Episort",
                    UiText.workspaceConfiguredStatus(language),
                    translateWorkspaceDescription(description, language),
                    Optional.empty(),
                    Optional.empty(),
                    Theme.DARK,
                    language);
        }
        return new AppShellViewModel("Episort", status, description, Optional.empty(), Optional.empty(), Theme.DARK, language);
    }

    private static String translateWorkspaceDescription(String description, AppLanguage language) {
        String workspace = description
                .replace("Workspace sélectionné :", "")
                .replace("Selected workspace:", "")
                .replace("Workspace actuel :", "")
                .replace("Current workspace:", "")
                .trim();
        if (workspace.isBlank()) {
            return language == AppLanguage.ENGLISH ? "No workspace selected" : "Aucun workspace sélectionné";
        }
        return (language == AppLanguage.ENGLISH ? "Selected workspace: " : "Workspace sélectionné : ") + workspace;
    }
}

package com.episort.ui;

import java.nio.file.Path;
import java.util.Optional;

public final class UiText {
    private UiText() {
    }

    public static String languageLabel(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Language" : "Langue";
    }

    public static String prerequisitesHeading(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "// PREREQUISITES" : "// PR\u00c9REQUIS";
    }

    public static String preferencesHeading(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "// PREFERENCES" : "// PR\u00c9F\u00c9RENCES";
    }

    public static String settingsButton(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Settings" : "Param\u00e8tres";
    }

    public static String closeSettingsButton(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Close" : "Fermer";
    }

    public static String loadFolderButton(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Load folder" : "Charger un dossier";
    }

    public static String settingsRequiredStatus(AppLanguage language) {
        return language == AppLanguage.ENGLISH
                ? "Choose a workspace before scanning files."
                : "Choisis un workspace avant de scanner des fichiers.";
    }

    public static String settingsRequiredDescription(AppLanguage language) {
        return language == AppLanguage.ENGLISH
                ? "First define the authorized root folder."
                : "D\u00e9finis d'abord le dossier racine autoris\u00e9.";
    }

    public static String workspaceConfiguredStatus(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Workspace configured" : "Workspace configur\u00e9";
    }

    public static String workspaceConfiguredDescription(AppLanguage language, Path workspace) {
        return (language == AppLanguage.ENGLISH ? "Selected workspace: " : "Workspace s\u00e9lectionn\u00e9 : ")
                + workspace.toAbsolutePath().normalize();
    }

    public static String workspaceSectionTitle(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Workspace" : "Espace de travail";
    }

    public static String workspaceSectionDescription(AppLanguage language) {
        return language == AppLanguage.ENGLISH
                ? "Please select the folder that defines Episort's allowed working area."
                : "Veuillez s\u00e9lectionner le dossier qui d\u00e9finit l'espace de travail autoris\u00e9 d'Episort.";
    }

    public static String chooseWorkspaceButton(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Choose workspace" : "Choisir le workspace";
    }

    public static String continueButton(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Continue" : "Continuer";
    }

    public static String workspaceValue(AppLanguage language, Optional<Path> workspace) {
        return workspace
                .map(path -> (language == AppLanguage.ENGLISH ? "Selected workspace: " : "Workspace s\u00e9lectionn\u00e9 : ")
                        + path)
                .orElseGet(() -> language == AppLanguage.ENGLISH
                        ? "No workspace selected"
                        : "Aucun workspace s\u00e9lectionn\u00e9");
    }

    public static String errorStatus(String code, AppLanguage language) {
        if (language == AppLanguage.ENGLISH) {
            return switch (code) {
                case "WORKSPACE_REQUIRED" -> "Choose a workspace before scanning files.";
                case "INPUT_OUTSIDE_WORKSPACE" -> "The selected folder is outside the workspace.";
                case "INPUT_FOLDER_INVALID" -> "Choose an existing readable folder.";
                case "INPUT_FOLDER_REQUIRED" -> "Choose a folder inside the workspace.";
                case "WORKSPACE_UNAVAILABLE" -> "Re-check the configured workspace; it cannot be accessed.";
                default -> "Episort cannot continue yet.";
            };
        }
        return switch (code) {
            case "WORKSPACE_REQUIRED" -> "Choisis un workspace avant de scanner des fichiers.";
            case "INPUT_OUTSIDE_WORKSPACE" -> "Le dossier choisi est en dehors du workspace.";
            case "INPUT_FOLDER_INVALID" -> "Choisis un dossier existant et lisible.";
            case "INPUT_FOLDER_REQUIRED" -> "Choisis un dossier situ\u00e9 dans le workspace.";
            case "WORKSPACE_UNAVAILABLE" -> "V\u00e9rifie le workspace configur\u00e9 ; il est inaccessible.";
            default -> "Episort ne peut pas encore continuer.";
        };
    }

    public static String errorDescription(String code, AppLanguage language) {
        if (language == AppLanguage.ENGLISH) {
            return switch (code) {
                case "INPUT_OUTSIDE_WORKSPACE" -> "Select a folder contained by the configured workspace.";
                case "WORKSPACE_REQUIRED" -> "First define the authorized root folder.";
                default -> "";
            };
        }
        return switch (code) {
            case "INPUT_OUTSIDE_WORKSPACE" -> "S\u00e9lectionne un dossier contenu dans le workspace configur\u00e9.";
            case "WORKSPACE_REQUIRED" -> "D\u00e9finis d'abord le dossier racine autoris\u00e9.";
            default -> "";
        };
    }
}

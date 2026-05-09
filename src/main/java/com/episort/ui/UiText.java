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
        return language == AppLanguage.ENGLISH ? "// PREREQUISITES" : "// PRÉREQUIS";
    }

    public static String preferencesHeading(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "// PREFERENCES" : "// PRÉFÉRENCES";
    }

    public static String settingsButton(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Settings" : "Paramètres";
    }

    public static String closeSettingsButton(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Close" : "Fermer";
    }

    public static String cancelButton(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Cancel" : "Annuler";
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
                : "Définis d'abord le dossier racine autorisé.";
    }

    public static String workspaceConfiguredStatus(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Workspace configured" : "Workspace configuré";
    }

    public static String workspaceConfiguredDescription(AppLanguage language, Path workspace) {
        return (language == AppLanguage.ENGLISH ? "Selected workspace: " : "Workspace sélectionné : ")
                + workspace.toAbsolutePath().normalize();
    }

    public static String workspaceSectionTitle(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Workspace" : "Espace de travail";
    }

    public static String workspaceSectionDescription(AppLanguage language) {
        return language == AppLanguage.ENGLISH
                ? "Please select the folder that defines Episort's allowed working area."
                : "Veuillez sélectionner le dossier qui définit l'espace de travail autorisé d'Episort.";
    }

    public static String chooseWorkspaceButton(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Choose workspace" : "Choisir le workspace";
    }

    public static String continueButton(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Continue" : "Continuer";
    }

    public static String workspaceValue(AppLanguage language, Optional<Path> workspace) {
        return workspace
                .map(path -> (language == AppLanguage.ENGLISH ? "Selected workspace: " : "Workspace sélectionné : ")
                        + path)
                .orElseGet(() -> language == AppLanguage.ENGLISH
                        ? "No workspace selected"
                        : "Aucun workspace sélectionné");
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
            case "INPUT_FOLDER_REQUIRED" -> "Choisis un dossier situé dans le workspace.";
            case "WORKSPACE_UNAVAILABLE" -> "Vérifie le workspace configuré ; il est inaccessible.";
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
            case "INPUT_OUTSIDE_WORKSPACE" -> "Sélectionne un dossier contenu dans le workspace configuré.";
            case "WORKSPACE_REQUIRED" -> "Définis d'abord le dossier racine autorisé.";
            default -> "";
        };
    }

    /* ---- Navigation -------------------------------------------------- */

    public static String navScan(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Scan" : "Scan";
    }

    public static String navHistory(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "History" : "Historique";
    }

    public static String navSettings(AppLanguage language) {
        return settingsButton(language);
    }

    /* ---- Top bar ----------------------------------------------------- */

    public static String searchPlaceholder(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Search…" : "Rechercher…";
    }

    public static String workspaceChipEmpty(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "No workspace" : "Aucun workspace";
    }

    public static String primaryActionLoad(AppLanguage language) {
        return loadFolderButton(language);
    }

    public static String primaryActionValidate(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Validate preview" : "Valider l'aperçu";
    }

    public static String statusPillReady(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "READY" : "PRÊT";
    }

    /* ---- Scan screen ------------------------------------------------- */

    public static String scanHeading(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "// SCAN" : "// SCAN";
    }

    public static String scanSafetyBanner(AppLanguage language) {
        return language == AppLanguage.ENGLISH
                ? "Preview only — no file will be modified before validation."
                : "Aperçu seul — aucun fichier ne sera modifié avant validation.";
    }

    public static String[] scanWorkflowSteps(AppLanguage language) {
        return language == AppLanguage.ENGLISH
                ? new String[] {"Workspace & Scan", "File scan", "TVDB matches", "Plan review", "Apply"}
                : new String[] {"Workspace & Scan", "Scan des fichiers", "Correspondances TVDB", "Revue du plan", "Appliquer"};
    }

    public static String scanMetricTotal(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Total" : "Total";
    }

    public static String scanMetricSeries(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Series" : "Séries";
    }

    public static String scanMetricMovies(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Movies" : "Films";
    }

    public static String scanMetricUnknown(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Unknown" : "Inconnus";
    }

    public static String scanMetricIgnored(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Ignored" : "Ignorés";
    }

    public static String scanMetricToProcess(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "To process" : "À traiter";
    }

    public static String scanMetricConflicts(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Conflicts" : "Conflits";
    }

    public static String scanMetricWarnings(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Warnings" : "Alertes";
    }

    public static String scanColumnSelection(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Sel." : "Sel.";
    }

    public static String scanColumnOriginal(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Current name" : "Nom actuel";
    }

    public static String scanColumnProposed(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Proposed name" : "Nom proposé";
    }

    public static String scanColumnExtension(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Ext." : "Ext.";
    }

    public static String scanColumnType(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Type" : "Type";
    }

    public static String scanColumnTvdb(AppLanguage language) {
        return "TVDB";
    }

    public static String scanColumnDestination(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Destination" : "Destination";
    }

    public static String scanColumnConfidence(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Conf." : "Conf.";
    }

    public static String scanColumnStatus(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Status" : "Statut";
    }

    public static String scanMediaTypeSeries(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Series" : "Série";
    }

    public static String scanMediaTypeMovie(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Movie" : "Film";
    }

    public static String scanMediaTypeUnknown(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Unknown" : "Inconnu";
    }

    public static String scanMediaTypeIgnored(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Ignored" : "Ignoré";
    }

    public static String scanRowStatusPreview(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "PREVIEW" : "APERÇU";
    }

    public static String scanRowStatusReady(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "READY" : "PRÊT";
    }

    public static String scanRowStatusWarning(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "WARNING" : "ALERTE";
    }

    public static String scanRowStatusConflict(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "CONFLICT" : "CONFLIT";
    }

    public static String scanRowStatusIgnored(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "IGNORED" : "IGNORÉ";
    }

    public static String scanTablePlaceholder(AppLanguage language) {
        return language == AppLanguage.ENGLISH
                ? "Load a folder to populate the preview."
                : "Charge un dossier pour remplir l'aperçu.";
    }

    public static String scanContextIgnore(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Mark as ignored" : "Marquer comme ignoré";
    }

    public static String scanContextResetMatch(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Reset TVDB match" : "Réinitialiser la correspondance";
    }

    public static String scanContextCopyPath(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Copy path" : "Copier le chemin";
    }

    public static String scanContextOpenFolder(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Open parent folder" : "Ouvrir le dossier parent";
    }

    /* ---- Detail panel ----------------------------------------------- */

    public static String detailNoSelection(AppLanguage language) {
        return language == AppLanguage.ENGLISH
                ? "Select a row to review and correct its TVDB match."
                : "Sélectionne une ligne pour vérifier et corriger sa correspondance TVDB.";
    }

    public static String detailSectionSource(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "// SOURCE" : "// SOURCE";
    }

    public static String detailSectionDetection(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "// DETECTION" : "// DÉTECTION";
    }

    public static String detailSectionTvdb(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "// TVDB MATCH" : "// CORRESPONDANCE TVDB";
    }

    public static String detailSectionDestination(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "// DESTINATION" : "// DESTINATION";
    }

    public static String detailFieldFilename(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Filename" : "Fichier";
    }

    public static String detailFieldFolder(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Parent folder" : "Dossier parent";
    }

    public static String detailFieldExtension(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Extension" : "Extension";
    }

    public static String detailFieldMediaType(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Detected as" : "Détecté comme";
    }

    public static String detailFieldStatus(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Status" : "Statut";
    }

    public static String detailFieldConfidence(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Confidence" : "Confiance";
    }

    public static String detailFieldDestination(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Target path" : "Chemin cible";
    }

    public static String detailTvdbSearchPlaceholder(AppLanguage language) {
        return language == AppLanguage.ENGLISH
                ? "Search TVDB by title…"
                : "Rechercher TVDB par titre…";
    }

    public static String detailTvdbStub(AppLanguage language) {
        return language == AppLanguage.ENGLISH
                ? "TVDB matching ships in a later story."
                : "La correspondance TVDB sera disponible dans un prochain incrément.";
    }

    public static String detailApplyButton(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Apply match" : "Appliquer";
    }

    public static String detailResetButton(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Reset" : "Réinitialiser";
    }

    /* ---- History screen --------------------------------------------- */

    public static String historyHeading(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "// HISTORY" : "// HISTORIQUE";
    }

    public static String historyBanner(AppLanguage language) {
        return language == AppLanguage.ENGLISH
                ? "Audit log — every completed run is recorded here."
                : "Journal d'audit — chaque exécution terminée est enregistrée ici.";
    }

    public static String historyMetricTotal(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Total" : "Total";
    }

    public static String historyMetricSuccess(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Successful" : "Réussites";
    }

    public static String historyMetricWarning(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Warnings" : "Alertes";
    }

    public static String historyMetricConflict(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Conflicts" : "Conflits";
    }

    public static String historyMetricFailed(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Failed" : "Échecs";
    }

    public static String historyFilterAll(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "All" : "Tout";
    }

    public static String historyFilterSuccessful(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Successful" : "Réussites";
    }

    public static String historyFilterWarnings(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Warnings" : "Alertes";
    }

    public static String historyFilterConflicts(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Conflicts" : "Conflits";
    }

    public static String historyFilterFailed(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Failed" : "Échecs";
    }

    public static String historyFilterIgnored(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Ignored" : "Ignorés";
    }

    public static String historyColumnTimestamp(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "When" : "Date";
    }

    public static String historyColumnEventType(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Event" : "Événement";
    }

    public static String historyColumnSource(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Source" : "Source";
    }

    public static String historyColumnSummary(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Summary" : "Résumé";
    }

    public static String historyColumnStatus(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Status" : "Statut";
    }

    public static String historyEmptyPlaceholder(AppLanguage language) {
        return language == AppLanguage.ENGLISH
                ? "No run recorded yet."
                : "Aucune exécution enregistrée pour l'instant.";
    }

    public static String historyEventScanCompleted(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Scan completed" : "Scan terminé";
    }

    public static String historyEventScanFailed(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Scan failed" : "Scan échoué";
    }

    public static String historyDetailNoSelection(AppLanguage language) {
        return language == AppLanguage.ENGLISH
                ? "Select a run to inspect its details."
                : "Sélectionne une exécution pour voir son détail.";
    }

    public static String historyDetailSectionSummary(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "// SUMMARY" : "// RÉSUMÉ";
    }

    public static String historyDetailSectionMetrics(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "// METRICS" : "// MÉTRIQUES";
    }

    /* ---- Settings page ---------------------------------------------- */

    public static String settingsHeading(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "// SETTINGS" : "// PARAMÈTRES";
    }

    public static String settingsPageTitle(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Settings" : "Paramètres";
    }

    public static String settingsPageSubtitle(AppLanguage language) {
        return language == AppLanguage.ENGLISH
                ? "Configure the workspace and the interface language."
                : "Configure l'espace de travail et la langue de l'interface.";
    }

    public static String preferencesSectionTitle(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Preferences" : "Préférences";
    }

    public static String preferencesSectionDescription(AppLanguage language) {
        return language == AppLanguage.ENGLISH
                ? "Choose the interface language."
                : "Choisis la langue de l'interface.";
    }

    public static String safetySectionTitle(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Safety & behavior" : "Sûreté & comportement";
    }

    public static String safetySectionDescription(AppLanguage language) {
        return language == AppLanguage.ENGLISH
                ? "Episort runs in preview-only mode. Validation and movement controls will appear here in a later step."
                : "Episort fonctionne en mode aperçu uniquement. Les contrôles de validation et de déplacement apparaîtront ici plus tard.";
    }

    public static String safetySectionPlaceholder(AppLanguage language) {
        return language == AppLanguage.ENGLISH
                ? "No options yet — preview-only is enforced for every run."
                : "Aucune option pour l'instant — l'aperçu seul est imposé à chaque exécution.";
    }

    /* ---- Empty states (table / detail) ------------------------------ */

    public static String scanEmptyTitle(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "No folder loaded" : "Aucun dossier chargé";
    }

    public static String scanEmptyHint(AppLanguage language) {
        return language == AppLanguage.ENGLISH
                ? "Load a folder to generate a rename preview."
                : "Charge un dossier pour générer un aperçu de renommage.";
    }

    public static String historyEmptyTitle(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "No completed run yet" : "Aucune exécution enregistrée";
    }

    public static String historyEmptyHint(AppLanguage language) {
        return language == AppLanguage.ENGLISH
                ? "Run a scan from the Scan screen — completed runs will appear here."
                : "Lance un scan depuis l'écran Scan — les exécutions terminées apparaîtront ici.";
    }

    public static String detailEmptyTitle(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "No selection" : "Aucune sélection";
    }

    public static String detailEmptyHint(AppLanguage language) {
        return language == AppLanguage.ENGLISH
                ? "Pick a row to review its match and destination."
                : "Sélectionne une ligne pour vérifier sa correspondance et sa destination.";
    }

    public static String historyDetailEmptyTitle(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "No selection" : "Aucune sélection";
    }

    public static String historyDetailEmptyHint(AppLanguage language) {
        return language == AppLanguage.ENGLISH
                ? "Pick a run to inspect its summary and metrics."
                : "Sélectionne une exécution pour voir son résumé et ses métriques.";
    }
    public static String historyClearButton(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Clear history" : "Vider l'historique";
    }

    public static String historyClearConfirmation(AppLanguage language) {
        return language == AppLanguage.ENGLISH
                ? "Do you really want to clear the history?"
                : "Voulez-vous vraiment vider l'historique ?";
    }

    public static String historyClearConfirmButton(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Clear" : "Vider";
    }

    public static String topActionChangeFolder(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Change folder" : "Changer de dossier";
    }

    public static String topActionResetFolder(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Reset folder" : "Réinitialiser le dossier";
    }

    public static String topActionResetSearch(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Reset search" : "Réinitialiser la recherche";
    }

    public static String topActionRescan(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Rescan" : "Réanalyser";
    }

    public static String scanColumnOrder(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Order" : "Ordre";
    }

    public static String detailSectionNotes(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "// ALERTS & NOTES" : "// ALERTES & NOTES";
    }

    public static String detailFieldSeasonEpisode(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Season / episode" : "Saison / épisode";
    }

    public static String detailFieldProposed(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Proposed filename" : "Nom proposé";
    }

    public static String detailFieldConflict(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Conflict" : "Conflit";
    }

    public static String detailFieldAlert(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Alert" : "Alerte";
    }

    public static String detailFieldNote(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Note" : "Note";
    }

    public static String scanBatchTvdbHeading(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "// BATCH TVDB MATCHES" : "// CORRESPONDANCES TVDB DU LOT";
    }

    public static String scanBatchTvdbCandidatePlaceholder(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "TVDB candidate unavailable" : "Candidat TVDB indisponible";
    }

    public static String scanBatchTvdbOrderPlaceholder(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Choose order" : "Choisir l'ordre";
    }

    public static String scanBatchTvdbOrderAired(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Aired order" : "Ordre de diffusion";
    }

    public static String scanBatchTvdbOrderDvd(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "DVD order" : "Ordre DVD";
    }

    public static String scanBatchTvdbOrderAbsolute(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Absolute order" : "Ordre absolu";
    }

    public static String scanBatchTvdbApply(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Apply to batch" : "Appliquer au lot";
    }

    public static String scanBatchTvdbReset(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "Reset match" : "Réinitialiser la correspondance";
    }

    public static String scanBatchTvdbStatusFinal(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "final" : "final";
    }

    public static String scanBatchTvdbStatusUnresolved(AppLanguage language) {
        return language == AppLanguage.ENGLISH ? "unresolved" : "non résolu";
    }
}

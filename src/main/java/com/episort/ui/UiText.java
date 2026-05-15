package com.episort.ui;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

public final class UiText {
    private static final String BUNDLE_BASE = "i18n.messages";
    private static final ResourceBundle.Control NO_FALLBACK_CONTROL =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    private UiText() {
    }

    private static String t(AppLanguage language, String key) {
        return ResourceBundle
                .getBundle(BUNDLE_BASE, Locale.of(language.languageTag()), NO_FALLBACK_CONTROL)
                .getString(key);
    }

    public static String languageLabel(AppLanguage language) { return t(language, "language.label"); }
    public static String prerequisitesHeading(AppLanguage language) { return t(language, "prerequisites.heading"); }
    public static String preferencesHeading(AppLanguage language) { return t(language, "preferences.heading"); }
    public static String settingsButton(AppLanguage language) { return t(language, "settings.button"); }
    public static String closeSettingsButton(AppLanguage language) { return t(language, "close.settings.button"); }
    public static String cancelButton(AppLanguage language) { return t(language, "cancel.button"); }
    public static String loadFolderButton(AppLanguage language) { return t(language, "load.folder.button"); }
    public static String settingsRequiredStatus(AppLanguage language) { return t(language, "settings.required.status"); }
    public static String settingsRequiredDescription(AppLanguage language) { return t(language, "settings.required.description"); }
    public static String workspaceConfiguredStatus(AppLanguage language) { return t(language, "workspace.configured.status"); }

    public static String workspaceConfiguredDescription(AppLanguage language, Path workspace) {
        return t(language, "workspace.configured.prefix") + " " + workspace.toAbsolutePath().normalize();
    }

    public static String workspaceSectionTitle(AppLanguage language) { return t(language, "workspace.section.title"); }
    public static String workspaceSectionDescription(AppLanguage language) { return t(language, "workspace.section.description"); }
    public static String chooseWorkspaceButton(AppLanguage language) { return t(language, "choose.workspace.button"); }
    public static String continueButton(AppLanguage language) { return t(language, "continue.button"); }

    public static String workspaceValue(AppLanguage language, Optional<Path> workspace) {
        return workspace
                .map(path -> t(language, "workspace.configured.prefix") + " " + path)
                .orElseGet(() -> t(language, "workspace.value.empty"));
    }

    public static String errorStatus(String code, AppLanguage language) {
        return lookupOrDefault(language, "error.status.", code);
    }

    public static String errorDescription(String code, AppLanguage language) {
        return lookupOrDefault(language, "error.description.", code);
    }

    private static String lookupOrDefault(AppLanguage language, String prefix, String code) {
        ResourceBundle bundle = ResourceBundle.getBundle(
                BUNDLE_BASE, Locale.of(language.languageTag()), NO_FALLBACK_CONTROL);
        String specific = prefix + code;
        if (bundle.containsKey(specific)) {
            return bundle.getString(specific);
        }
        return bundle.getString(prefix + "default");
    }

    /* ---- AI models section ------------------------------------------ */

    public static String aiModelsSectionTitle(AppLanguage language) { return t(language, "ai.models.section.title"); }
    public static String aiModelsSectionDescription(AppLanguage language) { return t(language, "ai.models.section.description"); }
    public static String aiEnabledToggleLabel(AppLanguage language) { return t(language, "ai.enabled.toggle.label"); }
    public static String aiEnabledToggleDescription(AppLanguage language) { return t(language, "ai.enabled.toggle.description"); }
    public static String aiEnabledRestartHint(AppLanguage language) { return t(language, "ai.enabled.restart.hint"); }
    public static String aiEnabledRestartTitle(AppLanguage language) { return t(language, "ai.enabled.restart.title"); }
    public static String aiEnabledRestartMessage(AppLanguage language) { return t(language, "ai.enabled.restart.message"); }
    public static String aiEnabledRestartConfirm(AppLanguage language) { return t(language, "ai.enabled.restart.confirm"); }
    public static String aiEnabledDisabledNote(AppLanguage language) { return t(language, "ai.enabled.disabled.note"); }
    public static String aiModelsUse(AppLanguage language) { return t(language, "ai.models.use"); }
    public static String aiModelsRecommended(AppLanguage language) { return t(language, "ai.models.recommended"); }
    public static String aiModelsDownload(AppLanguage language) { return t(language, "ai.models.download"); }
    public static String aiModelsDelete(AppLanguage language) { return t(language, "ai.models.delete"); }
    public static String aiModelsStatusPresent(AppLanguage language) { return t(language, "ai.models.status.present"); }
    public static String aiModelsStatusMissing(AppLanguage language) { return t(language, "ai.models.status.missing"); }
    public static String aiModelsStatusDownloading(AppLanguage language) { return t(language, "ai.models.status.downloading"); }
    public static String aiModelsDownloadUnavailable(AppLanguage language) { return t(language, "ai.models.download.unavailable"); }
    public static String aiModelsDownloadFailed(AppLanguage language, String details) {
        return t(language, "ai.models.download.failed") + " " + details;
    }
    public static String aiModelsDeleteFailed(AppLanguage language, String details) {
        return t(language, "ai.models.delete.failed") + " " + details;
    }
    public static String aiModelsDeleteConfirm(AppLanguage language, String modelName) {
        return t(language, "ai.models.delete.confirm.prefix") + " " + modelName + " ?";
    }
    public static String aiModelsDeleteTitle(AppLanguage language) { return t(language, "ai.models.delete.title"); }
    public static String aiModelsDeleteMessage(AppLanguage language, String modelName) {
        return t(language, "ai.models.delete.message.prefix") + " " + modelName
                + " " + t(language, "ai.models.delete.message.suffix");
    }
    public static String aiModelsDownloadFailedTitle(AppLanguage language) { return t(language, "ai.models.download.failed.title"); }
    public static String aiModelsDownloadUnavailableTitle(AppLanguage language) { return t(language, "ai.models.download.unavailable.title"); }
    public static String aiModelsDeleteFailedTitle(AppLanguage language) { return t(language, "ai.models.delete.failed.title"); }
    public static String aiModelsActivating(AppLanguage language, String modelName) {
        return String.format(t(language, "ai.models.activating"), modelName);
    }
    public static String aiModelsActivated(AppLanguage language, String modelName) {
        return String.format(t(language, "ai.models.activated"), modelName);
    }
    public static String aiModelsActivationFailed(AppLanguage language, String details) {
        return t(language, "ai.models.activation.failed") + " " + details;
    }
    public static String aiModelsDownloadFirstHint(AppLanguage language) { return t(language, "ai.models.download.first.hint"); }
    public static String aiModelsEmpty(AppLanguage language) { return t(language, "ai.models.empty"); }
    public static String aiModelsDialogOk(AppLanguage language) { return t(language, "ai.models.dialog.ok"); }
    public static String aiModelsActiveLabel(AppLanguage language) { return t(language, "ai.models.active.label"); }
    public static String aiModelsActiveNone(AppLanguage language) { return t(language, "ai.models.active.none"); }
    public static String aiModelsSummaryPrivacyLabel(AppLanguage language) { return t(language, "ai.models.summary.privacy.label"); }
    public static String aiModelsSummaryPrivacyValue(AppLanguage language) { return t(language, "ai.models.summary.privacy.value"); }
    public static String aiModelsSummaryDownloadLabel(AppLanguage language) { return t(language, "ai.models.summary.download.label"); }
    public static String aiModelsSummaryDownloadValue(AppLanguage language) { return t(language, "ai.models.summary.download.value"); }
    public static String aiModelsSummaryStatusLabel(AppLanguage language) { return t(language, "ai.models.summary.status.label"); }

    /* ---- Navigation -------------------------------------------------- */

    public static String navScan(AppLanguage language) { return t(language, "nav.scan"); }
    public static String navHistory(AppLanguage language) { return t(language, "nav.history"); }
    public static String navSettings(AppLanguage language) { return settingsButton(language); }

    /* ---- Top bar ----------------------------------------------------- */

    public static String searchPlaceholder(AppLanguage language) { return t(language, "search.placeholder"); }
    public static String workspaceChipEmpty(AppLanguage language) { return t(language, "workspace.chip.empty"); }
    public static String primaryActionLoad(AppLanguage language) { return loadFolderButton(language); }
    public static String primaryActionValidate(AppLanguage language) { return t(language, "primary.action.validate"); }
    public static String topActionLoad(AppLanguage language) { return t(language, "top.action.load"); }
    public static String topActionLoadFolder(AppLanguage language) { return t(language, "top.action.loadFolder"); }
    public static String topActionLoadFiles(AppLanguage language) { return t(language, "top.action.loadFiles"); }
    public static String topActionAddFolder(AppLanguage language) { return t(language, "top.action.addFolder"); }
    public static String topActionAddFiles(AppLanguage language) { return t(language, "top.action.addFiles"); }
    public static String topActionReset(AppLanguage language) { return t(language, "top.action.reset"); }
    public static String topActionReanalyze(AppLanguage language) { return t(language, "top.action.reanalyze"); }
    public static String statusPillReady(AppLanguage language) { return t(language, "status.pill.ready"); }

    /* ---- Scan screen ------------------------------------------------- */

    public static String scanHeading(AppLanguage language) { return t(language, "scan.heading"); }
    public static String scanSafetyBanner(AppLanguage language) { return t(language, "scan.safety.banner"); }
    public static String scanPatternValidated(AppLanguage language) { return t(language, "scan.pattern.validated"); }
    public static String scanPatternBlocked(AppLanguage language) { return t(language, "scan.pattern.blocked"); }
    public static String scanExactPlanNextStep(AppLanguage language) { return t(language, "scan.exactPlan.nextStep"); }
    public static String loadingStartup(AppLanguage language) { return t(language, "loading.startup"); }
    public static String loadingScan(AppLanguage language) { return t(language, "loading.scan"); }

    public static String loadingScanAi(AppLanguage language) {
        return t(language, "loading.scan.ai");
    }

    public static String loadingScanTvdb(AppLanguage language) {
        return t(language, "loading.scan.tvdb");
    }

    public static String[] scanWorkflowSteps(AppLanguage language) {
        return t(language, "scan.workflow.steps").split("\\|");
    }

    public static String[] scanWorkflowStepsNoAi(AppLanguage language) {
        return t(language, "scan.workflow.steps.noAi").split("\\|");
    }

    public static String scanMetricTotal(AppLanguage language) { return t(language, "scan.metric.total"); }
    public static String scanMetricSeries(AppLanguage language) { return t(language, "scan.metric.series"); }
    public static String scanMetricMovies(AppLanguage language) { return t(language, "scan.metric.movies"); }
    public static String scanMetricUnknown(AppLanguage language) { return t(language, "scan.metric.unknown"); }
    public static String scanMetricIgnored(AppLanguage language) { return t(language, "scan.metric.ignored"); }
    public static String scanMetricToProcess(AppLanguage language) { return t(language, "scan.metric.toProcess"); }
    public static String scanMetricConflicts(AppLanguage language) { return t(language, "scan.metric.conflicts"); }
    public static String scanMetricWarnings(AppLanguage language) { return t(language, "scan.metric.warnings"); }

    public static String scanColumnSelection(AppLanguage language) { return t(language, "scan.column.selection"); }
    public static String scanColumnOriginal(AppLanguage language) { return t(language, "scan.column.original"); }
    public static String scanColumnProposed(AppLanguage language) { return t(language, "scan.column.proposed"); }
    public static String scanColumnSeries(AppLanguage language) { return t(language, "scan.column.series"); }
    public static String scanColumnSeason(AppLanguage language) { return t(language, "scan.column.season"); }
    public static String scanColumnEpisode(AppLanguage language) { return t(language, "scan.column.episode"); }
    public static String scanColumnTitle(AppLanguage language) { return t(language, "scan.column.title"); }
    public static String scanColumnYear(AppLanguage language) { return t(language, "scan.column.year"); }
    public static String scanColumnExtension(AppLanguage language) { return t(language, "scan.column.extension"); }
    public static String scanColumnType(AppLanguage language) { return t(language, "scan.column.type"); }
    public static String scanColumnTvdb(AppLanguage language) { return t(language, "scan.column.tvdb"); }
    public static String scanColumnDestination(AppLanguage language) { return t(language, "scan.column.destination"); }
    public static String scanColumnConfidence(AppLanguage language) { return t(language, "scan.column.confidence"); }
    public static String scanColumnStatus(AppLanguage language) { return t(language, "scan.column.status"); }
    public static String scanColumnOrder(AppLanguage language) { return t(language, "scan.column.order"); }
    public static String scanFilterAll(AppLanguage language) { return t(language, "scan.filter.all"); }
    public static String scanFilterMovies(AppLanguage language) { return t(language, "scan.filter.movies"); }
    public static String scanFilterSeries(AppLanguage language) { return t(language, "scan.filter.series"); }
    public static String scanFilterUnknown(AppLanguage language) { return t(language, "scan.filter.unknown"); }
    public static String scanStatusFilterAll(AppLanguage language) { return t(language, "scan.statusFilter.all"); }
    public static String scanStatusFilterToProcess(AppLanguage language) { return t(language, "scan.statusFilter.toProcess"); }
    public static String scanStatusFilterOk(AppLanguage language) { return t(language, "scan.statusFilter.ok"); }
    public static String scanStatusFilterTvdb(AppLanguage language) { return t(language, "scan.statusFilter.tvdb"); }
    public static String scanStatusFilterConflicts(AppLanguage language) { return t(language, "scan.statusFilter.conflicts"); }
    public static String scanStatusFilterIgnored(AppLanguage language) { return t(language, "scan.statusFilter.ignored"); }
    public static String scanStatusFilterAlerts(AppLanguage language) { return t(language, "scan.statusFilter.alerts"); }

    public static String scanMediaTypeSeries(AppLanguage language) { return t(language, "scan.media.type.series"); }
    public static String scanMediaTypeMovie(AppLanguage language) { return t(language, "scan.media.type.movie"); }
    public static String scanMediaTypeUnknown(AppLanguage language) { return t(language, "scan.media.type.unknown"); }
    public static String scanMediaTypeIgnored(AppLanguage language) { return t(language, "scan.media.type.ignored"); }

    public static String scanRowStatusOk(AppLanguage language) { return t(language, "scan.row.status.ok"); }
    public static String scanRowStatusReview(AppLanguage language) { return t(language, "scan.row.status.review"); }
    public static String scanRowStatusAi(AppLanguage language) { return t(language, "scan.row.status.ai"); }
    public static String scanRowStatusTvdb(AppLanguage language) { return t(language, "scan.row.status.tvdb"); }
    public static String scanRowStatusType(AppLanguage language) { return t(language, "scan.row.status.type"); }
    public static String scanRowStatusExt(AppLanguage language) { return t(language, "scan.row.status.ext"); }
    public static String scanRowStatusPattern(AppLanguage language) { return t(language, "scan.row.status.pattern"); }
    public static String scanRowStatusMeta(AppLanguage language) { return t(language, "scan.row.status.meta"); }
    public static String scanRowStatusConflict(AppLanguage language) { return t(language, "scan.row.status.conflict"); }
    public static String scanRowStatusDuplicate(AppLanguage language) { return t(language, "scan.row.status.duplicate"); }
    public static String scanRowStatusPath(AppLanguage language) { return t(language, "scan.row.status.path"); }
    public static String scanRowStatusError(AppLanguage language) { return t(language, "scan.row.status.error"); }
    public static String scanRowStatusIgnored(AppLanguage language) { return t(language, "scan.row.status.ignored"); }
    public static String scanOrderToDefine(AppLanguage language) { return t(language, "scan.order.toDefine"); }
    public static String scanOrderUnavailable(AppLanguage language) { return t(language, "scan.order.unavailable"); }
    public static String scanRowStatusPreview(AppLanguage language) { return scanRowStatusReview(language); }
    public static String scanRowStatusReady(AppLanguage language) { return scanRowStatusOk(language); }
    public static String scanRowStatusWarning(AppLanguage language) { return scanRowStatusReview(language); }

    public static String scanTablePlaceholder(AppLanguage language) { return t(language, "scan.table.placeholder"); }

    public static String scanContextIgnore(AppLanguage language) { return t(language, "scan.context.ignore"); }
    public static String scanContextStopIgnoring(AppLanguage language) { return t(language, "scan.context.stopIgnoring"); }
    public static String scanContextAskAi(AppLanguage language) { return t(language, "scan.context.askAi"); }
    public static String scanAiAdvisoryPrefix(AppLanguage language) { return t(language, "scan.ai.advisory.prefix"); }
    public static String scanAiUnavailableNote(AppLanguage language) { return t(language, "scan.ai.unavailable.note"); }
    public static String scanContextResetMatch(AppLanguage language) { return t(language, "scan.context.resetMatch"); }
    public static String scanContextCopyPath(AppLanguage language) { return t(language, "scan.context.copyPath"); }
    public static String scanContextOpenFolder(AppLanguage language) { return t(language, "scan.context.openFolder"); }

    public static String filePropertiesAction(AppLanguage language) { return t(language, "file.properties.action"); }
    public static String filePropertiesTitle(AppLanguage language) { return t(language, "file.properties.title"); }
    public static String filePropertiesSectionFile(AppLanguage language) { return t(language, "file.properties.section.file"); }
    public static String filePropertiesSectionDetection(AppLanguage language) { return t(language, "file.properties.section.detection"); }
    public static String filePropertiesSectionTvdb(AppLanguage language) { return t(language, "file.properties.section.tvdb"); }
    public static String filePropertiesSectionMedia(AppLanguage language) { return t(language, "file.properties.section.media"); }
    public static String filePropertiesCurrentName(AppLanguage language) { return t(language, "file.properties.currentName"); }
    public static String filePropertiesProposedName(AppLanguage language) { return t(language, "file.properties.proposedName"); }
    public static String filePropertiesExtension(AppLanguage language) { return t(language, "file.properties.extension"); }
    public static String filePropertiesSize(AppLanguage language) { return t(language, "file.properties.size"); }
    public static String filePropertiesLocation(AppLanguage language) { return t(language, "file.properties.location"); }
    public static String filePropertiesFullPath(AppLanguage language) { return t(language, "file.properties.fullPath"); }
    public static String filePropertiesLastModified(AppLanguage language) { return t(language, "file.properties.lastModified"); }
    public static String filePropertiesDetectedType(AppLanguage language) { return t(language, "file.properties.detectedType"); }
    public static String filePropertiesSeason(AppLanguage language) { return t(language, "file.properties.season"); }
    public static String filePropertiesEpisode(AppLanguage language) { return t(language, "file.properties.episode"); }
    public static String filePropertiesDetectedTitle(AppLanguage language) { return t(language, "file.properties.detectedTitle"); }
    public static String filePropertiesInputPattern(AppLanguage language) { return t(language, "file.properties.inputPattern"); }
    public static String filePropertiesConfidence(AppLanguage language) { return t(language, "file.properties.confidence"); }
    public static String filePropertiesSource(AppLanguage language) { return t(language, "file.properties.source"); }
    public static String filePropertiesStatus(AppLanguage language) { return t(language, "file.properties.status"); }
    public static String filePropertiesTvdbTitle(AppLanguage language) { return t(language, "file.properties.tvdbTitle"); }
    public static String filePropertiesTvdbType(AppLanguage language) { return t(language, "file.properties.tvdbType"); }
    public static String filePropertiesYear(AppLanguage language) { return t(language, "file.properties.year"); }
    public static String filePropertiesTvdbId(AppLanguage language) { return t(language, "file.properties.tvdbId"); }
    public static String filePropertiesOrder(AppLanguage language) { return t(language, "file.properties.order"); }
    public static String filePropertiesMediaUnavailable(AppLanguage language) { return t(language, "file.properties.mediaUnavailable"); }
    public static String filePropertiesVideoCodec(AppLanguage language) { return t(language, "file.properties.videoCodec"); }
    public static String filePropertiesAudioCodec(AppLanguage language) { return t(language, "file.properties.audioCodec"); }
    public static String filePropertiesResolution(AppLanguage language) { return t(language, "file.properties.resolution"); }
    public static String filePropertiesDuration(AppLanguage language) { return t(language, "file.properties.duration"); }
    public static String filePropertiesBitrate(AppLanguage language) { return t(language, "file.properties.bitrate"); }
    public static String filePropertiesFramerate(AppLanguage language) { return t(language, "file.properties.framerate"); }
    public static String filePropertiesContainer(AppLanguage language) { return t(language, "file.properties.container"); }
    public static String filePropertiesClose(AppLanguage language) { return t(language, "file.properties.close"); }
    public static String filePropertiesCopyPath(AppLanguage language) { return t(language, "file.properties.copyPath"); }
    public static String filePropertiesPathCopied(AppLanguage language) { return t(language, "file.properties.pathCopied"); }

    /* ---- Detail panel ----------------------------------------------- */

    public static String detailNoSelection(AppLanguage language) { return t(language, "detail.no.selection"); }
    public static String detailSectionSource(AppLanguage language) { return t(language, "detail.section.source"); }
    public static String detailSectionDetection(AppLanguage language) { return t(language, "detail.section.detection"); }
    public static String detailSectionTvdb(AppLanguage language) { return t(language, "detail.section.tvdb"); }
    public static String detailSectionDestination(AppLanguage language) { return t(language, "detail.section.destination"); }
    public static String detailSectionNotes(AppLanguage language) { return t(language, "detail.section.notes"); }

    public static String detailFieldFilename(AppLanguage language) { return t(language, "detail.field.filename"); }
    public static String detailFieldFolder(AppLanguage language) { return t(language, "detail.field.folder"); }
    public static String detailFieldExtension(AppLanguage language) { return t(language, "detail.field.extension"); }
    public static String detailFieldMediaType(AppLanguage language) { return t(language, "detail.field.mediaType"); }
    public static String detailFieldStatus(AppLanguage language) { return t(language, "detail.field.status"); }
    public static String detailFieldConfidence(AppLanguage language) { return t(language, "detail.field.confidence"); }
    public static String detailFieldDestination(AppLanguage language) { return t(language, "detail.field.destination"); }
    public static String detailFieldSeasonEpisode(AppLanguage language) { return t(language, "detail.field.seasonEpisode"); }
    public static String detailFieldInputPattern(AppLanguage language) { return t(language, "detail.field.inputPattern"); }
    public static String detailFieldProposed(AppLanguage language) { return t(language, "detail.field.proposed"); }
    public static String detailFieldConflict(AppLanguage language) { return t(language, "detail.field.conflict"); }
    public static String detailFieldAlert(AppLanguage language) { return t(language, "detail.field.alert"); }
    public static String detailFieldNote(AppLanguage language) { return t(language, "detail.field.note"); }

    public static String detailTvdbSearchPlaceholder(AppLanguage language) { return t(language, "detail.tvdb.search.placeholder"); }
    public static String detailTvdbStub(AppLanguage language) { return t(language, "detail.tvdb.stub"); }
    public static String detailApplyButton(AppLanguage language) { return t(language, "detail.apply.button"); }
    public static String detailApplyToSelection(AppLanguage language) { return t(language, "detail.apply.toSelection"); }
    public static String detailResetButton(AppLanguage language) { return t(language, "detail.reset.button"); }
    public static String tvdbSearchForMatch(AppLanguage language) { return t(language, "tvdb.searchForMatch"); }
    public static String tvdbChooseMatch(AppLanguage language) { return t(language, "tvdb.chooseMatch"); }
    public static String tvdbSearch(AppLanguage language) { return t(language, "tvdb.search"); }
    public static String tvdbSearchPlaceholder(AppLanguage language) { return t(language, "tvdb.searchPlaceholder"); }
    public static String tvdbNoResults(AppLanguage language) { return t(language, "tvdb.noResults"); }
    public static String tvdbNoResultsHint(AppLanguage language) { return t(language, "tvdb.noResultsHint"); }
    public static String tvdbInitialSearchHint(AppLanguage language) { return t(language, "tvdb.initialSearchHint"); }
    public static String tvdbNoMatchSelected(AppLanguage language) { return t(language, "tvdb.noMatchSelected"); }
    public static String tvdbResolveNow(AppLanguage language) { return t(language, "tvdb.resolveNow"); }
    public static String tvdbIgnore(AppLanguage language) { return t(language, "tvdb.ignore"); }
    public static String tvdbSelect(AppLanguage language) { return t(language, "tvdb.select"); }
    public static String tvdbApply(AppLanguage language) { return t(language, "tvdb.apply"); }
    public static String tvdbApplyMatch(AppLanguage language) { return t(language, "tvdb.applyMatch"); }
    public static String tvdbMatchApplied(AppLanguage language) { return t(language, "tvdb.matchApplied"); }
    public static String tvdbNoFileSelected(AppLanguage language) { return t(language, "tvdb.noFileSelected"); }
    public static String tvdbNoResultSelected(AppLanguage language) { return t(language, "tvdb.noResultSelected"); }
    public static String tvdbNoDescription(AppLanguage language) { return t(language, "tvdb.noDescription"); }
    public static String tvdbSearching(AppLanguage language) { return t(language, "tvdb.searching"); }
    public static String tvdbLoadingResults(AppLanguage language) { return t(language, "tvdb.loadingResults"); }
    public static String tvdbSeries(AppLanguage language) { return t(language, "tvdb.series"); }
    public static String tvdbMovie(AppLanguage language) { return t(language, "tvdb.movie"); }
    public static String tvdbIdLabel(AppLanguage language) { return t(language, "tvdb.id"); }
    public static String tvdbClose(AppLanguage language) { return t(language, "tvdb.close"); }

    public static String tvdbManualRequired(AppLanguage language, int count) {
        return t(language, "tvdb.manualRequired").replace("{0}", Integer.toString(count));
    }

    public static String tvdbSelectedFiles(AppLanguage language, int count) {
        String key = count == 1 ? "tvdb.selectedFiles.one" : "tvdb.selectedFiles.many";
        return t(language, key).replace("{count}", Integer.toString(count));
    }

    public static String tvdbMatchWillApplyTo(AppLanguage language, int count) {
        return t(language, "tvdb.matchWillApplyTo").replace("{count}", Integer.toString(count));
    }

    public static String tvdbFilesUpdated(AppLanguage language, int count) {
        return t(language, "tvdb.filesUpdated").replace("{count}", Integer.toString(count));
    }
    public static String tvdbMatchSuggested(AppLanguage language) { return t(language, "tvdb.matchSuggested"); }
    public static String tvdbAutomaticMatch(AppLanguage language) { return t(language, "tvdb.automaticMatch"); }
    public static String tvdbSuggestionNeedsValidation(AppLanguage language) { return t(language, "tvdb.suggestionNeedsValidation"); }

    /* ---- History screen --------------------------------------------- */

    public static String historyHeading(AppLanguage language) { return t(language, "history.heading"); }
    public static String historyBanner(AppLanguage language) { return t(language, "history.banner"); }
    public static String historyMetricTotal(AppLanguage language) { return t(language, "history.metric.total"); }
    public static String historyMetricSuccess(AppLanguage language) { return t(language, "history.metric.success"); }
    public static String historyMetricWarning(AppLanguage language) { return t(language, "history.metric.warning"); }
    public static String historyMetricConflict(AppLanguage language) { return t(language, "history.metric.conflict"); }
    public static String historyMetricFailed(AppLanguage language) { return t(language, "history.metric.failed"); }
    public static String historyFilterAll(AppLanguage language) { return t(language, "history.filter.all"); }
    public static String historyFilterSuccessful(AppLanguage language) { return t(language, "history.filter.successful"); }
    public static String historyFilterWarnings(AppLanguage language) { return t(language, "history.filter.warnings"); }
    public static String historyFilterConflicts(AppLanguage language) { return t(language, "history.filter.conflicts"); }
    public static String historyFilterFailed(AppLanguage language) { return t(language, "history.filter.failed"); }
    public static String historyFilterIgnored(AppLanguage language) { return t(language, "history.filter.ignored"); }
    public static String historyColumnTimestamp(AppLanguage language) { return t(language, "history.column.timestamp"); }
    public static String historyColumnEventType(AppLanguage language) { return t(language, "history.column.eventType"); }
    public static String historyColumnSource(AppLanguage language) { return t(language, "history.column.source"); }
    public static String historyColumnSummary(AppLanguage language) { return t(language, "history.column.summary"); }
    public static String historyColumnStatus(AppLanguage language) { return t(language, "history.column.status"); }
    public static String historyEmptyPlaceholder(AppLanguage language) { return t(language, "history.empty.placeholder"); }
    public static String historyEventScanCompleted(AppLanguage language) { return t(language, "history.event.scanCompleted"); }
    public static String historyEventScanFailed(AppLanguage language) { return t(language, "history.event.scanFailed"); }
    public static String historyDetailNoSelection(AppLanguage language) { return t(language, "history.detail.no.selection"); }
    public static String historyDetailSectionSummary(AppLanguage language) { return t(language, "history.detail.section.summary"); }
    public static String historyDetailSectionMetrics(AppLanguage language) { return t(language, "history.detail.section.metrics"); }

    /* ---- Settings page ---------------------------------------------- */

    public static String settingsHeading(AppLanguage language) { return t(language, "settings.heading"); }
    public static String settingsPageTitle(AppLanguage language) { return t(language, "settings.page.title"); }
    public static String settingsPageSubtitle(AppLanguage language) { return t(language, "settings.page.subtitle"); }
    public static String preferencesSectionTitle(AppLanguage language) { return t(language, "preferences.section.title"); }
    public static String preferencesSectionDescription(AppLanguage language) { return t(language, "preferences.section.description"); }
    public static String tvdbSettingsSectionTitle(AppLanguage language) { return t(language, "tvdb.settings.section.title"); }
    public static String tvdbSettingsSectionDescription(AppLanguage language) { return t(language, "tvdb.settings.section.description"); }
    public static String tvdbSettingsTestAndSave(AppLanguage language) { return t(language, "tvdb.settings.testAndSave"); }
    public static String tvdbSettingsNotChecked(AppLanguage language) { return t(language, "tvdb.settings.notChecked"); }
    public static String tvdbSettingsChecking(AppLanguage language) { return t(language, "tvdb.settings.checking"); }
    public static String tvdbSettingsOnline(AppLanguage language) { return t(language, "tvdb.settings.online"); }
    public static String tvdbSettingsUnavailable(AppLanguage language) { return t(language, "tvdb.settings.unavailable"); }
    public static String tvdbSettingsResetCache(AppLanguage language) { return t(language, "tvdb.settings.resetCache"); }
    public static String tvdbSettingsCacheCleared(AppLanguage language, int count) { return t(language, "tvdb.settings.cacheCleared").replace("{0}", Integer.toString(count)); }
    public static String tvdbSettingsCacheAlreadyEmpty(AppLanguage language) { return t(language, "tvdb.settings.cacheAlreadyEmpty"); }
    public static String safetySectionTitle(AppLanguage language) { return t(language, "safety.section.title"); }
    public static String safetySectionDescription(AppLanguage language) { return t(language, "safety.section.description"); }
    public static String safetySectionPlaceholder(AppLanguage language) { return t(language, "safety.section.placeholder"); }

    /* ---- Empty states (table / detail) ------------------------------ */

    public static String scanEmptyTitle(AppLanguage language) { return t(language, "scan.empty.title"); }
    public static String scanEmptyHint(AppLanguage language) { return t(language, "scan.empty.hint"); }
    public static String historyEmptyTitle(AppLanguage language) { return t(language, "history.empty.title"); }
    public static String historyEmptyHint(AppLanguage language) { return t(language, "history.empty.hint"); }
    public static String detailEmptyTitle(AppLanguage language) { return t(language, "detail.empty.title"); }
    public static String detailEmptyHint(AppLanguage language) { return t(language, "detail.empty.hint"); }
    public static String historyDetailEmptyTitle(AppLanguage language) { return t(language, "history.detail.empty.title"); }
    public static String historyDetailEmptyHint(AppLanguage language) { return t(language, "history.detail.empty.hint"); }

    public static String historyClearButton(AppLanguage language) { return t(language, "history.clear.button"); }
    public static String historyClearConfirmation(AppLanguage language) { return t(language, "history.clear.confirmation"); }
    public static String historyClearConfirmButton(AppLanguage language) { return t(language, "history.clear.confirm.button"); }

    public static String topActionChangeFolder(AppLanguage language) { return t(language, "top.action.changeFolder"); }
    public static String topActionResetFolder(AppLanguage language) { return t(language, "top.action.resetFolder"); }
    public static String topActionResetSearch(AppLanguage language) { return t(language, "top.action.resetSearch"); }
    public static String topActionRescan(AppLanguage language) { return t(language, "top.action.rescan"); }

    /* ---- Batch TVDB ------------------------------------------------- */

    public static String scanBatchTvdbHeading(AppLanguage language) { return t(language, "scan.batch.tvdb.heading"); }
    public static String scanBatchTvdbCandidatePlaceholder(AppLanguage language) { return t(language, "scan.batch.tvdb.candidate.placeholder"); }
    public static String scanBatchTvdbOrderPlaceholder(AppLanguage language) { return t(language, "scan.batch.tvdb.order.placeholder"); }
    public static String scanBatchTvdbOrderAired(AppLanguage language) { return t(language, "scan.batch.tvdb.order.aired"); }
    public static String scanBatchTvdbOrderDvd(AppLanguage language) { return t(language, "scan.batch.tvdb.order.dvd"); }
    public static String scanBatchTvdbOrderAbsolute(AppLanguage language) { return t(language, "scan.batch.tvdb.order.absolute"); }
    public static String scanBatchTvdbApply(AppLanguage language) { return t(language, "scan.batch.tvdb.apply"); }
    public static String scanBatchTvdbReset(AppLanguage language) { return t(language, "scan.batch.tvdb.reset"); }
    public static String scanBatchTvdbStatusFinal(AppLanguage language) { return t(language, "scan.batch.tvdb.status.final"); }
    public static String scanBatchTvdbStatusUnresolved(AppLanguage language) { return t(language, "scan.batch.tvdb.status.unresolved"); }

    /* ---- AI chat panel --------------------------------------------- */

    public static String aiChatHeading(AppLanguage language) { return t(language, "ai.chat.heading"); }
    public static String aiChatPlaceholder(AppLanguage language) { return t(language, "ai.chat.placeholder"); }
    public static String aiChatRenamePrompt(AppLanguage language) { return t(language, "ai.chat.rename.prompt"); }
    public static String aiChatSend(AppLanguage language) { return t(language, "ai.chat.send"); }
    public static String aiChatNoTarget(AppLanguage language) { return t(language, "ai.chat.no.target"); }
    public static String aiChatTargetPrefix(AppLanguage language) { return t(language, "ai.chat.target.prefix") + " "; }
    public static String aiChatUnavailable(AppLanguage language) { return t(language, "ai.chat.unavailable"); }
    public static String aiChatErrorPrefix(AppLanguage language) { return t(language, "ai.chat.error.prefix") + " "; }
    public static String aiChatToolCallTitle(AppLanguage language) { return t(language, "ai.chat.tool.call.title"); }
    public static String aiChatToolConfirm(AppLanguage language) { return t(language, "ai.chat.tool.confirm"); }
    public static String aiChatToolCancel(AppLanguage language) { return t(language, "ai.chat.tool.cancel"); }
    public static String aiChatToolApplied(AppLanguage language) { return t(language, "ai.chat.tool.applied"); }
    public static String aiChatToolCancelled(AppLanguage language) { return t(language, "ai.chat.tool.cancelled"); }

    /* ---- Local AI bootstrap modal ----------------------------------- */

    public static String localAiModalTitle(AppLanguage language) { return t(language, "localAi.modal.title"); }
    public static String localAiModalSubtitle(AppLanguage language) { return t(language, "localAi.modal.subtitle"); }
    public static String localAiModalOptionalPill(AppLanguage language) { return t(language, "localAi.modal.optional.pill"); }
    public static String localAiModalLabelModel(AppLanguage language) { return t(language, "localAi.modal.label.model"); }
    public static String localAiModalValueModel(AppLanguage language) { return t(language, "localAi.modal.value.model"); }
    public static String localAiModalLabelDownload(AppLanguage language) { return t(language, "localAi.modal.label.download"); }
    public static String localAiModalValueDownload(AppLanguage language) { return t(language, "localAi.modal.value.download"); }
    public static String localAiModalLabelPrivacy(AppLanguage language) { return t(language, "localAi.modal.label.privacy"); }
    public static String localAiModalValuePrivacy(AppLanguage language) { return t(language, "localAi.modal.value.privacy"); }
    public static String localAiModalLabelWithout(AppLanguage language) { return t(language, "localAi.modal.label.without"); }
    public static String localAiModalValueWithout(AppLanguage language) { return t(language, "localAi.modal.value.without"); }
    public static String localAiModalActivate(AppLanguage language) { return t(language, "localAi.modal.activate"); }
    public static String localAiModalLater(AppLanguage language) { return t(language, "localAi.modal.later"); }
    public static String localAiModalClose(AppLanguage language) { return t(language, "localAi.modal.close"); }

    public static String localAiModalDownloading(AppLanguage language, long writtenMb) {
        return t(language, "localAi.modal.downloading.prefix") + " " + writtenMb + " "
                + t(language, "localAi.modal.downloading.unit");
    }

    public static String localAiModalStarting(AppLanguage language) { return t(language, "localAi.modal.starting"); }
    public static String localAiModalAlreadyPresent(AppLanguage language) { return t(language, "localAi.modal.alreadyPresent"); }
    public static String localAiModalReady(AppLanguage language) { return t(language, "localAi.modal.ready"); }

    public static String prereqOverlayTitle(AppLanguage language) { return t(language, "prereq.overlay.title"); }
    public static String prereqOverlaySubtitle(AppLanguage language) { return t(language, "prereq.overlay.subtitle"); }
    public static String prereqMissingWorkspace(AppLanguage language) { return t(language, "prereq.missing.workspace"); }
    public static String prereqMissingLocalAi(AppLanguage language) { return t(language, "prereq.missing.localAi"); }
    public static String prereqOpenSettings(AppLanguage language) { return t(language, "prereq.openSettings"); }

    public static String localAiStatusModelPresent(AppLanguage language) { return t(language, "localAi.status.modelPresent"); }
    public static String localAiStatusModelMissing(AppLanguage language) { return t(language, "localAi.status.modelMissing"); }
    public static String localAiStatusRuntimeUp(AppLanguage language) { return t(language, "localAi.status.runtimeUp"); }
    public static String localAiStatusRuntimeDown(AppLanguage language) { return t(language, "localAi.status.runtimeDown"); }
    public static String localAiStatusLabel(AppLanguage language) { return t(language, "localAi.status.label"); }
    public static String localAiRuntimeMissing(AppLanguage language) { return t(language, "localAi.runtime.missing"); }
    public static String localAiRedownload(AppLanguage language) { return t(language, "localAi.redownload"); }
    public static String localAiDownload(AppLanguage language) { return t(language, "localAi.download"); }
    public static String localAiTest(AppLanguage language) { return t(language, "localAi.test"); }
    public static String localAiTestRunning(AppLanguage language) { return t(language, "localAi.test.running"); }

    public static String localAiTestGreen(AppLanguage language, long ms) {
        return t(language, "localAi.test.green.prefix") + " (" + ms + " ms)";
    }

    public static String localAiTestOrange(AppLanguage language, long ms) {
        return t(language, "localAi.test.orange.prefix") + " (" + ms + " ms)";
    }

    public static String localAiTestRed(AppLanguage language, String detail) {
        return t(language, "localAi.test.red.prefix") + " " + (detail == null ? "—" : detail);
    }

    public static String localAiTestUnavailable(AppLanguage language) { return t(language, "localAi.test.unavailable"); }
    public static String localAiRedownloadConfirm(AppLanguage language) { return t(language, "localAi.redownload.confirm"); }

    public static String localAiModalFailure(AppLanguage language, String detail) {
        return t(language, "localAi.modal.failure.prefix") + " " + (detail == null ? "—" : detail);
    }
}

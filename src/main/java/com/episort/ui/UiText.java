package com.episort.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

public final class UiText {
    /**
     * The single placeholder for "no value". The design system forbids
     * fabricated state: a label with nothing real behind it reads as this
     * em dash, never as a guess, a zero or a blank.
     */
    public static final String EMPTY = "-";

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
    public static String settingsButton(AppLanguage language) { return t(language, "settings.button"); }
    public static String cancelButton(AppLanguage language) { return t(language, "cancel.button"); }
    public static String settingsRequiredStatus(AppLanguage language) { return t(language, "settings.required.status"); }
    public static String settingsRequiredDescription(AppLanguage language) { return t(language, "settings.required.description"); }
    public static String workspaceConfiguredStatus(AppLanguage language) { return t(language, "workspace.configured.status"); }

    public static String workspaceSectionTitle(AppLanguage language) { return t(language, "workspace.section.title"); }
    public static String workspaceSectionDescription(AppLanguage language) { return t(language, "workspace.section.description"); }
    public static String chooseWorkspaceButton(AppLanguage language) { return t(language, "choose.workspace.button"); }

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

    /* ---- Navigation -------------------------------------------------- */

    /* ---- Window chrome and application menus -------------------------- */

    public static String menuFile(AppLanguage language) { return t(language, "menu.file"); }
    public static String menuAnalysis(AppLanguage language) { return t(language, "menu.analysis"); }
    public static String menuView(AppLanguage language) { return t(language, "menu.view"); }
    public static String menuHelp(AppLanguage language) { return t(language, "menu.help"); }
    public static String menuQuit(AppLanguage language) { return t(language, "menu.quit"); }
    public static String menuAbout(AppLanguage language) { return t(language, "menu.about"); }
    public static String aboutBody(AppLanguage language) { return t(language, "about.body"); }
    public static String aboutBack(AppLanguage language) { return t(language, "about.back"); }
    public static String aboutTagline(AppLanguage language) { return t(language, "about.tagline"); }
    public static String aboutVersionPrefix(AppLanguage language) { return t(language, "about.version.prefix"); }
    public static String aboutSectionPipeline(AppLanguage language) { return t(language, "about.section.pipeline"); }
    public static String aboutSectionData(AppLanguage language) { return t(language, "about.section.data"); }
    public static String aboutSectionCredits(AppLanguage language) { return t(language, "about.section.credits"); }
    public static String aboutFieldJava(AppLanguage language) { return t(language, "about.field.java"); }
    public static String aboutFieldJavaFx(AppLanguage language) { return t(language, "about.field.javafx"); }
    public static String aboutFieldSystem(AppLanguage language) { return t(language, "about.field.system"); }
    public static String aboutPathRunHistory(AppLanguage language) { return t(language, "about.path.runHistory"); }
    public static String aboutPathJournal(AppLanguage language) { return t(language, "about.path.journal"); }
    public static String aboutTmdbAttribution(AppLanguage language) { return t(language, "about.tmdb.attribution"); }
    public static String aboutFonts(AppLanguage language) { return t(language, "about.fonts"); }

    public static String aboutSectionEnvironment(AppLanguage language) {
        return t(language, "about.section.environment");
    }
    public static String windowMinimize(AppLanguage language) { return t(language, "window.minimize"); }
    public static String windowMaximize(AppLanguage language) { return t(language, "window.maximize"); }
    public static String windowRestore(AppLanguage language) { return t(language, "window.restore"); }
    public static String windowClose(AppLanguage language) { return t(language, "window.close"); }

    public static String navScan(AppLanguage language) { return t(language, "nav.scan"); }
    public static String navHistory(AppLanguage language) { return t(language, "nav.history"); }
    public static String navSettings(AppLanguage language) { return settingsButton(language); }

    /* ---- Top bar ----------------------------------------------------- */

    public static String searchPlaceholder(AppLanguage language) { return t(language, "search.placeholder"); }
    public static String scanSearchPlaceholder(AppLanguage language) { return t(language, "scan.searchPlaceholder"); }
    public static String historySearchPlaceholder(AppLanguage language) { return t(language, "history.searchPlaceholder"); }
    public static String workspaceChipEmpty(AppLanguage language) { return t(language, "workspace.chip.empty"); }
    public static String topActionLoad(AppLanguage language) { return t(language, "top.action.load"); }
    public static String topActionLoadFolder(AppLanguage language) { return t(language, "top.action.loadFolder"); }
    public static String topActionLoadFiles(AppLanguage language) { return t(language, "top.action.loadFiles"); }
    public static String topActionAddFolder(AppLanguage language) { return t(language, "top.action.addFolder"); }
    public static String topActionAddFiles(AppLanguage language) { return t(language, "top.action.addFiles"); }
    public static String topActionReset(AppLanguage language) { return t(language, "top.action.reset"); }
    public static String topActionReanalyze(AppLanguage language) { return t(language, "top.action.reanalyze"); }
    public static String topActionRefresh(AppLanguage language) { return t(language, "top.action.refresh"); }

    /* ---- Shell: file choosers, scan failures, accessibility ---------- */

    public static String chooserLoadFolderTitle(AppLanguage language) { return t(language, "chooser.loadFolder.title"); }
    public static String chooserAddFolderTitle(AppLanguage language) { return t(language, "chooser.addFolder.title"); }
    public static String chooserLoadFilesTitle(AppLanguage language) { return t(language, "chooser.loadFiles.title"); }
    public static String chooserVideoFiles(AppLanguage language) { return t(language, "chooser.videoFiles"); }
    public static String chooserWorkspaceTitle(AppLanguage language) { return t(language, "chooser.workspace.title"); }
    public static String scanFailedTitle(AppLanguage language) { return t(language, "scan.failed.title"); }
    public static String scanFailedFolder(AppLanguage language) { return t(language, "scan.failed.folder"); }
    public static String scanFailedFiles(AppLanguage language) { return t(language, "scan.failed.files"); }
    public static String sidebarSectionNavigation(AppLanguage language) { return t(language, "sidebar.section.navigation"); }
    public static String sidebarSectionWorkspace(AppLanguage language) { return t(language, "sidebar.section.workspace"); }
    public static String storageHeading(AppLanguage language) { return t(language, "sidebar.storage.heading"); }

    public static String storageUsedPercentage(AppLanguage language, long percentage) {
        return t(language, "sidebar.storage.used").replace("{0}", Long.toString(percentage));
    }

    /**
     * The figure alone. The readout draws it at the heading step with the word
     * that qualifies it beside it, so the phrase and its two parts are three
     * keys: a translation that reorders them keeps the reordering.
     */
    public static String storageUsedPercentageValue(AppLanguage language, long percentage) {
        return t(language, "sidebar.storage.used.value").replace("{0}", Long.toString(percentage));
    }

    public static String storageUsedSuffix(AppLanguage language) { return t(language, "sidebar.storage.used.suffix"); }

    public static String storageCapacity(AppLanguage language, String used, String total) {
        return t(language, "sidebar.storage.capacity").replace("{0}", used).replace("{1}", total);
    }

    public static String storageAvailable(AppLanguage language, String available) {
        return t(language, "sidebar.storage.available").replace("{0}", available);
    }

    public static String storageAccessible(
            AppLanguage language, String percentage, String capacity, String available) {
        return t(language, "sidebar.storage.accessible")
                .replace("{0}", percentage)
                .replace("{1}", capacity)
                .replace("{2}", available);
    }

    public static String workspaceExplorerEmpty(AppLanguage language) { return t(language, "workspace.explorer.empty"); }
    public static String workspaceExplorerLoading(AppLanguage language) { return t(language, "workspace.explorer.loading"); }
    public static String workspaceExplorerUnavailable(AppLanguage language) { return t(language, "workspace.explorer.unavailable"); }
    public static String workspaceExplorerAccessible(AppLanguage language) { return t(language, "workspace.explorer.accessible"); }
    public static String workspaceExplorerCollapse(AppLanguage language) { return t(language, "workspace.explorer.collapse"); }
    public static String workspaceExplorerExpand(AppLanguage language) { return t(language, "workspace.explorer.expand"); }
    public static String a11yClearSearch(AppLanguage language) { return t(language, "a11y.clearSearch"); }
    public static String a11ySelectAllRows(AppLanguage language) { return t(language, "a11y.selectAllRows"); }
    public static String a11ySelectRow(AppLanguage language) { return t(language, "a11y.selectRow"); }
    public static String a11yCloseWindow(AppLanguage language) { return t(language, "a11y.closeWindow"); }
    public static String workspaceValueEmpty(AppLanguage language) { return t(language, "workspace.value.empty"); }
    public static String workspaceSelectedPrefix(AppLanguage language) { return t(language, "workspace.configured.prefix"); }
    public static String historyColumnResult(AppLanguage language) { return t(language, "history.column.result"); }
    public static String historyDetailFieldWorkspace(AppLanguage language) { return t(language, "history.detail.field.workspace"); }
    public static String detailFieldGroup(AppLanguage language) { return t(language, "detail.field.group"); }
    public static String scanNoteTmdbIdentitySelected(AppLanguage language) { return t(language, "scan.note.tmdbIdentitySelected"); }
    public static String tmdbApplyingOrder(AppLanguage language) { return t(language, "tmdb.applyingOrder"); }

    /** Detail-panel note and alert copy produced while resolving TMDB matches. */
    private static String note(AppLanguage language, String key, String argument) {
        return t(language, key).replace("{0}", argument == null ? EMPTY : argument);
    }

    public static String scanNoteTmdbIdentityNoEpisode(AppLanguage language, String identity) { return note(language, "scan.note.tmdbIdentityNoEpisode", identity); }
    public static String scanNoteTmdbIdentityApplied(AppLanguage language, String identity) { return note(language, "scan.note.tmdbIdentityApplied", identity); }
    public static String scanNoteTmdbUnavailable(AppLanguage language) { return t(language, "scan.note.tmdbCredentialsMissing"); }
    public static String scanNoteTmdbNoCandidates(AppLanguage language, String query) { return note(language, "scan.note.tmdbNoCandidates", query); }
    public static String scanNoteTmdbCandidatesLoaded(AppLanguage language) { return t(language, "scan.note.tmdbCandidatesLoaded"); }
    public static String scanNoteTmdbMovieApplied(AppLanguage language, String title) { return note(language, "scan.note.tmdbMovieApplied", title); }
    public static String scanNoteTmdbOrderUnavailable(AppLanguage language) { return t(language, "scan.note.tmdbOrderUnavailable"); }
    public static String scanAlertTmdbOrderUnavailable(AppLanguage language) { return t(language, "scan.alert.tmdbOrderUnavailable"); }
    public static String scanNoteTmdbNoEpisodeMatch(AppLanguage language) { return t(language, "scan.note.tmdbNoEpisodeMatch"); }
    public static String scanNoteTmdbEpisodeApplied(AppLanguage language, String title) { return note(language, "scan.note.tmdbEpisodeApplied", title); }

    /* ---- Scan screen ------------------------------------------------- */

    public static String scanHeading(AppLanguage language) { return t(language, "scan.heading"); }
    public static String scanSafetyBanner(AppLanguage language) { return t(language, "scan.safety.banner"); }
    public static String scanPatternBlocked(AppLanguage language) { return t(language, "scan.pattern.blocked"); }

    /**
     * Explains which validation gates still block execution. Blocker codes come
     * from {@code ReviewSession}; unknown codes are dropped rather than shown
     * raw, so the banner never leaks internal identifiers.
     */
    public static String scanExecutionBlockers(AppLanguage language, List<String> blockerCodes) {
        List<String> parts = blockerCodes.stream()
                .map(code -> blockerText(language, code))
                .flatMap(Optional::stream)
                .toList();
        if (parts.isEmpty()) {
            return t(language, "scan.blocker.none");
        }
        return t(language, "scan.blocker.prefix") + " " + String.join(", ", parts) + ".";
    }

    private static Optional<String> blockerText(AppLanguage language, String code) {
        return switch (code) {
            case "BLOCKING_CONFLICTS" -> Optional.of(t(language, "scan.blocker.conflicts"));
            case "PATTERN_NOT_VALIDATED" -> Optional.of(t(language, "scan.blocker.pattern"));
            case "EXACT_PLAN_MISSING" -> Optional.of(t(language, "scan.blocker.planMissing"));
            case "EXACT_PLAN_NOT_VALIDATED" -> Optional.of(t(language, "scan.blocker.planNotValidated"));
            default -> Optional.empty();
        };
    }
    public static String loadingEyebrow(AppLanguage language) { return t(language, "loading.eyebrow"); }
    public static String loadingStartup(AppLanguage language) { return t(language, "loading.startup"); }
    public static String loadingScan(AppLanguage language) { return t(language, "loading.scan"); }

    public static String loadingScanTmdb(AppLanguage language) {
        return t(language, "loading.scan.tmdb");
    }

    public static String loadingCancel(AppLanguage language) {
        return t(language, "loading.cancel");
    }

    public static String loadingCancelHint(AppLanguage language) {
        return t(language, "loading.cancel.hint");
    }

    public static String loadingScanCancelled(AppLanguage language) {
        return t(language, "loading.scan.cancelled");
    }

    public static String[] scanWorkflowSteps(AppLanguage language) {
        return t(language, "scan.workflow.steps").split("\\|");
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
    public static String scanColumnGroup(AppLanguage language) { return t(language, "scan.column.group"); }
    public static String scanColumnSeries(AppLanguage language) { return t(language, "scan.column.series"); }
    public static String scanColumnSeason(AppLanguage language) { return t(language, "scan.column.season"); }
    public static String scanColumnEpisode(AppLanguage language) { return t(language, "scan.column.episode"); }
    public static String scanColumnTitle(AppLanguage language) { return t(language, "scan.column.title"); }
    public static String scanColumnYear(AppLanguage language) { return t(language, "scan.column.year"); }
    public static String scanColumnExtension(AppLanguage language) { return t(language, "scan.column.extension"); }
    public static String scanColumnType(AppLanguage language) { return t(language, "scan.column.type"); }
    public static String scanColumnTmdb(AppLanguage language) { return t(language, "scan.column.tmdb"); }
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
    public static String scanStatusFilterTmdb(AppLanguage language) { return t(language, "scan.statusFilter.tmdb"); }
    public static String scanStatusFilterConflicts(AppLanguage language) { return t(language, "scan.statusFilter.conflicts"); }
    public static String scanStatusFilterIgnored(AppLanguage language) { return t(language, "scan.statusFilter.ignored"); }
    public static String scanStatusFilterAlerts(AppLanguage language) { return t(language, "scan.statusFilter.alerts"); }

    public static String scanMediaTypeSeries(AppLanguage language) { return t(language, "scan.media.type.series"); }
    public static String scanMediaTypeMovie(AppLanguage language) { return t(language, "scan.media.type.movie"); }
    public static String scanMediaTypeUnknown(AppLanguage language) { return t(language, "scan.media.type.unknown"); }
    public static String scanMediaTypeIgnored(AppLanguage language) { return t(language, "scan.media.type.ignored"); }

    public static String scanRowStatusOk(AppLanguage language) { return t(language, "scan.row.status.ok"); }
    public static String scanRowStatusReview(AppLanguage language) { return t(language, "scan.row.status.review"); }
    public static String scanRowStatusLowConfidence(AppLanguage language) { return t(language, "scan.row.status.lowConfidence"); }
    public static String scanRowStatusTmdb(AppLanguage language) { return t(language, "scan.row.status.tmdb"); }
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

    public static String scanContextIgnore(AppLanguage language) { return t(language, "scan.context.ignore"); }
    public static String scanContextStopIgnoring(AppLanguage language) { return t(language, "scan.context.stopIgnoring"); }
    public static String scanContextResetMatch(AppLanguage language) { return t(language, "scan.context.resetMatch"); }
    public static String scanContextCopyPath(AppLanguage language) { return t(language, "scan.context.copyPath"); }
    public static String scanContextOpenFolder(AppLanguage language) { return t(language, "scan.context.openFolder"); }
    public static String scanContextOpenFile(AppLanguage language) { return t(language, "scan.context.openFile"); }

    public static String filePropertiesAction(AppLanguage language) { return t(language, "file.properties.action"); }
    public static String filePropertiesTitle(AppLanguage language) { return t(language, "file.properties.title"); }
    public static String filePropertiesSectionFile(AppLanguage language) { return t(language, "file.properties.section.file"); }
    public static String filePropertiesSectionDetection(AppLanguage language) { return t(language, "file.properties.section.detection"); }
    public static String filePropertiesSectionTmdb(AppLanguage language) { return t(language, "file.properties.section.tmdb"); }
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
    public static String filePropertiesTmdbTitle(AppLanguage language) { return t(language, "file.properties.tmdbTitle"); }
    public static String filePropertiesTmdbType(AppLanguage language) { return t(language, "file.properties.tmdbType"); }
    public static String filePropertiesYear(AppLanguage language) { return t(language, "file.properties.year"); }
    public static String filePropertiesTmdbId(AppLanguage language) { return t(language, "file.properties.tmdbId"); }
    public static String filePropertiesOrder(AppLanguage language) { return t(language, "file.properties.order"); }
    public static String filePropertiesMediaUnavailable(AppLanguage language) { return t(language, "file.properties.mediaUnavailable"); }
    public static String filePropertiesClose(AppLanguage language) { return t(language, "file.properties.close"); }
    public static String filePropertiesCopyPath(AppLanguage language) { return t(language, "file.properties.copyPath"); }
    public static String filePropertiesPathCopied(AppLanguage language) { return t(language, "file.properties.pathCopied"); }

    /* ---- Detail panel ----------------------------------------------- */

    public static String detailSectionSource(AppLanguage language) { return t(language, "detail.section.source"); }
    public static String detailSectionDetection(AppLanguage language) { return t(language, "detail.section.detection"); }
    public static String detailSectionTmdb(AppLanguage language) { return t(language, "detail.section.tmdb"); }
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

    public static String detailApplyToSelection(AppLanguage language) { return t(language, "detail.apply.toSelection"); }
    public static String detailResetButton(AppLanguage language) { return t(language, "detail.reset.button"); }
    public static String tmdbSearchForMatch(AppLanguage language) { return t(language, "tmdb.searchForMatch"); }
    public static String tmdbChooseMatch(AppLanguage language) { return t(language, "tmdb.chooseMatch"); }
    public static String tmdbSearch(AppLanguage language) { return t(language, "tmdb.search"); }
    public static String tmdbSearchPlaceholder(AppLanguage language) { return t(language, "tmdb.searchPlaceholder"); }
    public static String tmdbSearchYearPlaceholder(AppLanguage language) { return t(language, "tmdb.searchYearPlaceholder"); }
    public static String tmdbSearchIdPlaceholder(AppLanguage language) { return t(language, "tmdb.searchIdPlaceholder"); }
    public static String tmdbSearchCriteriaRequired(AppLanguage language) { return t(language, "tmdb.searchCriteriaRequired"); }
    public static String tmdbSearchInvalidYear(AppLanguage language) { return t(language, "tmdb.searchInvalidYear"); }
    public static String tmdbNoResults(AppLanguage language) { return t(language, "tmdb.noResults"); }
    public static String tmdbNoResultsHint(AppLanguage language) { return t(language, "tmdb.noResultsHint"); }
    public static String tmdbInitialSearchHint(AppLanguage language) { return t(language, "tmdb.initialSearchHint"); }
    public static String tmdbNoMatchSelected(AppLanguage language) { return t(language, "tmdb.noMatchSelected"); }
    public static String tmdbResolveNow(AppLanguage language) { return t(language, "tmdb.resolveNow"); }
    public static String tmdbIgnore(AppLanguage language) { return t(language, "tmdb.ignore"); }
    public static String tmdbSelect(AppLanguage language) { return t(language, "tmdb.select"); }
    public static String tmdbApply(AppLanguage language) { return t(language, "tmdb.apply"); }
    public static String tmdbNoFileSelected(AppLanguage language) { return t(language, "tmdb.noFileSelected"); }
    public static String tmdbNoResultSelected(AppLanguage language) { return t(language, "tmdb.noResultSelected"); }
    public static String tmdbNoDescription(AppLanguage language) { return t(language, "tmdb.noDescription"); }
    public static String tmdbSearching(AppLanguage language) { return t(language, "tmdb.searching"); }
    public static String tmdbLoadingResults(AppLanguage language) { return t(language, "tmdb.loadingResults"); }
    public static String tmdbSeries(AppLanguage language) { return t(language, "tmdb.series"); }
    public static String tmdbMovie(AppLanguage language) { return t(language, "tmdb.movie"); }
    public static String tmdbIdLabel(AppLanguage language) { return t(language, "tmdb.id"); }
    public static String tmdbClose(AppLanguage language) { return t(language, "tmdb.close"); }

    public static String tmdbManualRequired(AppLanguage language, int count) {
        return t(language, "tmdb.manualRequired").replace("{0}", Integer.toString(count));
    }

    public static String tmdbMatchWillApplyTo(AppLanguage language, int count) {
        return t(language, "tmdb.matchWillApplyTo").replace("{count}", Integer.toString(count));
    }

    public static String tmdbFilesUpdated(AppLanguage language, int count) {
        return t(language, "tmdb.filesUpdated").replace("{count}", Integer.toString(count));
    }
    public static String tmdbFirstEpisode(AppLanguage language) { return t(language, "tmdb.firstEpisode"); }
    public static String tmdbFirstEpisodePlaceholder(AppLanguage language) { return t(language, "tmdb.firstEpisodePlaceholder"); }
    public static String tmdbApplySequence(AppLanguage language) { return t(language, "tmdb.applySequence"); }
    public static String tmdbEpisodeSeason(AppLanguage language, int season) {
        return t(language, "tmdb.episodeSeason").replace("{season}", String.format("%02d", season));
    }
    public static String tmdbEpisodeSpecials(AppLanguage language) { return t(language, "tmdb.episodeSpecials"); }
    public static String tmdbEpisodeAbsoluteRange(AppLanguage language, int start, int end) {
        return t(language, "tmdb.episodeAbsoluteRange")
                .replace("{start}", String.format("%03d", start))
                .replace("{end}", String.format("%03d", end));
    }
    public static String tmdbSequenceIncomplete(AppLanguage language, int applied, int requested) {
        return t(language, "tmdb.sequenceIncomplete")
                .replace("{applied}", Integer.toString(applied))
                .replace("{requested}", Integer.toString(requested));
    }
    public static String tmdbMatchSuggested(AppLanguage language) { return t(language, "tmdb.matchSuggested"); }
    public static String tmdbAutomaticMatch(AppLanguage language) { return t(language, "tmdb.automaticMatch"); }
    public static String tmdbSuggestionNeedsValidation(AppLanguage language) { return t(language, "tmdb.suggestionNeedsValidation"); }

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
    public static String historyEventScanCompleted(AppLanguage language) { return t(language, "history.event.scanCompleted"); }
    public static String historyEventScanFailed(AppLanguage language) { return t(language, "history.event.scanFailed"); }
    public static String historyEventExecutionCompleted(AppLanguage language) { return t(language, "history.event.executionCompleted"); }
    public static String historyEventExecutionFailed(AppLanguage language) { return t(language, "history.event.executionFailed"); }
    public static String historyDetailSectionSummary(AppLanguage language) { return t(language, "history.detail.section.summary"); }
    public static String historyDetailSectionMetrics(AppLanguage language) { return t(language, "history.detail.section.metrics"); }

    /* ---- Settings page ---------------------------------------------- */

    public static String settingsHeading(AppLanguage language) { return t(language, "settings.heading"); }
    public static String settingsPageSubtitle(AppLanguage language) { return t(language, "settings.page.subtitle"); }
    public static String preferencesSectionTitle(AppLanguage language) { return t(language, "preferences.section.title"); }
    public static String preferencesSectionDescription(AppLanguage language) { return t(language, "preferences.section.description"); }
    public static String themeLabel(AppLanguage language) { return t(language, "preferences.theme.label"); }
    public static String themePreference(AppLanguage language, ThemePreference preference) {
        return t(language, "preferences.theme." + preference.name().toLowerCase(java.util.Locale.ROOT));
    }
    public static String tmdbSettingsSectionTitle(AppLanguage language) { return t(language, "tmdb.settings.section.title"); }
    public static String tmdbSettingsSectionDescription(AppLanguage language) { return t(language, "tmdb.settings.section.description"); }
    public static String tmdbSettingsActive(AppLanguage language) { return t(language, "tmdb.settings.active"); }
    public static String tmdbSettingsInactive(AppLanguage language) { return t(language, "tmdb.settings.inactive"); }
    public static String tmdbSettingsUnavailable(AppLanguage language) { return t(language, "tmdb.settings.unavailable"); }
    public static String tmdbSettingsAttribution(AppLanguage language) { return t(language, "tmdb.settings.attribution"); }
    public static String tmdbSettingsAttributionLink(AppLanguage language) { return t(language, "tmdb.settings.attributionLink"); }
    public static String tmdbSettingsAttributionLinkAccessible(AppLanguage language) { return t(language, "tmdb.settings.attributionLinkAccessible"); }

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
    public static String historyRollbackButton(AppLanguage language) { return t(language, "history.rollback.button"); }
    public static String historyRollbackUnavailable(AppLanguage language) { return t(language, "history.rollback.unavailable"); }
    public static String historyRollbackTitle(AppLanguage language) { return t(language, "history.rollback.title"); }
    public static String historyRollbackConfirmation(AppLanguage language) { return t(language, "history.rollback.confirmation"); }
    public static String historyRollbackColumnCurrent(AppLanguage language) { return t(language, "history.rollback.column.current"); }
    public static String historyRollbackColumnOriginal(AppLanguage language) { return t(language, "history.rollback.column.original"); }
    public static String historyRollbackConfirmButton(AppLanguage language) { return t(language, "history.rollback.confirm.button"); }
    public static String historyRollbackCompleted(AppLanguage language) { return t(language, "history.rollback.completed"); }
    public static String historyRollbackFailed(AppLanguage language) { return t(language, "history.rollback.failed"); }
    public static String rollbackReviewTitle(AppLanguage language) { return t(language, "rollback.review.title"); }
    public static String rollbackExecutionTitle(AppLanguage language) { return t(language, "rollback.execution.title"); }
    public static String rollbackBack(AppLanguage language) { return t(language, "rollback.back"); }
    public static String rollbackMetricFiles(AppLanguage language) { return t(language, "rollback.metric.files"); }
    public static String rollbackMetricRestored(AppLanguage language) { return t(language, "rollback.metric.restored"); }
    public static String rollbackReady(AppLanguage language) { return t(language, "rollback.ready"); }
    public static String rollbackNotice(AppLanguage language) { return t(language, "rollback.notice"); }
    public static String rollbackProgressLabel(AppLanguage language) { return t(language, "rollback.progress.label"); }
    public static String rollbackRecapLabel(AppLanguage language) { return t(language, "rollback.recap.label"); }
    public static String rollbackRecapDisk(AppLanguage language) { return t(language, "rollback.recap.disk"); }
    public static String rollbackEmpty(AppLanguage language) { return t(language, "rollback.empty"); }
    public static String rollbackCompletedCount(AppLanguage language, int count) {
        return t(language, "rollback.completed.count").replace("{count}", Integer.toString(count));
    }
    public static String historyEventRollbackCompleted(AppLanguage language) { return t(language, "history.event.rollbackCompleted"); }
    public static String historyEventRollbackFailed(AppLanguage language) { return t(language, "history.event.rollbackFailed"); }

    /* ---- Batch TMDB ------------------------------------------------- */

    public static String scanBatchTmdbCandidatePlaceholder(AppLanguage language) { return t(language, "scan.batch.tmdb.candidate.placeholder"); }
    public static String scanBatchTmdbOrderPlaceholder(AppLanguage language) { return t(language, "scan.batch.tmdb.order.placeholder"); }
    public static String scanBatchTmdbOrderAired(AppLanguage language) { return t(language, "scan.batch.tmdb.order.aired"); }
    public static String scanBatchTmdbOrderDvd(AppLanguage language) { return t(language, "scan.batch.tmdb.order.dvd"); }
    public static String scanBatchTmdbOrderAbsolute(AppLanguage language) { return t(language, "scan.batch.tmdb.order.absolute"); }
    public static String scanBatchTmdbOrderDigital(AppLanguage language) { return t(language, "scan.batch.tmdb.order.digital"); }
    public static String scanBatchTmdbOrderStoryArc(AppLanguage language) { return t(language, "scan.batch.tmdb.order.storyArc"); }
    public static String scanBatchTmdbOrderProduction(AppLanguage language) { return t(language, "scan.batch.tmdb.order.production"); }
    public static String scanBatchTmdbOrderTv(AppLanguage language) { return t(language, "scan.batch.tmdb.order.tv"); }
    public static String tmdbOpenExternal(AppLanguage language) { return t(language, "tmdb.openExternal"); }
    public static String tmdbDefaultEpisodeOrder(AppLanguage language) { return t(language, "tmdb.episodeGroup.default"); }
    public static String tmdbEpisodeGroupOption(
            AppLanguage language, String name, String type, int groups, int episodes) {
        return t(language, "tmdb.episodeGroup.option")
                .replace("{name}", name)
                .replace("{type}", type)
                .replace("{groups}", Integer.toString(groups))
                .replace("{episodes}", Integer.toString(episodes));
    }
    public static String scanBatchTmdbStatusFinal(AppLanguage language) { return t(language, "scan.batch.tmdb.status.final"); }
    public static String scanBatchTmdbStatusUnresolved(AppLanguage language) { return t(language, "scan.batch.tmdb.status.unresolved"); }

    public static String prereqOverlayTitle(AppLanguage language) { return t(language, "prereq.overlay.title"); }
    public static String prereqOverlaySubtitle(AppLanguage language) { return t(language, "prereq.overlay.subtitle"); }
    public static String prereqMissingWorkspace(AppLanguage language) { return t(language, "prereq.missing.workspace"); }
    public static String prereqOpenSettings(AppLanguage language) { return t(language, "prereq.openSettings"); }

    /* ---- Exact plan review (Epic 6) ---------------------------------- */

    public static String primaryActionReviewPlan(AppLanguage language) { return t(language, "primary.action.reviewPlan"); }
    public static String primaryActionExecute(AppLanguage language) { return t(language, "primary.action.execute"); }
    public static String planDialogTitle(AppLanguage language) { return t(language, "plan.dialog.title"); }
    public static String planDialogSubtitle(AppLanguage language) { return t(language, "plan.dialog.subtitle"); }
    public static String planColumnSource(AppLanguage language) { return t(language, "plan.column.source"); }
    public static String planColumnSize(AppLanguage language) { return t(language, "plan.column.size"); }
    public static String planColumnComparedCopy(AppLanguage language) { return t(language, "plan.column.comparedCopy"); }
    public static String planColumnDestination(AppLanguage language) { return t(language, "plan.column.destination"); }
    public static String planColumnStatus(AppLanguage language) { return t(language, "plan.column.status"); }
    public static String planStatusExecutable(AppLanguage language) { return t(language, "plan.status.executable"); }
    public static String planStatusInPlace(AppLanguage language) { return t(language, "plan.status.inPlace"); }
    public static String planStatusExcluded(AppLanguage language) { return t(language, "plan.status.excluded"); }
    public static String planStatusConflict(AppLanguage language) { return t(language, "plan.status.conflict"); }
    public static String planStatusDelete(AppLanguage language) { return t(language, "plan.status.delete"); }
    public static String planStatusDeleteDetail(AppLanguage language) { return t(language, "plan.status.delete.detail"); }
    public static String planSummaryToMove(AppLanguage language) { return t(language, "plan.summary.toMove"); }
    public static String planSummaryToDelete(AppLanguage language) { return t(language, "plan.summary.toDelete"); }
    public static String planSummaryInPlace(AppLanguage language) { return t(language, "plan.summary.inPlace"); }
    public static String planSummaryExcluded(AppLanguage language) { return t(language, "plan.summary.excluded"); }
    public static String planSummaryConflicts(AppLanguage language) { return t(language, "plan.summary.conflicts"); }
    public static String planSummaryFoldersToCreate(AppLanguage language) { return t(language, "plan.summary.foldersToCreate"); }
    public static String planSummaryFoldersReused(AppLanguage language) { return t(language, "plan.summary.foldersReused"); }
    public static String planStatusReplace(AppLanguage language) { return t(language, "plan.status.replace"); }
    public static String planStatusReplaceDetail(AppLanguage language) { return t(language, "plan.status.replace.detail"); }
    public static String planValidate(AppLanguage language) { return t(language, "plan.validate"); }
    public static String planBack(AppLanguage language) { return t(language, "plan.back"); }
    public static String planBlocked(AppLanguage language) { return t(language, "plan.blocked"); }
    public static String planReady(AppLanguage language) { return t(language, "plan.ready"); }
    public static String planEmpty(AppLanguage language) { return t(language, "plan.empty"); }
    public static String planNotice(AppLanguage language) { return t(language, "plan.notice"); }
    public static String planNoticeSortingFolders(AppLanguage language) {
        return t(language, "plan.notice.sortingFolders");
    }

    /** The extra line under a plan that will remove files, naming how many. */
    public static String planNoticeDelete(AppLanguage language, int count) {
        return t(language, "plan.notice.delete").replace("{0}", Integer.toString(count));
    }

    public static String planFailed(AppLanguage language, String detail) {
        return t(language, "plan.failed") + " " + (detail == null || detail.isBlank() ? EMPTY : detail);
    }

    /** Localized explanation of a plan conflict; falls back to the placeholder for unknown codes. */
    public static String planConflict(AppLanguage language, String conflictTypeName) {
        return switch (conflictTypeName) {
            case "DUPLICATE_DESTINATION" -> t(language, "plan.conflict.duplicateDestination");
            case "DUPLICATE_MEDIA" -> t(language, "plan.conflict.duplicateMedia");
            case "MEDIA_ALREADY_IN_LIBRARY" -> t(language, "plan.conflict.mediaAlreadyInLibrary");
            case "DESTINATION_FILE_EXISTS" -> t(language, "plan.conflict.destinationExists");
            case "SOURCE_OUTSIDE_WORKSPACE" -> t(language, "plan.conflict.sourceOutside");
            case "DESTINATION_OUTSIDE_WORKSPACE" -> t(language, "plan.conflict.destinationOutside");
            case "PATH_TOO_LONG" -> t(language, "plan.conflict.pathTooLong");
            case "DESTINATION_FOLDER_BLOCKED" -> t(language, "plan.conflict.folderBlocked");
            default -> EMPTY;
        };
    }

    /** Localized explanation of why an item is excluded from execution. */
    public static String planExclusion(AppLanguage language, String exclusionReasonName) {
        return switch (exclusionReasonName) {
            case "IGNORED" -> t(language, "plan.excluded.ignored");
            case "UNSUPPORTED" -> t(language, "plan.excluded.unsupported");
            case "DUPLICATE" -> t(language, "plan.excluded.duplicate");
            case "UNASSIGNED" -> t(language, "plan.excluded.unassigned");
            case "AMBIGUOUS" -> t(language, "plan.excluded.ambiguous");
            case "CONFLICT_SKIPPED" -> t(language, "plan.excluded.conflictSkipped");
            default -> EMPTY;
        };
    }

    /* ---- Conflict resolution (Story 6.4) ------------------------------ */

    public static String conflictDialogSubtitle(AppLanguage language) { return t(language, "conflict.dialog.subtitle"); }
    public static String conflictBatchMostRecent(AppLanguage language) { return t(language, "conflict.batch.mostRecent"); }
    public static String conflictBatchOldest(AppLanguage language) { return t(language, "conflict.batch.oldest"); }
    public static String conflictBatchReplace(AppLanguage language) { return t(language, "conflict.batch.replace"); }
    public static String conflictBatchKeep(AppLanguage language) { return t(language, "conflict.batch.keep"); }
    public static String conflictBatchDelete(AppLanguage language) { return t(language, "conflict.batch.delete"); }
    public static String conflictColumnDates(AppLanguage language) { return t(language, "conflict.column.dates"); }
    public static String conflictColumnDecision(AppLanguage language) { return t(language, "conflict.column.decision"); }
    public static String conflictOptionReplace(AppLanguage language) { return t(language, "conflict.option.replace"); }
    public static String conflictOptionKeepThis(AppLanguage language) { return t(language, "conflict.option.keepThis"); }
    public static String conflictOptionKeepExisting(AppLanguage language) { return t(language, "conflict.option.keepExisting"); }
    public static String conflictOptionIgnore(AppLanguage language) { return t(language, "conflict.option.ignore"); }
    public static String conflictOptionDeleteSource(AppLanguage language) { return t(language, "conflict.option.deleteSource"); }
    public static String conflictOptionDrop(AppLanguage language) { return t(language, "conflict.option.drop"); }
    public static String conflictApply(AppLanguage language) { return t(language, "conflict.apply"); }
    public static String conflictStillBlocked(AppLanguage language) { return t(language, "conflict.stillBlocked"); }

    /** "{n} of {total} files selected", driving what the batch actions reach. */
    public static String conflictSelectionCount(AppLanguage language, int selected, int total) {
        return t(language, "conflict.selection.count")
                .replace("{0}", Integer.toString(selected))
                .replace("{1}", Integer.toString(total));
    }

    /** "Source: {date} • Destination: {date}", the basis of the most-recent-wins rule. */
    public static String conflictDatesLabel(AppLanguage language, String sourceDate, String destinationDate) {
        return t(language, "conflict.dates.source") + " " + sourceDate
                + "  •  " + t(language, "conflict.dates.destination") + " " + destinationDate;
    }

    /* ---- Execution (Epic 7) ------------------------------------------ */

    public static String execDialogTitle(AppLanguage language) { return t(language, "exec.dialog.title"); }
    public static String execProgressLabel(AppLanguage language) { return t(language, "exec.progress.label"); }
    public static String execFieldSource(AppLanguage language) { return t(language, "exec.field.source"); }
    public static String execFieldDestination(AppLanguage language) { return t(language, "exec.field.destination"); }
    public static String execFailureLog(AppLanguage language) { return t(language, "exec.failure.log"); }
    public static String execRecapSectionDisk(AppLanguage language) { return t(language, "exec.recap.section.disk"); }

    public static String execRecapSectionUntouched(AppLanguage language) {
        return t(language, "exec.recap.section.untouched");
    }

    public static String execFailureTitle(AppLanguage language) { return t(language, "exec.failure.title"); }
    public static String execFailureRetry(AppLanguage language) { return t(language, "exec.failure.retry"); }
    public static String execFailureContinue(AppLanguage language) { return t(language, "exec.failure.continue"); }
    public static String execFailureAbort(AppLanguage language) { return t(language, "exec.failure.abort"); }
    public static String execRecapTitle(AppLanguage language) { return t(language, "exec.recap.title"); }
    public static String execRecapMoved(AppLanguage language) { return t(language, "exec.recap.moved"); }
    public static String execRecapRenamed(AppLanguage language) { return t(language, "exec.recap.renamed"); }
    public static String execRecapDeleted(AppLanguage language) { return t(language, "exec.recap.deleted"); }
    public static String execRecapFailed(AppLanguage language) { return t(language, "exec.recap.failed"); }
    public static String execRecapSkipped(AppLanguage language) { return t(language, "exec.recap.skipped"); }
    public static String execRecapUntouched(AppLanguage language) { return t(language, "exec.recap.untouched"); }
    public static String execRecapIgnored(AppLanguage language) { return t(language, "exec.recap.ignored"); }
    public static String execRecapUnsupported(AppLanguage language) { return t(language, "exec.recap.unsupported"); }
    public static String execRecapDuplicates(AppLanguage language) { return t(language, "exec.recap.duplicates"); }
    public static String execRecapUnassigned(AppLanguage language) { return t(language, "exec.recap.unassigned"); }
    public static String execRecapFoldersDeleted(AppLanguage language) { return t(language, "exec.recap.foldersDeleted"); }
    public static String execRecapFoldersTagged(AppLanguage language) { return t(language, "exec.recap.foldersTagged"); }
    public static String execRecapComplete(AppLanguage language) { return t(language, "exec.recap.complete"); }
    public static String execRecapPartial(AppLanguage language) { return t(language, "exec.recap.partial"); }
    public static String execRecapNone(AppLanguage language) { return t(language, "exec.recap.none"); }
    public static String execRecapAborted(AppLanguage language) { return t(language, "exec.recap.aborted"); }
    public static String execRecapDiagnostics(AppLanguage language) { return t(language, "exec.recap.diagnostics"); }
    public static String execRecapNextActions(AppLanguage language) { return t(language, "exec.recap.nextActions"); }
    public static String execRecapClose(AppLanguage language) { return t(language, "exec.recap.close"); }
    public static String execInterruptedTitle(AppLanguage language) { return t(language, "exec.interrupted.title"); }
    public static String execInterruptedMessage(AppLanguage language) { return t(language, "exec.interrupted.message"); }
    public static String execInterruptedProgress(AppLanguage language) { return t(language, "exec.interrupted.progress"); }

    public static String scanGroupMixedTitle(AppLanguage language) { return t(language, "scan.group.mixed.title"); }
    public static String scanGroupMixedMessage(AppLanguage language) { return t(language, "scan.group.mixed.message"); }
    public static String scanGroupMixedLimit(AppLanguage language) { return t(language, "scan.group.mixed.limit"); }
    public static String scanGroupMixedApplyAll(AppLanguage language) { return t(language, "scan.group.mixed.applyAll"); }
    public static String scanGroupMixedCancel(AppLanguage language) { return t(language, "scan.group.mixed.cancel"); }
    public static String scanGroupFiles(AppLanguage language) { return t(language, "scan.group.files"); }
    public static String scanGroupFile(AppLanguage language) { return t(language, "scan.group.file"); }

    public static String scanIdentitiesTitle(AppLanguage language) { return t(language, "scan.identities.title"); }
    public static String scanIdentitiesMessage(AppLanguage language) { return t(language, "scan.identities.message"); }
    public static String scanIdentitiesUnresolved(AppLanguage language) { return t(language, "scan.identities.unresolved"); }
    public static String scanIdentitiesSuggested(AppLanguage language) { return t(language, "scan.identities.suggested"); }
    public static String scanIdentitiesAutomatic(AppLanguage language) { return t(language, "scan.identities.automatic"); }
    public static String scanIdentitiesManual(AppLanguage language) { return t(language, "scan.identities.manual"); }
    public static String scanIdentitiesFix(AppLanguage language) { return t(language, "scan.identities.fix"); }
    public static String scanIdentitiesConfirm(AppLanguage language) { return t(language, "scan.identities.confirm"); }
    public static String scanIdentitiesClose(AppLanguage language) { return t(language, "scan.identities.close"); }
    public static String scanIdentitiesUnresolvedWarning(AppLanguage language) {
        return t(language, "scan.identities.unresolvedWarning");
    }
}

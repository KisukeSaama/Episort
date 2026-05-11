package com.episort.ui.settings;

import com.episort.ai.AiModelCatalog;
import com.episort.ai.AiModelEntry;
import com.episort.ai.AiModelLibrary;
import com.episort.ai.embedded.EmbeddedLlamaRuntime;
import com.episort.ai.embedded.LlamaServerClient;
import com.episort.ui.AppLanguage;
import com.episort.ui.UiText;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * Compact, card-style settings section listing the catalog of local AI
 * models. Each row is a clickable card showing identity, status, and an
 * inline icon-button action (download or delete). Selection is gated on the
 * model being installed locally.
 *
 * <p>Selecting a different model persists the choice and hot-swaps the
 * running llama-server via {@link EmbeddedLlamaRuntime#restart}; if no server
 * is running yet, the selection is honored on the next launch.
 */
public final class AiModelsSection {

    // Lucide-style icon paths (24x24 viewport).
    private static final String ICON_DOWNLOAD =
            "M12 3v12m0 0l-4-4m4 4l4-4M5 21h14";
    private static final String ICON_TRASH =
            "M3 6h18M8 6V4a2 2 0 012-2h4a2 2 0 012 2v2m3 0v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6h14z";
    private static final String ICON_CHECK =
            "M5 12l5 5L20 7";

    private static final long HEALTH_GREEN_MS = 8_000;
    private static final long HEALTH_ORANGE_MS = 30_000;

    private final AiModelLibrary library;
    private final EmbeddedLlamaRuntime runtime;
    private final Runnable onStateChanged;
    private final java.util.function.Consumer<Boolean> onEnabledChange;
    private final boolean initialEnabled;
    private final VBox root;
    private final Label sectionTitle;
    private final Label sectionDescription;
    private final Label emptyHint;
    private final Switch enabledToggle;
    private final Label restartHint;
    private final Label disabledNote;
    private final VBox bodyBox;
    private final Button healthButton;
    private final Circle healthDot;
    private final Label healthLabel;
    private final HBox healthRow;
    private final Label summaryActiveLabel;
    private final Label summaryActiveValue;
    private final Label summaryDownloadLabel;
    private final Label summaryDownloadValue;
    private final Label summaryPrivacyLabel;
    private final Label summaryPrivacyValue;
    private final Label summaryStatusLabel;
    private final Label summaryStatusValue;
    private final Map<String, ModelRow> rows = new LinkedHashMap<>();

    private AppLanguage currentLanguage = AppLanguage.FRENCH;

    public AiModelsSection(AppLanguage initialLanguage, AiModelLibrary library) {
        this(initialLanguage, library, null, null, true, null);
    }

    public AiModelsSection(
            AppLanguage initialLanguage,
            AiModelLibrary library,
            EmbeddedLlamaRuntime runtime) {
        this(initialLanguage, library, runtime, null, true, null);
    }

    public AiModelsSection(
            AppLanguage initialLanguage,
            AiModelLibrary library,
            EmbeddedLlamaRuntime runtime,
            Runnable onStateChanged) {
        this(initialLanguage, library, runtime, onStateChanged, true, null);
    }

    public AiModelsSection(
            AppLanguage initialLanguage,
            AiModelLibrary library,
            EmbeddedLlamaRuntime runtime,
            Runnable onStateChanged,
            boolean initialEnabled,
            java.util.function.Consumer<Boolean> onEnabledChange) {
        this.library = library;
        this.runtime = runtime;
        this.onStateChanged = onStateChanged == null ? () -> {} : onStateChanged;
        this.initialEnabled = initialEnabled;
        this.onEnabledChange = onEnabledChange == null ? v -> {} : onEnabledChange;

        sectionTitle = new Label();
        sectionTitle.getStyleClass().add("settings-section-title");

        sectionDescription = new Label();
        sectionDescription.getStyleClass().add("settings-section-description");
        sectionDescription.setWrapText(true);

        healthButton = new Button();
        healthButton.getStyleClass().add("ghost");
        healthButton.setOnAction(e -> runHealthTest());

        healthDot = new Circle(5, javafx.scene.paint.Color.web("#3a3f4b"));
        healthDot.setStroke(javafx.scene.paint.Color.web("#1a1d24"));
        healthDot.setStrokeWidth(1);
        healthLabel = new Label("");
        healthLabel.getStyleClass().add("ai-models-empty-hint");
        healthLabel.setWrapText(true);
        healthRow = new HBox(8, healthDot, healthLabel);
        healthRow.setAlignment(Pos.CENTER_LEFT);
        healthRow.setVisible(false);
        healthRow.setManaged(false);

        enabledToggle = new Switch(initialEnabled);
        enabledToggle.setOnToggle(newValue -> promptRestartFor(newValue));

        restartHint = new Label();
        restartHint.getStyleClass().add("ai-enabled-restart-hint");
        restartHint.setWrapText(true);
        restartHint.setVisible(false);
        restartHint.setManaged(false);

        disabledNote = new Label();
        disabledNote.getStyleClass().add("ai-models-empty-hint");
        disabledNote.setWrapText(true);
        disabledNote.setVisible(false);
        disabledNote.setManaged(false);

        HBox headerActions = new HBox(8);
        headerActions.setAlignment(Pos.CENTER_RIGHT);
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(10, sectionTitle, headerSpacer, healthButton, enabledToggle);
        header.setAlignment(Pos.CENTER_LEFT);

        summaryActiveLabel = summaryLabel();
        summaryActiveValue = summaryValue(true);
        summaryDownloadLabel = summaryLabel();
        summaryDownloadValue = summaryValue(false);
        summaryPrivacyLabel = summaryLabel();
        summaryPrivacyValue = summaryValue(false);
        summaryStatusLabel = summaryLabel();
        summaryStatusValue = summaryValue(false);

        GridPane summaryGrid = new GridPane();
        summaryGrid.setHgap(24);
        summaryGrid.setVgap(8);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(50);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(50);
        summaryGrid.getColumnConstraints().addAll(col1, col2);
        summaryGrid.add(summaryCell(summaryActiveLabel, summaryActiveValue), 0, 0);
        summaryGrid.add(summaryCell(summaryDownloadLabel, summaryDownloadValue), 1, 0);
        summaryGrid.add(summaryCell(summaryStatusLabel, summaryStatusValue), 0, 1);
        summaryGrid.add(summaryCell(summaryPrivacyLabel, summaryPrivacyValue), 1, 1);
        summaryGrid.getStyleClass().add("ai-models-summary");

        VBox rowsBox = new VBox(8);
        rowsBox.getStyleClass().add("ai-models-list");
        for (AiModelEntry entry : AiModelCatalog.entries()) {
            ModelRow row = new ModelRow(entry);
            rows.put(entry.id(), row);
            rowsBox.getChildren().add(row.root);
        }

        emptyHint = new Label();
        emptyHint.getStyleClass().add("ai-models-empty-hint");
        emptyHint.setWrapText(true);
        emptyHint.setVisible(false);
        emptyHint.setManaged(false);

        bodyBox = new VBox(10, sectionDescription, summaryGrid, divider(), rowsBox, emptyHint, healthRow);

        root = new VBox(10, header, restartHint, disabledNote, bodyBox);
        root.getStyleClass().add("settings-section");

        applyLanguage(initialLanguage);
        refresh();
        refreshEnabledState();
    }

    private void promptRestartFor(boolean newValue) {
        refreshEnabledState();
        Window owner = root.getScene() == null ? null : root.getScene().getWindow();
        CustomDialog.confirm(
                owner,
                UiText.aiEnabledRestartTitle(currentLanguage),
                UiText.aiEnabledRestartMessage(currentLanguage),
                UiText.cancelButton(currentLanguage),
                UiText.aiEnabledRestartConfirm(currentLanguage),
                /* destructive */ false,
                () -> {
                    onEnabledChange.accept(newValue);
                    if (!attemptRestart()) {
                        restartHintVisible(true);
                    }
                },
                () -> {
                    enabledToggle.setOnSilently(!newValue);
                    refreshEnabledState();
                });
    }

    private static boolean attemptRestart() {
        try {
            ProcessHandle current = ProcessHandle.current();
            Optional<String> cmd = current.info().command();
            if (cmd.isEmpty()) return false;
            java.util.List<String> command = new java.util.ArrayList<>();
            command.add(cmd.get());
            String[] args = current.info().arguments().orElse(new String[0]);
            for (String a : args) command.add(a);
            new ProcessBuilder(command).inheritIO().start();
            Platform.runLater(() -> {
                Platform.exit();
                // Belt-and-braces: some launchers keep non-daemon threads alive.
                new Thread(() -> {
                    try { Thread.sleep(400); } catch (InterruptedException ignored) {}
                    System.exit(0);
                }, "episort-restart-exit").start();
            });
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private void refreshEnabledState() {
        boolean on = enabledToggle.isOn();
        bodyBox.setVisible(on);
        bodyBox.setManaged(on);
        disabledNote.setVisible(!on);
        disabledNote.setManaged(!on);
    }

    private void restartHintVisible(boolean visible) {
        restartHint.setVisible(visible);
        restartHint.setManaged(visible);
    }

    public Region root() {
        return root;
    }

    public void applyLanguage(AppLanguage language) {
        currentLanguage = language;
        sectionTitle.setText(UiText.aiModelsSectionTitle(language));
        sectionDescription.setText(UiText.aiModelsSectionDescription(language));
        emptyHint.setText(UiText.aiModelsEmpty(language));
        healthButton.setText(UiText.localAiTest(language));
        healthButton.setTooltip(new Tooltip(UiText.localAiTest(language)));
        healthButton.setAccessibleText(UiText.localAiTest(language));
        summaryActiveLabel.setText(UiText.aiModelsActiveLabel(language));
        summaryDownloadLabel.setText(UiText.aiModelsSummaryDownloadLabel(language));
        summaryDownloadValue.setText(UiText.aiModelsSummaryDownloadValue(language));
        summaryPrivacyLabel.setText(UiText.aiModelsSummaryPrivacyLabel(language));
        summaryPrivacyValue.setText(UiText.aiModelsSummaryPrivacyValue(language));
        summaryStatusLabel.setText(UiText.aiModelsSummaryStatusLabel(language));
        Tooltip.install(enabledToggle, new Tooltip(UiText.aiEnabledToggleDescription(language)));
        restartHint.setText(UiText.aiEnabledRestartHint(language));
        disabledNote.setText(UiText.aiEnabledDisabledNote(language));
        for (ModelRow row : rows.values()) {
            row.applyLanguage(language);
        }
        refreshSummary();
        refreshHealthButton();
    }

    public void refresh() {
        Optional<String> selected = library.selectedId();
        boolean anyInstalled = false;
        for (ModelRow row : rows.values()) {
            row.refresh(selected.orElse(null));
            if (library.isPresent(row.entry)) {
                anyInstalled = true;
            }
        }
        emptyHint.setVisible(!anyInstalled);
        emptyHint.setManaged(!anyInstalled);
        refreshSummary();
        refreshHealthButton();
    }

    private void refreshSummary() {
        Optional<String> selected = library.selectedId();
        String activeName = selected
                .flatMap(AiModelCatalog::findById)
                .map(AiModelEntry::displayName)
                .orElseGet(() -> UiText.aiModelsActiveNone(currentLanguage));
        summaryActiveValue.setText(activeName);

        boolean modelPresent = selected.isPresent();
        StringBuilder sb = new StringBuilder();
        sb.append(modelPresent
                ? UiText.localAiStatusModelPresent(currentLanguage)
                : UiText.localAiStatusModelMissing(currentLanguage));
        sb.append(" • ");
        if (runtime == null) {
            sb.append(UiText.localAiStatusRuntimeDown(currentLanguage));
        } else if (!runtime.runtimeBinariesAvailable()) {
            sb.append(UiText.localAiRuntimeMissing(currentLanguage));
        } else {
            sb.append(runtime.baseUri().isPresent()
                    ? UiText.localAiStatusRuntimeUp(currentLanguage)
                    : UiText.localAiStatusRuntimeDown(currentLanguage));
        }
        summaryStatusValue.setText(sb.toString());
    }

    private static Label summaryLabel() {
        Label l = new Label();
        l.getStyleClass().add("modal-info-label");
        return l;
    }

    private static Label summaryValue(boolean mono) {
        Label v = new Label();
        v.getStyleClass().add(mono ? "modal-info-value-mono" : "modal-info-value");
        v.setWrapText(true);
        return v;
    }

    private static VBox summaryCell(Label label, Label value) {
        VBox cell = new VBox(2, label, value);
        cell.getStyleClass().add("modal-info-row");
        return cell;
    }

    private void refreshHealthButton() {
        boolean canTest = runtime != null && runtime.baseUri().isPresent();
        boolean visible = runtime != null;
        healthButton.setVisible(visible);
        healthButton.setManaged(visible);
        healthButton.setDisable(!canTest);
        if (!canTest && runtime != null) {
            healthButton.setTooltip(new Tooltip(UiText.localAiTestUnavailable(currentLanguage)));
        }
    }

    /**
     * Restarts the embedded llama-server so it picks up the freshly-selected
     * model file. Runs off the FX thread because {@link EmbeddedLlamaRuntime#restart}
     * blocks for several seconds while llama-server reloads weights.
     */
    private void hotSwapTo(AiModelEntry entry) {
        if (runtime == null || !runtime.runtimeBinariesAvailable()) {
            // No runtime to drive at all — the new selection will be honored
            // on next launch (or never, if binaries are missing).
            return;
        }
        // restart() stops first then starts, so it also recovers from a
        // previously-failed start (e.g. a model whose chat template broke
        // llama-server). Clear any stale error banner before kicking off.
        hideHealth();
        healthButton.setDisable(true);
        showHealth("#f5a524", UiText.aiModelsActivating(currentLanguage, entry.displayName()));
        Thread worker = new Thread(() -> {
            try {
                runtime.restart(java.time.Duration.ofSeconds(60));
                Platform.runLater(() -> {
                    showHealth("#30a46c", UiText.aiModelsActivated(currentLanguage, entry.displayName()));
                    refreshSummary();
                    refreshHealthButton();
                    onStateChanged.run();
                });
            } catch (IOException | InterruptedException | RuntimeException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                Platform.runLater(() -> {
                    showHealth("#e5484d", UiText.aiModelsActivationFailed(currentLanguage,
                            ex.getMessage() == null ? "" : ex.getMessage()));
                    refreshSummary();
                    refreshHealthButton();
                    onStateChanged.run();
                });
            }
        }, "episort-model-hotswap-" + entry.id());
        worker.setDaemon(true);
        worker.start();
    }

    private void runHealthTest() {
        if (runtime == null || runtime.baseUri().isEmpty()) {
            showHealth("#e5484d", UiText.localAiTestUnavailable(currentLanguage));
            return;
        }
        healthButton.setDisable(true);
        showHealth("#3a3f4b", UiText.localAiTestRunning(currentLanguage));
        Thread worker = new Thread(() -> {
            long start = System.nanoTime();
            try {
                LlamaServerClient client = new LlamaServerClient(runtime.baseUri().orElseThrow());
                java.util.List<LlamaServerClient.ChatMessage> messages = java.util.List.of(
                        LlamaServerClient.ChatMessage.system(
                                "You are a health probe. Reply with exactly the two characters: OK\n"
                                        + "Nothing else. No punctuation. No newline."),
                        LlamaServerClient.ChatMessage.user("ping /no_think"));
                String reply = client.complete("health-test", messages, 16, null);
                long ms = (System.nanoTime() - start) / 1_000_000L;
                Platform.runLater(() -> {
                    if (reply == null || reply.isBlank()) {
                        showHealth("#e5484d",
                                UiText.localAiTestRed(currentLanguage, "empty reply"));
                    } else if (ms <= HEALTH_GREEN_MS) {
                        showHealth("#30a46c",
                                UiText.localAiTestGreen(currentLanguage, ms));
                    } else if (ms <= HEALTH_ORANGE_MS) {
                        showHealth("#f5a524",
                                UiText.localAiTestOrange(currentLanguage, ms));
                    } else {
                        showHealth("#e5484d",
                                UiText.localAiTestRed(currentLanguage, ms + " ms"));
                    }
                    refreshHealthButton();
                });
            } catch (RuntimeException ex) {
                Platform.runLater(() -> {
                    showHealth("#e5484d",
                            UiText.localAiTestRed(currentLanguage,
                                    ex.getMessage() == null ? "" : ex.getMessage()));
                    refreshHealthButton();
                });
            }
        }, "episort-health-test");
        worker.setDaemon(true);
        worker.start();
    }

    private void showHealth(String hexColor, String text) {
        healthDot.setFill(javafx.scene.paint.Color.web(hexColor));
        healthLabel.setText(text);
        healthRow.setVisible(true);
        healthRow.setManaged(true);
    }

    private void hideHealth() {
        healthLabel.setText("");
        healthRow.setVisible(false);
        healthRow.setManaged(false);
    }

    private static Region divider() {
        Region div = new Region();
        div.getStyleClass().add("settings-section-divider");
        return div;
    }

    /**
     * After a delete, ensure the persisted selection still points at a present
     * model. If the freshly-deleted entry was active, fall back to the first
     * remaining install; if none remain, clear the selection.
     */
    private void reconcileSelectionAfterDelete(AiModelEntry deleted) {
        Optional<String> stored = library.selectedId();
        if (stored.isPresent() && !stored.get().equals(deleted.id())) {
            return; // Selection already moved (or stayed) to a present model.
        }
        Optional<AiModelEntry> next = AiModelCatalog.entries().stream()
                .filter(library::isPresent)
                .findFirst();
        if (next.isPresent()) {
            library.setSelectedId(next.get().id());
        } else {
            library.clearSelection();
        }
    }

    /* -------- Row -------------------------------------------------------- */

    private final class ModelRow {
        final AiModelEntry entry;
        final HBox root;
        final Circle statusDot;
        final Label nameLabel;
        final Label activeIcon;
        final Label providerChip;
        final Label recommendedChip;
        final Label descriptionLabel;
        final Label statusPill;
        final Button actionButton;
        final SVGPath actionIcon;
        final ProgressIndicator actionSpinner;
        final StackPane actionGraphic;
        volatile boolean busy;
        boolean active;
        Tooltip rowTooltip;

        ModelRow(AiModelEntry entry) {
            this.entry = entry;

            statusDot = new Circle(4);
            statusDot.getStyleClass().add("ai-status-dot");

            nameLabel = new Label(entry.displayName());
            nameLabel.getStyleClass().add("ai-model-name");
            nameLabel.setMaxWidth(Double.MAX_VALUE);

            SVGPath check = new SVGPath();
            check.setContent(ICON_CHECK);
            check.getStyleClass().add("ai-model-active-check");
            activeIcon = new Label();
            activeIcon.setGraphic(check);
            activeIcon.getStyleClass().add("ai-model-active-icon");
            activeIcon.setVisible(false);
            activeIcon.setManaged(false);

            providerChip = new Label(entry.provider());
            providerChip.getStyleClass().addAll("chip", "chip-provider");

            recommendedChip = new Label();
            recommendedChip.getStyleClass().addAll("chip", "chip-recommended");
            recommendedChip.setVisible(entry.recommended());
            recommendedChip.setManaged(entry.recommended());

            HBox topRow = new HBox(8, activeIcon, nameLabel, providerChip, recommendedChip);
            topRow.setAlignment(Pos.CENTER_LEFT);

            descriptionLabel = new Label(entry.description());
            descriptionLabel.getStyleClass().add("ai-model-description");
            descriptionLabel.setMaxWidth(Double.MAX_VALUE);
            descriptionLabel.setWrapText(false);

            VBox textColumn = new VBox(2, topRow, descriptionLabel);
            textColumn.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(textColumn, Priority.ALWAYS);

            statusPill = new Label();
            statusPill.getStyleClass().add("ai-status-pill");

            actionIcon = new SVGPath();
            actionIcon.getStyleClass().add("icon-button-glyph");
            actionSpinner = new ProgressIndicator();
            actionSpinner.getStyleClass().add("icon-button-spinner");
            actionSpinner.setMaxSize(16, 16);
            actionSpinner.setPrefSize(16, 16);
            actionSpinner.setVisible(false);
            actionSpinner.setManaged(false);
            actionGraphic = new StackPane(actionIcon, actionSpinner);
            actionButton = new Button();
            actionButton.setGraphic(actionGraphic);
            actionButton.getStyleClass().add("icon-button");
            actionButton.setFocusTraversable(true);
            actionButton.setOnAction(e -> {
                e.consume();
                triggerAction();
            });
            // Stop the row's MouseClicked handler from also firing when the
            // user clicks the icon button (which would otherwise try to
            // select the model behind the trash/download click).
            actionButton.addEventFilter(MouseEvent.MOUSE_CLICKED, Event::consume);

            StackPane dotWrap = new StackPane(statusDot);
            dotWrap.setMinWidth(10);
            dotWrap.setPrefWidth(10);

            root = new HBox(14, dotWrap, textColumn, statusPill, actionButton);
            root.setAlignment(Pos.CENTER_LEFT);
            root.getStyleClass().add("ai-model-row");
            root.setOnMouseClicked(e -> selectThis());
            root.setFocusTraversable(true);
            root.setOnKeyPressed(e -> {
                switch (e.getCode()) {
                    case ENTER, SPACE -> selectThis();
                    default -> { /* no-op */ }
                }
            });
        }

        void selectThis() {
            if (!library.isPresent(entry)) {
                return; // Cannot activate a model that is not downloaded.
            }
            Optional<String> current = library.selectedId();
            if (current.isPresent() && current.get().equals(entry.id())) {
                return;
            }
            library.setSelectedId(entry.id());
            AiModelsSection.this.refresh();
            AiModelsSection.this.hotSwapTo(entry);
        }

        void applyLanguage(AppLanguage language) {
            recommendedChip.setText(UiText.aiModelsRecommended(language));
            refreshStatus();
            refreshAction();
        }

        void refresh(String selectedId) {
            active = entry.id().equals(selectedId);
            if (active) {
                if (!root.getStyleClass().contains("active")) {
                    root.getStyleClass().add("active");
                }
            } else {
                root.getStyleClass().remove("active");
            }
            activeIcon.setVisible(active);
            activeIcon.setManaged(active);

            boolean present = library.isPresent(entry);
            if (present) {
                root.getStyleClass().remove("not-installed");
                root.setCursor(Cursor.HAND);
                if (rowTooltip != null) {
                    Tooltip.uninstall(root, rowTooltip);
                    rowTooltip = null;
                }
            } else {
                if (!root.getStyleClass().contains("not-installed")) {
                    root.getStyleClass().add("not-installed");
                }
                root.setCursor(Cursor.DEFAULT);
                if (rowTooltip != null) {
                    Tooltip.uninstall(root, rowTooltip);
                }
                rowTooltip = new Tooltip(UiText.aiModelsDownloadFirstHint(currentLanguage));
                Tooltip.install(root, rowTooltip);
            }

            refreshStatus();
            refreshAction();
        }

        void refreshStatus() {
            boolean present = library.isPresent(entry);
            statusPill.getStyleClass().removeAll(
                    "ai-status-pill-installed",
                    "ai-status-pill-missing",
                    "ai-status-pill-working");
            statusDot.getStyleClass().removeAll(
                    "ai-status-dot-installed",
                    "ai-status-dot-missing",
                    "ai-status-dot-working");
            if (busy) {
                statusPill.setText(UiText.aiModelsStatusDownloading(currentLanguage));
                statusPill.getStyleClass().add("ai-status-pill-working");
                statusDot.getStyleClass().add("ai-status-dot-working");
            } else if (present) {
                statusPill.setText(UiText.aiModelsStatusPresent(currentLanguage));
                statusPill.getStyleClass().add("ai-status-pill-installed");
                statusDot.getStyleClass().add("ai-status-dot-installed");
            } else {
                statusPill.setText(UiText.aiModelsStatusMissing(currentLanguage));
                statusPill.getStyleClass().add("ai-status-pill-missing");
                statusDot.getStyleClass().add("ai-status-dot-missing");
            }
        }

        void refreshAction() {
            boolean present = library.isPresent(entry);
            actionButton.getStyleClass().removeAll("icon-button-danger", "icon-button-accent");
            // Spinner overlays the glyph while busy.
            actionIcon.setVisible(!busy);
            actionSpinner.setVisible(busy);
            actionSpinner.setManaged(busy);
            if (present) {
                actionIcon.setContent(ICON_TRASH);
                actionButton.getStyleClass().add("icon-button-danger");
                String label = UiText.aiModelsDelete(currentLanguage);
                actionButton.setTooltip(new Tooltip(label));
                actionButton.setAccessibleText(label);
                // Disabled only while an action is in flight on this row.
                actionButton.setDisable(busy);
            } else {
                actionIcon.setContent(ICON_DOWNLOAD);
                actionButton.getStyleClass().add("icon-button-accent");
                String label = entry.downloadable()
                        ? UiText.aiModelsDownload(currentLanguage)
                        : UiText.aiModelsDownloadUnavailable(currentLanguage);
                actionButton.setTooltip(new Tooltip(label));
                actionButton.setAccessibleText(label);
                // Always actionable when missing — clicking a non-downloadable
                // entry surfaces a custom error dialog explaining why.
                actionButton.setDisable(busy);
            }
        }

        void triggerAction() {
            if (busy) {
                return;
            }
            if (library.isPresent(entry)) {
                confirmAndDelete();
            } else {
                startDownload();
            }
        }

        void startDownload() {
            if (!entry.downloadable()) {
                showError(
                        UiText.aiModelsDownloadUnavailableTitle(currentLanguage),
                        UiText.aiModelsDownloadUnavailable(currentLanguage));
                return;
            }
            busy = true;
            refreshStatus();
            refreshAction();
            Thread worker = new Thread(() -> {
                try {
                    library.download(entry, written -> { /* TODO surface progress */ });
                    Platform.runLater(() -> {
                        busy = false;
                        AiModelsSection.this.refresh();
                        AiModelsSection.this.onStateChanged.run();
                    });
                } catch (UnsupportedOperationException ex) {
                    Platform.runLater(() -> {
                        busy = false;
                        AiModelsSection.this.refresh();
                        showError(
                                UiText.aiModelsDownloadUnavailableTitle(currentLanguage),
                                UiText.aiModelsDownloadUnavailable(currentLanguage));
                    });
                } catch (IOException | InterruptedException ex) {
                    if (ex instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    Platform.runLater(() -> {
                        busy = false;
                        AiModelsSection.this.refresh();
                        showError(
                                UiText.aiModelsDownloadFailedTitle(currentLanguage),
                                UiText.aiModelsDownloadFailed(currentLanguage,
                                        ex.getMessage() == null ? "" : ex.getMessage()));
                    });
                }
            }, "episort-model-download-" + entry.id());
            worker.setDaemon(true);
            worker.start();
        }

        void confirmAndDelete() {
            CustomDialog.confirm(
                    root.getScene() == null ? null : root.getScene().getWindow(),
                    UiText.aiModelsDeleteTitle(currentLanguage),
                    UiText.aiModelsDeleteMessage(currentLanguage, entry.displayName()),
                    UiText.cancelButton(currentLanguage),
                    UiText.aiModelsDelete(currentLanguage),
                    /* destructive */ true,
                    () -> doDelete());
        }

        void doDelete() {
            busy = true;
            refreshStatus();
            refreshAction();
            Thread worker = new Thread(() -> {
                try {
                    library.delete(entry);
                    Platform.runLater(() -> {
                        busy = false;
                        AiModelsSection.this.reconcileSelectionAfterDelete(entry);
                        AiModelsSection.this.refresh();
                        AiModelsSection.this.onStateChanged.run();
                    });
                } catch (IOException ex) {
                    Platform.runLater(() -> {
                        busy = false;
                        AiModelsSection.this.refresh();
                        showError(
                                UiText.aiModelsDeleteFailedTitle(currentLanguage),
                                UiText.aiModelsDeleteFailed(currentLanguage,
                                        ex.getMessage() == null ? "" : ex.getMessage()));
                    });
                }
            }, "episort-model-delete-" + entry.id());
            worker.setDaemon(true);
            worker.start();
        }

        private void showError(String title, String message) {
            CustomDialog.alert(
                    root.getScene() == null ? null : root.getScene().getWindow(),
                    title,
                    message,
                    UiText.aiModelsDialogOk(currentLanguage));
        }
    }

    /* -------- Custom dialog (themed, replaces native Alert) ------------- */

    private static final class CustomDialog {
        static void confirm(Window owner, String title, String message,
                            String cancelText, String confirmText,
                            boolean destructive, Runnable onConfirm) {
            confirm(owner, title, message, cancelText, confirmText, destructive, onConfirm, null);
        }

        static void confirm(Window owner, String title, String message,
                            String cancelText, String confirmText,
                            boolean destructive, Runnable onConfirm, Runnable onCancel) {
            Stage dialog = createStage(owner, title);
            boolean[] confirmed = {false};
            Label titleLabel = new Label(title);
            titleLabel.getStyleClass().add("custom-dialog-title");
            Label messageLabel = new Label(message);
            messageLabel.getStyleClass().add("custom-dialog-message");
            messageLabel.setWrapText(true);

            Button cancel = new Button(cancelText);
            cancel.getStyleClass().add("ghost");
            cancel.setCancelButton(true);
            cancel.setOnAction(e -> dialog.close());

            Button confirm = new Button(confirmText);
            confirm.getStyleClass().add(destructive ? "destructive" : "primary");
            confirm.setDefaultButton(true);
            confirm.setOnAction(e -> {
                confirmed[0] = true;
                dialog.close();
                if (onConfirm != null) onConfirm.run();
            });
            dialog.setOnHidden(e -> {
                if (!confirmed[0] && onCancel != null) onCancel.run();
            });

            HBox actions = new HBox(10, cancel, confirm);
            actions.getStyleClass().add("custom-dialog-actions");
            actions.setAlignment(Pos.CENTER_RIGHT);

            VBox content = new VBox(12, titleLabel, messageLabel, actions);
            content.getStyleClass().add("custom-dialog");
            mountScene(dialog, content);
            dialog.showAndWait();
        }

        static void alert(Window owner, String title, String message, String okText) {
            Stage dialog = createStage(owner, title);
            Label titleLabel = new Label(title);
            titleLabel.getStyleClass().add("custom-dialog-title");
            Label messageLabel = new Label(message);
            messageLabel.getStyleClass().add("custom-dialog-message");
            messageLabel.setWrapText(true);

            Button ok = new Button(okText);
            ok.getStyleClass().add("primary");
            ok.setDefaultButton(true);
            ok.setOnAction(e -> dialog.close());

            HBox actions = new HBox(10, ok);
            actions.getStyleClass().add("custom-dialog-actions");
            actions.setAlignment(Pos.CENTER_RIGHT);

            VBox content = new VBox(12, titleLabel, messageLabel, actions);
            content.getStyleClass().add("custom-dialog");
            mountScene(dialog, content);
            dialog.showAndWait();
        }

        private static Stage createStage(Window owner, String title) {
            Stage dialog = new Stage(StageStyle.TRANSPARENT);
            dialog.setTitle(title);
            dialog.initModality(Modality.WINDOW_MODAL);
            if (owner != null) {
                dialog.initOwner(owner);
            }
            return dialog;
        }

        private static void mountScene(Stage dialog, VBox content) {
            Scene scene = new Scene(content);
            scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
            scene.getStylesheets().add(Objects.requireNonNull(
                            AiModelsSection.class.getResource("/styles/app.css"),
                            "Missing stylesheet /styles/app.css")
                    .toExternalForm());
            dialog.setScene(scene);
        }
    }

    /** Pill-shaped on/off switch. Orange track + light thumb when on. */
    private static final class Switch extends StackPane {
        private static final double TRACK_W = 34;
        private static final double TRACK_H = 18;
        private static final double THUMB = 14;
        private final Circle thumb;
        private boolean on;
        private java.util.function.Consumer<Boolean> onToggle = v -> {};

        Switch(boolean initial) {
            this.on = initial;
            setPrefSize(TRACK_W, TRACK_H);
            setMinSize(TRACK_W, TRACK_H);
            setMaxSize(TRACK_W, TRACK_H);
            getStyleClass().add("ai-switch");
            setCursor(javafx.scene.Cursor.HAND);
            setAlignment(Pos.CENTER_LEFT);
            thumb = new Circle(THUMB / 2.0);
            thumb.getStyleClass().add("ai-switch-thumb");
            thumb.setTranslateX(2);
            getChildren().add(thumb);
            setFocusTraversable(true);
            setOnMouseClicked(e -> { e.consume(); toggle(); });
            setOnKeyPressed(e -> {
                switch (e.getCode()) {
                    case ENTER, SPACE -> { e.consume(); toggle(); }
                    default -> { /* no-op */ }
                }
            });
            applyState();
        }

        boolean isOn() { return on; }

        void setOnSilently(boolean value) {
            this.on = value;
            applyState();
        }

        void setOnToggle(java.util.function.Consumer<Boolean> handler) {
            this.onToggle = handler == null ? v -> {} : handler;
        }

        private void toggle() {
            on = !on;
            applyState();
            onToggle.accept(on);
        }

        private void applyState() {
            if (on) {
                if (!getStyleClass().contains("on")) getStyleClass().add("on");
            } else {
                getStyleClass().remove("on");
            }
            thumb.setTranslateX(on ? (TRACK_W - THUMB - 2) : 2);
        }
    }
}

package com.episort.ui.settings;

import com.episort.ai.embedded.EmbeddedLlamaRuntime;
import com.episort.ai.embedded.LlamaServerClient;
import com.episort.ai.embedded.Qwen3ModelDownloader;
import com.episort.ui.AppLanguage;
import com.episort.ui.UiText;
import java.io.IOException;
import java.nio.file.Files;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

public final class LocalAiSection {

    private static final long GREEN_THRESHOLD_MS = 8_000;
    private static final long ORANGE_THRESHOLD_MS = 30_000;

    private final Qwen3ModelDownloader downloader;
    private final EmbeddedLlamaRuntime runtime;
    private final Runnable onStateChanged;

    private final VBox root;
    private final Label sectionTitle;
    private final Label sectionDescription;
    private final Label modelLabel;
    private final Label modelValue;
    private final Label downloadLabel;
    private final Label downloadValue;
    private final Label privacyLabel;
    private final Label privacyValue;
    private final Label statusLabelHeader;
    private final Label statusValue;
    private final ProgressBar progressBar;
    private final Label statusLabel;
    private final VBox progressBox;
    private final Button redownloadButton;
    private final Button testButton;
    private final Circle healthDot;
    private final Label healthLabel;
    private final HBox healthRow;

    private AppLanguage currentLanguage = AppLanguage.FRENCH;

    public LocalAiSection(
            AppLanguage initialLanguage,
            Qwen3ModelDownloader downloader,
            EmbeddedLlamaRuntime runtime) {
        this(initialLanguage, downloader, runtime, () -> {});
    }

    public LocalAiSection(
            AppLanguage initialLanguage,
            Qwen3ModelDownloader downloader,
            EmbeddedLlamaRuntime runtime,
            Runnable onStateChanged) {
        this.downloader = downloader;
        this.runtime = runtime;
        this.onStateChanged = onStateChanged == null ? () -> {} : onStateChanged;

        sectionTitle = new Label();
        sectionTitle.getStyleClass().add("settings-section-title");

        sectionDescription = new Label();
        sectionDescription.getStyleClass().add("settings-section-description");
        sectionDescription.setWrapText(true);

        modelLabel = infoLabel();
        modelValue = infoValue(true);
        downloadLabel = infoLabel();
        downloadValue = infoValue(false);
        privacyLabel = infoLabel();
        privacyValue = infoValue(false);
        statusLabelHeader = infoLabel();
        statusValue = infoValue(false);

        VBox infoRows = new VBox(8,
                infoRow(modelLabel, modelValue),
                infoRow(downloadLabel, downloadValue),
                infoRow(privacyLabel, privacyValue),
                infoRow(statusLabelHeader, statusValue));
        infoRows.getStyleClass().add("modal-body");

        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        statusLabel = new Label("");
        statusLabel.getStyleClass().add("modal-progress-status");
        statusLabel.setWrapText(true);
        progressBox = new VBox(6, progressBar, statusLabel);
        progressBox.getStyleClass().add("modal-progress");
        progressBox.setVisible(false);
        progressBox.setManaged(false);

        redownloadButton = new Button();
        redownloadButton.getStyleClass().add("primary");
        redownloadButton.setOnAction(event -> startProvisioning(true));

        testButton = new Button();
        testButton.getStyleClass().add("ghost");
        testButton.setOnAction(event -> runHealthTest());

        healthDot = new Circle(7, Color.web("#3a3f4b"));
        healthDot.setStroke(Color.web("#1a1d24"));
        healthDot.setStrokeWidth(1);
        healthLabel = new Label("");
        healthLabel.getStyleClass().add("modal-progress-status");
        healthLabel.setWrapText(true);

        healthRow = new HBox(10, healthDot, healthLabel);
        healthRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(healthLabel, Priority.ALWAYS);
        healthRow.setVisible(false);
        healthRow.setManaged(false);

        HBox actionRow = new HBox(12, redownloadButton, testButton);
        actionRow.setAlignment(Pos.CENTER_LEFT);
        actionRow.getStyleClass().add("settings-row");

        root = new VBox(10,
                sectionTitle,
                sectionDescription,
                divider(),
                infoRows,
                progressBox,
                actionRow,
                healthRow);
        root.getStyleClass().add("settings-section");

        applyLanguage(initialLanguage);
        refreshButtonState();
    }

    public Region root() {
        return root;
    }

    public void applyLanguage(AppLanguage language) {
        currentLanguage = language;
        sectionTitle.setText(UiText.localAiModalTitle(language));
        sectionDescription.setText(UiText.localAiModalSubtitle(language));
        modelLabel.setText(UiText.localAiModalLabelModel(language));
        modelValue.setText(UiText.localAiModalValueModel(language));
        downloadLabel.setText(UiText.localAiModalLabelDownload(language));
        downloadValue.setText(UiText.localAiModalValueDownload(language));
        privacyLabel.setText(UiText.localAiModalLabelPrivacy(language));
        privacyValue.setText(UiText.localAiModalValuePrivacy(language));
        statusLabelHeader.setText(UiText.localAiStatusLabel(language));
        redownloadButton.setText(downloader.isPresent()
                ? UiText.localAiRedownload(language)
                : UiText.localAiDownload(language));
        testButton.setText(UiText.localAiTest(language));
        refreshStatusValue();
    }

    private void refreshStatusValue() {
        boolean modelPresent = downloader.isPresent();
        boolean runtimeUp = runtime.baseUri().isPresent();
        boolean binariesPresent = runtime.runtimeBinariesAvailable();
        StringBuilder sb = new StringBuilder();
        sb.append(modelPresent
                ? UiText.localAiStatusModelPresent(currentLanguage)
                : UiText.localAiStatusModelMissing(currentLanguage));
        sb.append(" • ");
        if (!binariesPresent) {
            sb.append(UiText.localAiRuntimeMissing(currentLanguage));
        } else {
            sb.append(runtimeUp
                    ? UiText.localAiStatusRuntimeUp(currentLanguage)
                    : UiText.localAiStatusRuntimeDown(currentLanguage));
        }
        statusValue.setText(sb.toString());
    }

    public void refresh() {
        refreshButtonState();
    }

    private void refreshButtonState() {
        refreshStatusValue();
        boolean binariesPresent = runtime.runtimeBinariesAvailable();
        boolean runtimeUp = runtime.baseUri().isPresent();
        redownloadButton.setText(downloader.isPresent()
                ? UiText.localAiRedownload(currentLanguage)
                : UiText.localAiDownload(currentLanguage));
        redownloadButton.setDisable(false);
        testButton.setDisable(!binariesPresent || !runtimeUp);
    }

    private void startProvisioning(boolean forceRedownload) {
        redownloadButton.setDisable(true);
        testButton.setDisable(true);
        progressBox.setVisible(true);
        progressBox.setManaged(true);
        progressBar.setProgress(0);
        statusLabel.setText(forceRedownload
                ? UiText.localAiRedownloadConfirm(currentLanguage)
                : UiText.localAiModalDownloading(currentLanguage, 0));
        Thread worker = new Thread(() -> provisionInBackground(forceRedownload),
                "episort-local-ai-bootstrap");
        worker.setDaemon(true);
        worker.start();
    }

    private void provisionInBackground(boolean forceRedownload) {
        boolean binariesPresent = runtime.runtimeBinariesAvailable();
        try {
            if (forceRedownload) {
                if (binariesPresent) {
                    runtime.stop();
                }
                Files.deleteIfExists(downloader.modelPath());
            }
            Qwen3ModelDownloader.DownloadResult download = downloader.downloadIfMissing(
                    written -> Platform.runLater(() -> updateDownloadProgress(
                            written, downloader.modelPath().toFile().length())));
            Platform.runLater(() -> {
                progressBar.setProgress(1.0);
                if (!binariesPresent) {
                    statusLabel.setText(UiText.localAiRuntimeMissing(currentLanguage));
                    refreshButtonState();
                    onStateChanged.run();
                } else {
                    statusLabel.setText(download.downloaded()
                            ? UiText.localAiModalStarting(currentLanguage)
                            : UiText.localAiModalAlreadyPresent(currentLanguage));
                }
            });
            if (binariesPresent) {
                runtime.startBlocking(java.time.Duration.ofMinutes(5));
                Platform.runLater(() -> {
                    statusLabel.setText(UiText.localAiModalReady(currentLanguage));
                    refreshButtonState();
                    onStateChanged.run();
                });
            }
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Platform.runLater(() -> {
                statusLabel.setText(UiText.localAiModalFailure(currentLanguage, ex.getMessage()));
                refreshButtonState();
            });
        }
    }

    private void updateDownloadProgress(long written, long totalKnown) {
        if (totalKnown > 0 && written <= totalKnown) {
            progressBar.setProgress((double) written / (double) totalKnown);
        } else {
            progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        }
        long writtenMb = written / 1_048_576L;
        statusLabel.setText(UiText.localAiModalDownloading(currentLanguage, writtenMb));
    }

    private void runHealthTest() {
        if (runtime.baseUri().isEmpty()) {
            showHealth(Color.web("#e5484d"), UiText.localAiTestUnavailable(currentLanguage));
            return;
        }
        testButton.setDisable(true);
        showHealth(Color.web("#3a3f4b"), UiText.localAiTestRunning(currentLanguage));
        Thread worker = new Thread(() -> {
            long start = System.nanoTime();
            try {
                LlamaServerClient client = new LlamaServerClient(runtime.baseUri().orElseThrow());
                String prompt = "<|im_start|>system\n"
                        + "You are a health probe. Reply with exactly the two characters: OK\n"
                        + "Nothing else. No punctuation. No newline.<|im_end|>\n"
                        + "<|im_start|>user\nping /no_think<|im_end|>\n"
                        + "<|im_start|>assistant\n";
                String reply = client.complete("health-test", prompt, 16);
                long ms = (System.nanoTime() - start) / 1_000_000L;
                Platform.runLater(() -> {
                    if (reply == null || reply.isBlank()) {
                        showHealth(Color.web("#e5484d"),
                                UiText.localAiTestRed(currentLanguage, "empty reply"));
                    } else if (ms <= GREEN_THRESHOLD_MS) {
                        showHealth(Color.web("#30a46c"),
                                UiText.localAiTestGreen(currentLanguage, ms));
                    } else if (ms <= ORANGE_THRESHOLD_MS) {
                        showHealth(Color.web("#f5a524"),
                                UiText.localAiTestOrange(currentLanguage, ms));
                    } else {
                        showHealth(Color.web("#e5484d"),
                                UiText.localAiTestRed(currentLanguage, ms + " ms"));
                    }
                    testButton.setDisable(false);
                });
            } catch (RuntimeException ex) {
                Platform.runLater(() -> {
                    showHealth(Color.web("#e5484d"),
                            UiText.localAiTestRed(currentLanguage, ex.getMessage()));
                    testButton.setDisable(false);
                });
            }
        }, "episort-local-ai-test");
        worker.setDaemon(true);
        worker.start();
    }

    private void showHealth(Color color, String text) {
        healthDot.setFill(color);
        healthLabel.setText(text);
        healthRow.setVisible(true);
        healthRow.setManaged(true);
    }

    private static Label infoLabel() {
        Label l = new Label();
        l.getStyleClass().add("modal-info-label");
        return l;
    }

    private static Label infoValue(boolean mono) {
        Label v = new Label();
        v.getStyleClass().add(mono ? "modal-info-value-mono" : "modal-info-value");
        v.setWrapText(true);
        return v;
    }

    private static VBox infoRow(Label label, Label value) {
        VBox row = new VBox(4, label, value);
        row.getStyleClass().add("modal-info-row");
        HBox.setHgrow(row, Priority.ALWAYS);
        return row;
    }

    private static Region divider() {
        Region div = new Region();
        div.getStyleClass().add("settings-section-divider");
        return div;
    }
}

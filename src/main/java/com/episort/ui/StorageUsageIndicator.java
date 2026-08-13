package com.episort.ui;

import com.episort.filesystem.VolumeSpace;
import com.episort.filesystem.VolumeSpaceService;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Compact readout for the logical volume that hosts the workspace. */
final class StorageUsageIndicator {
    private final VolumeSpaceService service;
    private final VBox root;
    private final Label heading;
    private final Label percentage;
    private final Label percentageSuffix;
    private final Label capacity;
    private final Label available;
    private final ProgressBar progress;
    private final VBox detail;

    private Optional<Path> workspace = Optional.empty();
    private Optional<VolumeSpace> volume = Optional.empty();
    private AppLanguage language = AppLanguage.FRENCH;
    private long generation;
    private boolean collapsed;
    private Tooltip installedTooltip;

    StorageUsageIndicator() {
        this(new VolumeSpaceService());
    }

    StorageUsageIndicator(VolumeSpaceService service) {
        this.service = Objects.requireNonNull(service, "service");
        heading = new Label();
        heading.getStyleClass().add("storage-heading");
        percentage = new Label();
        percentage.getStyleClass().add("storage-percentage");
        percentageSuffix = new Label();
        percentageSuffix.getStyleClass().add("storage-percentage-suffix");
        capacity = new Label();
        capacity.getStyleClass().add("storage-capacity");
        available = new Label();
        available.getStyleClass().add("storage-available");
        progress = new ProgressBar(0);
        progress.getStyleClass().add("storage-progress");
        progress.setMaxWidth(Double.MAX_VALUE);

        // Three groups, not five evenly spaced lines. The figure and the bar are
        // one reading — the bar is the figure drawn — so they sit at the 6 step,
        // while the measurements that substantiate them sit a 10 away. A flat
        // gap between all five made the caption as close to the figure as the
        // figure was to the bar, and nothing read as primary.
        HBox percentageRow = new HBox(4, percentage, percentageSuffix);
        percentageRow.getStyleClass().add("storage-percentage-row");
        percentageRow.setAlignment(Pos.BASELINE_LEFT);
        VBox reading = new VBox(6, percentageRow, progress);
        reading.getStyleClass().add("storage-reading");
        detail = new VBox(4, capacity, available);
        detail.getStyleClass().add("storage-detail");

        root = new VBox(10, heading, reading, detail);
        root.getStyleClass().add("storage-usage");
        applyLanguage(language);
    }

    Region root() {
        return root;
    }

    void applyLanguage(AppLanguage language) {
        this.language = Objects.requireNonNull(language, "language");
        heading.setText(UiText.storageHeading(language));
        percentageSuffix.setText(UiText.storageUsedSuffix(language));
        render();
    }

    /**
     * On a 64 px rail the readout keeps the one thing that answers the question
     * it exists to answer — how full the volume is — plus the bar. The caption,
     * the measurements and the word that qualifies the figure have no measure
     * left to sit in: about 48 px remain, and {@code 71 % utilisé} needed 70 of
     * them, so it truncated to {@code 71 ...} and the rail reported nothing.
     * The figure keeps the whole width, centred over the glyphs above it, and
     * the full reading stays available as the tooltip and the accessible text.
     */
    void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        show(heading, !collapsed);
        show(detail, !collapsed);
        render();
    }

    private static void show(javafx.scene.Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    void setWorkspace(Optional<Path> workspace) {
        Optional<Path> normalized = workspace == null
                ? Optional.empty()
                : workspace.map(path -> path.toAbsolutePath().normalize());
        if (this.workspace.equals(normalized)) {
            return;
        }
        this.workspace = normalized;
        refresh();
    }

    void refresh() {
        long request = ++generation;
        volume = Optional.empty();
        render();
        workspace.ifPresent(path -> CompletableFuture
                .supplyAsync(() -> service.read(path))
                .thenAccept(result -> Platform.runLater(() -> {
                    if (request != generation) {
                        return;
                    }
                    volume = result;
                    render();
                })));
    }

    private void render() {
        StorageUsagePresentation state = StorageUsagePresentation.from(volume, language);
        percentage.setText(state.percentageValue());
        capacity.setText(state.capacity());
        available.setText(state.available());
        progress.setProgress(state.progress());
        boolean hasData = volume.isPresent();
        progress.setVisible(hasData);
        progress.setManaged(hasData);
        // No volume means the figure is the placeholder, and "utilisé" after a
        // dash reads as a measurement rather than an absent one.
        show(percentageSuffix, hasData && !collapsed);
        String reading = UiText.storageAccessible(
                language, state.percentage(), state.capacity(), state.available());
        root.setAccessibleText(reading);
        // Tracked and uninstalled explicitly: Tooltip.uninstall(node, null) is a
        // no-op, so expanding the rail again would leave the old reading
        // hovering over a readout that now states it in full.
        if (installedTooltip != null) {
            Tooltip.uninstall(root, installedTooltip);
            installedTooltip = null;
        }
        if (collapsed) {
            installedTooltip = new Tooltip(reading);
            Tooltip.install(root, installedTooltip);
        }
    }
}

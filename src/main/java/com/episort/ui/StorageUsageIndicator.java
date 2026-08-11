package com.episort.ui;

import com.episort.filesystem.VolumeSpace;
import com.episort.filesystem.VolumeSpaceService;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Compact readout for the logical volume that hosts the workspace. */
final class StorageUsageIndicator {
    private final VolumeSpaceService service;
    private final VBox root;
    private final Label heading;
    private final Label percentage;
    private final Label capacity;
    private final Label available;
    private final ProgressBar progress;

    private Optional<Path> workspace = Optional.empty();
    private Optional<VolumeSpace> volume = Optional.empty();
    private AppLanguage language = AppLanguage.FRENCH;
    private long generation;

    StorageUsageIndicator() {
        this(new VolumeSpaceService());
    }

    StorageUsageIndicator(VolumeSpaceService service) {
        this.service = Objects.requireNonNull(service, "service");
        heading = new Label();
        heading.getStyleClass().add("storage-heading");
        percentage = new Label();
        percentage.getStyleClass().add("storage-percentage");
        capacity = new Label();
        capacity.getStyleClass().add("storage-capacity");
        available = new Label();
        available.getStyleClass().add("storage-available");
        progress = new ProgressBar(0);
        progress.getStyleClass().add("storage-progress");
        progress.setMaxWidth(Double.MAX_VALUE);

        root = new VBox(6, heading, percentage, progress, capacity, available);
        root.getStyleClass().add("storage-usage");
        applyLanguage(language);
    }

    Region root() {
        return root;
    }

    void applyLanguage(AppLanguage language) {
        this.language = Objects.requireNonNull(language, "language");
        heading.setText(UiText.storageHeading(language));
        render();
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
        percentage.setText(state.percentage());
        capacity.setText(state.capacity());
        available.setText(state.available());
        progress.setProgress(state.progress());
        boolean hasData = volume.isPresent();
        progress.setVisible(hasData);
        progress.setManaged(hasData);
        root.setAccessibleText(UiText.storageAccessible(
                language, state.percentage(), state.capacity(), state.available()));
    }
}

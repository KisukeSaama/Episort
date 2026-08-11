package com.episort.ui;

import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * The gate shown when the app cannot do its job yet — today, when no working
 * directory is configured.
 *
 * <p>It lists what is missing and offers the one action that fixes it, rather
 * than letting the user reach a scan screen that would fail on the first click.
 */
final class PrerequisiteOverlay {

    private final Label title = new Label();
    private final Label subtitle = new Label();
    private final VBox missingItems = new VBox(6);
    private final Button openSettings = new Button();
    private final VBox root;

    PrerequisiteOverlay(Runnable onOpenSettings) {
        title.getStyleClass().add("prereq-title");
        subtitle.getStyleClass().add("prereq-subtitle");
        subtitle.setWrapText(true);
        openSettings.getStyleClass().add("primary");
        openSettings.setOnAction(event -> onOpenSettings.run());

        VBox card = new VBox(14, title, subtitle, missingItems, openSettings);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMaxWidth(520);
        card.getStyleClass().add("prereq-card");

        root = new VBox(card);
        root.setAlignment(Pos.CENTER);
        root.getStyleClass().add("prereq-overlay");
        root.setVisible(false);
        root.setManaged(false);
    }

    VBox root() {
        return root;
    }

    boolean isVisible() {
        return root.isVisible();
    }

    /**
     * Restates what is missing and whether the gate is up.
     *
     * @param missing one line per unmet prerequisite, already translated
     */
    void show(boolean visible, List<String> missing, AppLanguage language) {
        missingItems.getChildren().clear();
        for (String item : missing) {
            missingItems.getChildren().add(bullet(item));
        }
        title.setText(UiText.prereqOverlayTitle(language));
        subtitle.setText(UiText.prereqOverlaySubtitle(language));
        openSettings.setText(UiText.prereqOpenSettings(language));
        root.setVisible(visible);
        root.setManaged(visible);
    }

    private static Label bullet(String text) {
        Label label = new Label("•  " + text);
        label.getStyleClass().add("prereq-bullet");
        label.setWrapText(true);
        return label;
    }
}

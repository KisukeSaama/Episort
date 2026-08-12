package com.episort.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The full-window veil shown while work is in flight.
 *
 * <p>Built as a status panel rather than a splash: a compact eyebrow, the sentence
 * naming the work, one bar, and — only when the work can actually be aborted — a
 * cancel button with its keyboard hint. Everything is left-aligned, because the
 * message is a line of text to read, not a logo to admire.
 *
 * <p>The bar is always there and carries both states itself: indeterminate while
 * the work cannot count itself, filling with a percentage next to it as soon as it
 * can. The spinning arc that used to sit above it said nothing the bar did not,
 * and JavaFX's default indicator is the one control on screen that looks like it
 * came from another application.
 *
 * <p>The overlay never decides whether something is cancellable; the shell tells
 * it, so the affordance and the real capability cannot drift apart.
 */
final class LoadingOverlay {

    private final Label eyebrow = new Label();
    private final Label message = new Label();
    private final ProgressBar bar = new ProgressBar();
    private final Label percent = new Label();
    private final Button cancelButton = new Button();
    private final Label cancelHint = new Label();
    private final HBox actions;
    private final VBox root;

    LoadingOverlay(Runnable onCancel) {
        eyebrow.getStyleClass().add("app-loader-eyebrow");

        message.getStyleClass().add("app-loader-text");
        message.setWrapText(true);
        message.setMaxWidth(Double.MAX_VALUE);

        bar.getStyleClass().add("episort-progress");
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        HBox.setHgrow(bar, Priority.ALWAYS);

        percent.getStyleClass().add("app-loader-percent");
        hide(percent);

        HBox progressRow = new HBox(12, bar, percent);
        progressRow.setAlignment(Pos.CENTER_LEFT);

        cancelButton.getStyleClass().addAll("ghost", "app-loader-cancel");
        cancelButton.setOnAction(event -> onCancel.run());

        cancelHint.getStyleClass().add("app-loader-hint");

        actions = new HBox(12, cancelButton, cancelHint);
        actions.setAlignment(Pos.CENTER_LEFT);
        hide(actions);

        VBox card = new VBox(14, eyebrow, message, progressRow, actions);
        card.getStyleClass().add("app-loader-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMaxWidth(460);

        root = new VBox(card);
        root.getStyleClass().add("app-loader-overlay");
        root.setAlignment(Pos.CENTER);
        hide(root);

        applyLanguage(AppLanguage.FRENCH);
    }

    VBox root() {
        return root;
    }

    boolean isVisible() {
        return root.isVisible();
    }

    void setMessage(String text) {
        if (text != null) {
            message.setText(text);
        }
    }

    void setVisible(boolean visible) {
        show(root, visible);
    }

    /**
     * Sets a determinate progress value in [0, 1]. A negative value returns the bar
     * to its indeterminate state and drops the percentage — honest about not
     * knowing, rather than parking at a number that never moves.
     */
    void setProgress(double progress) {
        if (progress < 0) {
            bar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
            hide(percent);
            return;
        }
        double clamped = Math.max(0, Math.min(1, progress));
        bar.setProgress(clamped);
        percent.setText(Math.round(clamped * 100) + " %");
        show(percent, true);
    }

    /** Shows or hides the cancel affordance, refreshing its labels. */
    void setCancellable(boolean cancellable, AppLanguage language) {
        applyLanguage(language);
        cancelButton.setDisable(false);
        show(actions, cancellable);
    }

    private void applyLanguage(AppLanguage language) {
        eyebrow.setText(UiText.loadingEyebrow(language));
        cancelButton.setText(UiText.loadingCancel(language));
        cancelHint.setText(UiText.loadingCancelHint(language));
    }

    private static void hide(Node node) {
        show(node, false);
    }

    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}

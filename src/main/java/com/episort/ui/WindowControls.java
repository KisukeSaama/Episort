package com.episort.ui;

import com.episort.ui.platform.WindowManager;
import com.episort.ui.platform.WindowState;
import java.util.Objects;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * The minimize / maximize / close trio, drawn in-app because the stage is
 * undecorated. It lives at the right end of the top bar rather than in a strip
 * of its own: the window chrome costs no vertical space that way.
 *
 * <p>The stage is resolved lazily from the scene — the controls are built
 * before the shell is ever attached to a window.
 */
public final class WindowControls {
    /** Plain Unicode rather than the Segoe MDL2 private-use range: the app
     * bundles its own fonts, and a missing icon font would draw three tofus. */
    private static final String MAXIMIZE_GLYPH = "☐";
    private static final String RESTORE_GLYPH = "❐";

    private final HBox root;
    private final Button minimizeButton;
    private final Button maximizeButton;
    private final Button closeButton;
    private Stage stage;
    private WindowManager windowManager;
    private AppLanguage currentLanguage = AppLanguage.FRENCH;

    public WindowControls() {
        minimizeButton = controlButton("─", "window-minimize");
        maximizeButton = controlButton(MAXIMIZE_GLYPH, "window-maximize");
        closeButton = controlButton("✕", "window-close");
        closeButton.getStyleClass().add("danger");

        minimizeButton.setOnAction(event -> withWindowManager(WindowManager::minimize));
        maximizeButton.setOnAction(event -> toggleMaximized());
        closeButton.setOnAction(event -> withStage(Stage::close));

        root = new HBox(minimizeButton, maximizeButton, closeButton);
        root.getStyleClass().add("window-controls");
        applyLanguage(AppLanguage.FRENCH);
    }

    public Region root() {
        return root;
    }

    /**
     * Binds the controls to their stage and keeps the maximize glyph in sync,
     * including when Windows itself changes the state (Win+Up, snapping).
     */
    public void attach(Stage stage, WindowManager windowManager) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(windowManager, "windowManager");
        if (this.stage == stage) {
            return;
        }
        this.stage = stage;
        this.windowManager = windowManager;
        windowManager.addStateListener(this::refreshMaximizeGlyph);
    }

    public void toggleMaximized() {
        withWindowManager(WindowManager::toggleMaximize);
    }

    public void applyLanguage(AppLanguage language) {
        minimizeButton.setAccessibleText(UiText.windowMinimize(language));
        minimizeButton.setTooltip(new Tooltip(UiText.windowMinimize(language)));
        closeButton.setAccessibleText(UiText.windowClose(language));
        closeButton.setTooltip(new Tooltip(UiText.windowClose(language)));
        refreshMaximizeTooltip(language);
    }

    private void refreshMaximizeGlyph(WindowState state) {
        // Windows uses two distinct glyphs here; reusing one would leave the
        // button claiming "maximize" on an already maximized window.
        maximizeButton.setText(state == WindowState.NORMAL ? MAXIMIZE_GLYPH : RESTORE_GLYPH);
        refreshMaximizeTooltip(currentLanguage);
    }

    private void refreshMaximizeTooltip(AppLanguage language) {
        currentLanguage = language;
        boolean maximized = windowManager != null && windowManager.state() != WindowState.NORMAL;
        String text = maximized ? UiText.windowRestore(language) : UiText.windowMaximize(language);
        maximizeButton.setAccessibleText(text);
        maximizeButton.setTooltip(new Tooltip(text));
    }

    private void withStage(java.util.function.Consumer<Stage> action) {
        Stage target = resolveStage();
        if (target != null) {
            action.accept(target);
        }
    }

    private void withWindowManager(java.util.function.Consumer<WindowManager> action) {
        if (windowManager != null) {
            action.accept(windowManager);
        }
    }

    private Stage resolveStage() {
        if (stage != null) {
            return stage;
        }
        if (root.getScene() == null) {
            return null;
        }
        Window window = root.getScene().getWindow();
        if (window instanceof Stage resolved) {
            return resolved;
        }
        return null;
    }

    /**
     * The glyph is the button's text, not a graphic. A graphic with empty text
     * still reserves the graphic-text gap, which pushed every symbol a couple of
     * pixels left of the centre of its hover block.
     */
    private static Button controlButton(String glyph, String styleClass) {
        Button button = new Button(glyph);
        button.setFocusTraversable(false);
        button.getStyleClass().setAll("window-button", styleClass);
        return button;
    }
}

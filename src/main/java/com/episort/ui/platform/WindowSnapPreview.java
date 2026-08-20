package com.episort.ui.platform;

import java.util.Objects;
import javafx.scene.layout.Region;
import javafx.stage.Popup;
import javafx.stage.Stage;
import com.episort.ui.ThemeStyles;

/** Lightweight, non-interactive indication of the placement selected while dragging. */
final class WindowSnapPreview {
    private final Popup popup = new Popup();
    private final Region surface = new Region();
    /** What the popup currently shows, so an unchanged frame costs nothing. */
    private WindowBounds shown;

    WindowSnapPreview() {
        surface.getStyleClass().add("window-snap-preview");
        surface.getStylesheets().add(Objects.requireNonNull(
                WindowSnapPreview.class.getResource("/styles/app.css")).toExternalForm());
        ThemeStyles.register(surface);
        surface.setMouseTransparent(true);
        popup.getContent().add(surface);
        popup.setAutoFix(false);
        popup.setAutoHide(false);
        popup.setHideOnEscape(false);
    }

    void show(Stage owner, WindowBounds bounds) {
        if (bounds.equals(shown)) {
            return;
        }
        shown = bounds;
        surface.setPrefSize(bounds.width(), bounds.height());
        surface.setMinSize(bounds.width(), bounds.height());
        surface.setMaxSize(bounds.width(), bounds.height());
        if (!popup.isShowing()) {
            popup.show(owner, bounds.x(), bounds.y());
        } else {
            popup.setX(bounds.x());
            popup.setY(bounds.y());
        }
    }

    void hide() {
        if (shown == null) {
            return;
        }
        shown = null;
        popup.hide();
    }
}

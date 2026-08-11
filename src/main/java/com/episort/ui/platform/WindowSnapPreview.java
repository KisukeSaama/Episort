package com.episort.ui.platform;

import java.util.Objects;
import javafx.scene.layout.Region;
import javafx.stage.Popup;
import javafx.stage.Stage;

/** Lightweight, non-interactive indication of the placement selected while dragging. */
final class WindowSnapPreview {
    private final Popup popup = new Popup();
    private final Region surface = new Region();

    WindowSnapPreview() {
        surface.getStyleClass().add("window-snap-preview");
        surface.getStylesheets().add(Objects.requireNonNull(
                WindowSnapPreview.class.getResource("/styles/app.css")).toExternalForm());
        surface.setMouseTransparent(true);
        popup.getContent().add(surface);
        popup.setAutoFix(false);
        popup.setAutoHide(false);
        popup.setHideOnEscape(false);
    }

    void show(Stage owner, WindowBounds bounds) {
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
        popup.hide();
    }
}

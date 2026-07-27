package com.episort.ui.platform;

import java.util.Objects;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.MenuBar;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.Slider;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

/**
 * What Windows used to give the window for free: dragging it by its bar,
 * double-click to maximize, and resizing from the edges. The stage is
 * undecorated so the app can draw its own controls, which means all of it has
 * to be re-implemented here.
 */
public final class StageDecorations {
    /** Grab band along each edge, in pixels. Matches the Windows feel closely enough. */
    private static final double RESIZE_MARGIN = 6;

    private final Stage stage;

    private double dragOffsetX;
    private double dragOffsetY;
    private boolean dragging;

    private Cursor resizeCursor = Cursor.DEFAULT;
    private boolean resizing;
    private double resizeStartScreenX;
    private double resizeStartScreenY;
    private double resizeStartX;
    private double resizeStartY;
    private double resizeStartWidth;
    private double resizeStartHeight;

    private StageDecorations(Stage stage) {
        this.stage = stage;
    }

    /**
     * @param dragRegion the node that behaves like a title bar. Presses landing
     *                   on a button, a text field or the menu bar inside it are
     *                   left alone, so the bar's own controls keep working.
     */
    public static void install(Stage stage, Region dragRegion) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(dragRegion, "dragRegion");
        StageDecorations decorations = new StageDecorations(stage);
        decorations.installDrag(dragRegion);
        decorations.installResize(stage.getScene());
    }

    private void installDrag(Region dragRegion) {
        dragRegion.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() != MouseButton.PRIMARY || isInteractive(event.getTarget(), dragRegion)) {
                dragging = false;
                return;
            }
            dragging = true;
            dragOffsetX = event.getScreenX() - stage.getX();
            dragOffsetY = event.getScreenY() - stage.getY();
        });

        dragRegion.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
            if (!dragging) {
                return;
            }
            if (stage.isMaximized()) {
                // Restore under the pointer, keeping it at the same relative
                // spot along the bar: unmaximizing to the old top-left would
                // teleport the window out from under the cursor.
                double ratio = stage.getWidth() <= 0 ? 0.5 : dragOffsetX / stage.getWidth();
                stage.setMaximized(false);
                dragOffsetX = ratio * stage.getWidth();
                dragOffsetY = Math.min(dragOffsetY, Math.max(0, stage.getHeight() - 1));
            }
            stage.setX(event.getScreenX() - dragOffsetX);
            stage.setY(event.getScreenY() - dragOffsetY);
        });

        dragRegion.addEventHandler(MouseEvent.MOUSE_RELEASED, event -> dragging = false);

        dragRegion.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.PRIMARY
                    && event.getClickCount() == 2
                    && !isInteractive(event.getTarget(), dragRegion)) {
                stage.setMaximized(!stage.isMaximized());
            }
        });
    }

    private void installResize(Scene scene) {
        if (scene == null) {
            return;
        }
        // Filters, not handlers: the edge band overlaps whatever control sits
        // against the window border, and the resize gesture has to win there.
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, event -> {
            if (resizing) {
                return;
            }
            resizeCursor = cursorFor(scene, event.getSceneX(), event.getSceneY());
            scene.setCursor(resizeCursor);
        });

        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() != MouseButton.PRIMARY || resizeCursor == Cursor.DEFAULT) {
                return;
            }
            resizing = true;
            resizeStartScreenX = event.getScreenX();
            resizeStartScreenY = event.getScreenY();
            resizeStartX = stage.getX();
            resizeStartY = stage.getY();
            resizeStartWidth = stage.getWidth();
            resizeStartHeight = stage.getHeight();
            event.consume();
        });

        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (!resizing) {
                return;
            }
            applyResize(event.getScreenX() - resizeStartScreenX, event.getScreenY() - resizeStartScreenY);
            event.consume();
        });

        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (resizing) {
                resizing = false;
                event.consume();
            }
        });

        scene.addEventFilter(MouseEvent.MOUSE_EXITED, event -> {
            if (!resizing) {
                scene.setCursor(Cursor.DEFAULT);
                resizeCursor = Cursor.DEFAULT;
            }
        });
    }

    private void applyResize(double deltaX, double deltaY) {
        double minWidth = Math.max(stage.getMinWidth(), 1);
        double minHeight = Math.max(stage.getMinHeight(), 1);

        if (resizeCursor == Cursor.E_RESIZE || resizeCursor == Cursor.NE_RESIZE || resizeCursor == Cursor.SE_RESIZE) {
            stage.setWidth(Math.max(minWidth, resizeStartWidth + deltaX));
        }
        if (resizeCursor == Cursor.S_RESIZE || resizeCursor == Cursor.SE_RESIZE || resizeCursor == Cursor.SW_RESIZE) {
            stage.setHeight(Math.max(minHeight, resizeStartHeight + deltaY));
        }
        if (resizeCursor == Cursor.W_RESIZE || resizeCursor == Cursor.NW_RESIZE || resizeCursor == Cursor.SW_RESIZE) {
            // Clamping the width first keeps the left edge from walking past the
            // minimum and dragging the window along with it.
            double width = Math.max(minWidth, resizeStartWidth - deltaX);
            stage.setX(resizeStartX + (resizeStartWidth - width));
            stage.setWidth(width);
        }
        if (resizeCursor == Cursor.N_RESIZE || resizeCursor == Cursor.NE_RESIZE || resizeCursor == Cursor.NW_RESIZE) {
            double height = Math.max(minHeight, resizeStartHeight - deltaY);
            stage.setY(resizeStartY + (resizeStartHeight - height));
            stage.setHeight(height);
        }
    }

    private Cursor cursorFor(Scene scene, double x, double y) {
        if (stage.isMaximized() || stage.isFullScreen()) {
            return Cursor.DEFAULT;
        }
        boolean left = x < RESIZE_MARGIN;
        boolean right = x > scene.getWidth() - RESIZE_MARGIN;
        boolean top = y < RESIZE_MARGIN;
        boolean bottom = y > scene.getHeight() - RESIZE_MARGIN;

        if (top && left) return Cursor.NW_RESIZE;
        if (top && right) return Cursor.NE_RESIZE;
        if (bottom && left) return Cursor.SW_RESIZE;
        if (bottom && right) return Cursor.SE_RESIZE;
        if (left) return Cursor.W_RESIZE;
        if (right) return Cursor.E_RESIZE;
        if (top) return Cursor.N_RESIZE;
        if (bottom) return Cursor.S_RESIZE;
        return Cursor.DEFAULT;
    }

    /**
     * True when the press landed on something that reacts to it. Labels and
     * layout panes are draggable surface; buttons, fields and menus are not.
     */
    private static boolean isInteractive(Object target, Region dragRegion) {
        Node node = target instanceof Node candidate ? candidate : null;
        while (node != null && node != dragRegion) {
            if (node instanceof ButtonBase
                    || node instanceof TextInputControl
                    || node instanceof MenuBar
                    || node instanceof ComboBoxBase
                    || node instanceof ScrollBar
                    || node instanceof Slider) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }
}

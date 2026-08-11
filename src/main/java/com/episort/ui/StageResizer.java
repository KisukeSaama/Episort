package com.episort.ui;

import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

/**
 * Gives an undecorated {@link Stage} the edge-drag resizing the window manager
 * would normally provide.
 *
 * <p>Dialogs here are borderless so they can carry the app's own header, which
 * also costs them the native resize grips. A table-heavy dialog is unusable at
 * a fixed size: long paths need width the designer cannot guess. This restores
 * the eight standard grips (four edges, four corners) with the matching cursor
 * feedback, clamped by the stage's own min width/height.
 *
 * <p>The filters run before the header's own drag handler, so a press inside a
 * border band resizes instead of moving the window.
 */
public final class StageResizer {

    /** How far inside the window edge still counts as a resize grip. */
    private static final double BORDER = 6;

    private StageResizer() {
    }

    public static void install(Stage stage) {
        Scene scene = stage.getScene();
        if (scene == null) {
            throw new IllegalStateException("StageResizer.install requires a stage with a scene");
        }
        new Resizer(stage, scene).install();
    }

    private static final class Resizer {
        private final Stage stage;
        private final Scene scene;
        private boolean north;
        private boolean south;
        private boolean west;
        private boolean east;
        private boolean dragging;
        private double startScreenX;
        private double startScreenY;
        private double startX;
        private double startY;
        private double startWidth;
        private double startHeight;

        Resizer(Stage stage, Scene scene) {
            this.stage = stage;
            this.scene = scene;
        }

        void install() {
            scene.addEventFilter(MouseEvent.MOUSE_MOVED, this::onMoved);
            scene.addEventFilter(MouseEvent.MOUSE_PRESSED, this::onPressed);
            scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::onDragged);
            scene.addEventFilter(MouseEvent.MOUSE_RELEASED, this::onReleased);
            scene.addEventFilter(MouseEvent.MOUSE_EXITED, event -> {
                if (!dragging) {
                    scene.setCursor(Cursor.DEFAULT);
                }
            });
        }

        private void onMoved(MouseEvent event) {
            if (dragging) {
                return;
            }
            detectEdges(event);
            scene.setCursor(cursor());
        }

        private void onPressed(MouseEvent event) {
            detectEdges(event);
            if (!onAnyEdge()) {
                return;
            }
            dragging = true;
            startScreenX = event.getScreenX();
            startScreenY = event.getScreenY();
            startX = stage.getX();
            startY = stage.getY();
            startWidth = stage.getWidth();
            startHeight = stage.getHeight();
            event.consume();
        }

        private void onDragged(MouseEvent event) {
            if (!dragging) {
                return;
            }
            double deltaX = event.getScreenX() - startScreenX;
            double deltaY = event.getScreenY() - startScreenY;

            if (east) {
                stage.setWidth(Math.max(minWidth(), startWidth + deltaX));
            } else if (west) {
                double width = Math.max(minWidth(), startWidth - deltaX);
                stage.setX(startX + startWidth - width);
                stage.setWidth(width);
            }
            if (south) {
                stage.setHeight(Math.max(minHeight(), startHeight + deltaY));
            } else if (north) {
                double height = Math.max(minHeight(), startHeight - deltaY);
                stage.setY(startY + startHeight - height);
                stage.setHeight(height);
            }
            event.consume();
        }

        private void onReleased(MouseEvent event) {
            if (!dragging) {
                return;
            }
            dragging = false;
            detectEdges(event);
            scene.setCursor(cursor());
            event.consume();
        }

        private void detectEdges(MouseEvent event) {
            double x = event.getSceneX();
            double y = event.getSceneY();
            west = x <= BORDER;
            east = x >= scene.getWidth() - BORDER;
            north = y <= BORDER;
            south = y >= scene.getHeight() - BORDER;
        }

        private boolean onAnyEdge() {
            return north || south || west || east;
        }

        private Cursor cursor() {
            if (north && west) {
                return Cursor.NW_RESIZE;
            }
            if (north && east) {
                return Cursor.NE_RESIZE;
            }
            if (south && west) {
                return Cursor.SW_RESIZE;
            }
            if (south && east) {
                return Cursor.SE_RESIZE;
            }
            if (north) {
                return Cursor.N_RESIZE;
            }
            if (south) {
                return Cursor.S_RESIZE;
            }
            if (west) {
                return Cursor.W_RESIZE;
            }
            if (east) {
                return Cursor.E_RESIZE;
            }
            return Cursor.DEFAULT;
        }

        private double minWidth() {
            return Math.max(stage.getMinWidth(), 2 * BORDER);
        }

        private double minHeight() {
            return Math.max(stage.getMinHeight(), 2 * BORDER);
        }
    }
}

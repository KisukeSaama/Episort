package com.episort.ui.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.geometry.Rectangle2D;
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
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

/** Owns all placement behavior for Episort's custom window chrome. */
public final class WindowManager {
    private static final double RESIZE_MARGIN = 6;
    private static final double TITLE_BAR_GRAB_HEIGHT = 32;
    private static final double DRAG_START_DISTANCE = 4;

    private final Stage stage;
    private final Region dragRegion;
    private final WindowStateModel stateModel = new WindowStateModel();
    private final WindowSnapPreview preview = new WindowSnapPreview();
    private final double normalMinWidth;
    private final double normalMinHeight;

    private final List<Consumer<WindowState>> stateListeners = new ArrayList<>();
    private boolean dragging;
    private boolean dragMoved;
    private boolean resizing;
    private boolean restoredForDrag;
    private double dragOffsetX;
    private double dragOffsetY;
    private double pressScreenX;
    private double pressScreenY;
    private Cursor resizeCursor = Cursor.DEFAULT;
    private double resizeStartScreenX;
    private double resizeStartScreenY;
    private WindowBounds resizeStartBounds;
    private WindowSnapTarget snapTarget = WindowSnapTarget.NONE;
    private WindowWorkArea snapArea;

    private WindowManager(Stage stage, Region dragRegion) {
        this.stage = stage;
        this.dragRegion = dragRegion;
        normalMinWidth = stage.getMinWidth();
        normalMinHeight = stage.getMinHeight();
    }

    public static WindowManager install(Stage stage, Region dragRegion) {
        WindowManager manager = new WindowManager(Objects.requireNonNull(stage), Objects.requireNonNull(dragRegion));
        manager.installHandlers();
        return manager;
    }

    public void addStateListener(Consumer<WindowState> listener) {
        Consumer<WindowState> safeListener = Objects.requireNonNull(listener);
        stateListeners.add(safeListener);
        safeListener.accept(stateModel.state());
    }

    public WindowState state() {
        return stateModel.state();
    }

    public void minimize() {
        stage.setIconified(true);
    }

    public void toggleMaximize() {
        if (stateModel.state() == WindowState.NORMAL) {
            maximize(workAreaAt(stage.getX() + stage.getWidth() / 2, stage.getY() + stage.getHeight() / 2));
        } else {
            restore();
        }
    }

    private void installHandlers() {
        dragRegion.addEventHandler(MouseEvent.MOUSE_PRESSED, this::onMousePressed);
        dragRegion.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::onMouseDragged);
        dragRegion.addEventHandler(MouseEvent.MOUSE_RELEASED, this::onMouseReleased);
        dragRegion.addEventHandler(MouseEvent.MOUSE_CLICKED, this::onMouseClicked);

        Scene scene = stage.getScene();
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, this::updateResizeCursor);
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, this::beginResize);
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::resize);
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, this::finishResize);
        stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> preview.hide());
    }

    private void onMousePressed(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY || isInteractive(event.getTarget(), dragRegion) || resizing) {
            return;
        }
        dragging = true;
        dragMoved = false;
        restoredForDrag = false;
        pressScreenX = event.getScreenX();
        pressScreenY = event.getScreenY();
        dragOffsetX = event.getScreenX() - stage.getX();
        dragOffsetY = event.getScreenY() - stage.getY();
        snapTarget = WindowSnapTarget.NONE;
        preview.hide();
    }

    private void onMouseDragged(MouseEvent event) {
        if (!dragging) {
            return;
        }
        if (!dragMoved) {
            double deltaX = event.getScreenX() - pressScreenX;
            double deltaY = event.getScreenY() - pressScreenY;
            if (Math.hypot(deltaX, deltaY) < DRAG_START_DISTANCE) {
                return;
            }
            dragMoved = true;
        }
        if (stateModel.state() != WindowState.NORMAL && !restoredForDrag) {
            restoreForDrag(event);
        }
        stage.setX(event.getScreenX() - dragOffsetX);
        stage.setY(event.getScreenY() - dragOffsetY);
        updateSnapPreview(event.getScreenX(), event.getScreenY());
    }

    private void onMouseReleased(MouseEvent event) {
        if (!dragging) {
            return;
        }
        dragging = false;
        if (!dragMoved) {
            preview.hide();
            snapTarget = WindowSnapTarget.NONE;
            return;
        }
        applySnapOrKeepVisible(event.getScreenX(), event.getScreenY());
        preview.hide();
        snapTarget = WindowSnapTarget.NONE;
    }

    private void onMouseClicked(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY
                && event.getClickCount() == 2
                && !isInteractive(event.getTarget(), dragRegion)) {
            toggleMaximize();
        }
    }

    private void restoreForDrag(MouseEvent event) {
        WindowBounds restoreBounds = stateModel.normalBounds().orElseGet(this::currentBounds);
        double ratio = stage.getWidth() <= 0 ? 0.5 : (event.getScreenX() - stage.getX()) / stage.getWidth();
        if (stage.isMaximized()) {
            stage.setMaximized(false);
        }
        restoreMinimumSize();
        applyBounds(new WindowBounds(
                event.getScreenX() - restoreBounds.width() * ratio,
                event.getScreenY() - Math.min(TITLE_BAR_GRAB_HEIGHT, restoreBounds.height() - 1),
                restoreBounds.width(),
                restoreBounds.height()));
        dragOffsetX = restoreBounds.width() * ratio;
        dragOffsetY = Math.min(TITLE_BAR_GRAB_HEIGHT, restoreBounds.height() - 1);
        stateModel.restore(restoreBounds);
        notifyState();
        restoredForDrag = true;
    }

    private void updateSnapPreview(double screenX, double screenY) {
        snapArea = workAreaAt(screenX, screenY);
        snapTarget = WindowGeometry.snapTargetAt(screenX, screenY, snapArea);
        if (snapTarget == WindowSnapTarget.NONE) {
            preview.hide();
        } else {
            preview.show(stage, WindowGeometry.boundsFor(snapTarget, snapArea));
        }
    }

    private void applySnapOrKeepVisible(double screenX, double screenY) {
        if (snapTarget == WindowSnapTarget.MAXIMIZE) {
            maximize(snapArea);
        } else if (snapTarget == WindowSnapTarget.LEFT || snapTarget == WindowSnapTarget.RIGHT) {
            snap(snapTarget, snapArea);
        } else {
            WindowBounds visible = WindowGeometry.keepTitleBarVisible(currentBounds(), workAreaAt(screenX, screenY));
            applyBounds(visible);
            stateModel.restore(visible);
            notifyState();
        }
    }

    private void maximize(WindowWorkArea area) {
        stateModel.enter(WindowState.MAXIMIZED, currentBounds());
        if (stage.isMaximized()) {
            stage.setMaximized(false);
        }
        applyPlacementBounds(WindowGeometry.maximized(area));
        notifyState();
    }

    private void snap(WindowSnapTarget target, WindowWorkArea area) {
        if (stage.isMaximized()) {
            stage.setMaximized(false);
        }
        WindowState state = target == WindowSnapTarget.LEFT ? WindowState.SNAPPED_LEFT : WindowState.SNAPPED_RIGHT;
        stateModel.enter(state, currentBounds());
        applyPlacementBounds(WindowGeometry.boundsFor(target, area));
        notifyState();
    }

    private void restore() {
        WindowBounds restoreBounds = stateModel.normalBounds().orElseGet(this::currentBounds);
        if (stage.isMaximized()) {
            stage.setMaximized(false);
        }
        restoreMinimumSize();
        applyBounds(restoreBounds);
        stateModel.restore(restoreBounds);
        notifyState();
    }

    private void updateResizeCursor(MouseEvent event) {
        if (dragging || resizing || stateModel.state() != WindowState.NORMAL) {
            return;
        }
        resizeCursor = cursorFor(event.getSceneX(), event.getSceneY());
        stage.getScene().setCursor(resizeCursor);
    }

    private void beginResize(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY || resizeCursor == Cursor.DEFAULT || dragging) {
            return;
        }
        resizing = true;
        resizeStartScreenX = event.getScreenX();
        resizeStartScreenY = event.getScreenY();
        resizeStartBounds = currentBounds();
        event.consume();
    }

    private void resize(MouseEvent event) {
        if (!resizing) {
            return;
        }
        double deltaX = event.getScreenX() - resizeStartScreenX;
        double deltaY = event.getScreenY() - resizeStartScreenY;
        double minWidth = Math.max(stage.getMinWidth(), 1);
        double minHeight = Math.max(stage.getMinHeight(), 1);
        double x = resizeStartBounds.x();
        double y = resizeStartBounds.y();
        double width = resizeStartBounds.width();
        double height = resizeStartBounds.height();
        if (isEast(resizeCursor)) width = Math.max(minWidth, width + deltaX);
        if (isSouth(resizeCursor)) height = Math.max(minHeight, height + deltaY);
        if (isWest(resizeCursor)) {
            width = Math.max(minWidth, width - deltaX);
            x = resizeStartBounds.right() - width;
        }
        if (isNorth(resizeCursor)) {
            height = Math.max(minHeight, height - deltaY);
            y = resizeStartBounds.bottom() - height;
        }
        applyBounds(new WindowBounds(x, y, width, height));
        event.consume();
    }

    private void finishResize(MouseEvent event) {
        if (!resizing) return;
        resizing = false;
        WindowBounds bounds = currentBounds();
        stateModel.restore(bounds);
        notifyState();
        event.consume();
    }

    private Cursor cursorFor(double x, double y) {
        Scene scene = stage.getScene();
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

    private WindowWorkArea workAreaAt(double screenX, double screenY) {
        Rectangle2D bounds = Screen.getScreensForRectangle(screenX, screenY, 1, 1).stream()
                .findFirst()
                .orElseGet(Screen::getPrimary)
                .getVisualBounds();
        return new WindowWorkArea(bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight());
    }

    private WindowBounds currentBounds() {
        return new WindowBounds(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
    }

    private void applyBounds(WindowBounds bounds) {
        stage.setX(bounds.x());
        stage.setY(bounds.y());
        stage.setWidth(bounds.width());
        stage.setHeight(bounds.height());
    }

    private void applyPlacementBounds(WindowBounds bounds) {
        stage.setMinWidth(Math.min(normalMinWidth, bounds.width()));
        stage.setMinHeight(Math.min(normalMinHeight, bounds.height()));
        applyBounds(bounds);
    }

    private void restoreMinimumSize() {
        stage.setMinWidth(normalMinWidth);
        stage.setMinHeight(normalMinHeight);
    }

    private void notifyState() {
        for (Consumer<WindowState> listener : List.copyOf(stateListeners)) {
            listener.accept(stateModel.state());
        }
    }

    private static boolean isEast(Cursor cursor) { return cursor == Cursor.E_RESIZE || cursor == Cursor.NE_RESIZE || cursor == Cursor.SE_RESIZE; }
    private static boolean isSouth(Cursor cursor) { return cursor == Cursor.S_RESIZE || cursor == Cursor.SE_RESIZE || cursor == Cursor.SW_RESIZE; }
    private static boolean isWest(Cursor cursor) { return cursor == Cursor.W_RESIZE || cursor == Cursor.NW_RESIZE || cursor == Cursor.SW_RESIZE; }
    private static boolean isNorth(Cursor cursor) { return cursor == Cursor.N_RESIZE || cursor == Cursor.NE_RESIZE || cursor == Cursor.NW_RESIZE; }

    private static boolean isInteractive(Object target, Region dragRegion) {
        Node node = target instanceof Node candidate ? candidate : null;
        while (node != null && node != dragRegion) {
            if (node instanceof ButtonBase || node instanceof TextInputControl || node instanceof MenuBar
                    || node instanceof ComboBoxBase || node instanceof ScrollBar || node instanceof Slider) return true;
            node = node.getParent();
        }
        return false;
    }
}

package com.episort.ui.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
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

/**
 * Owns all placement behavior for Episort's custom window chrome.
 *
 * <p>Two arrangements live here. On Windows the window is given the system
 * frame ({@link NativeWindowFrame}) and the system takes the placement over
 * entirely: it moves the window, snaps it, maximizes it and animates all three,
 * and what remains below is the part that follows along — reading back what
 * Windows decided so the title bar's own buttons stay in step. Everywhere else,
 * and if adopting the frame fails, Episort keeps doing it itself: its own drag,
 * its own edge detection, its own preview.
 */
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
    private final List<Runnable> frameListeners = new ArrayList<>();
    /** Whether the native frame question has been settled, one way or the other. */
    private boolean frameSettled;
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
    /** Non-null once Windows owns the placement. */
    private NativeWindowFrame systemFrame;
    /** A saved placement waiting for the window to be on screen to be applied. */
    private WindowState pendingState = WindowState.NORMAL;

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

    /**
     * Runs once the window's native frame is final, adopted or not.
     *
     * <p>Adopting the frame puts {@code WS_THICKFRAME} back on a window that was
     * created without it, and tells Windows the frame changed. Whatever DWM was
     * asked about the window before that — its border colour, its corners — was
     * asked about a frame that no longer exists, and the system answers the
     * question again with its own defaults. Anything set on the window handle
     * therefore has to be set again from here.
     */
    public void whenFrameSettled(Runnable action) {
        Runnable safeAction = Objects.requireNonNull(action);
        if (frameSettled) {
            safeAction.run();
            return;
        }
        frameListeners.add(safeAction);
    }

    public WindowState state() {
        return stateModel.state();
    }

    public WindowBounds normalBounds() {
        return stateModel.normalBounds().orElseGet(this::currentBounds);
    }

    /** Restores a saved placement, constraining it to an available monitor. */
    public void restorePlacement(WindowBounds savedBounds, WindowState savedState) {
        WindowWorkArea area = workAreaAt(savedBounds.x() + savedBounds.width() / 2,
                savedBounds.y() + Math.min(savedBounds.height(), TITLE_BAR_GRAB_HEIGHT) / 2);
        double width = Math.max(normalMinWidth, Math.min(savedBounds.width(), area.width()));
        double height = Math.max(normalMinHeight, Math.min(savedBounds.height(), area.height()));
        WindowBounds visible = WindowGeometry.keepTitleBarVisible(
                new WindowBounds(savedBounds.x(), savedBounds.y(), width, height), area);
        applyBounds(visible);
        stateModel.restore(visible);
        pendingState = savedState;
        if (NativeWindowFrame.isSupported()) {
            // Held back until the window is on screen, since the frame that will
            // maximize it does not exist before then. The window keeps its normal
            // rectangle meanwhile, which is the one Windows must restore it to.
            notifyState();
            return;
        }
        applyPendingState(area);
    }

    private void applyPendingState(WindowWorkArea area) {
        WindowState wanted = pendingState;
        pendingState = WindowState.NORMAL;
        if (systemFrame != null) {
            // A half-screen placement is, to Windows, an ordinary window at that
            // size; only being maximized is a state it keeps.
            if (wanted == WindowState.MAXIMIZED) {
                systemFrame.setMaximized(true);
            }
            return;
        }
        switch (wanted) {
            case MAXIMIZED -> maximize(area);
            case SNAPPED_LEFT -> snap(WindowSnapTarget.LEFT, area);
            case SNAPPED_RIGHT -> snap(WindowSnapTarget.RIGHT, area);
            case NORMAL -> notifyState();
        }
    }

    public void minimize() {
        stage.setIconified(true);
    }

    public void toggleMaximize() {
        if (systemFrame != null) {
            systemFrame.setMaximized(!systemFrame.isMaximized());
            return;
        }
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
        // One pulse after the event rather than inside it: the window is only
        // findable once glass has actually put it on screen, which it finishes
        // doing after the handler returns.
        stage.addEventHandler(WindowEvent.WINDOW_SHOWN, event -> Platform.runLater(this::adoptSystemFrame));
    }

    /**
     * Hands the placement to Windows, now that there is a window to hand over.
     *
     * <p>Nothing here is required for the application to work: if the frame
     * cannot be adopted — another platform, an old Windows, the property turned
     * off — Episort keeps placing the window itself, exactly as it did.
     */
    private void adoptSystemFrame() {
        keepWindowLargeEnoughForItsContent();
        systemFrame = NativeWindowFrame.install(stage).orElse(null);
        if (Boolean.getBoolean("episort.debug.window")) {
            System.out.println(systemFrame == null
                    ? "[episort.window] no system frame; Episort places the window itself"
                    : systemFrame.describe());
            System.out.printf(
                    "[episort.window] stage=%.0fx%.0f content needs at least %.0fx%.0f%n",
                    stage.getWidth(), stage.getHeight(),
                    stage.getScene().getRoot().minWidth(-1), stage.getScene().getRoot().minHeight(-1));
        }
        if (systemFrame != null) {
            // Windows changes the placement without asking, from a drag, from
            // Win+Left, from a snap layout. Following the stage is how the title
            // bar's own maximize button learns about it.
            stage.xProperty().addListener(observable -> syncSystemState());
            stage.yProperty().addListener(observable -> syncSystemState());
            stage.widthProperty().addListener(observable -> syncSystemState());
            stage.heightProperty().addListener(observable -> syncSystemState());
        }
        applyPendingState(workAreaAt(
                stage.getX() + stage.getWidth() / 2, stage.getY() + stage.getHeight() / 2));
        notifyFrameSettled();
    }

    private void notifyFrameSettled() {
        frameSettled = true;
        List<Runnable> waiting = List.copyOf(frameListeners);
        frameListeners.clear();
        waiting.forEach(Runnable::run);
    }

    /**
     * Raises the window's minimum to what its content cannot go below.
     *
     * <p>Measured rather than declared, because the declared one was wishful:
     * the shell needs more room than the 1180×760 it asked for, and below that
     * the interface is not made smaller, it is cut off. Nobody noticed while
     * Episort placed its own windows and never went that small. Windows does:
     * asked for a half of the screen it takes one, and the close button left
     * with the part that no longer fitted.
     *
     * <p>Honouring it is what makes Windows snap this window to its minimum
     * rather than to an exact half — which is what the system does for any
     * application that will not go narrower.
     */
    private void keepWindowLargeEnoughForItsContent() {
        Parent root = stage.getScene().getRoot();
        stage.setMinWidth(Math.max(normalMinWidth, root.minWidth(-1)));
        stage.setMinHeight(Math.max(normalMinHeight, root.minHeight(-1)));
    }

    /** Reads back what Windows did to the window, and reports it once it differs. */
    private void syncSystemState() {
        if (systemFrame == null) {
            return;
        }
        WindowState before = stateModel.state();
        if (systemFrame.isMaximized()) {
            stateModel.enter(WindowState.MAXIMIZED, stateModel.normalBounds().orElseGet(this::currentBounds));
        } else {
            stateModel.restore(currentBounds());
        }
        if (stateModel.state() != before) {
            notifyState();
        }
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
        // Windows pulls a maximized window back to its normal size under the
        // pointer by itself; only the fallback has to do it by hand.
        if (systemFrame == null && stateModel.state() != WindowState.NORMAL && !restoredForDrag) {
            restoreForDrag(event);
        }
        // Only once the press has become a real drag: a plain click must not
        // enter the system loop, or it would swallow the release and with it the
        // double click that maximizes.
        if (moveNatively(event)) {
            return;
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

    /**
     * Hands the rest of the drag to the system, and reports whether it did.
     *
     * <p>With the system frame in place this is the whole of dragging: Windows
     * moves the window, shows its own snap overlay at the edges, offers Snap
     * Assist afterwards, and this method has nothing left to do but let the loop
     * run and read the result back.
     *
     * <p>Without it the move is still the system's, but the edges do nothing, so
     * the drop has to be snapped by hand. No mouse event describes that drop —
     * the loop consumed the release — so it is reconstructed from where the
     * pointer stands inside the window once the loop hands back. The preview
     * follows through an {@link AnimationTimer} rather than mouse events for the
     * same reason; when Windows does not let frames through during its loop the
     * timer simply never fires, and the drag runs without a preview rather than
     * leaving an unpainted one on screen.
     */
    private boolean moveNatively(MouseEvent event) {
        if (!NativeWindowMove.isSupported()) {
            return false;
        }
        double grabOffsetX = event.getScreenX() - stage.getX();
        double grabOffsetY = event.getScreenY() - stage.getY();
        AnimationTimer previewTracker = new AnimationTimer() {
            @Override
            public void handle(long now) {
                updateSnapPreview(stage.getX() + grabOffsetX, stage.getY() + grabOffsetY);
            }
        };
        if (systemFrame == null) {
            previewTracker.start();
        }
        boolean started;
        try {
            started = NativeWindowMove.begin(stage);
        } finally {
            previewTracker.stop();
        }
        if (!started) {
            preview.hide();
            snapTarget = WindowSnapTarget.NONE;
            return false;
        }
        dragging = false;
        dragMoved = false;
        if (systemFrame != null) {
            Platform.runLater(this::syncSystemState);
            event.consume();
            return true;
        }
        // Read now, while the pointer is still where it was let go.
        Point2D dropOffset = NativeWindowMove.pointerOffset(stage)
                .orElseGet(() -> new Point2D(grabOffsetX, grabOffsetY));
        // Applied one pulse later: the window's final position reaches the stage
        // through the same queue as everything else, and reading it here could
        // still return where the drag started — which would snap to the wrong
        // edge, or move the window back.
        Platform.runLater(() -> finishNativeMove(dropOffset.getX(), dropOffset.getY()));
        event.consume();
        return true;
    }

    private void finishNativeMove(double pointerOffsetX, double pointerOffsetY) {
        double dropX = stage.getX() + pointerOffsetX;
        double dropY = stage.getY() + pointerOffsetY;
        evaluateSnapTarget(dropX, dropY);
        applySnapOrKeepVisible(dropX, dropY);
        preview.hide();
        snapTarget = WindowSnapTarget.NONE;
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

    /** Records which edge the given point selects, without touching the preview. */
    private WindowSnapTarget evaluateSnapTarget(double screenX, double screenY) {
        snapArea = workAreaAt(screenX, screenY);
        snapTarget = WindowGeometry.snapTargetAt(screenX, screenY, snapArea);
        return snapTarget;
    }

    private void updateSnapPreview(double screenX, double screenY) {
        if (evaluateSnapTarget(screenX, screenY) == WindowSnapTarget.NONE) {
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

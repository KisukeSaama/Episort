package com.episort.ui;

import javafx.animation.Interpolator;
import javafx.animation.Transition;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.PopupControl;
import javafx.stage.PopupWindow;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * The entrance every dropdown shares: context menus, the menu bar's menus,
 * {@code ComboBox} and {@code MenuButton} popups, and tooltips.
 *
 * <p>None of this can come from the stylesheet. A popup is a window of its own,
 * so its content is laid out and shown in its final state — there is no state
 * change for a CSS transition to carry, and JavaFX has no rule that fires when a
 * scene first appears. The hook is therefore on the window list itself: every
 * {@link PopupControl} the application opens, including the ones built by skins
 * Episort never touches, is caught the moment it is added and starts from the
 * same place.
 *
 * <p>Only {@code PopupControl} is claimed, not every {@code PopupWindow}. The
 * window-snap preview is a bare {@code Popup} that has to answer a drag in the
 * frame it is asked for, and a fade would make it lag the pointer.
 *
 * <p>The modal dialogs are not claimed either, and cannot be until they are
 * given transparent stages. They run on {@code StageStyle.UNDECORATED} with the
 * default opaque scene fill behind an opaque panel, so fading the scene root
 * would fade the panel off the fill rather than off the desktop: the entrance
 * would flash white.
 *
 * <p>The movement is a fade and an 8 px slide out of the anchor, on the 160 ms
 * surface step of docs/design-system.md §6b: a panel unfolding from the control
 * that opened it, not an object flying in. The slide moves the popup's window,
 * not its root — a popup's scene is sized exactly to its content, so offsetting
 * anything inside it is cut off by the window edge and opens a strip of nothing
 * along the opposite one. The travel is applied as a delta each frame rather
 * than against a remembered resting Y, so a popup that JavaFX repositions
 * mid-entrance still lands where JavaFX put it.
 *
 * <p>Closing is not animated, for the reason {@link ViewTransition} gives: the
 * answer to a click has to be immediate. A menu that faded out would still be on
 * screen, taking up the space its own dismissal was meant to give back.
 */
public final class PopupMotion {

    /** A surface changing condition. See docs/design-system.md §6b. */
    public static final Duration POPUP_OPEN = Duration.millis(160);

    /** How far from its resting place a popup starts, in pixels. */
    static final double TRAVEL = 8;

    private static final String RUNNING_KEY = "episort.popupMotion";

    private static boolean installed;

    private PopupMotion() {
    }

    /**
     * Starts animating every popup the application opens from now on. Calling
     * this more than once is harmless: the listener is registered once, and a
     * second registration would play each entrance twice.
     */
    public static void install() {
        if (installed) {
            return;
        }
        installed = true;
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                for (Window window : change.getAddedSubList()) {
                    if (window instanceof PopupControl) {
                        playOpening(window);
                    }
                }
                for (Window window : change.getRemoved()) {
                    if (window instanceof PopupControl) {
                        settle(window);
                    }
                }
            }
        });
    }

    /**
     * The popup is hidden in the same pulse as the show, before anything is
     * rendered, so the first frame the user sees is already the faded one. Doing
     * this after the window is on screen would flash the popup at full strength
     * and then fade it in from nothing.
     *
     * <p>The travel waits one pulse longer. A popup joins the window list while
     * its skin is still placing it, and an offset applied against a position
     * that is overwritten a moment later leaves the popup parked 8 px off its
     * anchor for as long as it stays open — the gap under a combo box. Nothing
     * is lost by waiting: the frame in between is the invisible one.
     */
    private static void playOpening(Window window) {
        Node root = rootOf(window);
        if (root == null) {
            return;
        }
        stopRunning(root);
        root.setOpacity(0);
        Platform.runLater(() -> {
            if (!window.isShowing()) {
                settle(window);
                return;
            }
            Entrance opening = new Entrance(window, root, startOffsetFor(window));
            opening.setOnFinished(event -> root.getProperties().remove(RUNNING_KEY));
            root.getProperties().put(RUNNING_KEY, opening);
            opening.play();
        });
    }

    /**
     * A popup dismissed mid-entrance would otherwise keep animating off screen,
     * and a menu instance is reused: the next thing to read its opacity has to
     * find a finished popup, not a frozen one. Its position needs no repair —
     * the next {@code show} sets it before this one is ever seen again.
     */
    private static void settle(Window window) {
        Node root = rootOf(window);
        if (root == null) {
            return;
        }
        stopRunning(root);
        root.setOpacity(1);
    }

    /**
     * A dropdown belongs to the control that opened it, and the entrance says
     * so by sliding out of it: down from the anchor when the popup sits below,
     * up from it when there was no room underneath and JavaFX flipped the popup
     * above. Anything with no anchor to read — a tooltip, a stray popup —
     * unfolds downwards, which is where a dropdown goes by default.
     */
    static double startOffsetFor(Window window) {
        return opensDownwards(window) ? -TRAVEL : TRAVEL;
    }

    private static boolean opensDownwards(Window window) {
        if (!(window instanceof PopupWindow popup)) {
            return true;
        }
        Node anchor = popup.getOwnerNode();
        if (anchor == null || anchor.getScene() == null) {
            return true;
        }
        Bounds anchorOnScreen = anchor.localToScreen(anchor.getBoundsInLocal());
        return anchorOnScreen == null || window.getY() >= anchorOnScreen.getMinY();
    }

    private static Node rootOf(Window window) {
        Scene scene = window.getScene();
        return scene == null ? null : scene.getRoot();
    }

    private static void stopRunning(Node node) {
        if (node.getProperties().remove(RUNNING_KEY) instanceof Transition running) {
            running.stop();
        }
    }

    /** Fade and slide in one interpolation: the window moves, the root fades. */
    private static final class Entrance extends Transition {
        private final Window window;
        private final Node root;
        private final double startOffset;
        private double applied;

        private Entrance(Window window, Node root, double startOffset) {
            this.window = window;
            this.root = root;
            this.startOffset = startOffset;
            setCycleDuration(POPUP_OPEN);
            setInterpolator(Interpolator.EASE_OUT);
            // The offset is in place before the first frame is painted, so the
            // popup is never seen at its resting place and then pulled off it.
            interpolate(0);
        }

        @Override
        protected void interpolate(double fraction) {
            root.setOpacity(fraction);
            double offset = startOffset * (1 - fraction);
            window.setY(window.getY() - applied + offset);
            applied = offset;
        }
    }
}

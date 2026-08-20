package com.episort.ui;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.util.Duration;

/**
 * The movements the stylesheet cannot carry: a screen arriving, a full-window
 * overlay appearing or leaving, and a table answering a filter.
 *
 * <p>Both are driven here rather than from CSS because the nodes involved are
 * long-lived. A screen root is built once and put back on the view host every
 * time the user returns to it, still holding the opacity and offset it had when
 * it left. A CSS entrance class applied to such a node animates it *away* from
 * its current value first, which reads as a flash. Setting the start state
 * directly and playing forward cannot.
 *
 * <p>Only the incoming side is animated. Fading a screen out before showing the
 * next one would add its duration to every navigation, and this is a tool: the
 * answer to a click has to be immediate, then settle.
 */
public final class ViewTransition {

    /** A whole view or overlay arriving or leaving. See docs/design-system.md §6b. */
    public static final Duration VIEW_CHANGE = Duration.millis(240);

    /** A surface changing condition. See docs/design-system.md §6b. */
    public static final Duration SURFACE_CHANGE = Duration.millis(160);

    /** How far below its resting place an incoming screen starts, in pixels. */
    static final double RISE = 8;

    /** How faint a table's rows are the instant a filter replaces them. */
    static final double SWAP_FROM = 0.55;

    private ViewTransition() {
    }

    /**
     * Plays a screen's entrance: it rises the last few pixels into place while
     * fading in. Any entrance still running on the node is replaced, so rapid
     * navigation cannot stack animations.
     */
    public static void playEntrance(Node node) {
        stopRunning(node);
        node.setOpacity(0);
        node.setTranslateY(RISE);
        Timeline entrance = new Timeline(new KeyFrame(
                VIEW_CHANGE,
                new KeyValue(node.opacityProperty(), 1.0, Interpolator.EASE_OUT),
                new KeyValue(node.translateYProperty(), 0.0, Interpolator.EASE_OUT)));
        entrance.setOnFinished(event -> node.getProperties().remove(RUNNING_KEY));
        node.getProperties().put(RUNNING_KEY, entrance);
        entrance.play();
    }

    /**
     * Fades a full-window overlay in or out, taking its visibility with it: the
     * node is only hidden once it has finished fading, and is only made visible
     * before it starts. While fading out it stops taking clicks, so a veil on
     * its way off screen cannot swallow the first interaction after it.
     */
    public static void fade(Node node, boolean visible) {
        stopRunning(node);
        if (visible && node.isVisible() && node.getOpacity() == 1.0) {
            return;
        }
        if (!visible && !node.isVisible()) {
            return;
        }
        if (visible) {
            node.setOpacity(0);
            node.setVisible(true);
            node.setManaged(true);
            node.setMouseTransparent(false);
        } else {
            node.setMouseTransparent(true);
        }
        Timeline fade = new Timeline(new KeyFrame(
                VIEW_CHANGE,
                new KeyValue(node.opacityProperty(), visible ? 1.0 : 0.0, Interpolator.EASE_OUT)));
        fade.setOnFinished(event -> {
            node.getProperties().remove(RUNNING_KEY);
            if (!visible) {
                node.setVisible(false);
                node.setManaged(false);
                node.setMouseTransparent(false);
            }
        });
        node.getProperties().put(RUNNING_KEY, fade);
        fade.play();
    }

    /**
     * Rides the node's opacity to a target without touching its visibility —
     * used for the content dimming behind a blocking overlay, which would
     * otherwise step from full to dim in a single frame.
     */
    public static void fadeTo(Node node, double target) {
        stopRunning(node);
        if (node.getOpacity() == target) {
            return;
        }
        Timeline fade = new Timeline(new KeyFrame(
                VIEW_CHANGE,
                new KeyValue(node.opacityProperty(), target, Interpolator.EASE_OUT)));
        fade.setOnFinished(event -> node.getProperties().remove(RUNNING_KEY));
        node.getProperties().put(RUNNING_KEY, fade);
        fade.play();
    }

    /**
     * The rows of a table coming back after a filter chip replaced them. Only
     * the rows: the node handed in is the virtual flow, so the column headers,
     * which did not change, hold still while the list under them does not.
     *
     * <p>Opacity alone, no offset. The flow is clipped to the table, so moving
     * it would open a strip of empty table along one edge for the length of the
     * animation, and a seam is a worse answer than no movement at all.
     *
     * <p>Typing in the search box re-filters too, and is deliberately not
     * routed here: a settle per keystroke would make the table strobe.
     */
    public static void playContentSwap(Node content) {
        if (content == null) {
            return;
        }
        stopRunning(content);
        content.setOpacity(SWAP_FROM);
        Timeline swap = new Timeline(new KeyFrame(
                SURFACE_CHANGE,
                new KeyValue(content.opacityProperty(), 1.0, Interpolator.EASE_OUT)));
        swap.setOnFinished(event -> content.getProperties().remove(RUNNING_KEY));
        content.getProperties().put(RUNNING_KEY, swap);
        swap.play();
    }

    private static final String RUNNING_KEY = "episort.viewTransition";

    private static void stopRunning(Node node) {
        if (node.getProperties().remove(RUNNING_KEY) instanceof Timeline running) {
            running.stop();
        }
    }
}

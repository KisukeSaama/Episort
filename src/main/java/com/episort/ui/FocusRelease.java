package com.episort.ui;

import java.util.Objects;
import javafx.event.EventTarget;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.MouseEvent;

/**
 * Gives a scene the "click away to leave the field" reflex.
 *
 * <p>JavaFX only moves the focus when the press lands on something that takes
 * it, and most of a screen — headings, spacers, chip rows, the background of a
 * pane — takes nothing. So the search box stayed lit with its caret blinking
 * long after the user had clicked elsewhere, and kept swallowing keystrokes
 * meant for the table.
 *
 * <p>The filter runs on the capture phase, before the pressed node claims the
 * focus for itself, so a press on a button still ends with the button focused;
 * only a press on nothing at all lands the focus back on the scene root.
 */
public final class FocusRelease {

    private FocusRelease() {
    }

    /** Installs the reflex on {@code scene}; safe to call once per scene. */
    public static void install(Scene scene) {
        Objects.requireNonNull(scene, "scene");
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (releasesFocus(scene.getFocusOwner(), event.getTarget())) {
                scene.getRoot().requestFocus();
            }
        });
    }

    /**
     * Whether this press should take the focus off the field that holds it.
     *
     * <p>Only text inputs are released: every other control either keeps the
     * focus harmlessly or loses it to the press on its own. A press inside the
     * field itself — including a drag that starts a selection — is left alone.
     */
    static boolean releasesFocus(Node focusOwner, EventTarget target) {
        if (!(focusOwner instanceof TextInputControl)) {
            return false;
        }
        return !(target instanceof Node node) || !isInside(node, focusOwner);
    }

    private static boolean isInside(Node node, Node ancestor) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current == ancestor) {
                return true;
            }
        }
        return false;
    }
}

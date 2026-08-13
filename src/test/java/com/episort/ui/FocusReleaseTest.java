package com.episort.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The rule behind "click away and the search field lets go": what a press has
 * to land on for the focus to leave the field.
 *
 * <p>Only the decision is exercised here. Wiring it to a scene is one event
 * filter in {@link FocusRelease#install}, while the part that was wrong in the
 * app — nothing released the field on a press that landed on the background —
 * is this predicate.
 *
 * <p>Instantiating a control initialises the toolkit, so the class skips itself
 * where none is available rather than failing a headless build.
 */
class FocusReleaseTest {

    /** A field that lets the test place a node where its skin would sit. */
    private static final class ProbeField extends TextField {
        void addSkinNode(Node node) {
            getChildren().add(node);
        }
    }

    @BeforeEach
    void requireToolkit() {
        assumeTrue(startToolkit(), "no JavaFX toolkit available on this machine");
    }

    @Test
    void aPressOnTheBackgroundReleasesTheField() {
        TextField field = new TextField();
        Label heading = new Label("Fichiers");
        new VBox(heading, field);

        assertTrue(FocusRelease.releasesFocus(field, heading));
    }

    @Test
    void aPressInsideTheFieldKeepsIt() {
        ProbeField field = new ProbeField();
        Label caret = new Label("caret");
        new VBox(field);
        // A drag that selects text reports a piece of the field's own skin as
        // the target, not the control.
        field.addSkinNode(caret);

        assertFalse(FocusRelease.releasesFocus(field, field));
        assertFalse(FocusRelease.releasesFocus(field, caret));
    }

    @Test
    void anythingButATextInputIsLeftAlone() {
        Label focused = new Label("not a field");
        Label elsewhere = new Label("elsewhere");
        new VBox(focused, elsewhere);

        assertFalse(FocusRelease.releasesFocus(focused, elsewhere));
        assertFalse(FocusRelease.releasesFocus(null, elsewhere));
    }

    private static final AtomicBoolean TOOLKIT_STARTED = new AtomicBoolean();

    private static synchronized boolean startToolkit() {
        if (TOOLKIT_STARTED.get()) {
            return true;
        }
        try {
            CountDownLatch ready = new CountDownLatch(1);
            Platform.startup(ready::countDown);
            Platform.setImplicitExit(false);
            if (!ready.await(20, TimeUnit.SECONDS)) {
                return false;
            }
            TOOLKIT_STARTED.set(true);
            return true;
        } catch (IllegalStateException alreadyRunning) {
            TOOLKIT_STARTED.set(true);
            return true;
        } catch (RuntimeException | InterruptedException | LinkageError unavailable) {
            return false;
        }
    }
}

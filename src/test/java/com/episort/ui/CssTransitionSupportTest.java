package com.episort.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

/**
 * Guards the assumption the motion layer of {@code app.css} rests on: that this
 * JavaFX runtime interpolates a CSS state change instead of snapping to it.
 *
 * <p>CSS transitions arrived in JavaFX 23, so a downgrade of the toolkit would
 * silently turn every animated state back into a jump — visible to a user,
 * invisible to a compiler. The subject is {@code -fx-text-fill} on a
 * {@link Button} because that is the workhorse of §28 of the stylesheet, and
 * because a control with a skin is the case that could plausibly differ from a
 * bare region.
 *
 * <p>It skips itself where no display is available rather than failing, so a
 * headless build is not blocked by a check about on-screen behaviour.
 */
class CssTransitionSupportTest {

    private static final String STYLESHEET = """
            .probe {
                -fx-text-fill: #000000;
                transition: -fx-text-fill 400ms linear;
            }
            .probe.lit {
                -fx-text-fill: #ffffff;
            }
            """;

    @Test
    void interpolatesAStateChangeInsteadOfSnappingToIt() throws Exception {
        assumeTrue(startToolkit(), "no JavaFX toolkit available on this machine");

        List<Color> samples = new ArrayList<>();
        CountDownLatch done = new CountDownLatch(1);

        Platform.runLater(() -> {
            Button probe = new Button("probe");
            probe.getStyleClass().add("probe");
            Scene scene = new Scene(new StackPane(probe), 120, 60);
            scene.getStylesheets().add("data:text/css;base64,"
                    + Base64.getEncoder().encodeToString(STYLESHEET.getBytes()));

            Stage stage = new Stage();
            stage.setScene(scene);
            // Off-screen and invisible: the test needs pulses, not a window in
            // the developer's face.
            stage.setOpacity(0);
            stage.setX(-20_000);
            stage.setY(-20_000);
            stage.show();
            probe.applyCss();

            new AnimationTimer() {
                private int frames;

                @Override
                public void handle(long now) {
                    if (frames == 0) {
                        // CSS never transitions the first application of a
                        // property, only a later change to it, so the state
                        // change waits for a pulse that has applied the start.
                        probe.getStyleClass().add("lit");
                    } else if (probe.getTextFill() instanceof Color color) {
                        samples.add(color);
                    }
                    if (++frames >= 40) {
                        stop();
                        stage.close();
                        done.countDown();
                    }
                }
            }.start();
        });

        assertTrue(done.await(30, TimeUnit.SECONDS), "the probe never finished sampling");
        assertTrue(samples.size() > 5, "the probe collected too few samples: " + samples.size());

        boolean sawIntermediateShade = samples.stream()
                .anyMatch(color -> color.getRed() > 0.02 && color.getRed() < 0.98);
        assertTrue(sawIntermediateShade,
                "text fill snapped instead of transitioning; samples were " + samples);
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

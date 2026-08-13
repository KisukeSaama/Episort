package com.episort.ui;

import javafx.animation.AnimationTimer;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.ScrollEvent;

/**
 * Replaces JavaFX's stepped wheel scrolling with a continuous one.
 *
 * <p>Out of the box a wheel notch jumps the viewport by a fixed distance in a
 * single frame. On a long table that reads as the content teleporting, and it
 * is the one interaction in this application where the difference with every
 * other desktop application is immediate — a released wheel notch should glide
 * to a stop, not land.
 *
 * <p>Works on anything backed by a {@link ScrollBar}: a {@code ScrollPane} and a
 * virtualised {@code TableView} both expose one, both count from {@code min} to
 * {@code max} with a {@code visibleAmount} giving the fraction of the content on
 * screen, and that is all the conversion from wheel pixels needs.
 *
 * <p>The scroll bar keeps being the source of truth, so dragging its thumb,
 * keyboard navigation and {@code scrollTo} all keep working: a glide in flight
 * is simply abandoned in favour of wherever the bar has been put.
 */
public final class SmoothScroll {

    private SmoothScroll() {
    }

    /**
     * Smooths vertical wheel scrolling inside the given control.
     *
     * <p>The scroll bar is resolved lazily: a table has no skin, and therefore
     * no scroll bar, until it has been laid out at least once.
     */
    public static void install(Parent scrollable) {
        Glide glide = new Glide(scrollable);
        scrollable.addEventFilter(ScrollEvent.SCROLL, glide::onScroll);
    }

    private static final class Glide {
        private final Parent scrollable;
        private final AnimationTimer timer;

        private ScrollBar bar;
        private double target;
        private boolean gliding;
        private long lastNanos;

        Glide(Parent scrollable) {
            this.scrollable = scrollable;
            this.timer = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    step(now);
                }
            };
        }

        void onScroll(ScrollEvent event) {
            // Shift+wheel is the horizontal gesture, which the table owns.
            if (event.isShiftDown() || event.getDeltaY() == 0) {
                return;
            }
            ScrollBar vertical = verticalBar();
            if (vertical == null || !vertical.isVisible()) {
                return;
            }
            double perPixel = barUnitsPerPixel(vertical);
            if (perPixel <= 0) {
                return;
            }
            // A glide already running keeps its own target as the starting
            // point, so a second notch adds to the first instead of restarting
            // from where the content happens to be mid-flight.
            double from = gliding ? target : vertical.getValue();
            target = clamp(from - event.getDeltaY() * perPixel, vertical.getMin(), vertical.getMax());
            event.consume();
            if (!gliding) {
                gliding = true;
                lastNanos = 0;
                timer.start();
            }
        }

        private void step(long now) {
            ScrollBar vertical = bar;
            if (vertical == null) {
                stop();
                return;
            }
            if (lastNanos == 0) {
                lastNanos = now;
                return;
            }
            double elapsedSeconds = (now - lastNanos) / 1_000_000_000.0;
            lastNanos = now;
            double range = vertical.getMax() - vertical.getMin();
            double next = ScrollEasing.advance(vertical.getValue(), target, elapsedSeconds);
            if (ScrollEasing.settled(next, target, range)) {
                vertical.setValue(target);
                stop();
                return;
            }
            vertical.setValue(next);
        }

        private void stop() {
            gliding = false;
            timer.stop();
        }

        /**
         * How much of the bar's range one pixel of wheel travel is worth.
         *
         * <p>{@code visibleAmount} is the on-screen fraction of the content, so
         * the content is {@code viewport / visibleAmount} tall and the part that
         * can actually be scrolled past is what is left once the viewport
         * itself is taken out.
         */
        private double barUnitsPerPixel(ScrollBar vertical) {
            double range = vertical.getMax() - vertical.getMin();
            double visibleFraction = vertical.getVisibleAmount() / (range == 0 ? 1 : range);
            double viewportPixels = vertical.getHeight();
            if (range <= 0 || viewportPixels <= 0 || visibleFraction <= 0 || visibleFraction >= 1) {
                return 0;
            }
            double scrollablePixels = viewportPixels * (1 - visibleFraction) / visibleFraction;
            return range / scrollablePixels;
        }

        private ScrollBar verticalBar() {
            if (bar == null) {
                for (Node node : scrollable.lookupAll(".scroll-bar")) {
                    if (node instanceof ScrollBar candidate && candidate.getOrientation() == Orientation.VERTICAL) {
                        bar = candidate;
                        break;
                    }
                }
            }
            return bar;
        }

        private static double clamp(double value, double min, double max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}

package com.episort.ui.diagnostics;

/**
 * One second's worth of frame timing, as measured on the JavaFX pulse.
 *
 * @param framesPerSecond frames counted over the window, divided by its real duration
 * @param worstFrameMillis longest gap between two consecutive frames in the window
 * @param frames how many frames the window counted
 */
public record FrameRateSample(double framesPerSecond, double worstFrameMillis, int frames) {
}

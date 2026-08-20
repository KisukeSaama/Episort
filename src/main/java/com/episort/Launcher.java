package com.episort;

import com.episort.ui.platform.RenderTuning;

/**
 * Bootstrap entry point that avoids JavaFX's "modules required" check.
 *
 * <p>When {@code java} is invoked with a main class that extends
 * {@link javafx.application.Application}, the JavaFX runtime refuses to start
 * unless its modules are on {@code --module-path}. The Gradle Application
 * plugin generates start scripts that put every dependency on the classpath
 * instead. By using a launcher class that is not itself an
 * {@code Application}, we sidestep the check; {@link EpisortApplication} is
 * loaded normally from the classpath when {@code main} forwards control.
 *
 * <p>It is also the only place where the JavaFX pulse rate can still be set:
 * the toolkit reads it once, when it starts, which happens inside
 * {@link EpisortApplication#main}.
 */
public final class Launcher {
    public static void main(String[] args) {
        RenderTuning.applyDisplayPulse();
        EpisortApplication.main(args);
    }

    private Launcher() {
    }
}

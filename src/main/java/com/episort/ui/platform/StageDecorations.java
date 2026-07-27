package com.episort.ui.platform;

import javafx.scene.layout.Region;
import javafx.stage.Stage;

/** Compatibility entry point for the custom title-bar behavior. */
public final class StageDecorations {
    private StageDecorations() {
    }

    public static WindowManager install(Stage stage, Region dragRegion) {
        return WindowManager.install(stage, dragRegion);
    }
}

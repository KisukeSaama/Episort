package com.episort.ui;

import javafx.scene.text.Font;

public final class Fonts {
    private static boolean loaded;

    private Fonts() {}

    public static synchronized void loadAll() {
        if (loaded) {
            return;
        }
        load("/fonts/Inter-Regular.ttf");
        load("/fonts/Inter-SemiBold.ttf");
        load("/fonts/Inter-Bold.ttf");
        load("/fonts/Inter-ExtraBold.ttf");
        load("/fonts/JetBrainsMono-Regular.ttf");
        load("/fonts/JetBrainsMono-Bold.ttf");
        loaded = true;
    }

    private static void load(String resource) {
        var stream = Fonts.class.getResourceAsStream(resource);
        if (stream == null) {
            return;
        }
        Font.loadFont(stream, 12);
    }
}

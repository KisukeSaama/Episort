package com.episort.ui;

import javafx.scene.text.Font;

public final class Fonts {
    private static boolean loaded;

    private Fonts() {}

    public static synchronized void loadAll() {
        if (loaded) {
            return;
        }
        load("/fonts/InstrumentSans-Regular.ttf");
        load("/fonts/InstrumentSans-Medium.ttf");
        load("/fonts/InstrumentSans-SemiBold.ttf");
        load("/fonts/InstrumentSans-Bold.ttf");
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

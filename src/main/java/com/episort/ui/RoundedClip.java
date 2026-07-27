package com.episort.ui;

import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;

public final class RoundedClip {
    private RoundedClip() {
    }

    public static void install(Region region, double radius) {
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(region.widthProperty());
        clip.heightProperty().bind(region.heightProperty());
        clip.setArcWidth(radius * 2);
        clip.setArcHeight(radius * 2);
        region.setClip(clip);
    }
}

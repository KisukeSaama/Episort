package com.episort.ui;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import java.util.Objects;

/** Applies the active theme class to main and auxiliary JavaFX roots. */
public final class ThemeStyles {
    /**
     * What the window shows before the scene graph has been painted.
     *
     * <p>A Scene with no fill is white, and the shell's own background is a CSS
     * gradient on the root node, which only exists once JavaFX has rendered a
     * frame. Windows shows the raw scene surface in between: restoring the
     * window, coming back to it from another application or resizing it flashed
     * white for a frame over a near-black interface. These are the first colour
     * stop of each theme's shell gradient, so the placeholder is the background
     * rather than a contrast against it.
     */
    private static final Color DARK_FILL = Color.web("#07080b");
    private static final Color LIGHT_FILL = Color.web("#f1ede7");

    private static final Set<Parent> ROOTS =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<Scene> SCENES =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static Theme current = Theme.DARK;
    private static final String LIGHT_STYLESHEET = Objects.requireNonNull(
            ThemeStyles.class.getResource("/styles/light.css"),
            "Missing stylesheet /styles/light.css").toExternalForm();

    private ThemeStyles() {}

    public static void register(Parent root) {
        ROOTS.add(root);
        apply(root, current);
    }

    /**
     * Opts a scene into the themed window fill. Not for scenes that are meant to
     * stay transparent: those draw their own rounded surface over the desktop.
     */
    public static void registerScene(Scene scene) {
        SCENES.add(scene);
        scene.setFill(windowFill(current));
    }

    static Color windowFill(Theme theme) {
        return theme == Theme.DARK ? DARK_FILL : LIGHT_FILL;
    }

    public static void setCurrent(Theme theme) {
        current = theme;
        for (Parent root : Set.copyOf(ROOTS)) apply(root, theme);
        for (Scene scene : Set.copyOf(SCENES)) scene.setFill(windowFill(theme));
    }

    private static void apply(Parent root, Theme theme) {
        root.getStyleClass().removeAll("theme-dark", "theme-light");
        root.getStyleClass().add(theme == Theme.DARK ? "theme-dark" : "theme-light");
        root.getStylesheets().remove(LIGHT_STYLESHEET);
        if (theme == Theme.LIGHT) root.getStylesheets().add(LIGHT_STYLESHEET);
    }
}

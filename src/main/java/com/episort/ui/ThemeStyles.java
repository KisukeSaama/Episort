package com.episort.ui;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import javafx.scene.Parent;
import java.util.Objects;

/** Applies the active theme class to main and auxiliary JavaFX roots. */
public final class ThemeStyles {
    private static final Set<Parent> ROOTS =
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

    public static void setCurrent(Theme theme) {
        current = theme;
        for (Parent root : Set.copyOf(ROOTS)) apply(root, theme);
    }

    private static void apply(Parent root, Theme theme) {
        root.getStyleClass().removeAll("theme-dark", "theme-light");
        root.getStyleClass().add(theme == Theme.DARK ? "theme-dark" : "theme-light");
        root.getStylesheets().remove(LIGHT_STYLESHEET);
        if (theme == Theme.LIGHT) root.getStylesheets().add(LIGHT_STYLESHEET);
    }
}

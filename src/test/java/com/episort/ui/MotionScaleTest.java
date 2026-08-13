package com.episort.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Keeps the stylesheet's motion inside the scale documented in
 * docs/design-system.md §6b.
 *
 * <p>Durations cannot be tokenised in JavaFX CSS — looked-up values exist for
 * colours only — so nothing stops a fourth duration or a springy easing from
 * appearing in one rule and quietly making the interface inconsistent. This is
 * the enforcement the missing token would have provided.
 */
class MotionScaleTest {

    /** See docs/design-system.md §6b. The 240 ms view step is Java-side only. */
    private static final Set<String> ALLOWED_DURATIONS = Set.of("90ms", "160ms");

    /** The same three steps, in milliseconds, for the Java-side constants. */
    private static final Set<Double> SCALE_MILLIS = Set.of(90.0, 160.0, 240.0);

    private static final Set<String> ALLOWED_EASINGS = Set.of("ease-out");

    private static final Pattern TRANSITION = Pattern.compile("transition:\\s*([^;]+);", Pattern.DOTALL);

    @Test
    void everyTransitionUsesTheDocumentedScale() throws IOException {
        String css = stylesheet();
        Set<String> durations = new LinkedHashSet<>();
        Set<String> easings = new LinkedHashSet<>();

        Matcher declarations = TRANSITION.matcher(css);
        int found = 0;
        while (declarations.find()) {
            for (String single : declarations.group(1).split(",")) {
                found++;
                List<String> parts = List.of(single.trim().split("\\s+"));
                for (String part : parts) {
                    if (part.endsWith("ms") || part.endsWith("s")) {
                        durations.add(part);
                    } else if (!part.startsWith("-fx-") && !part.equals("all")) {
                        easings.add(part);
                    }
                }
            }
        }

        assertTrue(found > 0, "no transition declared in app.css");
        assertTrue(ALLOWED_DURATIONS.containsAll(durations),
                "durations outside the motion scale: " + durations);
        assertTrue(ALLOWED_EASINGS.containsAll(easings),
                "easings outside the motion scale: " + easings);
    }

    @Test
    void neverTransitionsAPropertyJavaFxCannotInterpolate() throws IOException {
        String css = stylesheet();
        Matcher declarations = TRANSITION.matcher(css);
        while (declarations.find()) {
            String declaration = declarations.group(1);
            // A composite CSS property: JavaFX holds the start value and flips
            // it, so the declaration would parse, do nothing, and warn nobody.
            assertFalse(declaration.contains("-fx-background-color"),
                    "background colour cannot be transitioned: " + declaration);
            assertFalse(declaration.contains("-fx-border-color"),
                    "border colour cannot be transitioned: " + declaration);
        }
    }

    /**
     * The stylesheet is only half the motion layer. Whatever CSS cannot carry —
     * a screen mounted for the first time, a popup window opening, a list
     * replaced wholesale — is timed by a constant in Java, where nothing else
     * would stop a fourth duration from appearing.
     */
    @Test
    void everyJavaSideDurationUsesTheDocumentedScale() {
        assertTrue(SCALE_MILLIS.contains(ViewTransition.VIEW_CHANGE.toMillis()),
                "ViewTransition.VIEW_CHANGE is outside the motion scale: "
                        + ViewTransition.VIEW_CHANGE);
        assertTrue(SCALE_MILLIS.contains(ViewTransition.SURFACE_CHANGE.toMillis()),
                "ViewTransition.SURFACE_CHANGE is outside the motion scale: "
                        + ViewTransition.SURFACE_CHANGE);
        assertTrue(SCALE_MILLIS.contains(PopupMotion.POPUP_OPEN.toMillis()),
                "PopupMotion.POPUP_OPEN is outside the motion scale: " + PopupMotion.POPUP_OPEN);
    }

    /**
     * The stylesheet with its comments removed. §28 documents the rules by
     * quoting them, including the declaration that must never be written, so
     * scanning the raw file would flag the explanation of the rule as a
     * breach of it.
     */
    private static String stylesheet() throws IOException {
        try (var stream = MotionScaleTest.class.getResourceAsStream("/styles/app.css")) {
            assertNotNull(stream);
            String css = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return css.replaceAll("(?s)/\\*.*?\\*/", "");
        }
    }
}

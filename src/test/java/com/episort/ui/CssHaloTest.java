package com.episort.ui;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Keeps coloured glows out of the stylesheets, per §1.6 of
 * docs/design-system.md.
 *
 * <p>The interface used to answer hover and focus with an accent-coloured
 * {@code dropshadow} at zero offset — a halo. Three things were wrong with it
 * and none of them are visible in a diff: it reads as a light-emitting object
 * on a matte surface, it spreads the answer over a dozen soft pixels where a
 * control needs an edge, and any node under an {@code -fx-effect} loses
 * subpixel text rendering (§2.2), so the halos were quietly deciding how a
 * third of the application's text was rasterised.
 *
 * <p>What survives is elevation: black shadows, offset downward, saying how far
 * a surface sits above the one behind it. This test states exactly that — the
 * ink must be one of the two shadow inks, and the shadow must fall somewhere.
 */
class CssHaloTest {

    /**
     * The dark theme's black and the light theme's warm ink. Both are neutral
     * against their own ground; neither is the accent.
     */
    private static final Set<String> SHADOW_INKS = Set.of("0,0,0", "55,43,34");

    private static final List<String> STYLESHEETS = List.of("/styles/app.css", "/styles/light.css");

    private static final Pattern EFFECT = Pattern.compile("-fx-effect:\\s*([^;]+);", Pattern.DOTALL);

    private static final Pattern SHADOW = Pattern.compile(
            "dropshadow\\(\\s*\\w+\\s*,\\s*rgba?\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*(?:,[^)]*)?\\)"
                    + "\\s*,\\s*([-\\d.]+)\\s*,\\s*([-\\d.]+)\\s*,\\s*([-\\d.]+)\\s*,\\s*([-\\d.]+)\\s*\\)");

    @Test
    void everyShadowIsNeutralInkAndNotAHalo() throws IOException {
        int found = 0;
        for (String stylesheet : STYLESHEETS) {
            String css = read(stylesheet);
            Matcher declarations = EFFECT.matcher(css);
            while (declarations.find()) {
                String declaration = declarations.group(1).trim();
                if (declaration.equals("null")) {
                    continue;
                }
                found++;
                Matcher shadow = SHADOW.matcher(declaration);
                if (!shadow.find()) {
                    fail(stylesheet + " declares an effect that is neither null nor a "
                            + "dropshadow this test can read: " + declaration);
                }
                String ink = shadow.group(1) + "," + shadow.group(2) + "," + shadow.group(3);
                assertTrue(SHADOW_INKS.contains(ink),
                        stylesheet + " draws a coloured shadow — that is a halo, not elevation: "
                                + declaration);
                double verticalOffset = Double.parseDouble(shadow.group(7));
                assertTrue(verticalOffset > 0,
                        stylesheet + " draws a shadow with no fall, which is a glow whatever "
                                + "its colour: " + declaration);
            }
        }
        assertTrue(found > 0, "no -fx-effect declared in either stylesheet");
    }

    /**
     * The stylesheet with its comments removed: §28 of {@code app.css} and this
     * project's other comments quote the declarations they forbid, and scanning
     * the raw file would flag the explanation of a rule as a breach of it.
     */
    private static String read(String resource) throws IOException {
        try (var stream = CssHaloTest.class.getResourceAsStream(resource)) {
            assertNotNull(stream, "missing stylesheet " + resource);
            String css = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return css.replaceAll("(?s)/\\*.*?\\*/", "");
        }
    }
}

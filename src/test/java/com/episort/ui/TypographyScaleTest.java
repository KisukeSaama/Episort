package com.episort.ui;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Keeps the stylesheet's typography inside the scale documented in
 * docs/design-system.md §2.2.
 *
 * <p>The weight assertion is the important one. Instrument Sans ships Medium
 * and SemiBold as their own RIBBI families, and JavaFX's font model holds one
 * regular and one bold face per family: under {@code "Instrument Sans"} it
 * knows Regular and Bold and nothing else. A rule that asks for weight 500 or
 * 600 without naming the family that carries it therefore renders at 400, in
 * silence — no warning, no fallback, just a caption that reads like body copy.
 * The whole hierarchy went that way once; this is what stops it happening
 * again.
 */
class TypographyScaleTest {

    /** See docs/design-system.md §2.2. Six steps, nothing between them. */
    private static final Set<String> ALLOWED_SIZES = Set.of("10", "13", "17", "22", "28", "36");

    /** The family that actually carries each weight, as JavaFX registers them. */
    private static final Map<String, String> FAMILY_FOR_WEIGHT = Map.of(
            "400", "\"Instrument Sans\"",
            "500", "\"Instrument Sans Medium\"",
            "600", "\"Instrument Sans SemiBold\"",
            "700", "\"Instrument Sans\"");

    private static final Pattern RULE = Pattern.compile("([^{}]*)\\{([^{}]*)\\}");
    private static final Pattern SIZE = Pattern.compile("-fx-font-size:\\s*([0-9.]+)");
    private static final Pattern WEIGHT = Pattern.compile("-fx-font-weight:\\s*([0-9]+)");
    private static final Pattern FAMILY = Pattern.compile("-fx-font-family:\\s*([^;]+);");

    @Test
    void everyFontSizeIsOneOfTheSixSteps() throws IOException {
        Set<String> sizes = new LinkedHashSet<>();
        Matcher matcher = SIZE.matcher(stylesheet());
        while (matcher.find()) {
            sizes.add(matcher.group(1));
        }
        assertTrue(!sizes.isEmpty(), "no font size declared in app.css");
        assertTrue(ALLOWED_SIZES.containsAll(sizes), "font sizes outside the scale: " + sizes);
    }

    @Test
    void everyWeightNamesTheFamilyThatCarriesIt() throws IOException {
        List<String> breaches = new ArrayList<>();
        Matcher rules = RULE.matcher(stylesheet());
        while (rules.find()) {
            String selector = rules.group(1).trim().replaceAll("\\s+", " ");
            String body = rules.group(2);
            Matcher weight = WEIGHT.matcher(body);
            if (!weight.find()) {
                continue;
            }
            String expected = FAMILY_FOR_WEIGHT.get(weight.group(1));
            assertNotNull(expected, "undocumented font weight in " + selector);
            Matcher family = FAMILY.matcher(body);
            // Declared in the same rule, not inherited: a lower-specificity rule
            // on the same node may well name a different face, and the cascade
            // merges the two into a weight the font cannot honour.
            if (!family.find() || !family.group(1).trim().startsWith(expected)) {
                breaches.add(selector + " asks for " + weight.group(1)
                        + " without leading with " + expected);
            }
        }
        assertTrue(breaches.isEmpty(), "weights that cannot render as asked:\n  "
                + String.join("\n  ", breaches));
    }

    @Test
    void everySceneRootCarriesTheInterfaceFont() throws IOException {
        String css = stylesheet();
        // Dialog stages, context menus, combo popups and tooltips each own a
        // scene root of their own, and -fx-font-family does not cross that line.
        for (String root : List.of(".app-shell", ".custom-dialog", ".tmdb-dialog",
                ".file-properties-dialog", ".context-menu", ".combo-box-popup", ".tooltip")) {
            assertTrue(declaresInterfaceFont(css, root),
                    root + " is a scene root and must declare the interface font");
        }
    }

    private static boolean declaresInterfaceFont(String css, String selector) {
        Matcher rules = RULE.matcher(css);
        while (rules.find()) {
            List<String> selectors = List.of(rules.group(1).split(","));
            boolean named = selectors.stream().anyMatch(s -> s.trim().equals(selector));
            if (named && FAMILY.matcher(rules.group(2)).find()) {
                return true;
            }
        }
        return false;
    }

    /** Comments quote the declarations they warn against, so they are stripped. */
    private static String stylesheet() throws IOException {
        try (var stream = TypographyScaleTest.class.getResourceAsStream("/styles/app.css")) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("(?s)/\\*.*?\\*/", "");
        }
    }
}

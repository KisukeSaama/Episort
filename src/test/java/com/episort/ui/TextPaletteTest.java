package com.episort.ui;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Keeps text ink inside the palette documented in docs/design-system.md §2.1.
 *
 * <p>The four text tokens had grown into twelve: an `#a8b0bd` here, a
 * `#d2d8e0` there, and a parallel ramp of white at 0.42 / 0.45 / 0.58 / 0.62 /
 * 0.72 / 0.86 opacity doing the same job with different numbers. No single one
 * of them looks wrong on its own — that is exactly why they accumulate — but a
 * screen carrying five near-identical greys reads as approximate rather than
 * decided.
 *
 * <p>Colour is also the app's status channel (§1.2), so a hue that is neither
 * the accent nor one of the three status inks is a legend the user has to
 * decode. A yellow had appeared twice.
 */
class TextPaletteTest {

    /** §2.1 text tokens, the accent pair, and the three status inks. */
    private static final Set<String> TOKENS = Set.of(
            "#f5f7fb", "#b8c0cc", "#8a93a3", "#767e8d",
            "#f97316", "#fb923c",
            "#4ade80", "#f87171",
            "inherit", "transparent");

    /**
     * Inks that are deliberately not tokens, each with a reason that holds:
     * the row-status scale of §4.11, dark ink readable on a solid orange fill,
     * text tinted toward the coloured surface it sits on, faded accent glyphs,
     * and the one documented contrast exemption, disabled controls.
     */
    private static final Set<String> SANCTIONED = Set.of(
            "#d0904f", "#7fae8c", "#ff4d4d",
            "#111827",
            "#fecaca", "#fca5a5", "#fef2f2",
            "rgba(249,115,22,0.40)", "rgba(249,115,22,0.32)",
            "rgba(184,192,204,0.52)");

    private static final Pattern RULE = Pattern.compile("([^{}]*)\\{([^{}]*)\\}");
    private static final Pattern FILL = Pattern.compile("-fx-text-fill:\\s*([^;]+);");

    @Test
    void everyTextInkIsATokenOrASanctionedException() throws IOException {
        List<String> strays = new ArrayList<>();
        Matcher rules = RULE.matcher(stylesheet());
        while (rules.find()) {
            Matcher fill = FILL.matcher(rules.group(2));
            while (fill.find()) {
                String ink = fill.group(1).trim().replaceAll("\\s+", "");
                if (!TOKENS.contains(ink) && !SANCTIONED.contains(ink)) {
                    strays.add(ink + "  in  " + rules.group(1).trim().replaceAll("\\s+", " "));
                }
            }
        }
        assertTrue(strays.isEmpty(), "text ink outside the palette:\n  " + String.join("\n  ", strays));
    }

    @Test
    void neverPureBlackOrPureWhite() throws IOException {
        String css = stylesheet().toLowerCase();
        for (String banned : List.of("#ffffff", "#fff;", "#000000", "#000;")) {
            assertTrue(!css.contains(banned), "pure black or white in app.css: " + banned);
        }
    }

    private static String stylesheet() throws IOException {
        try (var stream = TextPaletteTest.class.getResourceAsStream("/styles/app.css")) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("(?s)/\\*.*?\\*/", "");
        }
    }
}

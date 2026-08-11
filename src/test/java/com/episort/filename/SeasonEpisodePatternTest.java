package com.episort.filename;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Matcher;
import org.junit.jupiter.api.Test;

class SeasonEpisodePatternTest {

    private static Matcher strict(String value) {
        return SeasonEpisodePattern.STRICT.matcher(value);
    }

    private static Matcher lenient(String value) {
        return SeasonEpisodePattern.LENIENT.matcher(value);
    }

    @Test
    void bothFormsReadTheCommonCase() {
        for (String value : new String[] {"Show.S01E02.mkv", "Show S01 E02.mkv", "Show.S01-E02.mkv"}) {
            Matcher strictMatch = strict(value);
            assertTrue(strictMatch.find(), "strict should read " + value);
            assertEquals("01", strictMatch.group(1));
            assertEquals("02", strictMatch.group(2));

            Matcher lenientMatch = lenient(value);
            assertTrue(lenientMatch.find(), "lenient should read " + value);
            assertEquals("01", lenientMatch.group(1));
            assertEquals("02", lenientMatch.group(2));
        }
    }

    @Test
    void groupOneIsAlwaysTheSeasonAndGroupTwoTheEpisode() {
        Matcher matcher = lenient("Show.S12E345.mkv");

        assertTrue(matcher.find());
        assertEquals("12", matcher.group(1));
        assertEquals("345", matcher.group(2));
    }

    /**
     * Pins the divergence the two forms deliberately preserve, so widening the
     * matcher becomes a visible, intentional change rather than a silent one.
     */
    @Test
    void lenientReadsAThreeDigitSeasonAndStrictDoesNot() {
        assertFalse(strict("Show.S001E02.mkv").find(), "strict caps the season at two digits");

        Matcher matcher = lenient("Show.S001E02.mkv");
        assertTrue(matcher.find(), "lenient allows a three-digit season");
        assertEquals("001", matcher.group(1));
    }

    /**
     * The sharp edge of the strict form: on a four-digit episode it does not
     * fail, it <em>truncates</em>. "S01E1234" is read as episode 123 and the
     * trailing digit is dropped, so the matcher silently targets the wrong
     * episode instead of reporting no match.
     */
    @Test
    void strictSilentlyTruncatesAFourDigitEpisode() {
        Matcher strictMatch = strict("Show.S01E1234.mkv");
        assertTrue(strictMatch.find());
        assertEquals("123", strictMatch.group(2), "strict drops the fourth digit");

        Matcher lenientMatch = lenient("Show.S01E1234.mkv");
        assertTrue(lenientMatch.find());
        assertEquals("1234", lenientMatch.group(2), "lenient keeps the whole number");
    }

    @Test
    void neitherFormReadsANameWithoutAnOrder() {
        assertFalse(strict("Arrival.2016.1080p.mkv").find());
        assertFalse(lenient("Arrival.2016.1080p.mkv").find());
    }
}

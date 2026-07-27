package com.episort.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class WindowsPathSafetyTest {
    @Test
    void forbiddenCharactersAreDroppedDeterministically() {
        assertEquals("Doctor Who The Movie", WindowsPathSafety.sanitizeComponent("Doctor Who: The Movie"));
        assertEquals("ACDC Live", WindowsPathSafety.sanitizeComponent("AC/DC | Live"));
        assertEquals("What", WindowsPathSafety.sanitizeComponent("What?"));
        assertEquals("ab", WindowsPathSafety.sanitizeComponent("a\u0001b"));
    }

    @Test
    void trailingDotsAndSpacesAreRemoved() {
        assertEquals("Firefly", WindowsPathSafety.sanitizeComponent("Firefly. "));
        assertEquals("Firefly", WindowsPathSafety.sanitizeComponent("  Firefly   "));
        assertEquals("Mr. Robot", WindowsPathSafety.sanitizeComponent("Mr.  Robot"));
    }

    @Test
    void reservedDeviceNamesAreEscaped() {
        assertEquals("CON_", WindowsPathSafety.sanitizeComponent("CON"));
        assertEquals("com1_", WindowsPathSafety.sanitizeComponent("com1"));
        assertEquals("Contact", WindowsPathSafety.sanitizeComponent("Contact"));
    }

    @Test
    void blankOrFullyStrippedNamesFallBackInsteadOfProducingAnEmptyComponent() {
        assertEquals("untitled", WindowsPathSafety.sanitizeComponent(""));
        assertEquals("untitled", WindowsPathSafety.sanitizeComponent("   "));
        assertEquals("untitled", WindowsPathSafety.sanitizeComponent("..."));
        assertEquals("untitled", WindowsPathSafety.sanitizeComponent(null));
    }

    @Test
    void componentsAreCappedAtTheNtfsLimit() {
        String long260 = "x".repeat(260);

        assertEquals(255, WindowsPathSafety.sanitizeComponent(long260).length());
    }

    @Test
    void extensionsArePreservedExactlyIncludingTheirCase() {
        assertEquals(".MKV", WindowsPathSafety.sanitizeExtension("MKV"));
        assertEquals(".mkv", WindowsPathSafety.sanitizeExtension(".mkv"));
        assertEquals(".mp4", WindowsPathSafety.sanitizeExtension("  .mp4 "));
        assertEquals("", WindowsPathSafety.sanitizeExtension(""));
    }

    @Test
    void longTitlesAreTruncatedSoThePathFitsWithinMaxPath() {
        Path root = Path.of("C:/Media");
        String series = "S".repeat(200);
        String base = series + " - S01E01 - " + "E".repeat(200);

        Path result = WindowsPathSafety.fitWithinMaxPath(root, List.of(series, "Season 01"), base, ".mkv");

        assertFalse(WindowsPathSafety.exceedsMaxPath(result), "generated path was " + result.toString().length());
        assertTrue(result.getFileName().toString().endsWith(".mkv"));
    }

    @Test
    void truncationIsDeterministicAcrossRuns() {
        Path root = Path.of("C:/Media");
        String base = "T".repeat(400);

        Path first = WindowsPathSafety.fitWithinMaxPath(root, List.of("Series", "Season 01"), base, ".mkv");
        Path second = WindowsPathSafety.fitWithinMaxPath(root, List.of("Series", "Season 01"), base, ".mkv");

        assertEquals(first, second);
    }

    @Test
    void shortPathsAreLeftUntouched() {
        Path result = WindowsPathSafety.fitWithinMaxPath(
                Path.of("C:/Media"), List.of("Firefly", "Season 01"), "Firefly - S01E01 - Serenity", ".mkv");

        assertEquals(Path.of("C:/Media/Firefly/Season 01/Firefly - S01E01 - Serenity.mkv"), result);
    }

    @Test
    void deepWorkspaceRootsShrinkFoldersRatherThanOverflow() {
        Path root = Path.of("C:/" + "d".repeat(150));
        String series = "S".repeat(120);

        Path result = WindowsPathSafety.fitWithinMaxPath(
                root, List.of(series, "Season 01"), series + " - S01E01", ".mkv");

        assertFalse(WindowsPathSafety.exceedsMaxPath(result), "generated path was " + result.toString().length());
    }
}

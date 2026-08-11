package com.episort.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class PlexDestinationNamerTest {
    private static final Path WORKSPACE = Path.of("C:/Media");

    private final PlexDestinationNamer namer = new PlexDestinationNamer();

    @Test
    void regularEpisodesUseSeriesFolderSeasonFolderAndSxxExxFilename() {
        PlanSourceItem item = PlanSourceItem.forSource(
                        Path.of("C:/Media/firefly.s01e02.mkv"), ".mkv", PlanMediaKind.SERIES_EPISODE)
                .series("Firefly", 1, 2)
                .episodeTitle("The Train Job")
                .build();

        assertEquals(
                Path.of("C:/Media/Firefly/Season 01/Firefly - S01E02 - The Train Job.mkv"),
                namer.destinationFor(WORKSPACE, item));
    }

    @Test
    void seasonNumbersAreZeroPaddedToTwoDigitsAndKeepThreeDigitEpisodes() {
        PlanSourceItem item = PlanSourceItem.forSource(
                        Path.of("C:/Media/one-piece-1000.mkv"), ".mkv", PlanMediaKind.SERIES_EPISODE)
                .series("One Piece", 21, 1000)
                .build();

        assertEquals(
                Path.of("C:/Media/One Piece/Season 21/One Piece - S21E1000.mkv"),
                namer.destinationFor(WORKSPACE, item));
    }

    @Test
    void missingEpisodeTitleStillProducesAPlexCompatibleName() {
        PlanSourceItem item = PlanSourceItem.forSource(
                        Path.of("C:/Media/show.mkv"), ".mkv", PlanMediaKind.SERIES_EPISODE)
                .series("Show", 3, 7)
                .build();

        assertEquals(
                Path.of("C:/Media/Show/Season 03/Show - S03E07.mkv"),
                namer.destinationFor(WORKSPACE, item));
    }

    @Test
    void theOriginalExtensionIsPreservedExactly() {
        PlanSourceItem upper = PlanSourceItem.forSource(
                        Path.of("C:/Media/show.MKV"), ".MKV", PlanMediaKind.SERIES_EPISODE)
                .series("Show", 1, 1)
                .build();
        PlanSourceItem avi = PlanSourceItem.forSource(
                        Path.of("C:/Media/show.avi"), "avi", PlanMediaKind.SERIES_EPISODE)
                .series("Show", 1, 1)
                .build();

        assertTrue(namer.destinationFor(WORKSPACE, upper).toString().endsWith(".MKV"));
        assertTrue(namer.destinationFor(WORKSPACE, avi).toString().endsWith(".avi"));
    }

    @Test
    void specialsLandInASpecialsFolderNumberedAsSeasonZero() {
        PlanSourceItem item = PlanSourceItem.forSource(
                        Path.of("C:/Media/ova.mkv"), ".mkv", PlanMediaKind.SPECIAL)
                .special("Cowboy Bebop", 3)
                .episodeTitle("Session XX")
                .build();

        assertEquals(
                Path.of("C:/Media/Cowboy Bebop/Specials/Cowboy Bebop - S00E03 - Session XX.mkv"),
                namer.destinationFor(WORKSPACE, item));
    }

    @Test
    void specialsKeepAnExplicitSeasonNumberWhenTmdbProvidesOne() {
        PlanSourceItem item = PlanSourceItem.forSource(
                        Path.of("C:/Media/ova.mkv"), ".mkv", PlanMediaKind.SPECIAL)
                .special("Show", 2)
                .seasonNumber(OptionalInt.of(0))
                .build();

        assertEquals(
                Path.of("C:/Media/Show/Specials/Show - S00E02.mkv"),
                namer.destinationFor(WORKSPACE, item));
    }

    @Test
    void moviesLandAtTheWorkspaceRootWithATitleAndYearFilename() {
        PlanSourceItem item = PlanSourceItem.forSource(
                        Path.of("C:/Media/blade.runner.1982.mkv"), ".mkv", PlanMediaKind.MOVIE)
                .movie("Blade Runner", 1982)
                .build();

        assertEquals(
                Path.of("C:/Media/Blade Runner (1982).mkv"),
                namer.destinationFor(WORKSPACE, item));
    }

    @Test
    void moviesWithoutAKnownYearKeepJustTheirTitle() {
        PlanSourceItem item = PlanSourceItem.forSource(
                        Path.of("C:/Media/unknown.mkv"), ".mkv", PlanMediaKind.MOVIE)
                .movie("Unknown Movie", null)
                .build();

        assertEquals(
                Path.of("C:/Media/Unknown Movie.mkv"),
                namer.destinationFor(WORKSPACE, item));
    }

    @Test
    void homonymousMoviesRemainDistinguishableThroughTheirReleaseYear() {
        PlanSourceItem original = PlanSourceItem.forSource(
                        Path.of("C:/Media/dune-1984.mkv"), ".mkv", PlanMediaKind.MOVIE)
                .movie("Dune", 1984)
                .build();
        PlanSourceItem remake = PlanSourceItem.forSource(
                        Path.of("C:/Media/dune-2021.mkv"), ".mkv", PlanMediaKind.MOVIE)
                .movie("Dune", 2021)
                .build();

        assertNotEquals(namer.destinationFor(WORKSPACE, original), namer.destinationFor(WORKSPACE, remake));
    }

    @Test
    void titlesWithForbiddenWindowsCharactersAreSanitized() {
        PlanSourceItem item = PlanSourceItem.forSource(
                        Path.of("C:/Media/ep.mkv"), ".mkv", PlanMediaKind.SERIES_EPISODE)
                .series("Doctor Who: Legacy", 1, 1)
                .episodeTitle("What? / Why!")
                .build();

        assertEquals(
                Path.of("C:/Media/Doctor Who Legacy/Season 01/Doctor Who Legacy - S01E01 - What Why!.mkv"),
                namer.destinationFor(WORKSPACE, item));
    }

    @Test
    void incompleteIdentitiesAreRefusedInsteadOfGuessed() {
        PlanSourceItem noSeason = PlanSourceItem.forSource(
                        Path.of("C:/Media/ep.mkv"), ".mkv", PlanMediaKind.SERIES_EPISODE)
                .seriesTitle(Optional.of("Show"))
                .build();

        assertThrows(IllegalArgumentException.class, () -> namer.destinationFor(WORKSPACE, noSeason));
    }
}

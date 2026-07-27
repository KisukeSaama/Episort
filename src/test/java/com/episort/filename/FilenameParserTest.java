package com.episort.filename;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The catalogue of file names that used to break the analysis, kept as
 * executable specification. Each nested class is one failure family.
 */
class FilenameParserTest {

    @Nested
    class EpisodeMarkers {
        @Test
        void readsTheCanonicalSxxExxMarker() {
            ParsedFilename parsed = FilenameParser.parse("Show.S01E02.Title.mkv");

            assertEquals("SxxExx", parsed.patternLabel());
            assertEquals(MediaKindHint.SERIES, parsed.kind());
            assertEquals("Show", parsed.title().orElseThrow());
            assertEquals(OptionalInt.of(1), parsed.season());
            assertEquals(List.of(2), parsed.episodes());
            assertEquals("Title", parsed.episodeTitle().orElseThrow());
            assertEquals("S01E02", parsed.normalizedOrder().orElseThrow());
        }

        @Test
        void readsEveryCommonSpellingOfTheSameEpisode() {
            for (String name : List.of(
                    "Show.S01E02.mkv",
                    "show.s01e02.mkv",
                    "Show.S01.E02.mkv",
                    "Show S1 E2.mkv",
                    "Show - 1x02.mkv",
                    "Show.Season 1 Episode 2.mkv",
                    "Show.Season.01.Episode.02.mkv")) {
                ParsedFilename parsed = FilenameParser.parse(name);
                assertEquals("S01E02", parsed.normalizedOrder().orElseThrow(), name);
            }
        }

        @Test
        void collectsMultiEpisodeFilesAndFlagsThem() {
            for (String name : List.of("Show.S01E02E03.mkv", "Show.S01E02-E03.mkv", "Show.S01E02-03.mkv")) {
                ParsedFilename parsed = FilenameParser.parse(name);
                assertEquals(List.of(2, 3), parsed.episodes(), name);
                assertEquals("S01E02-E03", parsed.normalizedOrder().orElseThrow(), name);
                assertTrue(parsed.hasWarning(ParseWarning.MULTI_EPISODE), name);
            }
        }

        @Test
        void doesNotTurnTrailingNoiseIntoASecondEpisode() {
            assertEquals(List.of(2), FilenameParser.parse("Show.S01E02-1080p.mkv").episodes());
            assertEquals(List.of(2), FilenameParser.parse("Show.S01E02 - 2019 - Title.mkv").episodes());
        }

        @Test
        void supportsYearNumberedSeasonsAndThreeDigitEpisodes() {
            assertEquals(OptionalInt.of(2024), FilenameParser.parse("Show.S2024E05.mkv").season());
            assertEquals(List.of(100), FilenameParser.parse("Show.S01E100.mkv").episodes());
        }

        @Test
        void readsDateBasedEpisodesInBothOrders() {
            ParsedFilename ymd = FilenameParser.parse("Daily.Show.2020.05.12.Guest.mkv");
            assertEquals(LocalDate.of(2020, 5, 12), ymd.airDate().orElseThrow());
            assertTrue(ymd.hasWarning(ParseWarning.DATE_BASED_EPISODE));
            assertEquals("Daily Show", ymd.title().orElseThrow());

            assertEquals(LocalDate.of(2020, 5, 12),
                    FilenameParser.parse("Daily.Show.12.05.2020.mkv").airDate().orElseThrow());
        }

        @Test
        void rejectsImpossibleDatesInsteadOfInventingThem() {
            assertTrue(FilenameParser.parse("Show.2020.13.45.mkv").airDate().isEmpty());
        }

        @Test
        void treatsSeasonZeroAsASpecial() {
            ParsedFilename parsed = FilenameParser.parse("Show.S00E01.Christmas.mkv");

            assertEquals(MediaKindHint.SPECIAL, parsed.kind());
            assertEquals(OptionalInt.of(0), parsed.season());
            assertTrue(parsed.hasWarning(ParseWarning.SPECIAL_EPISODE));
        }

        @Test
        void reportsTwoDisagreeingMarkersInsteadOfPickingSilently() {
            ParsedFilename parsed = FilenameParser.parse("Show.S01E02.1x05.mkv");

            assertTrue(parsed.hasWarning(ParseWarning.CONFLICTING_MARKERS));
            assertTrue(parsed.confidence() < 0.8, "a contradiction must cost confidence");
        }
    }

    @Nested
    class ReleaseNoise {
        @Test
        void neverReadsAnEpisodeOrAYearOutOfResolutionCodecOrAudioTags() {
            ParsedFilename parsed = FilenameParser.parse(
                    "Show.S01E02.720p.WEB-DL.x264.DDP5.1.mkv");

            assertEquals(List.of(2), parsed.episodes());
            assertTrue(parsed.year().isEmpty());
            assertEquals("720p", parsed.quality().orElseThrow());
            assertEquals("WEB-DL", parsed.source().orElseThrow());
            assertEquals("x264", parsed.codec().orElseThrow());
        }

        @Test
        void doesNotReadTwoThousandOneHundredSixtyAsAYear() {
            assertTrue(FilenameParser.parse("Movie.2160p.BluRay.mkv").year().isEmpty());
            assertTrue(FilenameParser.parse("Show.1080p.x265.mkv").episodes().isEmpty());
        }

        @Test
        void keepsNoiseOutOfTheEpisodeTitle() {
            ParsedFilename parsed = FilenameParser.parse(
                    "The.Office.US.S02E05.Halloween.DVDRip.XviD-TOPAZ.avi");

            assertEquals("The Office US", parsed.title().orElseThrow());
            assertEquals("Halloween", parsed.episodeTitle().orElseThrow());
            assertEquals("TOPAZ", parsed.releaseGroup().orElseThrow());
        }

        @Test
        void extractsLanguageAndEditionWithoutPollutingTheTitle() {
            ParsedFilename parsed = FilenameParser.parse(
                    "Movie.Title.2019.MULTI.Extended.1080p.BluRay.x265-GRP.mkv");

            assertEquals("Movie Title", parsed.title().orElseThrow());
            assertEquals(OptionalInt.of(2019), parsed.year());
            assertEquals("MULTI", parsed.language().orElseThrow());
            assertEquals("Extended", parsed.edition().orElseThrow());
        }

        @Test
        void dropsSiteStampsChecksumsAndBracketedGroups() {
            ParsedFilename parsed = FilenameParser.parse("[SubsGrp] Show - 12 [1080p][A1B2C3D4].mkv");

            assertEquals("Show", parsed.title().orElseThrow());
            assertEquals("SubsGrp", parsed.releaseGroup().orElseThrow());
            assertEquals(OptionalInt.of(12), parsed.absoluteNumber());

            assertEquals("Show", FilenameParser.parse("www.site.com - Show.S01E02.HDTV.mkv")
                    .title().orElseThrow());
        }

        @Test
        void doesNotMistakeAHyphenatedTitleForAReleaseGroup() {
            assertEquals("Spider-Man Into the Spider-Verse",
                    FilenameParser.parse("Spider-Man.Into.the.Spider-Verse.2018.mkv").title().orElseThrow());
        }
    }

    @Nested
    class Years {
        @Test
        void prefersTheBracketedYearOverANumericTitle() {
            ParsedFilename parsed = FilenameParser.parse("Blade Runner 2049 (2017).mkv");

            assertEquals("Blade Runner 2049", parsed.title().orElseThrow());
            assertEquals(OptionalInt.of(2017), parsed.year());
        }

        @Test
        void keepsANumericTitleWhenTheYearFollowsIt() {
            ParsedFilename parsed = FilenameParser.parse("2012.2009.1080p.BluRay.mkv");

            assertEquals("2012", parsed.title().orElseThrow());
            assertEquals(OptionalInt.of(2009), parsed.year());
            assertTrue(parsed.hasWarning(ParseWarning.NUMERIC_TITLE));
        }

        @Test
        void keepsTheYearOutOfTheSeriesTitle() {
            ParsedFilename parsed = FilenameParser.parse("Show 2019 S01E02 Title.mkv");

            assertEquals("Show", parsed.title().orElseThrow());
            assertEquals(OptionalInt.of(2019), parsed.year());
        }

        @Test
        void discardsYearsThatCannotExistYet() {
            assertTrue(FilenameParser.parse("Show.9999.mkv").year().isEmpty());
        }
    }

    @Nested
    class BareNumbers {
        @Test
        void treatsAnIsolatedNumberAsAnAbsoluteEpisodeButSaysSo() {
            ParsedFilename parsed = FilenameParser.parse("Naruto Shippuden - 001 - Homecoming.mkv");

            assertEquals("Naruto Shippuden", parsed.title().orElseThrow());
            assertEquals(OptionalInt.of(1), parsed.absoluteNumber());
            assertEquals("Homecoming", parsed.episodeTitle().orElseThrow());
            assertTrue(parsed.hasWarning(ParseWarning.AMBIGUOUS_ABSOLUTE_NUMBER));
            assertTrue(parsed.hasWarning(ParseWarning.ASSUMED_SEASON));
            assertTrue(parsed.confidence() <= 0.5);
        }

        @Test
        void neverTurnsALeadingNumericTitleIntoAnEpisode() {
            assertTrue(FilenameParser.parse("1917 (2019).mkv").episodes().isEmpty());
            assertEquals("24", FilenameParser.parse("24.S01E01.mkv").title().orElseThrow());
        }

        @Test
        void keepsASequelNumberInsideTheMovieTitle() {
            ParsedFilename parsed = FilenameParser.parse("Le.Flic.de.Hong.Kong.2.1985.1080p.BluRay.mkv");

            assertEquals("Le Flic de Hong Kong 2", parsed.title().orElseThrow());
            assertEquals(OptionalInt.of(1985), parsed.year());
            assertTrue(parsed.episodes().isEmpty());

            assertEquals("Le Flic de Hong Kong 2",
                    FilenameParser.parse("Le Flic de Hong Kong 2 (1985).mkv").title().orElseThrow());
            assertEquals("Le Flic de Hong Kong",
                    FilenameParser.parse("Le Flic de Hong Kong (1985).mkv").title().orElseThrow());
        }

        @Test
        void keepsASequelNumberEvenWithoutAYear() {
            ParsedFilename parsed = FilenameParser.parse("Le Flic de Hong Kong 2.avi");

            assertEquals("Le Flic de Hong Kong 2", parsed.title().orElseThrow());
            assertTrue(parsed.episodes().isEmpty());

            assertEquals("Le Flic de Hong Kong 2",
                    FilenameParser.parse("Le Flic de Hong Kong 2 1080p BluRay.mkv").title().orElseThrow());
        }

        @Test
        void stillReadsASmallEpisodeNumberBehindASeparator() {
            ParsedFilename parsed = FilenameParser.parse("[SubsGrp] Show - 5 [1080p].mkv");

            assertEquals("Show", parsed.title().orElseThrow());
            assertEquals(OptionalInt.of(5), parsed.absoluteNumber());
        }

        @Test
        void stillReadsAnAbsoluteEpisodeWhenTheNumberIsNotGluedToTheYear() {
            ParsedFilename parsed = FilenameParser.parse("Show 2019 - 12 - Title.mkv");

            assertEquals(OptionalInt.of(12), parsed.absoluteNumber());
        }

        @Test
        void usesTheFolderSeasonToExpandCompactNumbering() {
            ParsedFilename parsed = FilenameParser.parse(
                    "Show.102.mkv", new FolderContext("Season 1", "Show"));

            assertEquals(OptionalInt.of(1), parsed.season());
            assertEquals(List.of(2), parsed.episodes());
            assertTrue(parsed.hasWarning(ParseWarning.SEASON_FROM_FOLDER));
        }
    }

    @Nested
    class Folders {
        @Test
        void takesTheSeasonFromABareSeasonFolder() {
            ParsedFilename parsed = FilenameParser.parse(
                    "E02.mkv", new FolderContext("Season 03", "Breaking Bad"));

            assertEquals(OptionalInt.of(3), parsed.season());
            assertEquals(List.of(2), parsed.episodes());
            assertEquals("Breaking Bad", parsed.title().orElseThrow());
        }

        @Test
        void readsTheSeriesTitleOutOfAReleaseFolder() {
            ParsedFilename parsed = FilenameParser.parse(
                    "sgi-hkyu01.1080p.multi.mkv",
                    new FolderContext("Haikyu.S01.MULTi.1080p.BluRay.x264-SHiNiGAMi", "Series"));

            assertEquals("Haikyu", parsed.title().orElseThrow());
            assertEquals("S01E01", parsed.normalizedOrder().orElseThrow());
        }

        @Test
        void keepsAFileTitleTheReleaseFolderContradicts() {
            ParsedFilename parsed = FilenameParser.parse(
                    "My.Hero.Academia.Vigilantes.S01E01.MULTi.1080p.mkv",
                    new FolderContext("Haikyu.S01.MULTi.1080p.BluRay.x264-SHiNiGAMi", "Series"));

            assertEquals("My Hero Academia Vigilantes", parsed.title().orElseThrow());
            assertTrue(parsed.hasWarning(ParseWarning.FOLDER_TITLE_MISMATCH));
        }

        @Test
        void stillLetsTheFolderCompleteAnAbbreviatedFileTitle() {
            ParsedFilename parsed = FilenameParser.parse(
                    "Haikyu.S01E05.mkv",
                    new FolderContext("Haikyu.S01.MULTi.1080p.BluRay.x264-SHiNiGAMi", "Series"));

            assertEquals("Haikyu", parsed.title().orElseThrow());
            assertFalse(parsed.hasWarning(ParseWarning.FOLDER_TITLE_MISMATCH));
        }

        @Test
        void treatsASpecialsFolderAsSeasonZero() {
            ParsedFilename parsed = FilenameParser.parse("E01.mkv", new FolderContext("Specials", "Show"));

            assertEquals(OptionalInt.of(0), parsed.season());
            assertEquals(MediaKindHint.SPECIAL, parsed.kind());
        }
    }

    @Nested
    class ExtrasAndHostileInput {
        @Test
        void flagsSampleAndTrailerMaterial() {
            assertEquals(MediaKindHint.EXTRA, FilenameParser.parse("Show.S01E02.sample.mkv").kind());
            assertEquals(MediaKindHint.EXTRA, FilenameParser.parse("Movie.2019.trailer.mkv").kind());
        }

        @Test
        void flagsMultiPartReleases() {
            assertEquals(OptionalInt.of(1), FilenameParser.parse("Movie.2019.CD1.avi").partNumber());
        }

        @Test
        void survivesEmptyExtensionOnlyAndControlCharacterNames() {
            assertTrue(FilenameParser.parse("").hasWarning(ParseWarning.UNUSABLE_NAME));
            assertTrue(FilenameParser.parse(null).hasWarning(ParseWarning.UNUSABLE_NAME));
            assertTrue(FilenameParser.parse(".mkv").hasWarning(ParseWarning.UNUSABLE_NAME));
            assertEquals("Show", FilenameParser.parse("Show.S01E02.mkv").title().orElseThrow());
        }

        @Test
        void ignoresADownloadSuffixWhenReadingTheName() {
            ParsedFilename parsed = FilenameParser.parse("Show.S01E02.mkv.part");

            assertEquals(".part", parsed.extension());
            assertTrue(parsed.episodeTitle().isEmpty(), "the leftover \"mkv\" must not become a title");
        }

        @Test
        void doesNotThrowOnUnbalancedBracketsOrVeryLongNames() {
            assertFalse(FilenameParser.parse("[Grp Show - S01E02 (2019 [1080p.mkv").title().isEmpty());
            assertFalse(FilenameParser.parse("A".repeat(4000) + ".S01E02.mkv").title().isEmpty());
        }
    }

    @Nested
    class Offsets {
        @Test
        void spansPointAtTheExactCharactersTheyCameFrom() {
            String name = "Show.S01E02.Title.mkv";
            ParsedFilename parsed = FilenameParser.parse(name);

            FilenameSpan season = span(parsed, SpanRole.SEASON);
            FilenameSpan episode = span(parsed, SpanRole.EPISODE);
            assertEquals("01", name.substring(season.start(), season.end()));
            assertEquals("02", name.substring(episode.start(), episode.end()));
            FilenameSpan extension = span(parsed, SpanRole.EXTENSION);
            assertEquals("mkv", name.substring(extension.start(), extension.end()));
        }

        private static FilenameSpan span(ParsedFilename parsed, SpanRole role) {
            return parsed.spans().stream()
                    .filter(span -> span.role() == role)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("missing span " + role));
        }
    }
}

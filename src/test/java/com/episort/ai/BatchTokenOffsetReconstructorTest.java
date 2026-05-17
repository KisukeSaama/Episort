package com.episort.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.ai.BatchTokenOffsetReconstructor.RawToken;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Equivalence harness for {@link BatchTokenOffsetReconstructor}. Each
 * scenario fixes a filename plus the {@code {role,rawValue,normalizedValue}}
 * the model would have emitted under the compact batch schema, and asserts
 * the reconstructed {@code (start,end)} offsets match what the rich-schema
 * (per-file) path would have produced.
 *
 * <p>Coverage targets the categories that motivated the offset hand-back to
 * the Java side: vanilla SxxExx, NxNN, absolute/anime (folder-derived
 * SERIES/SEASON with empty rawValue), movies, duplicated rawValues
 * (cursor-disambiguated), out-of-order tokens (recovered by a from-zero
 * scan), and hallucinated/missing rawValues that degrade gracefully to
 * {@code (0,0)}.
 */
class BatchTokenOffsetReconstructorTest {

    private static RawToken raw(String role, String rawValue, String normalizedValue) {
        return new RawToken(role, rawValue, normalizedValue);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void reconstructsExpectedOffsets(
            String name, String filename, List<RawToken> raws, int[][] expected) {
        List<AiPatternToken> tokens = BatchTokenOffsetReconstructor.reconstruct(filename, raws);
        assertEquals(raws.size(), tokens.size(), name + ": token count");
        for (int i = 0; i < tokens.size(); i++) {
            AiPatternToken got = tokens.get(i);
            assertEquals(raws.get(i).role(), got.role(), name + " token[" + i + "].role");
            assertEquals(raws.get(i).rawValue(), got.rawValue(), name + " token[" + i + "].rawValue");
            assertEquals(raws.get(i).normalizedValue(), got.normalizedValue(),
                    name + " token[" + i + "].normalizedValue");
            assertEquals(expected[i][0], got.start(), name + " token[" + i + "].start");
            assertEquals(expected[i][1], got.end(), name + " token[" + i + "].end");
        }
    }

    static Stream<Arguments> cases() {
        return Stream.of(
                // ---- A. Vanilla SxxExx series (rawValue extracted left-to-right) ----
                Arguments.of("A1-breaking-bad", "Breaking.Bad.S01E03.mkv",
                        List.of(raw("SERIES", "Breaking.Bad", "Breaking Bad"),
                                raw("SEASON", "S01", "01"),
                                raw("EPISODE", "E03", "03")),
                        new int[][]{{0, 12}, {13, 16}, {16, 19}}),
                Arguments.of("A2-show-s2e10", "Show.S02E10.mkv",
                        List.of(raw("SERIES", "Show", "Show"),
                                raw("SEASON", "S02", "02"),
                                raw("EPISODE", "E10", "10")),
                        new int[][]{{0, 4}, {5, 8}, {8, 11}}),
                Arguments.of("A3-got-final", "Game.of.Thrones.S08E06.mkv",
                        List.of(raw("SERIES", "Game.of.Thrones", "Game of Thrones"),
                                raw("SEASON", "S08", "08"),
                                raw("EPISODE", "E06", "06")),
                        new int[][]{{0, 15}, {16, 19}, {19, 22}}),
                Arguments.of("A4-the-wire-noise", "The.Wire.S03E07.HDTV.x264.mkv",
                        List.of(raw("SERIES", "The.Wire", "The Wire"),
                                raw("SEASON", "S03", "03"),
                                raw("EPISODE", "E07", "07"),
                                raw("NOISE", "HDTV.x264", "HDTV x264")),
                        new int[][]{{0, 8}, {9, 12}, {12, 15}, {16, 25}}),
                Arguments.of("A5-chernobyl", "Chernobyl.S01E04.mkv",
                        List.of(raw("SERIES", "Chernobyl", "Chernobyl"),
                                raw("SEASON", "S01", "01"),
                                raw("EPISODE", "E04", "04")),
                        new int[][]{{0, 9}, {10, 13}, {13, 16}}),

                // ---- B. SxxExx with TITLE token ----
                Arguments.of("B1-bb-with-title", "Breaking.Bad.S01E03.Bit.By.A.Dead.Bee.mkv",
                        List.of(raw("SERIES", "Breaking.Bad", "Breaking Bad"),
                                raw("SEASON", "S01", "01"),
                                raw("EPISODE", "E03", "03"),
                                raw("TITLE", "Bit.By.A.Dead.Bee", "Bit By A Dead Bee")),
                        new int[][]{{0, 12}, {13, 16}, {16, 19}, {20, 37}}),
                Arguments.of("B2-lost-pilot", "Lost.S01E01.Pilot.mkv",
                        List.of(raw("SERIES", "Lost", "Lost"),
                                raw("SEASON", "S01", "01"),
                                raw("EPISODE", "E01", "01"),
                                raw("TITLE", "Pilot", "Pilot")),
                        new int[][]{{0, 4}, {5, 8}, {8, 11}, {12, 17}}),
                Arguments.of("B3-house-apostrophe", "House.MD.S04E15.House's.Head.mkv",
                        List.of(raw("SERIES", "House.MD", "House MD"),
                                raw("SEASON", "S04", "04"),
                                raw("EPISODE", "E15", "15"),
                                raw("TITLE", "House's.Head", "House's Head")),
                        new int[][]{{0, 8}, {9, 12}, {12, 15}, {16, 28}}),

                // ---- C. NxNN season-by-episode format ----
                Arguments.of("C1-2x10", "Show.2x10.mkv",
                        List.of(raw("SERIES", "Show", "Show"),
                                raw("SEASON", "2", "02"),
                                raw("EPISODE", "10", "10")),
                        new int[][]{{0, 4}, {5, 6}, {7, 9}}),
                Arguments.of("C2-10x05", "Show.10x05.mkv",
                        List.of(raw("SERIES", "Show", "Show"),
                                raw("SEASON", "10", "10"),
                                raw("EPISODE", "05", "05")),
                        new int[][]{{0, 4}, {5, 7}, {8, 10}}),
                Arguments.of("C3-friends-7x24", "Friends.7x24.mkv",
                        List.of(raw("SERIES", "Friends", "Friends"),
                                raw("SEASON", "7", "07"),
                                raw("EPISODE", "24", "24")),
                        new int[][]{{0, 7}, {8, 9}, {10, 12}}),

                // ---- D. absolute (anime), often with folder-derived SERIES/SEASON ----
                Arguments.of("D1-naruto-420", "Naruto.420.mkv",
                        List.of(raw("SERIES", "Naruto", "Naruto"),
                                raw("EPISODE", "420", "420")),
                        new int[][]{{0, 6}, {7, 10}}),
                Arguments.of("D2-onepiece-1000", "OnePiece.1000.mkv",
                        List.of(raw("SERIES", "OnePiece", "One Piece"),
                                raw("EPISODE", "1000", "1000")),
                        new int[][]{{0, 8}, {9, 13}}),
                Arguments.of("D3-bleach-glued", "BleachEp042.mkv",
                        List.of(raw("SERIES", "Bleach", "Bleach"),
                                raw("EPISODE", "042", "042")),
                        new int[][]{{0, 6}, {8, 11}}),
                Arguments.of("D4-sgi-hkyu-folder-series", "sgi-hkyu01.1080p.multi.mkv",
                        List.of(raw("SERIES", "", "Haikyu"),
                                raw("SEASON", "", "01"),
                                raw("EPISODE", "01", "01")),
                        new int[][]{{0, 0}, {0, 0}, {8, 10}}),
                Arguments.of("D5-sgi-hkyu03", "sgi-hkyu03.1080p.multi.mkv",
                        List.of(raw("SERIES", "", "Haikyu"),
                                raw("SEASON", "", "01"),
                                raw("EPISODE", "03", "03")),
                        new int[][]{{0, 0}, {0, 0}, {8, 10}}),

                // ---- E. Movies (no episode marker) ----
                Arguments.of("E1-inception", "Inception (2010).mkv",
                        List.of(raw("TITLE", "Inception", "Inception"),
                                raw("YEAR", "2010", "2010")),
                        new int[][]{{0, 9}, {11, 15}}),
                Arguments.of("E2-matrix-noise", "The.Matrix.1999.1080p.mkv",
                        List.of(raw("TITLE", "The.Matrix", "The Matrix"),
                                raw("YEAR", "1999", "1999"),
                                raw("NOISE", "1080p", "1080p")),
                        new int[][]{{0, 10}, {11, 15}, {16, 21}}),
                Arguments.of("E3-pulp-fiction-spaces", "Pulp Fiction (1994).mkv",
                        List.of(raw("TITLE", "Pulp Fiction", "Pulp Fiction"),
                                raw("YEAR", "1994", "1994")),
                        new int[][]{{0, 12}, {14, 18}}),
                Arguments.of("E4-apocalypse-now", "Apocalypse Now.1979.BluRay.x264.mkv",
                        List.of(raw("TITLE", "Apocalypse Now", "Apocalypse Now"),
                                raw("YEAR", "1979", "1979"),
                                raw("NOISE", "BluRay.x264", "BluRay x264")),
                        new int[][]{{0, 14}, {15, 19}, {20, 31}}),

                // ---- F. Folder-derived tokens: empty rawValue resolves to (0,0) ----
                Arguments.of("F1-series-from-folder", "E05.mkv",
                        List.of(raw("SERIES", "", "Some Show"),
                                raw("EPISODE", "E05", "05")),
                        new int[][]{{0, 0}, {0, 3}}),
                Arguments.of("F2-season-from-folder", "Show.E05.mkv",
                        List.of(raw("SERIES", "Show", "Show"),
                                raw("SEASON", "", "01"),
                                raw("EPISODE", "E05", "05")),
                        new int[][]{{0, 4}, {0, 0}, {5, 8}}),
                Arguments.of("F3-both-folder", "01.mkv",
                        List.of(raw("SERIES", "", "Hidden Show"),
                                raw("SEASON", "", "01"),
                                raw("EPISODE", "01", "01")),
                        new int[][]{{0, 0}, {0, 0}, {0, 2}}),
                Arguments.of("F4-empty-raw-with-norm", "Show.S01E01.mkv",
                        List.of(raw("SERIES", "", "Show"),
                                raw("SEASON", "S01", "01"),
                                raw("EPISODE", "E01", "01")),
                        new int[][]{{0, 0}, {5, 8}, {8, 11}}),
                Arguments.of("F5-empty-raw-empty-norm", "Show.S01E01.mkv",
                        List.of(raw("SERIES", "", ""),
                                raw("SEASON", "S01", "01"),
                                raw("EPISODE", "E01", "01")),
                        new int[][]{{0, 0}, {5, 8}, {8, 11}}),

                // ---- G. Duplicate rawValues disambiguated by cursor ----
                Arguments.of("G1-triple-01", "01.S01E01.mkv",
                        List.of(raw("SERIES", "01", "01"),
                                raw("SEASON", "01", "01"),
                                raw("EPISODE", "01", "01")),
                        new int[][]{{0, 2}, {4, 6}, {7, 9}}),
                Arguments.of("G2-two-S01", "Show.S01.S01E02.mkv",
                        List.of(raw("SERIES", "Show", "Show"),
                                raw("SEASON", "S01", "01"),
                                raw("EPISODE", "E02", "02")),
                        new int[][]{{0, 4}, {5, 8}, {12, 15}}),
                Arguments.of("G3-bob-bob", "Bob.Bob.S01E01.mkv",
                        List.of(raw("SERIES", "Bob.Bob", "Bob Bob"),
                                raw("SEASON", "S01", "01"),
                                raw("EPISODE", "E01", "01")),
                        new int[][]{{0, 7}, {8, 11}, {11, 14}}),
                Arguments.of("G4-the-the", "The.The.Show.S01E01.mkv",
                        List.of(raw("SERIES", "The.The.Show", "The The Show"),
                                raw("SEASON", "S01", "01"),
                                raw("EPISODE", "E01", "01")),
                        new int[][]{{0, 12}, {13, 16}, {16, 19}}),
                Arguments.of("G5-ep-ep-ep", "EpEpEp.S01E01.mkv",
                        List.of(raw("SERIES", "EpEpEp", "Ep Ep Ep"),
                                raw("SEASON", "S01", "01"),
                                raw("EPISODE", "E01", "01")),
                        new int[][]{{0, 6}, {7, 10}, {10, 13}}),

                // ---- H. Out-of-order tokens: fallback to from-zero scan ----
                Arguments.of("H1-episode-before-season", "Show.S01E03.mkv",
                        List.of(raw("EPISODE", "E03", "03"),
                                raw("SEASON", "S01", "01"),
                                raw("SERIES", "Show", "Show")),
                        new int[][]{{8, 11}, {5, 8}, {0, 4}}),
                Arguments.of("H2-title-before-series", "Show.S01E01.Pilot.mkv",
                        List.of(raw("TITLE", "Pilot", "Pilot"),
                                raw("SERIES", "Show", "Show"),
                                raw("SEASON", "S01", "01"),
                                raw("EPISODE", "E01", "01")),
                        new int[][]{{12, 17}, {0, 4}, {5, 8}, {8, 11}}),
                Arguments.of("H3-year-before-title", "Inception (2010).mkv",
                        List.of(raw("YEAR", "2010", "2010"),
                                raw("TITLE", "Inception", "Inception")),
                        new int[][]{{11, 15}, {0, 9}}),

                // ---- I. Hallucinated rawValue → graceful (0,0) ----
                Arguments.of("I1-not-in-name", "Show.S01E01.mkv",
                        List.of(raw("TITLE", "PhantomTitle", "Phantom Title")),
                        new int[][]{{0, 0}}),
                Arguments.of("I2-case-mismatch", "Show.S01E01.mkv",
                        List.of(raw("SERIES", "show", "Show")),
                        new int[][]{{0, 0}}),
                Arguments.of("I3-whitespace-mismatch", "Show.S01E01.mkv",
                        List.of(raw("TITLE", "Show S01E01", "Show S01E01")),
                        new int[][]{{0, 0}}),
                Arguments.of("I4-longer-than-filename", "x.mkv",
                        List.of(raw("TITLE", "thisIsWayTooLongToFit", "X"))
                        , new int[][]{{0, 0}}),

                // ---- J. Edge cases (empties, single tokens) ----
                Arguments.of("J1-empty-raws", "Show.S01E01.mkv",
                        List.<RawToken>of(),
                        new int[][]{}),
                Arguments.of("J2-null-filename-empty-raws", null,
                        List.<RawToken>of(),
                        new int[][]{}),
                Arguments.of("J3-single-empty-raw", "Show.S01E01.mkv",
                        List.of(raw("SERIES", "", "Hidden")),
                        new int[][]{{0, 0}}),
                Arguments.of("J4-whole-filename", "x.mkv",
                        List.of(raw("TITLE", "x.mkv", "x")),
                        new int[][]{{0, 5}}),
                Arguments.of("J5-single-char-found", "a.mkv",
                        List.of(raw("TITLE", "a", "a")),
                        new int[][]{{0, 1}}),

                // ---- K. Real-world scenarios ----
                Arguments.of("K1-haikyuu-bd",
                        "Haikyuu!!.S04E01.MULTi.1080p.BluRay.x264-SHiNiGAMi.mkv",
                        List.of(raw("SERIES", "Haikyuu!!", "Haikyuu!!"),
                                raw("SEASON", "S04", "04"),
                                raw("EPISODE", "E01", "01"),
                                raw("NOISE", "MULTi.1080p.BluRay.x264-SHiNiGAMi",
                                        "MULTi 1080p BluRay x264-SHiNiGAMi")),
                        new int[][]{{0, 9}, {10, 13}, {13, 16}, {17, 50}}),
                Arguments.of("K2-daily-date", "Daily.Show.2024-01-15.mkv",
                        List.of(raw("SERIES", "Daily.Show", "Daily Show"),
                                raw("EPISODE", "2024-01-15", "2024-01-15")),
                        new int[][]{{0, 10}, {11, 21}}),
                Arguments.of("K3-f1-round", "F1.2024.R01.Bahrain.GP.mkv",
                        List.of(raw("SERIES", "F1", "F1"),
                                raw("YEAR", "2024", "2024"),
                                raw("NOISE", "R01", "R01"),
                                raw("TITLE", "Bahrain.GP", "Bahrain GP")),
                        new int[][]{{0, 2}, {3, 7}, {8, 11}, {12, 22}}),
                Arguments.of("K4-bracket-group", "[GROUP] Title (BD 1080p).mkv",
                        List.of(raw("NOISE", "[GROUP]", "GROUP"),
                                raw("TITLE", "Title", "Title"),
                                raw("NOISE", "(BD 1080p)", "BD 1080p")),
                        new int[][]{{0, 7}, {8, 13}, {14, 24}}),
                Arguments.of("K5-unicode-hyphen", "Crayon.Shin-chan.S01E01.mkv",
                        List.of(raw("SERIES", "Crayon.Shin-chan", "Crayon Shin-chan"),
                                raw("SEASON", "S01", "01"),
                                raw("EPISODE", "E01", "01")),
                        new int[][]{{0, 16}, {17, 20}, {20, 23}}),

                // ---- L. Role/value variety covering remaining enum members ----
                Arguments.of("L1-noise-release-tag", "Movie.2019.WEB-DL.x265.mkv",
                        List.of(raw("TITLE", "Movie", "Movie"),
                                raw("YEAR", "2019", "2019"),
                                raw("NOISE", "WEB-DL", "WEB-DL"),
                                raw("NOISE", "x265", "x265")),
                        new int[][]{{0, 5}, {6, 10}, {11, 17}, {18, 22}}),
                Arguments.of("L2-year-mid", "Show.2010.S01E01.mkv",
                        List.of(raw("SERIES", "Show", "Show"),
                                raw("YEAR", "2010", "2010"),
                                raw("SEASON", "S01", "01"),
                                raw("EPISODE", "E01", "01")),
                        new int[][]{{0, 4}, {5, 9}, {10, 13}, {13, 16}}),
                Arguments.of("L3-title-with-hyphens", "Mr-Robot.S01E01.mkv",
                        List.of(raw("SERIES", "Mr-Robot", "Mr Robot"),
                                raw("SEASON", "S01", "01"),
                                raw("EPISODE", "E01", "01")),
                        new int[][]{{0, 8}, {9, 12}, {12, 15}}),
                Arguments.of("L4-multi-episode", "Show.S01E01E02.mkv",
                        List.of(raw("SERIES", "Show", "Show"),
                                raw("SEASON", "S01", "01"),
                                raw("EPISODE", "E01", "01"),
                                raw("EPISODE", "E02", "02")),
                        new int[][]{{0, 4}, {5, 8}, {8, 11}, {11, 14}}),
                Arguments.of("L5-title-then-noise", "Show.S01E01.Some.Title.HDTV.x264.mkv",
                        List.of(raw("SERIES", "Show", "Show"),
                                raw("SEASON", "S01", "01"),
                                raw("EPISODE", "E01", "01"),
                                raw("TITLE", "Some.Title", "Some Title"),
                                raw("NOISE", "HDTV.x264", "HDTV x264")),
                        new int[][]{{0, 4}, {5, 8}, {8, 11}, {12, 22}, {23, 32}}),

                // ---- M. NxNN with folder-derived season + extra absolute edge cases ----
                Arguments.of("M1-NxNN-folder-series", "8x06.mkv",
                        List.of(raw("SERIES", "", "Game of Thrones"),
                                raw("SEASON", "8", "08"),
                                raw("EPISODE", "06", "06")),
                        new int[][]{{0, 0}, {0, 1}, {2, 4}}),
                Arguments.of("M2-absolute-three-digits-folder", "show_055.mkv",
                        List.of(raw("SERIES", "show", "Show"),
                                raw("EPISODE", "055", "055")),
                        new int[][]{{0, 4}, {5, 8}})
        );
    }

    @Test
    void nullFilenameWithRawTokensYieldsZeroOffsets() {
        List<AiPatternToken> tokens = BatchTokenOffsetReconstructor.reconstruct(
                null, List.of(raw("SERIES", "Anything", "Anything")));
        assertEquals(1, tokens.size());
        assertEquals(0, tokens.get(0).start());
        assertEquals(0, tokens.get(0).end());
    }

    @Test
    void nullRawTokensListYieldsEmpty() {
        assertTrue(BatchTokenOffsetReconstructor.reconstruct("anything.mkv", null).isEmpty());
    }

    @Test
    void rawTokenNullFieldsAreNormalizedToEmpty() {
        List<AiPatternToken> tokens = BatchTokenOffsetReconstructor.reconstruct(
                "Show.mkv", List.of(new RawToken(null, null, null)));
        assertEquals(1, tokens.size());
        AiPatternToken t = tokens.get(0);
        assertEquals("", t.role());
        assertEquals("", t.rawValue());
        assertEquals("", t.normalizedValue());
        assertEquals(0, t.start());
        assertEquals(0, t.end());
    }
}

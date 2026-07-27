package com.episort.ui.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class ScanRowEditorTest {

    private static ScanRow row(String filename, ScanMediaType mediaType) {
        return new ScanRow(
                Path.of("C:", "media", filename),
                filename,
                filename.substring(filename.lastIndexOf('.') + 1),
                mediaType,
                ScanRowStatus.REVIEW);
    }

    private static ScanInputParse parse(ScanInputToken... tokens) {
        return new ScanInputParse(
                "label", List.of(tokens), Optional.empty(),
                OptionalDouble.empty(), ScanInputParseSource.HEURISTIC);
    }

    private static ScanInputToken token(ScanInputRole role, String value) {
        return new ScanInputToken(role, value, value, 0, 0);
    }

    /* ---- roleValue: which columns are meaningful for which media type ---- */

    @Test
    void seriesRolesReadBlankForAMovie() {
        ScanRow movie = row("Arrival.2016.mkv", ScanMediaType.MOVIE);
        movie.setInputParse(Optional.of(parse(
                token(ScanInputRole.SERIES, "Arrival"),
                token(ScanInputRole.SEASON, "1"),
                token(ScanInputRole.EPISODE, "2"))));

        assertEquals("", ScanRowEditor.roleValue(movie, ScanInputRole.SERIES));
        assertEquals("", ScanRowEditor.roleValue(movie, ScanInputRole.SEASON));
        assertEquals("", ScanRowEditor.roleValue(movie, ScanInputRole.EPISODE));
    }

    @Test
    void yearReadsBlankForAnEpisodeBecauseTvdbOwnsOrdering() {
        ScanRow episode = row("Show.S01E02.mkv", ScanMediaType.SERIES);
        episode.setInputParse(Optional.of(parse(token(ScanInputRole.YEAR, "2016"))));

        assertEquals("", ScanRowEditor.roleValue(episode, ScanInputRole.YEAR));
    }

    @Test
    void taggedTokenWins() {
        ScanRow episode = row("Show.S01E02.mkv", ScanMediaType.SERIES);
        episode.setInputParse(Optional.of(parse(token(ScanInputRole.SERIES, "Show"))));

        assertEquals("Show", ScanRowEditor.roleValue(episode, ScanInputRole.SERIES));
    }

    @Test
    void movieTitleFallsBackToTheFilenameWhenUntagged() {
        ScanRow movie = row("Arrival.2016.1080p.mkv", ScanMediaType.MOVIE);

        assertEquals(
                ScanPlanMapper.derivedMovieTitle("Arrival.2016.1080p.mkv"),
                ScanRowEditor.roleValue(movie, ScanInputRole.TITLE));
    }

    /* ---- applyTokenEdit ------------------------------------------------- */

    @Test
    void editingATokenReplacesItAndClaimsTheParseForTheUser() {
        ScanRow episode = row("Show.S01E02.mkv", ScanMediaType.SERIES);
        episode.setInputParse(Optional.of(parse(token(ScanInputRole.SERIES, "Shwo"))));

        ScanRowEditor.applyTokenEdit(episode, ScanInputRole.SERIES, "Show");

        ScanInputParse updated = episode.inputParse().orElseThrow();
        assertEquals(Optional.of("Show"), updated.tokenValue(ScanInputRole.SERIES));
        assertEquals(ScanInputParseSource.USER, updated.source());
    }

    @Test
    void blankingATokenRemovesIt() {
        ScanRow episode = row("Show.S01E02.mkv", ScanMediaType.SERIES);
        episode.setInputParse(Optional.of(parse(
                token(ScanInputRole.SERIES, "Show"),
                token(ScanInputRole.TITLE, "Pilot"))));

        ScanRowEditor.applyTokenEdit(episode, ScanInputRole.TITLE, "");

        assertTrue(episode.inputParse().orElseThrow().tokenValue(ScanInputRole.TITLE).isEmpty());
        assertEquals(Optional.of("Show"),
                episode.inputParse().orElseThrow().tokenValue(ScanInputRole.SERIES));
    }

    @Test
    void editingAMissingTokenAddsIt() {
        ScanRow episode = row("Show.S01E02.mkv", ScanMediaType.SERIES);
        episode.setInputParse(Optional.of(parse(token(ScanInputRole.SERIES, "Show"))));

        ScanRowEditor.applyTokenEdit(episode, ScanInputRole.TITLE, "Pilot");

        assertEquals(Optional.of("Pilot"),
                episode.inputParse().orElseThrow().tokenValue(ScanInputRole.TITLE));
    }

    @Test
    void editingSeasonRecomputesThePaddedOrder() {
        ScanRow episode = row("Show.S01E02.mkv", ScanMediaType.SERIES);
        episode.setInputParse(Optional.of(parse(
                token(ScanInputRole.SEASON, "1"),
                token(ScanInputRole.EPISODE, "2"))));

        ScanRowEditor.applyTokenEdit(episode, ScanInputRole.SEASON, "3");

        assertEquals(Optional.of("S03E02"), episode.order());
    }

    @Test
    void orderStaysEmptyWhenSeasonOrEpisodeIsMissing() {
        ScanRow episode = row("Show.mkv", ScanMediaType.SERIES);
        episode.setInputParse(Optional.of(parse(token(ScanInputRole.SEASON, "1"))));

        ScanRowEditor.applyTokenEdit(episode, ScanInputRole.SEASON, "2");

        assertEquals(Optional.empty(), episode.order());
    }

    @Test
    void nonNumericSeasonDoesNotProduceAnOrder() {
        ScanRow episode = row("Show.mkv", ScanMediaType.SERIES);
        episode.setInputParse(Optional.of(parse(
                token(ScanInputRole.SEASON, "one"),
                token(ScanInputRole.EPISODE, "2"))));

        ScanRowEditor.applyTokenEdit(episode, ScanInputRole.EPISODE, "3");

        assertEquals(Optional.empty(), episode.order());
    }

    @Test
    void editingATokenRefreshesTheProposedName() {
        ScanRow episode = row("Show.S01E02.mkv", ScanMediaType.SERIES);
        episode.setInputParse(Optional.of(parse(
                token(ScanInputRole.SERIES, "Show"),
                token(ScanInputRole.SEASON, "1"),
                token(ScanInputRole.EPISODE, "2"),
                token(ScanInputRole.TITLE, "Pilot"))));

        ScanRowEditor.applyTokenEdit(episode, ScanInputRole.TITLE, "Arrival");

        assertTrue(episode.proposedFilename().orElseThrow().contains("Arrival"),
                "proposed name should follow the edited title, was: "
                        + episode.proposedFilename().orElse("<none>"));
    }

    /* ---- applyManualInputPattern ---------------------------------------- */

    @Test
    void blankPatternClearsBothThePatternAndTheParse() {
        ScanRow episode = row("Show.S01E02.mkv", ScanMediaType.SERIES);
        episode.setInputPattern(Optional.of("something"));
        episode.setInputParse(Optional.of(parse(token(ScanInputRole.SERIES, "Show"))));

        ScanRowEditor.applyManualInputPattern(episode, "   ");

        assertEquals(Optional.empty(), episode.inputPattern());
        assertEquals(Optional.empty(), episode.inputParse());
    }

    @Test
    void placeholderPatternIsTreatedAsBlank() {
        ScanRow episode = row("Show.S01E02.mkv", ScanMediaType.SERIES);
        episode.setInputPattern(Optional.of("something"));

        ScanRowEditor.applyManualInputPattern(episode, com.episort.ui.UiText.EMPTY);

        assertEquals(Optional.empty(), episode.inputPattern());
    }

    @Test
    void manualPatternKeepsOnlyItsFirstLineAsTheLabel() {
        ScanRow episode = row("Show.S01E02.mkv", ScanMediaType.SERIES);

        ScanRowEditor.applyManualInputPattern(episode, "first line\nsecond line");

        assertEquals(Optional.of("first line\nsecond line"), episode.inputPattern());
        episode.inputParse().ifPresent(parsed -> {
            assertEquals("first line", parsed.label());
            assertEquals(ScanInputParseSource.USER, parsed.source());
        });
    }

    /* ---- patternTooltip ------------------------------------------------- */

    @Test
    void tooltipFallsBackToThePlaceholderWithNothingToShow() {
        ScanRow episode = row("Show.S01E02.mkv", ScanMediaType.SERIES);

        assertEquals(com.episort.ui.UiText.EMPTY, ScanRowEditor.patternTooltip(episode));
    }

    @Test
    void tooltipReportsTheParseSource() {
        ScanRow episode = row("Show.S01E02.mkv", ScanMediaType.SERIES);
        episode.setInputParse(Optional.of(parse(token(ScanInputRole.SERIES, "Show"))));

        assertTrue(ScanRowEditor.patternTooltip(episode).contains("source=HEURISTIC"));
    }
}

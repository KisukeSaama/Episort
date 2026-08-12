package com.episort.ui.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.scanner.InventoryGroup;
import com.episort.scanner.InventoryGroupType;
import com.episort.scanner.InventoryItem;
import com.episort.scanner.InventoryItemType;
import com.episort.ui.AppLanguage;
import com.episort.ui.UiText;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScanGroupIndexTest {

    private static final Path ROOT = Path.of("C:", "media");

    private static ScanRow row(String name) {
        return new ScanRow(ROOT.resolve(name), name, "mkv", ScanMediaType.SERIES, ScanRowStatus.REVIEW);
    }

    private static InventoryItem item(String name) {
        return new InventoryItem(
                ROOT.resolve(name), name, "mkv", ROOT, InventoryItemType.SUPPORTED_VIDEO, true);
    }

    private static InventoryGroup group(InventoryGroupType type, String seed, String... names) {
        return new InventoryGroup(type, seed, List.of(Arrays.stream(names)
                .map(ScanGroupIndexTest::item).toArray(InventoryItem[]::new)), false);
    }

    @Test
    void mapsEachRowToItsGroup() {
        ScanRow a = row("a.mkv");
        ScanRow b = row("b.mkv");
        ScanGroupIndex index = new ScanGroupIndex();
        index.replaceGroups(List.of(group(InventoryGroupType.LIKELY_SERIES, "Show", "a.mkv", "b.mkv")));

        index.rebuild(List.of(a, b));

        assertNotNull(index.groupOf(a));
        assertEquals("Show", index.groupOf(b).seedName());
        assertNotNull(index.matchOf(a));
    }

    @Test
    void groupsAccumulateAcrossSuccessiveLoads() {
        ScanRow first = row("a.mkv");
        ScanRow second = row("b.mkv");
        ScanGroupIndex index = new ScanGroupIndex();
        index.replaceGroups(List.of(group(InventoryGroupType.LIKELY_SERIES, "First", "a.mkv")));
        index.rebuild(List.of(first));

        index.addGroups(List.of(group(InventoryGroupType.LIKELY_SERIES, "Second", "b.mkv")));
        index.rebuild(List.of(first, second));

        assertEquals("First", index.groupOf(first).seedName(),
                "a second load must not strip the first load's rows of their group");
        assertEquals("Second", index.groupOf(second).seedName());
    }

    @Test
    void replaceGroupsDropsThePreviousScan() {
        ScanRow first = row("a.mkv");
        ScanGroupIndex index = new ScanGroupIndex();
        index.replaceGroups(List.of(group(InventoryGroupType.LIKELY_SERIES, "First", "a.mkv")));
        index.rebuild(List.of(first));

        index.replaceGroups(List.of(group(InventoryGroupType.LIKELY_SERIES, "Other", "b.mkv")));
        index.rebuild(List.of(first));

        assertNull(index.groupOf(first));
    }

    @Test
    void rowsOfOneGroupSeeEachOtherAsPeers() {
        ScanRow a = row("a.mkv");
        ScanRow b = row("b.mkv");
        ScanGroupIndex index = new ScanGroupIndex();
        index.replaceGroups(List.of(group(InventoryGroupType.LIKELY_SERIES, "Show", "a.mkv", "b.mkv")));
        index.rebuild(List.of(a, b));

        assertEquals(List.of(a, b), index.membersOf("Show"));
    }

    @Test
    void onlyMediaBearingGroupsGetAnIdentityEntry() {
        ScanRow video = row("a.mkv");
        ScanRow sidecar = new ScanRow(
                ROOT.resolve("b.srt"), "b.srt", "srt", ScanMediaType.IGNORED, ScanRowStatus.IGNORED);
        ScanGroupIndex index = new ScanGroupIndex();
        index.replaceGroups(List.of(
                group(InventoryGroupType.LIKELY_SERIES, "Show", "a.mkv"),
                group(InventoryGroupType.SIDECAR, "sidecar", "b.srt")));

        index.rebuild(List.of(video, sidecar));

        assertEquals(List.of("Show"), List.copyOf(index.rowsByGroupName().keySet()));
        assertTrue(index.namesAMedia(video));
        assertFalse(index.namesAMedia(sidecar));
    }

    @Test
    void ignoredGroupsReadAsIgnoredRatherThanTheirInternalSeed() {
        ScanRow sidecar = new ScanRow(
                ROOT.resolve("b.srt"), "b.srt", "srt", ScanMediaType.IGNORED, ScanRowStatus.IGNORED);
        ScanGroupIndex index = new ScanGroupIndex();
        index.replaceGroups(List.of(group(InventoryGroupType.SIDECAR, "sidecar", "b.srt")));
        index.rebuild(List.of(sidecar));

        assertEquals(
                UiText.scanMediaTypeIgnored(AppLanguage.FRENCH),
                index.displayName(sidecar, AppLanguage.FRENCH));
    }

    @Test
    void activeEpisodeOverridesItsNonMediaScanBucket() {
        ScanRow episode = row("Detective Conan - S16E30.mkv");
        episode.setInputParse(ScanInputPatternParser.parse(episode.originalFilename()));
        ScanGroupIndex index = new ScanGroupIndex();
        index.replaceGroups(List.of(group(
                InventoryGroupType.IGNORED,
                "ignored",
                "Detective Conan - S16E30.mkv")));

        index.rebuild(List.of(episode));

        assertTrue(index.namesAMedia(episode));
        assertEquals("Detective Conan", index.displayName(episode, AppLanguage.FRENCH));
        assertEquals(List.of(episode), index.membersOf("Detective Conan"));
    }

    @Test
    void tmdbUpdateOverridesCachedIgnoredGroupWithoutAnotherIndexRebuild() {
        ScanRow episode = new ScanRow(
                ROOT.resolve("Detective Conan - S16E30.mkv"),
                "Detective Conan - S16E30.mkv",
                "mkv",
                ScanMediaType.IGNORED,
                ScanRowStatus.IGNORED);
        episode.setInputParse(ScanInputPatternParser.parse(episode.originalFilename()));
        ScanGroupIndex index = new ScanGroupIndex();
        index.replaceGroups(List.of(group(
                InventoryGroupType.IGNORED,
                "ignored",
                "Detective Conan - S16E30.mkv")));
        index.rebuild(List.of(episode));

        episode.setMediaType(ScanMediaType.SERIES);
        episode.setStatus(ScanRowStatus.TMDB);

        assertTrue(index.namesAMedia(episode));
        assertTrue(index.matchOf(episode).namesAMedia());
        assertEquals("Detective Conan", index.displayName(episode, AppLanguage.FRENCH));
    }

    @Test
    void reactivatedIgnoredEpisodeImmediatelyBecomesAnInteractiveSeriesGroup() {
        ScanRow episode = new ScanRow(
                ROOT.resolve("Detective Conan - S16E30.mkv"),
                "Detective Conan - S16E30.mkv",
                "mkv",
                ScanMediaType.IGNORED,
                ScanRowStatus.IGNORED);
        episode.setInputParse(ScanInputPatternParser.parse(episode.originalFilename()));
        ScanGroupIndex index = new ScanGroupIndex();
        index.replaceGroups(List.of(group(
                InventoryGroupType.IGNORED,
                "ignored",
                "Detective Conan - S16E30.mkv")));
        index.rebuild(List.of(episode));

        episode.stopIgnoring();
        index.rebuild(List.of(episode));

        assertFalse(episode.isIgnored());
        assertEquals(ScanMediaType.SERIES, episode.mediaType());
        assertEquals(ScanRowStatus.REVIEW, episode.status());
        assertTrue(index.namesAMedia(episode));
        assertTrue(index.matchOf(episode).namesAMedia());
        assertEquals("Detective Conan", index.displayName(episode, AppLanguage.FRENCH));
        assertEquals(List.of(episode), index.membersOf("Detective Conan"));
    }

    @Test
    void reactivatedMovieUsesItsDerivedTitleAsTheMediaGroup() {
        ScanRow movie = new ScanRow(
                ROOT.resolve("Brice.de.Nice.2005.mkv"),
                "Brice.de.Nice.2005.mkv",
                "mkv",
                ScanMediaType.IGNORED,
                ScanRowStatus.IGNORED);
        ScanGroupIndex index = new ScanGroupIndex();
        index.replaceGroups(List.of(group(
                InventoryGroupType.IGNORED,
                "ignored",
                "Brice.de.Nice.2005.mkv")));
        index.rebuild(List.of(movie));

        movie.stopIgnoring();
        movie.setMediaType(ScanMediaType.MOVIE);
        index.rebuild(List.of(movie));

        assertTrue(index.namesAMedia(movie));
        assertEquals("Brice de Nice", index.displayName(movie, AppLanguage.FRENCH));
    }

    @Test
    void anUngroupedRowReadsThePlaceholder() {
        ScanRow orphan = row("a.mkv");

        assertEquals(UiText.EMPTY, new ScanGroupIndex().displayName(orphan, AppLanguage.FRENCH));
    }

    @Test
    void aBlankSeedReadsThePlaceholder() {
        ScanRow unnamed = row("a.mkv");
        ScanGroupIndex index = new ScanGroupIndex();
        index.replaceGroups(List.of(group(InventoryGroupType.UNKNOWN, "  ", "a.mkv")));
        index.rebuild(List.of(unnamed));

        assertEquals(UiText.EMPTY, index.displayName(unnamed, AppLanguage.FRENCH));
    }

    @Test
    void clearForgetsEverything() {
        ScanRow a = row("a.mkv");
        ScanGroupIndex index = new ScanGroupIndex();
        index.replaceGroups(List.of(group(InventoryGroupType.LIKELY_SERIES, "Show", "a.mkv")));
        index.rebuild(List.of(a));

        index.clear();

        assertTrue(index.isEmpty());
        assertNull(index.groupOf(a));
        assertEquals(List.of(), index.membersOf("Show"));
    }

    @Test
    void rowsAbsentFromTheScanAreNotIndexed() {
        ScanRow present = row("a.mkv");
        ScanGroupIndex index = new ScanGroupIndex();
        index.replaceGroups(List.of(group(InventoryGroupType.LIKELY_SERIES, "Show", "a.mkv", "missing.mkv")));

        index.rebuild(List.of(present));

        assertEquals(List.of(present), index.membersOf("Show"));
    }
}

package com.episort.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class LaunchRequestTest {
    @Test
    void parsesAFolderLinkTheWayUmbraWritesIt() {
        LaunchRequest request = LaunchRequest.parse(
                "episort://open?volume=Media&path=Series%2FSome+Show%2FSeason+01").orElseThrow();

        assertEquals("Media", request.volume());
        assertEquals(List.of("Series", "Some Show", "Season 01"), request.folder());
        assertFalse(request.hasFiles());
        assertEquals(Path.of("/mnt/plex/Series/Some Show/Season 01"),
                request.folderUnder(Path.of("/mnt/plex")));
        assertEquals("Media/Series/Some Show/Season 01", request.describe());
    }

    @Test
    void parsesFilesInsideTheFolder() {
        LaunchRequest request = LaunchRequest.parse(
                "episort://open?volume=Media&path=Incoming&file=a.mkv&file=b+%26+c.mkv").orElseThrow();

        assertEquals(List.of("a.mkv", "b & c.mkv"), request.files());
        assertEquals(
                List.of(Path.of("/root/Incoming/a.mkv"), Path.of("/root/Incoming/b & c.mkv")),
                request.filesUnder(Path.of("/root")));
    }

    @Test
    void anEmptyPathIsTheVolumeRoot() {
        LaunchRequest request = LaunchRequest.parse("episort://open?volume=Media&path=").orElseThrow();

        assertEquals(List.of(), request.folder());
        assertEquals(Path.of("/root"), request.folderUnder(Path.of("/root")));
    }

    @Test
    void refusesAnythingThatCouldLeaveTheRoot() {
        assertTrue(LaunchRequest.parse("episort://open?volume=Media&path=..%2Fetc").isEmpty());
        assertTrue(LaunchRequest.parse("episort://open?volume=Media&path=a&file=..").isEmpty());
        assertTrue(LaunchRequest.parse("episort://open?volume=Media&path=a&file=x%5Cy").isEmpty());
        assertTrue(LaunchRequest.parse("episort://open?volume=Media&path=a&file=x%00y").isEmpty());
        assertTrue(LaunchRequest.parse("episort://open?volume=Media&path=a&file=").isEmpty());
    }

    @Test
    void refusesOtherSchemesActionsAndGarbage() {
        assertTrue(LaunchRequest.parse("https://open?volume=Media").isEmpty());
        assertTrue(LaunchRequest.parse("episort://delete?volume=Media").isEmpty());
        assertTrue(LaunchRequest.parse("episort://open?path=a b").isEmpty());
        assertTrue(LaunchRequest.parse("not a link").isEmpty());
        assertTrue(LaunchRequest.parse("").isEmpty());
    }

    @Test
    void capsTheNumberOfFiles() {
        String files = IntStream.range(0, LaunchRequest.MAX_FILES + 1)
                .mapToObj(index -> "&file=f" + index + ".mkv")
                .collect(Collectors.joining());

        assertTrue(LaunchRequest.parse("episort://open?volume=Media&path=a" + files).isEmpty());
    }

    @Test
    void findsTheLinkAmongOtherArguments() {
        Optional<LaunchRequest> request = LaunchRequest.fromArguments(
                List.of("--flag", "EPISORT://open?volume=Media&path=Series"));

        assertEquals(List.of("Series"), request.orElseThrow().folder());
        assertTrue(LaunchRequest.fromArguments(List.of("--flag")).isEmpty());
        assertTrue(LaunchRequest.fromArguments(null).isEmpty());
    }
}

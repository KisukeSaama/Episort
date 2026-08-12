package com.episort.ui.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.matching.MediaMatchProposal;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TmdbMovieAlertPolicyTest {
    @Test
    void manualIdentitySelectionClearsAutomaticFilenameMismatch() {
        MediaMatchProposal mismatch = MediaMatchProposal.unmatched(
                Path.of("Brice.de.Nice.FRENCH.1080p.HDLight.mkv"),
                "Filename does not resemble selected TMDB movie.");

        assertTrue(TmdbMovieAlertPolicy.blockingAlert(true, mismatch).isEmpty());
    }

    @Test
    void automaticIdentityStillReportsFilenameMismatch() {
        MediaMatchProposal mismatch = MediaMatchProposal.unmatched(
                Path.of("Unrelated.mkv"),
                "Filename does not resemble selected TMDB movie.");

        assertEquals(
                Optional.of("Filename does not resemble selected TMDB movie."),
                TmdbMovieAlertPolicy.blockingAlert(false, mismatch));
    }
}

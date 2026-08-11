package com.episort.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.tmdb.TmdbIdentity;
import com.episort.tmdb.TmdbMediaType;
import java.util.List;
import org.junit.jupiter.api.Test;

class TmdbIdentitySelectionServiceTest {
    @Test
    void storesAndChangesSelectedIdentityForCurrentSession() {
        TmdbIdentitySelectionService service = new TmdbIdentitySelectionService();
        TmdbIdentity first = new TmdbIdentity("101", TmdbMediaType.SERIES, "Show");
        TmdbIdentity corrected = new TmdbIdentity("102", TmdbMediaType.SERIES, "Show 2005");

        service.select("group-a", first);
        service.select("group-a", corrected);

        assertEquals(corrected, service.selectedIdentity("group-a").orElseThrow());
        assertFalse(service.selectionAuthorizesValidationOrExecution());
    }

    @Test
    void identifiesGroupsThatCanBeMergedBySameTmdbIdentity() {
        TmdbIdentitySelectionService service = new TmdbIdentitySelectionService();
        TmdbIdentity identity = new TmdbIdentity("101", TmdbMediaType.SERIES, "Show");

        service.select("group-a", identity);
        service.select("group-b", identity);
        service.select("movie-a", new TmdbIdentity("101", TmdbMediaType.MOVIE, "Show"));

        assertEquals(List.of(List.of("group-a", "group-b")), service.mergeableGroupKeys());
    }

    @Test
    void doesNotAllowChangingMediaKindInsideSameGroup() {
        TmdbIdentitySelectionService service = new TmdbIdentitySelectionService();
        service.select("group-a", new TmdbIdentity("101", TmdbMediaType.SERIES, "Show"));

        assertFalse(service.canChangeBeforePatternValidation(
                "group-a", new TmdbIdentity("202", TmdbMediaType.MOVIE, "Movie")));
        assertTrue(service.canChangeBeforePatternValidation(
                "group-a", new TmdbIdentity("102", TmdbMediaType.SERIES, "Show 2005")));
    }
}

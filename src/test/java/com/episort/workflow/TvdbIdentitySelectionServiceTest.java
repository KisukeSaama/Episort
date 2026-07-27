package com.episort.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.tvdb.TvdbIdentity;
import com.episort.tvdb.TvdbMediaType;
import java.util.List;
import org.junit.jupiter.api.Test;

class TvdbIdentitySelectionServiceTest {
    @Test
    void storesAndChangesSelectedIdentityForCurrentSession() {
        TvdbIdentitySelectionService service = new TvdbIdentitySelectionService();
        TvdbIdentity first = new TvdbIdentity("101", TvdbMediaType.SERIES, "Show");
        TvdbIdentity corrected = new TvdbIdentity("102", TvdbMediaType.SERIES, "Show 2005");

        service.select("group-a", first);
        service.select("group-a", corrected);

        assertEquals(corrected, service.selectedIdentity("group-a").orElseThrow());
        assertFalse(service.selectionAuthorizesValidationOrExecution());
    }

    @Test
    void identifiesGroupsThatCanBeMergedBySameTvdbIdentity() {
        TvdbIdentitySelectionService service = new TvdbIdentitySelectionService();
        TvdbIdentity identity = new TvdbIdentity("101", TvdbMediaType.SERIES, "Show");

        service.select("group-a", identity);
        service.select("group-b", identity);
        service.select("movie-a", new TvdbIdentity("101", TvdbMediaType.MOVIE, "Show"));

        assertEquals(List.of(List.of("group-a", "group-b")), service.mergeableGroupKeys());
    }

    @Test
    void doesNotAllowChangingMediaKindInsideSameGroup() {
        TvdbIdentitySelectionService service = new TvdbIdentitySelectionService();
        service.select("group-a", new TvdbIdentity("101", TvdbMediaType.SERIES, "Show"));

        assertFalse(service.canChangeBeforePatternValidation(
                "group-a", new TvdbIdentity("202", TvdbMediaType.MOVIE, "Movie")));
        assertTrue(service.canChangeBeforePatternValidation(
                "group-a", new TvdbIdentity("102", TvdbMediaType.SERIES, "Show 2005")));
    }
}

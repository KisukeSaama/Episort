package com.episort.planning;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlanConflictTypeTest {

    @Test
    void conflictsBetweenRealFilesAllowDeletingTheSource() {
        assertTrue(PlanConflictType.DUPLICATE_DESTINATION.deletableSource());
        assertTrue(PlanConflictType.DUPLICATE_MEDIA.deletableSource());
        assertTrue(PlanConflictType.MEDIA_ALREADY_IN_LIBRARY.deletableSource());
        assertTrue(PlanConflictType.DESTINATION_FILE_EXISTS.deletableSource());
    }

    @Test
    void structuralPathConflictsNeverOfferDeletion() {
        assertFalse(PlanConflictType.SOURCE_OUTSIDE_WORKSPACE.deletableSource());
        assertFalse(PlanConflictType.DESTINATION_OUTSIDE_WORKSPACE.deletableSource());
        assertFalse(PlanConflictType.PATH_TOO_LONG.deletableSource());
        assertFalse(PlanConflictType.DESTINATION_FOLDER_BLOCKED.deletableSource());
    }
}

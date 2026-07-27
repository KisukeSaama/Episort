package com.episort.planning;


/** Why a planned operation cannot be executed as-is. All of these are blocking. */
public enum PlanConflictType {
    /** Two planned items resolve to the same destination path. */
    DUPLICATE_DESTINATION,
    /**
     * Two planned items are the same episode or the same movie under different
     * names, so each would get a valid destination of its own and the library
     * would end up holding both.
     *
     * <p>Only the extra copy carries this conflict; the copy the plan keeps is
     * named in {@link PlanConflict#duplicateOf()}. Replacing here means this copy
     * is the one to keep and the other one is retired — not that anything gets
     * overwritten.
     */
    DUPLICATE_MEDIA,
    /**
     * The library already holds this episode or this movie, under a name the
     * plan would not overwrite. Left alone, the file would land beside the copy
     * already there and double the space it takes.
     */
    MEDIA_ALREADY_IN_LIBRARY,
    /** An unrelated file already occupies the destination. */
    DESTINATION_FILE_EXISTS,
    /** The source file is not inside the configured workspace. */
    SOURCE_OUTSIDE_WORKSPACE,
    /** The destination would land outside the configured workspace. */
    DESTINATION_OUTSIDE_WORKSPACE,
    /** The destination still exceeds the Windows path limit after truncation. */
    PATH_TOO_LONG,
    /** A destination folder name is already taken by a file. */
    DESTINATION_FOLDER_BLOCKED;

    /**
     * True when the conflict is only about which of two real files wins the
     * destination — the sole case where replacing is a meaningful answer.
     *
     * <p>Everything else (a path outside the workspace, a path Windows cannot
     * hold, a folder blocked by a file) stays unfixable from the review window:
     * replacing would not make the destination valid, so those conflicts can only
     * be dropped from the plan.
     */
    public boolean resolvableByReplacement() {
        return this == DUPLICATE_DESTINATION
                || this == DUPLICATE_MEDIA
                || this == MEDIA_ALREADY_IN_LIBRARY
                || this == DESTINATION_FILE_EXISTS;
    }

    /**
     * True when removing the source file is a sensible answer: the conflict is
     * that the same episode exists twice, so throwing the extra copy away settles
     * it for good instead of leaving it to block the next run.
     *
     * <p>Deliberately the same set as {@link #resolvableByReplacement()}: a path
     * problem is never a reason to destroy the user's file, so those conflicts can
     * only be dropped from the plan.
     */
    public boolean deletableSource() {
        return resolvableByReplacement();
    }
}

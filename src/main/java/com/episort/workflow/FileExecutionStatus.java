package com.episort.workflow;


/** Per-file outcome of an execution run (Story 7.3). */
public enum FileExecutionStatus {
    /** The file changed folder. */
    MOVED,
    /** The file kept its folder and changed name. */
    RENAMED,
    /** The file was removed, on the user's explicit conflict decision. */
    DELETED,
    /** The operation failed; the file stayed where it was. */
    FAILED,
    /** The user chose to skip this file, or the run was aborted before reaching it. */
    SKIPPED,
    /** Nothing was planned for this file; it was never opened. */
    UNTOUCHED
}

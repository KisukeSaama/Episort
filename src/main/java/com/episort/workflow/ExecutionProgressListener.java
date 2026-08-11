package com.episort.workflow;


/** Receives execution progress updates. Implementations must not block. */
@FunctionalInterface
public interface ExecutionProgressListener {
    void onProgress(ExecutionProgress progress);

    static ExecutionProgressListener noop() {
        return progress -> {
        };
    }
}

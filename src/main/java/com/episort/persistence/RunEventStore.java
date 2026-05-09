package com.episort.persistence;

import java.util.List;

public interface RunEventStore {
    void append(RunEvent event);

    List<RunEvent> readAll();

    default void clear() {
        throw new UnsupportedOperationException("Run event store does not support clearing");
    }
}

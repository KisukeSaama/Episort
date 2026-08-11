package com.episort.ui.history;

import com.episort.persistence.RunEvent;
import com.episort.persistence.RunEventStatus;
import java.util.function.Predicate;

public enum HistoryFilter {
    ALL(event -> true),
    SUCCESSFUL(event -> event.status() == RunEventStatus.SUCCESS),
    WARNINGS(event -> event.status() == RunEventStatus.WARNING),
    CONFLICTS(event -> event.status() == RunEventStatus.CONFLICT),
    FAILED(event -> event.status() == RunEventStatus.FAILED),
    IGNORED(event -> event.status() == RunEventStatus.IGNORED);

    private final Predicate<RunEvent> predicate;

    HistoryFilter(Predicate<RunEvent> predicate) {
        this.predicate = predicate;
    }

    public boolean matches(RunEvent event) {
        return predicate.test(event);
    }
}

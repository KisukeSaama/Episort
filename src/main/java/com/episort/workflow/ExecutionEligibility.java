package com.episort.workflow;

import java.util.List;

public record ExecutionEligibility(boolean executable, List<String> blockers) {
    public ExecutionEligibility {
        blockers = List.copyOf(blockers);
    }
}

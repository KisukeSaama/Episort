package com.episort.ui.scan;

import com.episort.scanner.InventoryGroup;
import com.episort.scanner.InventoryGroupType;
import com.episort.scanner.InventoryScanResult;
import com.episort.ui.AppLanguage;
import com.episort.ui.UiText;
import java.util.List;
import java.util.Objects;

record BatchTvdbMatch(String seedName, InventoryGroupType type, int itemCount, boolean finalMatch) {
    private static final String EMPTY = "—";

    static List<BatchTvdbMatch> from(InventoryScanResult result) {
        Objects.requireNonNull(result, "result");
        return result.groups().stream()
                .map(BatchTvdbMatch::from)
                .toList();
    }

    private static BatchTvdbMatch from(InventoryGroup group) {
        String seed = group.seedName() == null || group.seedName().isBlank() ? EMPTY : group.seedName();
        return new BatchTvdbMatch(seed, group.type(), group.items().size(), group.tvdbIdentityFinal());
    }

    String typeText(AppLanguage language) {
        return switch (type) {
            case LIKELY_SERIES -> UiText.scanMediaTypeSeries(language);
            case LIKELY_MOVIE -> UiText.scanMediaTypeMovie(language);
            case UNKNOWN -> UiText.scanMediaTypeUnknown(language);
            case SIDECAR, UNSUPPORTED, IGNORED -> UiText.scanMediaTypeIgnored(language);
        };
    }

    String statusText(AppLanguage language) {
        return finalMatch
                ? UiText.scanBatchTvdbStatusFinal(language)
                : UiText.scanBatchTvdbStatusUnresolved(language);
    }
}

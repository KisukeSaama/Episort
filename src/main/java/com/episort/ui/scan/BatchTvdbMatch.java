package com.episort.ui.scan;

import com.episort.scanner.InventoryGroup;
import com.episort.scanner.InventoryGroupType;
import com.episort.scanner.InventoryScanResult;
import com.episort.ui.AppLanguage;
import com.episort.ui.UiText;
import java.util.List;
import java.util.Objects;

record BatchTvdbMatch(String seedName, InventoryGroupType type, int itemCount, boolean finalMatch) {

    static List<BatchTvdbMatch> from(InventoryScanResult result) {
        Objects.requireNonNull(result, "result");
        return result.groups().stream()
                .map(BatchTvdbMatch::from)
                .toList();
    }

    private static BatchTvdbMatch from(InventoryGroup group) {
        String seed = group.seedName() == null || group.seedName().isBlank() ? UiText.EMPTY : group.seedName();
        return new BatchTvdbMatch(seed, group.type(), group.items().size(), group.tvdbIdentityFinal());
    }

    /** Whether the group can carry a TVDB identity at all. */
    boolean namesAMedia() {
        return ScanGroupIndex.namesAMedia(type);
    }

    /**
     * The group label for the reader. Sidecars, unsupported files and ignored
     * entries carry an internal seed ("sidecar", "unsupported") that names no
     * media: showing it suggests a group waiting to be matched, which it is not.
     */
    String seedText(AppLanguage language) {
        return namesAMedia() ? seedName : UiText.scanMediaTypeIgnored(language);
    }

    String typeText(AppLanguage language) {
        return switch (type) {
            case LIKELY_SERIES -> UiText.scanMediaTypeSeries(language);
            case LIKELY_MOVIE -> UiText.scanMediaTypeMovie(language);
            case UNKNOWN -> UiText.scanMediaTypeUnknown(language);
            case SIDECAR, UNSUPPORTED, IGNORED -> UiText.scanMediaTypeIgnored(language);
        };
    }

    /**
     * A group that names no media is never "unresolved": there is nothing to
     * resolve, so the status stays blank rather than reading as a pending gate.
     */
    String statusText(AppLanguage language) {
        if (!namesAMedia()) {
            return UiText.EMPTY;
        }
        return finalMatch
                ? UiText.scanBatchTvdbStatusFinal(language)
                : UiText.scanBatchTvdbStatusUnresolved(language);
    }
}

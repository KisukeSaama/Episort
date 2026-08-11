package com.episort.ui.scan;

import com.episort.scanner.InventoryGroup;
import com.episort.scanner.InventoryGroupType;
import com.episort.scanner.InventoryItem;
import com.episort.ui.AppLanguage;
import com.episort.ui.UiText;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which scan row belongs to which inventory group, and what that group means.
 *
 * <p>The scan screen needs this from several angles at once — the group badge,
 * the mixed-selection guard, the per-group identity gate — so all of them are
 * derived here from one rebuild instead of from five maps kept in step by hand.
 *
 * <p>Groups accumulate across successive folder loads. Rebuilding from the
 * latest scan alone stripped earlier rows of their group, which silently broke
 * both the badge and the guard.
 */
final class ScanGroupIndex {

    private final Map<ScanRow, InventoryGroup> groupByRow = new HashMap<>();
    private final Map<ScanRow, BatchTmdbMatch> matchByRow = new HashMap<>();
    private final Map<ScanRow, List<ScanRow>> peersByRow = new HashMap<>();
    /** Scan order preserved: the identity screen lists groups as the table does. */
    private final Map<String, List<ScanRow>> rowsByGroupName = new LinkedHashMap<>();
    private final List<InventoryGroup> knownGroups = new ArrayList<>();

    /** Adds a freshly scanned batch of groups; call {@link #rebuild} afterwards. */
    void addGroups(List<InventoryGroup> groups) {
        knownGroups.addAll(groups);
    }

    /** Replaces the known groups with a freshly scanned batch. */
    void replaceGroups(List<InventoryGroup> groups) {
        knownGroups.clear();
        knownGroups.addAll(groups);
    }

    /** Re-derives every view from the known groups and the rows now on screen. */
    void rebuild(List<ScanRow> rows) {
        groupByRow.clear();
        matchByRow.clear();
        peersByRow.clear();
        rowsByGroupName.clear();
        Map<String, ScanRow> byPath = new HashMap<>();
        for (ScanRow row : rows) {
            byPath.putIfAbsent(pathKey(row.sourcePath()), row);
        }
        for (InventoryGroup group : knownGroups) {
            BatchTmdbMatch match = directMatch(group);
            List<ScanRow> members = new ArrayList<>();
            for (InventoryItem item : group.items()) {
                ScanRow row = byPath.get(pathKey(item.sourcePath()));
                if (row == null) {
                    continue;
                }
                members.add(row);
                groupByRow.put(row, group);
                matchByRow.put(row, match);
            }
            for (ScanRow member : members) {
                peersByRow.put(member, members);
            }
            if (!members.isEmpty() && namesAMedia(group.type())) {
                rowsByGroupName
                        .computeIfAbsent(match.seedName(), ignored -> new ArrayList<>())
                        .addAll(members);
            }
        }
    }

    void clear() {
        groupByRow.clear();
        matchByRow.clear();
        peersByRow.clear();
        rowsByGroupName.clear();
        knownGroups.clear();
    }

    InventoryGroup groupOf(ScanRow row) {
        return groupByRow.get(row);
    }

    BatchTmdbMatch matchOf(ScanRow row) {
        return matchByRow.get(row);
    }

    /** Every group that can carry an identity, in scan order. */
    Map<String, List<ScanRow>> rowsByGroupName() {
        return Collections.unmodifiableMap(rowsByGroupName);
    }

    List<ScanRow> membersOf(String groupName) {
        return rowsByGroupName.getOrDefault(groupName, List.of());
    }

    boolean isEmpty() {
        return rowsByGroupName.isEmpty();
    }

    /**
     * The label shown in the group column. Sidecars, unsupported files and
     * ignored entries have an internal seed ("sidecar", "unsupported") that
     * means nothing to the reader: those rows simply read "ignored".
     */
    String displayName(ScanRow row, AppLanguage language) {
        InventoryGroup group = groupByRow.get(row);
        if (group == null) {
            return UiText.EMPTY;
        }
        if (!namesAMedia(group.type())) {
            return UiText.scanMediaTypeIgnored(language);
        }
        if (group.seedName() == null || group.seedName().isBlank()) {
            return UiText.EMPTY;
        }
        return group.seedName();
    }

    /** Whether the row's group is one that can carry a TMDB identity at all. */
    boolean namesAMedia(ScanRow row) {
        InventoryGroup group = groupByRow.get(row);
        return group != null && namesAMedia(group.type());
    }

    /** Sidecars, unsupported files and ignored entries never carry an identity. */
    static boolean namesAMedia(InventoryGroupType type) {
        return type == InventoryGroupType.LIKELY_SERIES
                || type == InventoryGroupType.LIKELY_MOVIE
                || type == InventoryGroupType.UNKNOWN;
    }

    private static BatchTmdbMatch directMatch(InventoryGroup group) {
        String seed = group.seedName() == null || group.seedName().isBlank()
                ? UiText.EMPTY
                : group.seedName();
        return new BatchTmdbMatch(seed, group.type(), group.items().size(), group.tmdbIdentityFinal());
    }

    private static String pathKey(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }
}

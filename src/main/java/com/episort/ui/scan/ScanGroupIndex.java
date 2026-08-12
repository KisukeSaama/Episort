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
        peersByRow.clear();
        rowsByGroupName.clear();
        Map<String, ScanRow> byPath = new HashMap<>();
        for (ScanRow row : rows) {
            byPath.putIfAbsent(pathKey(row.sourcePath()), row);
        }
        for (InventoryGroup group : knownGroups) {
            List<ScanRow> members = new ArrayList<>();
            for (InventoryItem item : group.items()) {
                ScanRow row = byPath.get(pathKey(item.sourcePath()));
                if (row == null) {
                    continue;
                }
                members.add(row);
                groupByRow.put(row, group);
            }
        }
        for (ScanRow row : rows) {
            InventoryGroup group = groupByRow.get(row);
            BatchTmdbMatch match = effectiveMatch(group, row);
            if (match == null) {
                continue;
            }
            if (!row.isIgnored() && match.namesAMedia()) {
                rowsByGroupName
                        .computeIfAbsent(match.seedName(), ignored -> new ArrayList<>())
                        .add(row);
            }
        }
        rowsByGroupName.values().forEach(members ->
                members.forEach(member -> peersByRow.put(member, members)));
    }

    void clear() {
        groupByRow.clear();
        peersByRow.clear();
        rowsByGroupName.clear();
        knownGroups.clear();
    }

    InventoryGroup groupOf(ScanRow row) {
        return groupByRow.get(row);
    }

    BatchTmdbMatch matchOf(ScanRow row) {
        return effectiveMatch(groupByRow.get(row), row);
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
        BatchTmdbMatch match = matchOf(row);
        if (match == null) {
            return UiText.EMPTY;
        }
        if (match.seedName() == null || match.seedName().isBlank()) {
            return UiText.EMPTY;
        }
        return match.seedText(language);
    }

    /** Whether the row's group is one that can carry a TMDB identity at all. */
    boolean namesAMedia(ScanRow row) {
        BatchTmdbMatch match = matchOf(row);
        return match != null && match.namesAMedia();
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

    /**
     * A user can reactivate a supported video that the initial inventory placed
     * in a non-media bucket. Once they explicitly assign Series or Movie, that
     * current decision supersedes the immutable scan-time group for TMDB work.
     */
    private static BatchTmdbMatch effectiveMatch(InventoryGroup group, ScanRow row) {
        if (group == null) {
            return null;
        }
        BatchTmdbMatch scanned = directMatch(group);
        if (scanned.namesAMedia() || row.isIgnored()) {
            return scanned;
        }
        InventoryGroupType type = switch (row.mediaType()) {
            case SERIES -> InventoryGroupType.LIKELY_SERIES;
            case MOVIE -> InventoryGroupType.LIKELY_MOVIE;
            case UNKNOWN, IGNORED -> null;
        };
        if (type == null) {
            return scanned;
        }
        String seed = reactivatedSeed(row);
        return new BatchTmdbMatch(seed, type, 1, false);
    }

    private static String reactivatedSeed(ScanRow row) {
        String seed = row.mediaType() == ScanMediaType.MOVIE
                ? ScanRowEditor.derivedMovieTitle(row)
                : row.inputParse()
                        .flatMap(parse -> parse.tokenValue(ScanInputRole.SERIES))
                        .orElse("");
        if (seed == null || seed.isBlank()) {
            String filename = row.originalFilename();
            int dot = filename.lastIndexOf('.');
            seed = dot > 0 ? filename.substring(0, dot) : filename;
        }
        return seed.trim();
    }

    private static String pathKey(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }
}

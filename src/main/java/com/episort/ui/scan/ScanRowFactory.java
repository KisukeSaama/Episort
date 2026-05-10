package com.episort.ui.scan;

import com.episort.analysis.AnalysisStatus;
import com.episort.analysis.AnalysisValidationService;
import com.episort.analysis.AnalyzedVideoFile;
import com.episort.analysis.HeuristicAnalysisService;
import com.episort.analysis.RenameProposalService;
import com.episort.analysis.TvdbOrder;
import com.episort.analysis.VideoMediaType;
import com.episort.scanner.InventoryGroup;
import com.episort.scanner.InventoryGroupType;
import com.episort.scanner.InventoryItem;
import com.episort.scanner.InventoryItemType;
import com.episort.scanner.InventoryScanResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Maps {@link InventoryItem}s into {@link ScanRow}s for the preview table.
 * Pure mapping logic — no JavaFX, fully unit-testable.
 */
public final class ScanRowFactory {
    private static final HeuristicAnalysisService HEURISTICS = new HeuristicAnalysisService();
    private static final RenameProposalService RENAME = new RenameProposalService();
    private static final AnalysisValidationService VALIDATION = new AnalysisValidationService();

    private ScanRowFactory() {
    }

    public static List<ScanRow> from(InventoryScanResult result) {
        Objects.requireNonNull(result, "result");
        Map<String, InventoryGroupType> itemGroupTypes = indexItemsByGroup(result.groups());
        List<AnalyzedVideoFile> analyses = new ArrayList<>(result.items().size());
        for (InventoryItem item : result.items()) {
            InventoryGroupType groupType = itemGroupTypes.get(item.sourcePath().toAbsolutePath().normalize().toString());
            AnalyzedVideoFile analysis = HEURISTICS.analyze(item, groupType);
            RENAME.generate(analysis);
            analyses.add(analysis);
        }
        VALIDATION.validatePreTvdb(analyses);
        List<ScanRow> rows = new ArrayList<>(analyses.size());
        for (AnalyzedVideoFile analysis : analyses) {
            rows.add(toRow(analysis));
        }
        return rows;
    }

    private static Map<String, InventoryGroupType> indexItemsByGroup(List<InventoryGroup> groups) {
        Map<String, InventoryGroupType> index = new HashMap<>();
        for (InventoryGroup group : groups) {
            for (InventoryItem item : group.items()) {
                index.put(item.sourcePath().toAbsolutePath().normalize().toString(), group.type());
            }
        }
        return index;
    }

    private static ScanRow toRow(AnalyzedVideoFile analysis) {
        ScanRow row = new ScanRow(
                analysis.originalPath(),
                analysis.originalFileName(),
                normalizeExtension(analysis.extension()),
                mediaType(analysis.mediaType()),
                status(analysis.status()));
        ScanInputPatternParser.parse(analysis.originalFileName()).ifPresent(parse -> row.setInputParse(Optional.of(parse)));
        row.setInputPattern(analysis.inputPattern());
        row.setProposedFilename(analysis.proposedName());
        row.setOrder(Optional.of(orderText(analysis.tvdbOrder())));
        row.setConfidence(analysis.confidence());
        row.setStatusReasons(analysis.statusReasons());
        return row;
    }

    /**
     * Deterministic season/episode extraction. Runs on every folder load so the
     * Order column is pre-filled for unambiguous SxxExx-style names. The local
     * AI pattern-refinement pass still runs in parallel and remains advisory:
     * here we only act on what we can confirm with a regex, never on AI output.
     */
    private static void autoFillOrder(ScanRow row, String filename, InventoryGroupType groupType) {
        if (groupType != InventoryGroupType.LIKELY_SERIES && groupType != InventoryGroupType.UNKNOWN) {
            return;
        }
        Optional<ScanInputParse> extracted = ScanInputPatternParser.parse(filename);
        if (extracted.isEmpty()) {
            return;
        }
        ScanInputParse parse = extracted.orElseThrow();
        row.setInputParse(Optional.of(parse));
        row.setInputPattern(Optional.of(parse.summary().isBlank() ? parse.label() : parse.summary()));
        parse.normalizedOrder().ifPresent(order -> row.setOrder(Optional.of(order)));
        if (parse.confidence().isPresent()) {
            row.setConfidence(parse.confidence());
        }
    }

    static Optional<String> extractSeasonEpisode(String filename) {
        return ScanInputPatternParser.parse(filename).flatMap(ScanInputParse::normalizedOrder);
    }

    static Optional<ScanInputParse> extractInputPattern(String filename) {
        return ScanInputPatternParser.parse(filename);
    }

    private static ScanMediaType mediaType(VideoMediaType mediaType) {
        return switch (mediaType) {
            case SERIES, SPECIAL -> ScanMediaType.SERIES;
            case MOVIE -> ScanMediaType.MOVIE;
            case UNKNOWN -> ScanMediaType.UNKNOWN;
            case IGNORED -> ScanMediaType.IGNORED;
        };
    }

    private static ScanRowStatus status(AnalysisStatus status) {
        return switch (status) {
            case OK -> ScanRowStatus.OK;
            case REVIEW -> ScanRowStatus.REVIEW;
            case AI -> ScanRowStatus.AI;
            case TVDB -> ScanRowStatus.TVDB;
            case TYPE -> ScanRowStatus.TYPE;
            case EXT -> ScanRowStatus.EXT;
            case PATTERN -> ScanRowStatus.PATTERN;
            case META -> ScanRowStatus.META;
            case CONFLICT -> ScanRowStatus.CONFLICT;
            case DUPLICATE -> ScanRowStatus.DUPLICATE;
            case PATH -> ScanRowStatus.PATH;
            case ERROR -> ScanRowStatus.ERROR;
            case IGNORED -> ScanRowStatus.IGNORED;
        };
    }

    private static String orderText(TvdbOrder order) {
        return switch (order) {
            case NOT_APPLICABLE -> "N/A";
            case TO_DEFINE -> "TO_DEFINE";
            case AIRED -> "Aired";
            case DVD -> "DVD";
            case ABSOLUTE -> "Absolute";
            case UNAVAILABLE -> "UNAVAILABLE";
        };
    }

    private static String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return "";
        }
        String trimmed = extension.startsWith(".") ? extension.substring(1) : extension;
        return trimmed.toUpperCase(Locale.ROOT);
    }
}

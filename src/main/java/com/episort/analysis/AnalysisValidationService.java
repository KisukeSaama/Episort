package com.episort.analysis;

import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AnalysisValidationService {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".avi", ".mp4", ".mkv");
    private static final Set<String> TEMPORARY_EXTENSIONS = Set.of(".part", ".tmp", ".crdownload");
    private static final double LOW_CONFIDENCE = 0.60;

    public void validatePreTvdb(List<AnalyzedVideoFile> files) {
        Set<String> proposed = new HashSet<>();
        Set<String> duplicates = new HashSet<>();
        for (AnalyzedVideoFile file : files) {
            file.proposedName().map(String::toLowerCase).ifPresent(name -> {
                if (!proposed.add(name)) {
                    duplicates.add(name);
                }
            });
        }
        for (AnalyzedVideoFile file : files) {
            validateOne(file, duplicates);
        }
    }

    private static void validateOne(AnalyzedVideoFile file, Set<String> duplicates) {
        List<String> reasons = new ArrayList<>();
        String ext = file.extension().toLowerCase(Locale.ROOT);
        if (file.mediaType() == VideoMediaType.IGNORED) {
            file.setValidation(AnalysisStatus.IGNORED, List.of("File is not a supported video operation candidate."));
            return;
        }
        if (ext.isBlank() || TEMPORARY_EXTENSIONS.contains(ext) || !SUPPORTED_EXTENSIONS.contains(ext)) {
            reasons.add("Unsupported or temporary video extension.");
            file.setValidation(AnalysisStatus.EXT, reasons);
            return;
        }
        if (file.mediaType() == VideoMediaType.UNKNOWN) {
            reasons.add("Media type is unknown.");
            file.setValidation(AnalysisStatus.TYPE, reasons);
            return;
        }
        if (file.mediaType() == VideoMediaType.MOVIE
                && (file.seasonNumber().isPresent() || file.episodeNumber().isPresent())) {
            reasons.add("Movie has season or episode metadata.");
            file.setValidation(AnalysisStatus.CONFLICT, reasons);
            return;
        }
        if (file.inputPattern().isEmpty()) {
            reasons.add("Input pattern is missing.");
            file.setValidation(AnalysisStatus.PATTERN, reasons);
            return;
        }
        if (file.mediaType() == VideoMediaType.SERIES
                && (file.detectedTitle().isEmpty() || file.seasonNumber().isEmpty() || file.episodeNumber().isEmpty())) {
            reasons.add("Series metadata requires title, season and episode.");
            file.setValidation(AnalysisStatus.META, reasons);
            return;
        }
        if (file.mediaType() == VideoMediaType.MOVIE && file.detectedTitle().isEmpty()) {
            reasons.add("Movie metadata requires a detected title.");
            file.setValidation(AnalysisStatus.META, reasons);
            return;
        }
        if (file.confidence().isPresent() && file.confidence().orElseThrow() < LOW_CONFIDENCE) {
            reasons.add("AI confidence is below the configured threshold.");
            file.setValidation(AnalysisStatus.AI, reasons);
            return;
        }
        if (file.proposedName().isEmpty()) {
            reasons.add("No proposed name could be generated.");
            file.setValidation(AnalysisStatus.META, reasons);
            return;
        }
        if (duplicates.contains(file.proposedName().orElseThrow().toLowerCase(Locale.ROOT))) {
            reasons.add("Another file generates the same proposed name.");
            file.setValidation(AnalysisStatus.DUPLICATE, reasons);
            return;
        }
        try {
            file.originalPath().normalize();
        } catch (InvalidPathException exception) {
            reasons.add("Original path is invalid.");
            file.setValidation(AnalysisStatus.PATH, reasons);
            return;
        }
        file.setValidation(AnalysisStatus.OK, List.of());
    }
}

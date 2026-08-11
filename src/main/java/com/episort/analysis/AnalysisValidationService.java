package com.episort.analysis;

import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Decides, before any TMDB call, whether a file is safe to organize.
 *
 * <p>The checks run cheapest-and-most-decisive first, and every one of them
 * fails closed: anything that cannot be proven correct ends up in a review
 * bucket rather than in the plan. Duplicate detection compares proposed names
 * case-insensitively because the target filesystem is Windows, where
 * {@code Show - S01E01.mkv} and {@code show - s01e01.mkv} are the same file.
 */
public final class AnalysisValidationService {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".avi", ".mp4", ".mkv");
    private static final Set<String> TEMPORARY_EXTENSIONS = Set.of(
            ".part", ".tmp", ".crdownload", ".!ut", ".partial", ".download");
    private static final double LOW_CONFIDENCE = 0.60;
    private static final int MAX_SEASON = 99;
    private static final int MAX_EPISODE = 9999;

    public void validatePreTmdb(List<AnalyzedVideoFile> files) {
        Set<String> proposed = new HashSet<>();
        Set<String> duplicates = new HashSet<>();
        for (AnalyzedVideoFile file : files) {
            file.proposedName().map(name -> name.toLowerCase(Locale.ROOT)).ifPresent(name -> {
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
            file.setValidation(AnalysisStatus.IGNORED, ignoredReasons(file));
            return;
        }
        if (file.originalFileName().isBlank() || hasControlCharacters(file.originalFileName())) {
            reasons.add("The original file name is empty or contains control characters.");
            file.setValidation(AnalysisStatus.PATH, reasons);
            return;
        }
        if (ext.isBlank() || TEMPORARY_EXTENSIONS.contains(ext) || !SUPPORTED_EXTENSIONS.contains(ext)) {
            reasons.add("Unsupported or temporary video extension.");
            file.setValidation(AnalysisStatus.EXT, reasons);
            return;
        }
        try {
            file.originalPath().normalize();
        } catch (InvalidPathException exception) {
            reasons.add("Original path is invalid.");
            file.setValidation(AnalysisStatus.PATH, reasons);
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
        if (isEpisodic(file)
                && (file.detectedTitle().isEmpty()
                        || file.seasonNumber().isEmpty()
                        || file.episodeNumber().isEmpty())) {
            reasons.add("Series metadata requires title, season and episode.");
            file.setValidation(AnalysisStatus.META, reasons);
            return;
        }
        if (isEpisodic(file) && outOfRange(file)) {
            reasons.add("Season or episode number is outside the supported range.");
            file.setValidation(AnalysisStatus.META, reasons);
            return;
        }
        if (file.mediaType() == VideoMediaType.MOVIE && file.detectedTitle().isEmpty()) {
            reasons.add("Movie metadata requires a detected title.");
            file.setValidation(AnalysisStatus.META, reasons);
            return;
        }
        if (file.confidence().isPresent() && file.confidence().orElseThrow() < LOW_CONFIDENCE) {
            reasons.add("Confidence in the detected metadata is below the review threshold.");
            file.setValidation(AnalysisStatus.LOW_CONFIDENCE, reasons);
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
        if (file.reviewRequired()) {
            // Everything checks out mechanically, but the parse rests on an
            // assumption only the user can confirm.
            file.setValidation(AnalysisStatus.REVIEW, file.warnings());
            return;
        }
        file.setValidation(AnalysisStatus.OK, List.of());
    }

    private static List<String> ignoredReasons(AnalyzedVideoFile file) {
        List<String> reasons = new ArrayList<>(file.warnings());
        reasons.add("File is not a supported video operation candidate.");
        return reasons;
    }

    private static boolean isEpisodic(AnalyzedVideoFile file) {
        return file.mediaType() == VideoMediaType.SERIES || file.mediaType() == VideoMediaType.SPECIAL;
    }

    private static boolean outOfRange(AnalyzedVideoFile file) {
        int season = file.seasonNumber().orElse(0);
        int episode = file.episodeNumber().orElse(0);
        // Year-numbered seasons ("S2024E05") are legitimate for dated shows.
        boolean seasonOk = (season >= 0 && season <= MAX_SEASON) || (season >= 1900 && season <= 2100);
        return !seasonOk || episode < 0 || episode > MAX_EPISODE;
    }

    private static boolean hasControlCharacters(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }
}

package com.episort.analysis;

import java.util.Locale;

public final class RenameProposalService {
    public void generate(AnalyzedVideoFile file) {
        String extension = file.extension();
        if (extension.isBlank() || !extension.startsWith(".")) {
            return;
        }
        if (file.mediaType() == VideoMediaType.SERIES || file.mediaType() == VideoMediaType.SPECIAL) {
            if (file.detectedTitle().isEmpty() || file.seasonNumber().isEmpty() || file.episodeNumber().isEmpty()) {
                return;
            }
            String title = sanitize(file.detectedTitle().orElseThrow());
            String episodeTitle = file.episodeTitle().map(RenameProposalService::sanitize).orElse("");
            String name = String.format(Locale.ROOT, "%s - S%02dE%02d",
                    title,
                    file.seasonNumber().orElseThrow(),
                    file.episodeNumber().orElseThrow());
            if (!episodeTitle.isBlank()) {
                name += " - " + episodeTitle;
            }
            file.set(AnalysisField.PROPOSED_NAME, name + extension, FieldSource.HEURISTIC);
        } else if (file.mediaType() == VideoMediaType.MOVIE) {
            if (file.detectedTitle().isEmpty()) {
                return;
            }
            String title = sanitize(file.detectedTitle().orElseThrow());
            String year = file.year().map(value -> " (" + value + ")").orElse("");
            file.set(AnalysisField.PROPOSED_NAME, title + year + extension, FieldSource.HEURISTIC);
        }
    }

    private static String sanitize(String value) {
        return value.replaceAll("[\\\\/:*?\"<>|]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}

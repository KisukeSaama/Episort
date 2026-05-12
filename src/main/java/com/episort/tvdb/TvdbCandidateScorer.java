package com.episort.tvdb;

import com.episort.scanner.InventoryGroupType;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;

public final class TvdbCandidateScorer {
    public static final double AUTOMATIC_THRESHOLD = 0.85;
    public static final double SUGGESTION_THRESHOLD = 0.65;

    public ScoredCandidate score(String query, InventoryGroupType expectedType, TvdbCandidate candidate) {
        String normalizedQuery = normalizeTitle(query);
        String normalizedTitle = normalizeTitle(candidate.identity().displayName());
        double similarity = similarity(normalizedQuery, normalizedTitle);
        if (normalizedQuery.equals(normalizedTitle) && !normalizedQuery.isBlank()) {
            similarity = 1.0;
        } else if (!normalizedQuery.isBlank()
                && (normalizedTitle.contains(normalizedQuery) || normalizedQuery.contains(normalizedTitle))) {
            double ratio = (double) Math.min(normalizedQuery.length(), normalizedTitle.length())
                    / Math.max(normalizedQuery.length(), normalizedTitle.length());
            similarity = Math.max(similarity, ratio >= 0.80 ? 0.90 : 0.72);
        }

        double typeBoost = typeMatches(expectedType, candidate.identity().mediaType()) ? 0.05 : -0.10;
        double rankBoost = Math.max(0.0, 0.04 - (candidate.tvdbRank() * 0.01));
        double aiBoost = candidate.advisoryAiScore().present()
                && candidate.advisoryAiScore().value() >= 0.90 ? 0.03 : 0.0;
        double score = clamp(similarity + typeBoost + rankBoost + aiBoost);
        return new ScoredCandidate(candidate, score, score >= AUTOMATIC_THRESHOLD, score >= SUGGESTION_THRESHOLD);
    }

    public static String normalizeSearchKey(String query, InventoryGroupType type) {
        String prefix = type == null ? "unknown" : type.name().toLowerCase(Locale.ROOT);
        return prefix + ":" + normalizeTitle(query);
    }

    public static String normalizeTitle(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("(?i)\\b(2160p|1080p|720p|480p|multi|mult[i1]|bluray|blu-ray|web-dl|webdl|webrip|hdtv|x264|x265|h264|h265|hevc|vostfr|truefrench|french|vf|aac|ddp?\\d?|flac|atmos|10bit|8bit|proper|repack)\\b", " ")
                .replaceAll("[!\"#$%&'()*+,./:;<=>?@\\[\\]^_`{|}~]+", " ")
                .replace('-', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        return collapseRepeatedVowels(normalized);
    }

    private static String collapseRepeatedVowels(String value) {
        StringBuilder out = new StringBuilder(value.length());
        char previous = 0;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if ("aeiouy".indexOf(current) >= 0 && current == previous) {
                continue;
            }
            out.append(current);
            previous = current;
        }
        return out.toString();
    }

    private static boolean typeMatches(InventoryGroupType expectedType, TvdbMediaType mediaType) {
        return switch (expectedType) {
            case LIKELY_MOVIE -> mediaType == TvdbMediaType.MOVIE;
            case LIKELY_SERIES -> mediaType == TvdbMediaType.SERIES;
            default -> true;
        };
    }

    private static double similarity(String left, String right) {
        if (left.isBlank() || right.isBlank()) {
            return 0.0;
        }
        int distance = levenshtein(left, right);
        int max = Math.max(left.length(), right.length());
        return max == 0 ? 1.0 : 1.0 - ((double) distance / max);
    }

    private static int levenshtein(String left, String right) {
        int[] costs = new int[right.length() + 1];
        for (int j = 0; j < costs.length; j++) costs[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            costs[0] = i;
            int northwest = i - 1;
            for (int j = 1; j <= right.length(); j++) {
                int north = costs[j];
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                costs[j] = Math.min(Math.min(costs[j - 1] + 1, costs[j] + 1), northwest + cost);
                northwest = north;
            }
        }
        return costs[right.length()];
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public record ScoredCandidate(TvdbCandidate candidate, double score, boolean automatic, boolean suggestible) {
        public Optional<TvdbCandidate> candidateIfSuggestible() {
            return suggestible ? Optional.of(candidate) : Optional.empty();
        }
    }
}

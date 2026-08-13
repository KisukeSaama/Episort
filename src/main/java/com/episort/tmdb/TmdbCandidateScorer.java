package com.episort.tmdb;

import com.episort.scanner.InventoryGroupType;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TmdbCandidateScorer {
    public static final double AUTOMATIC_THRESHOLD = 0.85;
    public static final double SUGGESTION_THRESHOLD = 0.65;

    private static final Pattern TRAILING_YEAR = Pattern.compile("[\\s(\\[]+((?:19|20)\\d{2})[\\s)\\]]*$");
    /** Alias hits stay just under a primary-name hit so the canonical title wins ties. */
    private static final double ALIAS_CEILING = 0.98;
    /**
     * "Le Flic de Hong Kong" and "Le Flic de Hong Kong 2" are two films, but one
     * title contains the other, so the containment bonus alone rated the wrong
     * instalment as an exact hit. A sequel-number mismatch caps the similarity
     * below {@link #AUTOMATIC_THRESHOLD} even after every boost: the right
     * instalment still outranks it, and a lone wrong one asks the user.
     */
    private static final double SEQUEL_MISMATCH_CEILING = 0.62;
    private static final Pattern SEQUEL_ROMAN = Pattern.compile("ii|iii|iv|vi|vii|viii|ix");

    /**
     * Scores every candidate and returns the best one. TMDB's own ordering is
     * only a weak hint: the exact title we are looking for is regularly ranked
     * below a spin-off or a documentary, so scoring the whole pool (instead of
     * the first entry) is what turns a correctly named folder into an automatic
     * match rather than a manual one.
     */
    public Optional<ScoredCandidate> bestMatch(
            String query, InventoryGroupType expectedType, List<TmdbCandidate> pool) {
        if (pool == null || pool.isEmpty()) {
            return Optional.empty();
        }
        ScoredCandidate best = null;
        for (TmdbCandidate candidate : pool) {
            ScoredCandidate scored = score(query, expectedType, candidate);
            if (best == null
                    || scored.score() > best.score()
                    || (scored.score() == best.score()
                            && candidate.tmdbRank() < best.candidate().tmdbRank())) {
                best = scored;
            }
        }
        if (best.automatic() && hasRivalExactTitle(query, best, pool)) {
            // Two records carry the very same title (remakes, US/UK versions).
            // Picking one silently would be a coin flip: ask the user instead.
            return Optional.of(new ScoredCandidate(best.candidate(), best.score(), false, true));
        }
        return Optional.of(best);
    }

    /** Same ordering as {@link #bestMatch}, exposed so pick-lists show the likeliest title first. */
    public List<TmdbCandidate> rankedByRelevance(
            String query, InventoryGroupType expectedType, List<TmdbCandidate> pool) {
        if (pool == null || pool.isEmpty()) {
            return List.of();
        }
        return pool.stream()
                .sorted(Comparator
                        .comparingDouble((TmdbCandidate candidate) -> -score(query, expectedType, candidate).score())
                        .thenComparingInt(TmdbCandidate::tmdbRank))
                .toList();
    }

    public ScoredCandidate score(String query, InventoryGroupType expectedType, TmdbCandidate candidate) {
        String rawQuery = query == null ? "" : query;
        Optional<Integer> queryYear = trailingYear(rawQuery);
        String normalizedQuery = normalizeTitle(stripTrailingYear(rawQuery));
        double similarity = bestTitleSimilarity(normalizedQuery, candidate);

        double typeBoost = typeMatches(expectedType, candidate.identity().mediaType()) ? 0.05 : -0.10;
        double rankBoost = Math.max(0.0, 0.04 - (candidate.tmdbRank() * 0.01));
        double aiBoost = candidate.advisoryAiScore().present()
                && candidate.advisoryAiScore().value() >= 0.90 ? 0.03 : 0.0;
        double yearBoost = yearBoost(queryYear, candidate.year());
        double score = clamp(similarity + typeBoost + rankBoost + aiBoost + yearBoost);
        return new ScoredCandidate(candidate, score, score >= AUTOMATIC_THRESHOLD, score >= SUGGESTION_THRESHOLD);
    }

    private static double bestTitleSimilarity(String normalizedQuery, TmdbCandidate candidate) {
        double best = titleSimilarity(normalizedQuery, candidate.identity().displayName());
        for (String alternate : candidate.alternateTitles()) {
            best = Math.max(best, Math.min(ALIAS_CEILING, titleSimilarity(normalizedQuery, alternate)));
        }
        return best;
    }

    private static double titleSimilarity(String normalizedQuery, String rawTitle) {
        String normalizedTitle = normalizeTitle(stripTrailingYear(rawTitle));
        double similarity = similarity(normalizedQuery, normalizedTitle);
        if (normalizedQuery.equals(normalizedTitle) && !normalizedQuery.isBlank()) {
            return 1.0;
        }
        if (sequelNumber(normalizedQuery) != sequelNumber(normalizedTitle)) {
            return Math.min(similarity, SEQUEL_MISMATCH_CEILING);
        }
        if (!normalizedQuery.isBlank()
                && (normalizedTitle.contains(normalizedQuery) || normalizedQuery.contains(normalizedTitle))) {
            double ratio = (double) Math.min(normalizedQuery.length(), normalizedTitle.length())
                    / Math.max(normalizedQuery.length(), normalizedTitle.length());
            similarity = Math.max(similarity, ratio >= 0.80 ? 0.90 : 0.72);
        }
        return similarity;
    }

    /**
     * Which instalment a title names: a trailing "2" or "III" marks a sequel, and
     * a title without one is the first. Only small numbers count — "Blade Runner
     * 2049" and "Ocean's 11" are titles, not instalment markers — and the
     * ambiguous lone romans (I, V, X) are read as words.
     */
    private static int sequelNumber(String normalizedTitle) {
        if (normalizedTitle == null || normalizedTitle.isBlank()) {
            return 1;
        }
        int lastSpace = normalizedTitle.lastIndexOf(' ');
        if (lastSpace < 0) {
            // A bare number is the whole title ("1917"), never an instalment.
            return 1;
        }
        String last = normalizedTitle.substring(lastSpace + 1);
        if (last.matches("\\d{1,2}")) {
            int value = Integer.parseInt(last);
            return value >= 2 && value <= 20 ? value : 1;
        }
        if (SEQUEL_ROMAN.matcher(last).matches()) {
            return romanValue(last);
        }
        return 1;
    }

    private static int romanValue(String roman) {
        return switch (roman) {
            case "ii" -> 2;
            case "iii" -> 3;
            case "iv" -> 4;
            case "vi" -> 6;
            case "vii" -> 7;
            case "viii" -> 8;
            case "ix" -> 9;
            default -> 1;
        };
    }

    /** The year in the folder name is a strong signal when TMDB reports one too. */
    private static double yearBoost(Optional<Integer> queryYear, Optional<Integer> candidateYear) {
        if (queryYear.isEmpty() || candidateYear.isEmpty()) {
            return 0.0;
        }
        int delta = Math.abs(queryYear.get() - candidateYear.get());
        if (delta == 0) {
            return 0.06;
        }
        return delta <= 1 ? 0.02 : -0.15;
    }

    private static boolean hasRivalExactTitle(
            String query, ScoredCandidate best, List<TmdbCandidate> pool) {
        String raw = query == null ? "" : query;
        String normalizedQuery = normalizeTitle(stripTrailingYear(raw));
        if (normalizedQuery.isBlank() || !isExactTitle(normalizedQuery, best.candidate())) {
            return false;
        }
        // A year in the folder name already told the two apart.
        boolean yearDisambiguates = trailingYear(raw).isPresent();
        return pool.stream()
                .filter(candidate -> candidate != best.candidate())
                .anyMatch(candidate -> isExactTitle(normalizedQuery, candidate)
                        && !(yearDisambiguates && distinguishedByYear(best.candidate(), candidate)));
    }

    private static boolean distinguishedByYear(TmdbCandidate left, TmdbCandidate right) {
        return left.year().isPresent() && right.year().isPresent()
                && !left.year().get().equals(right.year().get());
    }

    private static boolean isExactTitle(String normalizedQuery, TmdbCandidate candidate) {
        if (normalizedQuery.equals(normalizeTitle(stripTrailingYear(candidate.identity().displayName())))) {
            return true;
        }
        return candidate.alternateTitles().stream()
                .anyMatch(title -> normalizedQuery.equals(normalizeTitle(stripTrailingYear(title))));
    }

    private static Optional<Integer> trailingYear(String value) {
        if (value == null) {
            return Optional.empty();
        }
        Matcher matcher = TRAILING_YEAR.matcher(value.trim());
        return matcher.find() ? Optional.of(Integer.parseInt(matcher.group(1))) : Optional.empty();
    }

    private static String stripTrailingYear(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        Matcher matcher = TRAILING_YEAR.matcher(trimmed);
        if (!matcher.find()) {
            return trimmed;
        }
        String stripped = trimmed.substring(0, matcher.start()).trim();
        // "1917" or "2012" are titles in their own right: never strip them away.
        return stripped.isBlank() ? trimmed : stripped;
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

    private static boolean typeMatches(InventoryGroupType expectedType, TmdbMediaType mediaType) {
        return switch (expectedType) {
            case LIKELY_MOVIE -> mediaType == TmdbMediaType.MOVIE;
            case LIKELY_SERIES -> mediaType == TmdbMediaType.SERIES;
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

    public record ScoredCandidate(TmdbCandidate candidate, double score, boolean automatic, boolean suggestible) {
        public Optional<TmdbCandidate> candidateIfSuggestible() {
            return suggestible ? Optional.of(candidate) : Optional.empty();
        }
    }
}

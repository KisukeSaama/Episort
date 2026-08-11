package com.episort.ui.scan;

import com.episort.ui.UiText;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;

final class ScanRowTableSupport {
    static final Comparator<String> NATURAL_TEXT = ScanRowTableSupport::compareNatural;
    static final Comparator<ScanMediaType> MEDIA_TYPE =
            Comparator.comparingInt(ScanRowTableSupport::mediaTypeRank);
    static final Comparator<ScanRowStatus> STATUS =
            Comparator.comparingInt(ScanRowTableSupport::statusRank);
    static final Comparator<String> CONFIDENCE_PERCENT =
            (left, right) -> Double.compare(parsePercent(left), parsePercent(right));
    static final Comparator<Optional<String>> OPTIONAL_NATURAL =
            (left, right) -> compareNatural(left.orElse(""), right.orElse(""));

    private ScanRowTableSupport() {
    }

    static int compareNatural(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        int ai = 0;
        int bi = 0;
        while (ai < a.length() && bi < b.length()) {
            char ac = a.charAt(ai);
            char bc = b.charAt(bi);
            if (Character.isDigit(ac) && Character.isDigit(bc)) {
                int aStart = ai;
                int bStart = bi;
                while (ai < a.length() && Character.isDigit(a.charAt(ai))) ai++;
                while (bi < b.length() && Character.isDigit(b.charAt(bi))) bi++;
                BigInteger an = new BigInteger(a.substring(aStart, ai));
                BigInteger bn = new BigInteger(b.substring(bStart, bi));
                int numeric = an.compareTo(bn);
                if (numeric != 0) {
                    return numeric;
                }
                int length = (ai - aStart) - (bi - bStart);
                if (length != 0) {
                    return length;
                }
            } else {
                int charCompare = Character.compare(ac, bc);
                if (charCompare != 0) {
                    return charCompare;
                }
                ai++;
                bi++;
            }
        }
        return Integer.compare(a.length(), b.length());
    }

    static int mediaTypeRank(ScanMediaType type) {
        if (type == null) {
            return 3;
        }
        return switch (type) {
            case SERIES -> 0;
            case MOVIE -> 1;
            case UNKNOWN -> 3;
            case IGNORED -> 4;
        };
    }

    static int statusRank(ScanRowStatus status) {
        if (status == null) {
            return 99;
        }
        return switch (status) {
            case OK -> 0;
            case REVIEW -> 1;
            case LOW_CONFIDENCE -> 2;
            case TMDB -> 3;
            case TYPE -> 4;
            case EXT -> 5;
            case PATTERN -> 6;
            case META -> 7;
            case CONFLICT -> 8;
            case DUPLICATE -> 9;
            case PATH -> 10;
            case ERROR -> 11;
            case IGNORED -> 12;
        };
    }

    static boolean matchesFilter(ScanRow row, ScanRowFilter filter) {
        return switch (filter) {
            case ALL -> true;
            case MOVIES -> row.mediaType() == ScanMediaType.MOVIE;
            case SERIES -> row.mediaType() == ScanMediaType.SERIES;
            case UNKNOWN -> isUnknownOrNeedsUnderstanding(row);
        };
    }

    static boolean matchesStatusFilter(ScanRow row, ScanRowStatusFilter filter) {
        return switch (filter) {
            case ALL -> true;
            case TO_PROCESS -> !row.isIgnored() && row.status() != ScanRowStatus.OK && row.status() != ScanRowStatus.TMDB;
            case OK -> !row.isIgnored() && row.status() == ScanRowStatus.OK;
            case TMDB -> !row.isIgnored() && row.status() == ScanRowStatus.TMDB;
            case CONFLICTS -> !row.isIgnored()
                    && (row.status() == ScanRowStatus.CONFLICT || row.status() == ScanRowStatus.DUPLICATE);
            case IGNORED -> row.isIgnored();
            case ALERTS -> hasAlert(row);
        };
    }

    static boolean hasAlert(ScanRow row) {
        return !row.isIgnored() && (row.alertText().isPresent() || row.status() == ScanRowStatus.ERROR);
    }

    static boolean isUnknownOrNeedsUnderstanding(ScanRow row) {
        if (row.mediaType() == ScanMediaType.UNKNOWN) {
            return true;
        }
        return switch (row.status()) {
            case TYPE, LOW_CONFIDENCE, PATTERN, META -> true;
            case ERROR -> row.mediaType() == ScanMediaType.UNKNOWN;
            default -> false;
        };
    }

    static double parsePercent(String value) {
        if (value == null || value.isBlank() || UiText.EMPTY.equals(value)) {
            return -1.0;
        }
        try {
            return Double.parseDouble(value.replace("%", "").trim());
        } catch (NumberFormatException ex) {
            return -1.0;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

package com.episort.ui.scan;

import com.episort.tvdb.TvdbMediaType;
import com.episort.ui.AppLanguage;
import com.episort.ui.UiText;

/**
 * The single mapping from scan-row domain enums to display text.
 *
 * <p>These labels used to live on {@code RowDetailPanel}, which made the table
 * columns and the manual-match dialog depend on the detail panel just to name a
 * status — and the media-type mapping existed twice, in two files. One home
 * means one wording everywhere.
 */
final class ScanRowText {

    private ScanRowText() {
    }

    static String mediaType(ScanMediaType mediaType, AppLanguage language) {
        return switch (mediaType) {
            case SERIES -> UiText.scanMediaTypeSeries(language);
            case MOVIE -> UiText.scanMediaTypeMovie(language);
            case UNKNOWN -> UiText.scanMediaTypeUnknown(language);
            case IGNORED -> UiText.scanMediaTypeIgnored(language);
        };
    }

    /** TVDB only distinguishes movies from series. */
    static String mediaType(TvdbMediaType mediaType, AppLanguage language) {
        return mediaType == TvdbMediaType.MOVIE
                ? UiText.tvdbMovie(language)
                : UiText.tvdbSeries(language);
    }

    static String status(ScanRowStatus status, AppLanguage language) {
        return switch (status) {
            case OK -> UiText.scanRowStatusOk(language);
            case REVIEW -> UiText.scanRowStatusReview(language);
            case LOW_CONFIDENCE -> UiText.scanRowStatusLowConfidence(language);
            case TVDB -> UiText.scanRowStatusTvdb(language);
            case TYPE -> UiText.scanRowStatusType(language);
            case EXT -> UiText.scanRowStatusExt(language);
            case PATTERN -> UiText.scanRowStatusPattern(language);
            case META -> UiText.scanRowStatusMeta(language);
            case CONFLICT -> UiText.scanRowStatusConflict(language);
            case DUPLICATE -> UiText.scanRowStatusDuplicate(language);
            case PATH -> UiText.scanRowStatusPath(language);
            case ERROR -> UiText.scanRowStatusError(language);
            case IGNORED -> UiText.scanRowStatusIgnored(language);
        };
    }
}

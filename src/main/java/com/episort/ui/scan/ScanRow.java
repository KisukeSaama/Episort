package com.episort.ui.scan;

import com.episort.tmdb.TmdbCandidate;
import com.episort.tmdb.TmdbEpisodeOrder;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;

/**
 * Per-row view-model backing the Scan preview table. Plain Java fields except
 * {@link #selectedProperty()} and {@link #ignoredProperty()} which are
 * observable so the table can react immediately to selection and ignore-state
 * changes.
 */
public final class ScanRow {
    private final BooleanProperty selected = new SimpleBooleanProperty(false);
    private final ReadOnlyBooleanWrapper ignored = new ReadOnlyBooleanWrapper(false);
    private final Path sourcePath;
    private final String originalFilename;
    private final String extension;
    private ScanMediaType mediaType;
    private ScanRowStatus status;
    private Optional<String> proposedFilename;
    private Optional<ScanInputParse> inputParse;
    private Optional<String> inputPattern;
    private Optional<String> pattern;
    private Optional<String> tmdbMatch;
    private Optional<TmdbCandidate> tmdbCandidate;
    private Optional<TmdbEpisodeOrder> appliedTmdbOrder;
    private boolean tmdbSelectedByUser;
    private Optional<String> order;
    private Optional<Path> destination;
    private OptionalDouble confidence;
    private Optional<String> alertText;
    private Optional<String> noteText;
    private Optional<String> conflictText;
    private List<String> statusReasons;
    private Optional<ScanMediaType> mediaTypeBeforeIgnore;
    private Optional<ScanRowStatus> statusBeforeIgnore;

    public ScanRow(
            Path sourcePath,
            String originalFilename,
            String extension,
            ScanMediaType mediaType,
            ScanRowStatus status) {
        this.sourcePath = Objects.requireNonNull(sourcePath, "sourcePath");
        this.originalFilename = Objects.requireNonNull(originalFilename, "originalFilename");
        this.extension = Objects.requireNonNull(extension, "extension");
        this.mediaType = Objects.requireNonNull(mediaType, "mediaType");
        this.status = Objects.requireNonNull(status, "status");
        this.proposedFilename = Optional.empty();
        this.inputParse = Optional.empty();
        this.inputPattern = Optional.empty();
        this.pattern = Optional.empty();
        this.tmdbMatch = Optional.empty();
        this.tmdbCandidate = Optional.empty();
        this.appliedTmdbOrder = Optional.empty();
        this.tmdbSelectedByUser = false;
        this.order = Optional.empty();
        this.destination = Optional.empty();
        this.confidence = OptionalDouble.empty();
        this.alertText = Optional.empty();
        this.noteText = Optional.empty();
        this.conflictText = Optional.empty();
        this.statusReasons = List.of();
        this.mediaTypeBeforeIgnore = Optional.empty();
        this.statusBeforeIgnore = Optional.empty();
        updateIgnoredState();
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public boolean isSelected() {
        return selected.get();
    }

    public void setSelected(boolean value) {
        selected.set(value && !isIgnored());
    }

    public boolean isIgnored() {
        return ignored.get();
    }

    public ReadOnlyBooleanProperty ignoredProperty() {
        return ignored.getReadOnlyProperty();
    }

    public void markIgnored() {
        if (isIgnored()) {
            return;
        }
        mediaTypeBeforeIgnore = Optional.of(mediaType);
        statusBeforeIgnore = Optional.of(status);
        setSelected(false);
        status = ScanRowStatus.IGNORED;
        updateIgnoredState();
    }

    public void stopIgnoring() {
        if (!isIgnored()) {
            return;
        }
        mediaType = mediaTypeBeforeIgnore.orElseGet(this::inferredMediaTypeAfterIgnore);
        status = statusBeforeIgnore.orElse(tmdbMatch.isPresent()
                ? ScanRowStatus.TMDB
                : ScanRowStatus.REVIEW);
        mediaTypeBeforeIgnore = Optional.empty();
        statusBeforeIgnore = Optional.empty();
        updateIgnoredState();
    }

    public Path sourcePath() {
        return sourcePath;
    }

    public String originalFilename() {
        return originalFilename;
    }

    public String extension() {
        return extension;
    }

    public ScanMediaType mediaType() {
        return mediaType;
    }

    public void setMediaType(ScanMediaType mediaType) {
        this.mediaType = Objects.requireNonNull(mediaType, "mediaType");
        updateIgnoredState();
    }

    public ScanRowStatus status() {
        return status;
    }

    public void setStatus(ScanRowStatus status) {
        this.status = Objects.requireNonNull(status, "status");
        updateIgnoredState();
    }

    public Optional<String> proposedFilename() {
        return proposedFilename;
    }

    public void setProposedFilename(Optional<String> proposedFilename) {
        Objects.requireNonNull(proposedFilename, "proposedFilename");
        this.proposedFilename = proposedFilename.map(ScanRow::fileNameOnly);
    }

    public Optional<String> pattern() {
        return pattern;
    }

    public Optional<ScanInputParse> inputParse() {
        return inputParse;
    }

    public void setInputParse(Optional<ScanInputParse> inputParse) {
        this.inputParse = Objects.requireNonNull(inputParse, "inputParse");
    }

    public Optional<String> inputPattern() {
        return inputPattern;
    }

    public void setInputPattern(Optional<String> inputPattern) {
        this.inputPattern = Objects.requireNonNull(inputPattern, "inputPattern");
    }

    public void setPattern(Optional<String> pattern) {
        this.pattern = Objects.requireNonNull(pattern, "pattern");
    }

    public Optional<String> tmdbMatch() {
        return tmdbMatch;
    }

    public void setTmdbMatch(Optional<String> tmdbMatch) {
        this.tmdbMatch = Objects.requireNonNull(tmdbMatch, "tmdbMatch");
    }

    public Optional<TmdbCandidate> tmdbCandidate() {
        return tmdbCandidate;
    }

    public void setTmdbCandidate(Optional<TmdbCandidate> tmdbCandidate) {
        this.tmdbCandidate = Objects.requireNonNull(tmdbCandidate, "tmdbCandidate");
    }

    public Optional<TmdbEpisodeOrder> appliedTmdbOrder() {
        return appliedTmdbOrder;
    }

    public void setAppliedTmdbOrder(Optional<TmdbEpisodeOrder> appliedTmdbOrder) {
        this.appliedTmdbOrder = Objects.requireNonNull(appliedTmdbOrder, "appliedTmdbOrder");
    }

    public boolean tmdbSelectedByUser() {
        return tmdbSelectedByUser;
    }

    public void setTmdbSelectedByUser(boolean tmdbSelectedByUser) {
        this.tmdbSelectedByUser = tmdbSelectedByUser;
    }

    public Optional<String> order() {
        return order;
    }

    public void setOrder(Optional<String> order) {
        this.order = Objects.requireNonNull(order, "order");
    }

    public Optional<Path> destination() {
        return destination;
    }

    public void setDestination(Optional<Path> destination) {
        this.destination = Objects.requireNonNull(destination, "destination");
    }

    public OptionalDouble confidence() {
        return confidence;
    }

    public void setConfidence(OptionalDouble confidence) {
        this.confidence = Objects.requireNonNull(confidence, "confidence");
    }

    public Optional<String> alertText() {
        return alertText;
    }

    public void setAlertText(Optional<String> alertText) {
        this.alertText = Objects.requireNonNull(alertText, "alertText");
    }

    public Optional<String> noteText() {
        return noteText;
    }

    public void setNoteText(Optional<String> noteText) {
        this.noteText = Objects.requireNonNull(noteText, "noteText");
    }

    public Optional<String> conflictText() {
        return conflictText;
    }

    public void setConflictText(Optional<String> conflictText) {
        this.conflictText = Objects.requireNonNull(conflictText, "conflictText");
    }

    public List<String> statusReasons() {
        return statusReasons;
    }

    public void setStatusReasons(List<String> statusReasons) {
        this.statusReasons = statusReasons == null ? List.of() : List.copyOf(statusReasons);
    }

    private static String fileNameOnly(String value) {
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        return slash >= 0 ? value.substring(slash + 1) : value;
    }

    private ScanMediaType inferredMediaTypeAfterIgnore() {
        if (mediaType != ScanMediaType.IGNORED) {
            return mediaType;
        }
        boolean hasEpisodeOrder = inputParse
                .flatMap(ScanInputParse::normalizedOrder)
                .filter(value -> !value.isBlank())
                .isPresent();
        return hasEpisodeOrder ? ScanMediaType.SERIES : ScanMediaType.UNKNOWN;
    }

    private void updateIgnoredState() {
        ignored.set(status == ScanRowStatus.IGNORED || mediaType == ScanMediaType.IGNORED);
    }
}

package com.episort.ui.scan;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/**
 * Per-row view-model backing the Scan preview table. Plain Java fields except
 * {@link #selected()} which is observable so the checkbox column and the
 * detail-panel binding can react to changes.
 */
public final class ScanRow {
    private final BooleanProperty selected = new SimpleBooleanProperty(false);
    private final Path sourcePath;
    private final String originalFilename;
    private final String extension;
    private ScanMediaType mediaType;
    private ScanRowStatus status;
    private Optional<String> proposedFilename;
    private Optional<ScanInputParse> inputParse;
    private Optional<String> inputPattern;
    private Optional<String> pattern;
    private Optional<String> tvdbMatch;
    private Optional<String> order;
    private Optional<Path> destination;
    private OptionalDouble confidence;
    private Optional<String> alertText;
    private Optional<String> noteText;
    private Optional<String> conflictText;
    private java.util.List<String> statusReasons;

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
        this.tvdbMatch = Optional.empty();
        this.order = Optional.empty();
        this.destination = Optional.empty();
        this.confidence = OptionalDouble.empty();
        this.alertText = Optional.empty();
        this.noteText = Optional.empty();
        this.conflictText = Optional.empty();
        this.statusReasons = java.util.List.of();
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public boolean isSelected() {
        return selected.get();
    }

    public void setSelected(boolean value) {
        selected.set(value);
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
    }

    public ScanRowStatus status() {
        return status;
    }

    public void setStatus(ScanRowStatus status) {
        this.status = Objects.requireNonNull(status, "status");
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

    public Optional<String> tvdbMatch() {
        return tvdbMatch;
    }

    public void setTvdbMatch(Optional<String> tvdbMatch) {
        this.tvdbMatch = Objects.requireNonNull(tvdbMatch, "tvdbMatch");
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

    public java.util.List<String> statusReasons() {
        return statusReasons;
    }

    public void setStatusReasons(java.util.List<String> statusReasons) {
        this.statusReasons = statusReasons == null ? java.util.List.of() : java.util.List.copyOf(statusReasons);
    }

    private static String fileNameOnly(String value) {
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        return slash >= 0 ? value.substring(slash + 1) : value;
    }
}

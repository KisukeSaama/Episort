package com.episort.ui;

import com.episort.filesystem.VolumeSpace;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

record StorageUsagePresentation(
        String percentage, String percentageValue, String capacity, String available, double progress) {
    private static final long KIBIBYTE = 1024L;
    private static final long MEBIBYTE = KIBIBYTE * 1024;
    private static final long GIBIBYTE = MEBIBYTE * 1024;
    private static final long TEBIBYTE = GIBIBYTE * 1024;

    static StorageUsagePresentation from(Optional<VolumeSpace> volume, AppLanguage language) {
        Objects.requireNonNull(volume, "volume");
        Objects.requireNonNull(language, "language");
        if (volume.isEmpty() || volume.orElseThrow().totalBytes() == 0) {
            return new StorageUsagePresentation(UiText.EMPTY, UiText.EMPTY, UiText.EMPTY, UiText.EMPTY, 0);
        }

        VolumeSpace space = volume.orElseThrow();
        long percentage = Math.round(space.usedFraction() * 100);
        return new StorageUsagePresentation(
                UiText.storageUsedPercentage(language, percentage),
                UiText.storageUsedPercentageValue(language, percentage),
                UiText.storageCapacity(language, formatBytes(space.usedBytes(), language), formatBytes(space.totalBytes(), language)),
                UiText.storageAvailable(language, formatBytes(space.availableBytes(), language)),
                space.usedFraction());
    }

    private static String formatBytes(long bytes, AppLanguage language) {
        long unit;
        String suffix;
        if (bytes >= TEBIBYTE) {
            unit = TEBIBYTE;
            suffix = language == AppLanguage.FRENCH ? "To" : "TB";
        } else if (bytes >= GIBIBYTE) {
            unit = GIBIBYTE;
            suffix = language == AppLanguage.FRENCH ? "Go" : "GB";
        } else if (bytes >= MEBIBYTE) {
            unit = MEBIBYTE;
            suffix = language == AppLanguage.FRENCH ? "Mo" : "MB";
        } else {
            unit = KIBIBYTE;
            suffix = language == AppLanguage.FRENCH ? "Ko" : "KB";
        }

        Locale locale = language == AppLanguage.FRENCH ? Locale.FRANCE : Locale.ENGLISH;
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(1);
        return format.format((double) bytes / unit) + " " + suffix;
    }
}

package com.episort.ui.execution;

import com.episort.ui.AppLanguage;
import com.episort.ui.UiText;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

final class FileSizeText {
    private static final String[] ENGLISH_UNITS = {"B", "KiB", "MiB", "GiB", "TiB"};
    private static final String[] FRENCH_UNITS = {"o", "Kio", "Mio", "Gio", "Tio"};

    private FileSizeText() {
    }

    static String forPath(Path path, AppLanguage language) {
        try {
            return format(Files.size(path), language);
        } catch (IOException | SecurityException exception) {
            return UiText.EMPTY;
        }
    }

    private static String format(long bytes, AppLanguage language) {
        int unit = 0;
        double value = Math.max(0, bytes);
        while (value >= 1024 && unit < ENGLISH_UNITS.length - 1) {
            value /= 1024;
            unit++;
        }
        Locale locale = language == AppLanguage.FRENCH ? Locale.FRENCH : Locale.ENGLISH;
        DecimalFormat format = new DecimalFormat(value < 10 && unit > 0 ? "0.#" : "0", DecimalFormatSymbols.getInstance(locale));
        String[] units = language == AppLanguage.FRENCH ? FRENCH_UNITS : ENGLISH_UNITS;
        return format.format(value) + " " + units[unit];
    }
}

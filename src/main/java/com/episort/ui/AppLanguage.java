package com.episort.ui;

import java.util.Locale;

public enum AppLanguage {
    ENGLISH("English", "en"),
    FRENCH("Français", "fr");

    public static final AppLanguage DEFAULT = ENGLISH;

    private final String displayName;
    private final String languageTag;

    AppLanguage(String displayName, String languageTag) {
        this.displayName = displayName;
        this.languageTag = languageTag;
    }

    public String displayName() {
        return displayName;
    }

    public String languageTag() {
        return languageTag;
    }

    public static AppLanguage detectFromOs() {
        return detectFromLocale(Locale.getDefault());
    }

    static AppLanguage detectFromLocale(Locale locale) {
        if (locale != null) {
            String tag = locale.getLanguage();
            for (AppLanguage candidate : values()) {
                if (candidate.languageTag.equalsIgnoreCase(tag)) {
                    return candidate;
                }
            }
        }
        return DEFAULT;
    }
}

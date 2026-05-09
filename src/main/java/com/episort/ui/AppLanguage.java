package com.episort.ui;

public enum AppLanguage {
    FRENCH("Français"),
    ENGLISH("English");

    private final String displayName;

    AppLanguage(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}

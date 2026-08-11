package com.episort.ui;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Everything the top bar and its menus can trigger. Passed as one value because
 * eleven positional {@code Runnable} parameters is a call site nobody can read.
 */
public record TopBarActions(
        Runnable primaryAction,
        Runnable loadFolder,
        Runnable loadFiles,
        Runnable addFolder,
        Runnable addFiles,
        Runnable reset,
        Runnable rescan,
        Consumer<AppView> showView,
        Runnable about,
        Runnable quit) {

    public TopBarActions {
        Objects.requireNonNull(primaryAction, "primaryAction");
        Objects.requireNonNull(loadFolder, "loadFolder");
        Objects.requireNonNull(loadFiles, "loadFiles");
        Objects.requireNonNull(addFolder, "addFolder");
        Objects.requireNonNull(addFiles, "addFiles");
        Objects.requireNonNull(reset, "reset");
        Objects.requireNonNull(rescan, "rescan");
        Objects.requireNonNull(showView, "showView");
        Objects.requireNonNull(about, "about");
        Objects.requireNonNull(quit, "quit");
    }
}

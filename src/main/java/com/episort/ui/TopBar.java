package com.episort.ui;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * The single chrome row: application menus, workspace chip, search, the plan
 * action, and the window buttons. The stage is undecorated, so this row is also
 * the title bar — which is why nothing was allowed to push it down.
 */
public final class TopBar {
    public static final double SHELL_HEIGHT = 59;
    private final HBox root;
    private final HBox content;
    private final AppMenuBar menuBar;
    private final WindowControls windowControls;
    private final Label workspaceChipPrefix;
    private final Label workspaceChipValue;
    private final HBox workspaceChip;
    private final Label searchIcon;
    private final TextField searchField;
    private final Button clearSearchAction;
    private final HBox searchBox;
    private final Label statusPill;
    private final MenuButton loadAction;
    private final MenuItem loadFolderItem;
    private final MenuItem loadFilesItem;
    private final MenuItem addFolderItem;
    private final MenuItem addFilesItem;
    private final Button primaryAction;
    private AppLanguage currentLanguage = AppLanguage.FRENCH;
    private Tooltip workspaceTooltip;

    public TopBar(TopBarActions actions) {
        Objects.requireNonNull(actions, "actions");

        menuBar = new AppMenuBar(actions);
        windowControls = new WindowControls();

        workspaceChipPrefix = new Label("WS");
        workspaceChipPrefix.getStyleClass().add("workspace-chip-prefix");

        workspaceChipValue = new Label();
        workspaceChipValue.getStyleClass().add("workspace-chip-label");
        workspaceChipValue.setMaxWidth(280);

        workspaceChip = new HBox(workspaceChipPrefix, workspaceChipValue);
        workspaceChip.getStyleClass().add("workspace-chip");
        workspaceChip.setAlignment(Pos.CENTER_LEFT);

        searchIcon = new Label("⌕");
        searchIcon.getStyleClass().addAll("episort-search-icon", "top-search-icon");

        searchField = new TextField();
        searchField.getStyleClass().add("episort-search-field");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        clearSearchAction = new Button("×");
        clearSearchAction.getStyleClass().addAll("episort-search-clear", "top-search-clear");
        clearSearchAction.setVisible(false);
        clearSearchAction.setManaged(false);
        clearSearchAction.setOnAction(event -> clearSearch());

        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            actions.onSearchChange().accept(newValue);
            boolean hasText = newValue != null && !newValue.isBlank();
            clearSearchAction.setVisible(hasText);
            clearSearchAction.setManaged(hasText);
        });

        searchBox = new HBox(searchIcon, searchField, clearSearchAction);
        searchBox.getStyleClass().addAll("episort-search-box", "top-search");
        HBox.setHgrow(searchBox, Priority.ALWAYS);
        searchBox.setMaxWidth(420);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusPill = new Label();
        statusPill.getStyleClass().add("status-pill");
        statusPill.setVisible(false);
        statusPill.setManaged(false);

        // Loading files is the other half of the workflow, so it carries the
        // same weight as reviewing the plan rather than hiding in a menu.
        loadFolderItem = new MenuItem();
        loadFolderItem.setOnAction(event -> actions.loadFolder().run());
        loadFilesItem = new MenuItem();
        loadFilesItem.setOnAction(event -> actions.loadFiles().run());
        addFolderItem = new MenuItem();
        addFolderItem.setOnAction(event -> actions.addFolder().run());
        addFilesItem = new MenuItem();
        addFilesItem.setOnAction(event -> actions.addFiles().run());
        loadAction = new MenuButton();
        loadAction.getStyleClass().addAll("header-action", "load-primary");
        loadAction.getItems().addAll(
                loadFolderItem, loadFilesItem, new SeparatorMenuItem(), addFolderItem, addFilesItem);

        primaryAction = new Button();
        primaryAction.getStyleClass().addAll("header-action", "validate-action");
        primaryAction.setOnAction(event -> actions.primaryAction().run());

        // Everything but the window buttons lives in one group: a run in
        // progress locks the shell, and locking it must never take away the
        // ability to minimize or close the window.
        content = new HBox(12,
                menuBar.root(),
                workspaceChip,
                searchBox,
                spacer,
                statusPill,
                loadAction,
                primaryAction);
        content.getStyleClass().add("top-bar-content");
        content.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(content, Priority.ALWAYS);

        root = new HBox(12, content, windowControls.root());
        root.getStyleClass().addAll("top-bar", "window-drag-region");
        root.setAlignment(Pos.CENTER_LEFT);
        root.setMinHeight(SHELL_HEIGHT);
        root.setPrefHeight(SHELL_HEIGHT);

        applyLanguage(AppLanguage.FRENCH);
        setWorkspace(Optional.empty());
        setStatusPill(Optional.empty(), AppLanguage.FRENCH);
        setAppendActionsEnabled(false);
    }

    public Region root() {
        return root;
    }

    public TextField searchField() {
        return searchField;
    }

    public Button primaryAction() {
        return primaryAction;
    }

    public AppMenuBar menuBar() {
        return menuBar;
    }

    public WindowControls windowControls() {
        return windowControls;
    }

    /**
     * Puts the initial focus somewhere harmless. Without it JavaFX lights up the
     * search field on startup, when the step the user actually needs is loading
     * a folder.
     */
    public void focusDefault() {
        // The load button: it is the step the user needs first, and it spares
        // the search field a focus ring nobody asked for.
        loadAction.requestFocus();
    }

    public MenuButton loadAction() {
        return loadAction;
    }

    public void setAppendActionsEnabled(boolean enabled) {
        menuBar.setAppendEnabled(enabled);
        addFolderItem.setDisable(!enabled);
        addFilesItem.setDisable(!enabled);
    }

    public void setLoadActionEnabled(boolean enabled) {
        menuBar.setLoadEnabled(enabled);
        loadAction.setDisable(!enabled);
    }

    public void setResetActionEnabled(boolean enabled) {
        menuBar.setResetEnabled(enabled);
    }

    public void setRescanActionEnabled(boolean enabled) {
        menuBar.setRescanEnabled(enabled);
    }

    /** Locks the bar's controls while a run is executing, window buttons aside. */
    public void setChromeDisabled(boolean disabled) {
        content.setDisable(disabled);
    }

    public void setScanActionsVisible(boolean visible) {
        menuBar.setScanActionsVisible(visible);
        loadAction.setVisible(visible);
        loadAction.setManaged(visible);
    }

    public void setActiveView(AppView view) {
        menuBar.setActiveView(view);
    }

    /** Refuses About by disabling it, never by hiding it (design system §4.7). */
    public void setAboutEnabled(boolean enabled) {
        menuBar.setAboutEnabled(enabled);
    }

    public void setSearchVisible(boolean visible) {
        searchBox.setVisible(visible);
        searchBox.setManaged(visible);
    }

    public void setSearchText(String text) {
        String safeText = text == null ? "" : text;
        if (!Objects.equals(searchField.getText(), safeText)) {
            searchField.setText(safeText);
        }
    }

    public void applyLanguage(AppLanguage language) {
        currentLanguage = language;
        searchField.setPromptText(UiText.searchPlaceholder(language));
        // The field and the clear button carry no visible label of their own.
        searchField.setAccessibleText(UiText.searchPlaceholder(language));
        clearSearchAction.setAccessibleText(UiText.a11yClearSearch(language));
        clearSearchAction.setTooltip(new Tooltip(UiText.a11yClearSearch(language)));
        loadAction.setText(UiText.topActionLoad(language));
        loadFolderItem.setText(UiText.topActionLoadFolder(language));
        loadFilesItem.setText(UiText.topActionLoadFiles(language));
        addFolderItem.setText(UiText.topActionAddFolder(language));
        addFilesItem.setText(UiText.topActionAddFiles(language));
        menuBar.applyLanguage(language);
        windowControls.applyLanguage(language);
    }

    public void setWorkspace(Optional<Path> workspace) {
        // Tooltip.uninstall only removes the tooltip it is handed, so the
        // installed one has to be kept: passing null leaves the previous path
        // hovering over a chip that now says "no workspace".
        if (workspaceTooltip != null) {
            Tooltip.uninstall(workspaceChip, workspaceTooltip);
            workspaceTooltip = null;
        }
        if (workspace.isPresent()) {
            String path = workspace.orElseThrow().toAbsolutePath().normalize().toString();
            workspaceChipValue.setText(path);
            workspaceTooltip = new Tooltip(path);
            Tooltip.install(workspaceChip, workspaceTooltip);
        } else {
            workspaceChipValue.setText(UiText.workspaceChipEmpty(currentLanguage));
        }
    }

    public void setStatusPill(Optional<String> errorCode, AppLanguage language) {
        statusPill.getStyleClass().setAll("status-pill");
        if (errorCode.isEmpty()) {
            statusPill.setVisible(false);
            statusPill.setManaged(false);
            return;
        }
        String code = errorCode.orElseThrow();
        statusPill.setText(code);
        statusPill.setVisible(true);
        statusPill.setManaged(true);
        if (code.contains("UNAVAILABLE") || code.contains("INVALID")) {
            statusPill.getStyleClass().add("error");
        } else {
            statusPill.getStyleClass().add("warn");
        }
    }

    public void setPrimaryActionText(String text) {
        primaryAction.setText(text);
    }

    public void setPrimaryActionDisabled(boolean disabled) {
        primaryAction.setDisable(disabled);
        menuBar.setReviewPlanEnabled(!disabled);
    }

    public void clearSearch() {
        searchField.clear();
    }
}

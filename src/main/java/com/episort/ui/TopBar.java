package com.episort.ui;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

public final class TopBar {
    public static final double SHELL_HEIGHT = 59;
    private final HBox root;
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
    private final Button resetFolderAction;
    private final Button rescanAction;
    private final Button primaryAction;
    private AppLanguage currentLanguage = AppLanguage.FRENCH;

    public TopBar(
            Runnable onPrimaryAction,
            Consumer<String> onSearchChange,
            Runnable onChangeFolder,
            Runnable onLoadFiles,
            Runnable onAddFolder,
            Runnable onAddFiles,
            Runnable onResetFolder,
            Runnable onRescan) {
        Objects.requireNonNull(onPrimaryAction, "onPrimaryAction");
        Objects.requireNonNull(onSearchChange, "onSearchChange");
        Objects.requireNonNull(onChangeFolder, "onChangeFolder");
        Objects.requireNonNull(onLoadFiles, "onLoadFiles");
        Objects.requireNonNull(onAddFolder, "onAddFolder");
        Objects.requireNonNull(onAddFiles, "onAddFiles");
        Objects.requireNonNull(onResetFolder, "onResetFolder");
        Objects.requireNonNull(onRescan, "onRescan");

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
            onSearchChange.accept(newValue);
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

        loadFolderItem = new MenuItem();
        loadFolderItem.setOnAction(event -> onChangeFolder.run());
        loadFilesItem = new MenuItem();
        loadFilesItem.setOnAction(event -> onLoadFiles.run());
        addFolderItem = new MenuItem();
        addFolderItem.setOnAction(event -> onAddFolder.run());
        addFilesItem = new MenuItem();
        addFilesItem.setOnAction(event -> onAddFiles.run());
        loadAction = new MenuButton();
        loadAction.getStyleClass().addAll("header-action", "load-primary");
        loadAction.getItems().addAll(loadFolderItem, loadFilesItem, new javafx.scene.control.SeparatorMenuItem(),
                addFolderItem, addFilesItem);

        resetFolderAction = secondaryButton(onResetFolder);
        rescanAction = secondaryButton(onRescan);

        primaryAction = new Button();
        primaryAction.getStyleClass().add("validate-action");
        primaryAction.setOnAction(event -> onPrimaryAction.run());

        root = new HBox(12,
                workspaceChip,
                searchBox,
                spacer,
                statusPill,
                loadAction,
                resetFolderAction,
                rescanAction,
                primaryAction);
        root.getStyleClass().add("top-bar");
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

    public MenuButton loadAction() {
        return loadAction;
    }

    public Button resetFolderAction() {
        return resetFolderAction;
    }

    public Button rescanAction() {
        return rescanAction;
    }

    public void setAppendActionsEnabled(boolean enabled) {
        addFolderItem.setDisable(!enabled);
        addFilesItem.setDisable(!enabled);
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
        loadAction.setText(UiText.topActionLoad(language));
        loadFolderItem.setText(UiText.topActionLoadFolder(language));
        loadFilesItem.setText(UiText.topActionLoadFiles(language));
        addFolderItem.setText(UiText.topActionAddFolder(language));
        addFilesItem.setText(UiText.topActionAddFiles(language));
        resetFolderAction.setText(UiText.topActionReset(language));
        rescanAction.setText(UiText.topActionReanalyze(language));
    }

    public void setWorkspace(Optional<Path> workspace) {
        if (workspace.isPresent()) {
            String path = workspace.orElseThrow().toAbsolutePath().normalize().toString();
            workspaceChipValue.setText(path);
            Tooltip.install(workspaceChip, new Tooltip(path));
        } else {
            workspaceChipValue.setText(UiText.workspaceChipEmpty(currentLanguage));
            Tooltip.uninstall(workspaceChip, null);
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
    }

    public void clearSearch() {
        searchField.clear();
    }

    private static Button secondaryButton(Runnable action) {
        Button button = new Button();
        button.getStyleClass().addAll("header-action", "ghost");
        button.setOnAction(event -> action.run());
        return button;
    }
}

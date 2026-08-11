package com.episort.ui;

import java.util.Objects;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/** A local search control for a table and its neighbouring filters. */
public final class TableSearchBox {
    private final HBox root;
    private final TextField field = new TextField();
    private final Button clearAction = new Button("×");

    public TableSearchBox(Consumer<String> onSearchChange) {
        Objects.requireNonNull(onSearchChange, "onSearchChange");

        Label icon = new Label("⌕");
        icon.getStyleClass().add("episort-search-icon");

        field.getStyleClass().add("episort-search-field");
        HBox.setHgrow(field, Priority.ALWAYS);

        clearAction.getStyleClass().add("episort-search-clear");
        clearAction.setVisible(false);
        clearAction.setManaged(false);
        clearAction.setOnAction(event -> field.clear());

        field.textProperty().addListener((observable, oldValue, newValue) -> {
            boolean hasText = newValue != null && !newValue.isBlank();
            clearAction.setVisible(hasText);
            clearAction.setManaged(hasText);
            onSearchChange.accept(newValue);
        });

        root = new HBox(icon, field, clearAction);
        root.getStyleClass().addAll("episort-search-box", "table-search");
        root.setAlignment(Pos.CENTER_LEFT);
        root.setMinWidth(220);
        root.setPrefWidth(280);
        root.setMaxWidth(320);
    }

    public HBox root() {
        return root;
    }

    public void applyLanguage(String placeholder, String clearLabel) {
        field.setPromptText(placeholder);
        field.setAccessibleText(placeholder);
        clearAction.setAccessibleText(clearLabel);
        clearAction.setTooltip(new Tooltip(clearLabel));
    }

    public void clear() {
        field.clear();
    }
}

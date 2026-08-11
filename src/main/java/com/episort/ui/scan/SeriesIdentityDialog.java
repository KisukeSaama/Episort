package com.episort.ui.scan;

import com.episort.ui.AppLanguage;
import com.episort.ui.UiText;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * The gate between "TMDB answered" and "the table is yours": one line per
 * detected group, each with the identity that will name its files.
 *
 * <p>A mixed folder produces several groups, and the damage a wrong identity
 * does is proportional to the group size — a single bad match silently renames
 * twenty-five files after the wrong show. Showing the identities as a short
 * list, before the user starts editing individual rows, makes that decision
 * explicit and cheap to correct: one click per group instead of one per file.
 *
 * <p>The screen never blocks. Groups TMDB could not resolve are flagged and
 * still reachable manually in the table afterwards.
 */
final class SeriesIdentityDialog {
    /**
     * Column geometry. The list reads as a table, not as a stack of free-form
     * cards: every row measures the same, so the badges, the identities and the
     * actions line up vertically whatever the length of the names. Text that
     * does not fit is cropped, never wrapped — a taller row would break the
     * alignment for the whole list.
     */
    private static final double GROUP_COLUMN = 208;

    private static final double ARROW_COLUMN = 18;

    private static final double ACTION_COLUMN = 128;

    /** Height of the first line of each column, so both stacks share a baseline. */
    private static final double PRIMARY_LINE = 22;

    private final AppLanguage language;
    private final Stage stage = new Stage();
    private final VBox groupList = new VBox(8);
    private final Label warning = new Label();
    private final Supplier<List<GroupIdentityRow>> rowSupplier;
    private final BiConsumer<String, Window> onFixGroup;
    private double dragOffsetX;
    private double dragOffsetY;

    SeriesIdentityDialog(
            Window owner,
            AppLanguage language,
            Supplier<List<GroupIdentityRow>> rowSupplier,
            BiConsumer<String, Window> onFixGroup) {
        this.language = language;
        this.rowSupplier = rowSupplier;
        this.onFixGroup = onFixGroup;
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setTitle(UiText.scanIdentitiesTitle(language));

        Label title = new Label(UiText.scanIdentitiesTitle(language));
        title.getStyleClass().add("tmdb-dialog-title");
        Button closeButton = new Button("×");
        closeButton.getStyleClass().addAll("icon-button", "tmdb-dialog-close");
        closeButton.setTooltip(new Tooltip(UiText.scanIdentitiesClose(language)));
        closeButton.setOnAction(event -> stage.close());
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(10, title, headerSpacer, closeButton);
        header.getStyleClass().add("tmdb-dialog-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setOnMousePressed(event -> {
            dragOffsetX = event.getSceneX();
            dragOffsetY = event.getSceneY();
        });
        header.setOnMouseDragged(event -> {
            stage.setX(event.getScreenX() - dragOffsetX);
            stage.setY(event.getScreenY() - dragOffsetY);
        });

        Label message = new Label(UiText.scanIdentitiesMessage(language));
        message.getStyleClass().add("tmdb-dialog-message");
        message.setWrapText(true);
        message.setMinHeight(Region.USE_PREF_SIZE);

        warning.getStyleClass().addAll("tmdb-dialog-message", "identity-warning");
        warning.setWrapText(true);
        warning.setVisible(false);
        warning.setManaged(false);

        ScrollPane scroll = new ScrollPane(groupList);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("tmdb-search-results");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Button confirm = new Button(UiText.scanIdentitiesConfirm(language));
        confirm.getStyleClass().add("primary");
        confirm.setDefaultButton(true);
        confirm.setOnAction(event -> stage.close());
        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);
        HBox footer = new HBox(8, footerSpacer, confirm);
        footer.setAlignment(Pos.CENTER_RIGHT);

        VBox body = new VBox(14, header, message, warning, scroll, footer);
        body.getStyleClass().add("tmdb-dialog");
        body.setPadding(new Insets(18));

        Scene scene = new Scene(body, 760, 520);
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                stage.close();
            }
        });
        scene.getStylesheets().add(
                SeriesIdentityDialog.class.getResource("/styles/app.css").toExternalForm());
        stage.setScene(scene);
        stage.setMinWidth(640);
        stage.setMinHeight(400);
        refresh();
    }

    void showAndWait() {
        stage.showAndWait();
    }

    /** Rebuilds the list from live screen state, after a fix or on open. */
    void refresh() {
        List<GroupIdentityRow> rows = rowSupplier.get();
        groupList.getChildren().clear();
        boolean anyUnresolved = false;
        for (GroupIdentityRow row : rows) {
            anyUnresolved |= !row.resolved();
            groupList.getChildren().add(card(row));
        }
        warning.setText(UiText.scanIdentitiesUnresolvedWarning(language));
        warning.setVisible(anyUnresolved);
        warning.setManaged(anyUnresolved);
    }

    private Region card(GroupIdentityRow row) {
        Label groupName = new Label(row.groupName());
        groupName.getStyleClass().setAll("group-name", "group-name-head", "identity-group-name");
        groupName.setTooltip(new Tooltip(row.groupName()));
        HBox badgeLine = new HBox(groupName);
        badgeLine.setAlignment(Pos.CENTER_LEFT);
        badgeLine.setMinHeight(PRIMARY_LINE);
        badgeLine.setPrefHeight(PRIMARY_LINE);

        Label files = new Label(row.fileCount() + " "
                + (row.fileCount() > 1 ? UiText.scanGroupFiles(language) : UiText.scanGroupFile(language)));
        files.getStyleClass().add("identity-meta");

        VBox left = new VBox(2, badgeLine, files);
        left.setAlignment(Pos.CENTER_LEFT);
        left.setMinWidth(GROUP_COLUMN);
        left.setPrefWidth(GROUP_COLUMN);
        left.setMaxWidth(GROUP_COLUMN);

        Label arrow = new Label("→");
        arrow.getStyleClass().add("identity-meta");
        arrow.setAlignment(Pos.CENTER);
        arrow.setMinWidth(ARROW_COLUMN);
        arrow.setPrefWidth(ARROW_COLUMN);
        arrow.setMaxWidth(ARROW_COLUMN);

        // The TMDB id lives at the end of the identity string. Moving it down to
        // the meta line keeps the ids aligned across rows and lets the title use
        // the whole column before being cropped.
        String identityText = row.identityText();
        String tmdbId = null;
        int idStart = identityText.lastIndexOf(" [");
        if (idStart > 0 && identityText.endsWith("]")) {
            tmdbId = identityText.substring(idStart + 2, identityText.length() - 1);
            identityText = identityText.substring(0, idStart);
        }

        Label identity = new Label(identityText);
        identity.getStyleClass().add(row.resolved() ? "identity-title" : "identity-title-missing");
        identity.setTooltip(new Tooltip(row.identityText()));
        identity.setMinWidth(0);
        identity.setMaxWidth(Double.MAX_VALUE);
        identity.setMinHeight(PRIMARY_LINE);
        identity.setPrefHeight(PRIMARY_LINE);

        Label state = new Label(row.stateText());
        state.getStyleClass().add(row.confirmed() ? "identity-state-ok" : "identity-state-pending");
        state.setMinWidth(Region.USE_PREF_SIZE);
        HBox metaLine = new HBox(8, state);
        metaLine.setAlignment(Pos.CENTER_LEFT);
        if (tmdbId != null) {
            Label id = new Label(tmdbId);
            id.getStyleClass().add("identity-id");
            id.setMinWidth(0);
            metaLine.getChildren().add(id);
        }

        VBox middle = new VBox(2, identity, metaLine);
        middle.setAlignment(Pos.CENTER_LEFT);
        middle.setMinWidth(0);
        middle.setPrefWidth(0);
        HBox.setHgrow(middle, Priority.ALWAYS);

        Button fix = new Button(UiText.scanIdentitiesFix(language));
        fix.getStyleClass().add(row.resolved() ? "ghost" : "primary");
        fix.setMinWidth(ACTION_COLUMN);
        fix.setPrefWidth(ACTION_COLUMN);
        fix.setMaxWidth(ACTION_COLUMN);
        fix.setOnAction(event -> {
            onFixGroup.accept(row.groupName(), stage);
            refresh();
        });

        HBox card = new HBox(12, left, arrow, middle, fix);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("identity-card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }
}

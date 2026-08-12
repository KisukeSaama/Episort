package com.episort.ui.scan;

import com.episort.ui.AppLanguage;
import com.episort.ui.UiText;
import com.episort.ui.ThemeStyles;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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
 * Asked before a single TMDB identity is applied to a selection that spans more
 * than one detected group.
 *
 * <p>Selecting the whole table and applying one match is the natural gesture
 * when a folder holds one series, and a silent disaster when it holds two: the
 * second series is renamed after the first. Rather than forbid the gesture, the
 * dialog names the groups the selection covers and offers to narrow it down to
 * the one the anchor row belongs to.
 */
final class MixedGroupDialog {
    enum Choice {
        /** Apply to the anchor row's group only. */
        LIMIT_TO_GROUP,
        /** Apply to every selected row, across groups. */
        APPLY_TO_ALL,
        /** Apply to nothing. */
        CANCEL
    }

    private final Stage stage = new Stage();
    private Choice choice = Choice.CANCEL;
    private double dragOffsetX;
    private double dragOffsetY;

    private MixedGroupDialog(
            Window owner, AppLanguage language, String anchorGroup, Map<String, Integer> countsByGroup) {
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setTitle(UiText.scanGroupMixedTitle(language));

        Label title = new Label(UiText.scanGroupMixedTitle(language));
        title.getStyleClass().add("tmdb-dialog-title");
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(10, title, headerSpacer);
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

        Label message = new Label(UiText.scanGroupMixedMessage(language));
        message.getStyleClass().add("tmdb-dialog-message");
        message.setWrapText(true);

        VBox groups = new VBox(4);
        countsByGroup.forEach((name, count) -> {
            Label groupName = new Label(name);
            groupName.getStyleClass().setAll("group-name", "group-name-head");
            Label countLabel = new Label(count + " " + UiText.scanGroupFiles(language));
            countLabel.getStyleClass().add("tmdb-dialog-message");
            HBox line = new HBox(10, groupName, countLabel);
            line.setAlignment(Pos.CENTER_LEFT);
            groups.getChildren().add(line);
        });

        Button limit = new Button(UiText.scanGroupMixedLimit(language) + " « " + anchorGroup + " »");
        limit.getStyleClass().add("primary");
        limit.setDefaultButton(true);
        limit.setOnAction(event -> close(Choice.LIMIT_TO_GROUP));
        Button applyAll = new Button(UiText.scanGroupMixedApplyAll(language));
        applyAll.getStyleClass().add("ghost");
        applyAll.setOnAction(event -> close(Choice.APPLY_TO_ALL));
        Button cancel = new Button(UiText.scanGroupMixedCancel(language));
        cancel.getStyleClass().add("ghost");
        cancel.setOnAction(event -> close(Choice.CANCEL));

        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);
        HBox footer = new HBox(8, cancel, footerSpacer, applyAll, limit);
        footer.setAlignment(Pos.CENTER_LEFT);

        javafx.scene.control.ScrollPane groupScroll = new javafx.scene.control.ScrollPane(groups);
        groupScroll.setFitToWidth(true);
        groupScroll.setMaxHeight(180);
        groupScroll.getStyleClass().add("tmdb-search-results");

        VBox body = new VBox(14, header, message, groupScroll, footer);
        body.getStyleClass().add("tmdb-dialog");
        body.setPadding(new Insets(18));

        Scene scene = new Scene(body, 620, 340);
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                close(Choice.CANCEL);
            }
        });
        scene.getStylesheets().add(
                MixedGroupDialog.class.getResource("/styles/app.css").toExternalForm());
        ThemeStyles.register(body);
        stage.setScene(scene);
    }

    static Choice ask(
            Window owner, AppLanguage language, String anchorGroup, Map<String, Integer> countsByGroup) {
        MixedGroupDialog dialog = new MixedGroupDialog(owner, language, anchorGroup, countsByGroup);
        dialog.stage.showAndWait();
        return dialog.choice;
    }

    private void close(Choice value) {
        choice = value;
        stage.close();
    }
}

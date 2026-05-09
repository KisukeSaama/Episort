package com.episort.ui.debug;

import com.episort.ai.debug.AiTrace;
import com.episort.ai.debug.AiTraceBus;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Developer-only window that subscribes to {@link AiTraceBus} and shows each
 * AI prompt/response pair live. Activated via {@code -Depisort.aiDebug=true}
 * or the {@code Ctrl+Shift+D} shortcut on the main window.
 */
public final class AiDebugWindow {
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private static AiDebugWindow instance;

    private final Stage stage;
    private final ObservableList<AiTrace> rows = FXCollections.observableArrayList();
    private final TextArea promptArea = new TextArea();
    private final TextArea responseArea = new TextArea();
    private final ToggleButton pauseToggle = new ToggleButton("Pause");
    private final Consumer<AiTrace> listener;

    private AiDebugWindow() {
        stage = new Stage();
        stage.setTitle("Episort — AI Debug");

        TableView<AiTrace> table = new TableView<>(rows);
        table.setPlaceholder(new Label("No AI traces yet."));

        TableColumn<AiTrace, String> timeCol = new TableColumn<>("Time");
        timeCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                TIME_FMT.format(c.getValue().timestamp())));
        timeCol.setPrefWidth(110);

        TableColumn<AiTrace, String> sourceCol = new TableColumn<>("Source");
        sourceCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().source()));
        sourceCol.setPrefWidth(100);

        TableColumn<AiTrace, String> latencyCol = new TableColumn<>("Latency");
        latencyCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().latencyMs() + " ms"));
        latencyCol.setPrefWidth(80);

        TableColumn<AiTrace, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().failed() ? "ERROR" : "OK"));
        statusCol.setPrefWidth(70);

        TableColumn<AiTrace, String> previewCol = new TableColumn<>("Preview");
        previewCol.setCellValueFactory(c -> {
            AiTrace t = c.getValue();
            String s = t.failed() ? t.error() : t.response();
            if (s == null) s = "";
            s = s.replace('\n', ' ').replace('\r', ' ');
            if (s.length() > 200) s = s.substring(0, 200) + "…";
            return new javafx.beans.property.SimpleStringProperty(s);
        });
        previewCol.setPrefWidth(500);

        @SuppressWarnings("unchecked")
        TableColumn<AiTrace, ?>[] cols =
                (TableColumn<AiTrace, ?>[]) new TableColumn<?, ?>[]{
                        timeCol, sourceCol, latencyCol, statusCol, previewCol};
        table.getColumns().setAll(cols);

        promptArea.setEditable(false);
        promptArea.setWrapText(true);
        responseArea.setEditable(false);
        responseArea.setWrapText(true);

        VBox promptBox = new VBox(4, new Label("Prompt"), promptArea);
        VBox responseBox = new VBox(4, new Label("Response / Error"), responseArea);
        VBox.setVgrow(promptArea, Priority.ALWAYS);
        VBox.setVgrow(responseArea, Priority.ALWAYS);

        SplitPane detailSplit = new SplitPane(promptBox, responseBox);
        detailSplit.setOrientation(javafx.geometry.Orientation.HORIZONTAL);
        detailSplit.setDividerPositions(0.5);

        SplitPane mainSplit = new SplitPane(table, detailSplit);
        mainSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);
        mainSplit.setDividerPositions(0.45);

        ChangeListener<AiTrace> selection = (obs, old, sel) -> {
            if (sel == null) {
                promptArea.clear();
                responseArea.clear();
            } else {
                promptArea.setText(sel.prompt());
                responseArea.setText(sel.failed() ? sel.error() : sel.response());
            }
        };
        table.getSelectionModel().selectedItemProperty().addListener(selection);

        Button clearBtn = new Button("Clear");
        clearBtn.setOnAction(e -> {
            AiTraceBus.get().clear();
            rows.clear();
        });
        Button copyBtn = new Button("Copy");
        copyBtn.setOnAction(e -> {
            AiTrace sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            ClipboardContent cc = new ClipboardContent();
            cc.putString("=== PROMPT ===\n" + sel.prompt()
                    + "\n\n=== RESPONSE ===\n"
                    + (sel.failed() ? sel.error() : sel.response()));
            Clipboard.getSystemClipboard().setContent(cc);
        });
        Label countLabel = new Label();
        rows.addListener((javafx.collections.ListChangeListener<AiTrace>) c ->
                countLabel.setText(rows.size() + " trace(s)"));
        countLabel.setText("0 trace(s)");

        HBox toolbar = new HBox(8, pauseToggle, clearBtn, copyBtn, countLabel);
        toolbar.setPadding(new Insets(6));

        VBox root = new VBox(toolbar, mainSplit);
        VBox.setVgrow(mainSplit, Priority.ALWAYS);

        rows.addAll(AiTraceBus.get().snapshot());

        listener = trace -> Platform.runLater(() -> {
            if (pauseToggle.isSelected()) return;
            rows.add(trace);
            if (rows.size() > 500) rows.remove(0, rows.size() - 500);
        });
        AiTraceBus.get().addListener(listener);

        stage.setOnHidden(e -> {
            AiTraceBus.get().removeListener(listener);
            instance = null;
        });

        stage.setScene(new Scene(root, 1100, 650));
    }

    public static void toggle() {
        if (instance != null && instance.stage.isShowing()) {
            instance.stage.hide();
            return;
        }
        show();
    }

    public static void show() {
        if (instance == null) {
            instance = new AiDebugWindow();
        }
        instance.stage.show();
        instance.stage.toFront();
    }
}

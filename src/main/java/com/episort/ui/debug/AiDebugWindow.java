package com.episort.ui.debug;

import com.episort.ai.debug.AiTrace;
import com.episort.ai.debug.AiTraceBus;
import com.episort.tvdb.debug.TvdbRequestBus;
import com.episort.tvdb.debug.TvdbRequestTrace;
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
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
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
 * Developer-only window that surfaces AI prompt/response traces and TVDB HTTP
 * traces live. Activated via {@code -Depisort.aiDebug=true} or the
 * {@code Ctrl+Shift+D} shortcut on the main window.
 */
public final class AiDebugWindow {
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private static AiDebugWindow instance;

    private final Stage stage;
    private final AiTab aiTab;
    private final TvdbTab tvdbTab;

    private AiDebugWindow() {
        stage = new Stage();
        stage.setTitle("Episort — Debug");

        aiTab = new AiTab();
        tvdbTab = new TvdbTab();

        TabPane tabs = new TabPane(aiTab.tab, tvdbTab.tab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        stage.setOnHidden(e -> {
            aiTab.dispose();
            tvdbTab.dispose();
            instance = null;
        });

        Scene scene = new Scene(tabs, 1100, 650);
        java.net.URL css = AiDebugWindow.class.getResource("/styles/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        tabs.getStyleClass().addAll("app-shell", "theme-dark", "debug-window");
        tabs.setStyle("-fx-background-color: #0b0f17;");
        stage.setScene(scene);
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

    private static final class AiTab {
        final Tab tab = new Tab("AI");
        private final ObservableList<AiTrace> rows = FXCollections.observableArrayList();
        private final TextArea promptArea = new TextArea();
        private final TextArea responseArea = new TextArea();
        private final ToggleButton pauseToggle = new ToggleButton("Pause");
        private final Consumer<AiTrace> listener;

        AiTab() {
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

            tab.setContent(root);
            tab.setClosable(false);
        }

        void dispose() {
            AiTraceBus.get().removeListener(listener);
        }
    }

    private static final class TvdbTab {
        final Tab tab = new Tab("TVDB");
        private final ObservableList<TvdbRequestTrace> rows = FXCollections.observableArrayList();
        private final TextArea bodyArea = new TextArea();
        private final ToggleButton pauseToggle = new ToggleButton("Pause");
        private final Consumer<TvdbRequestTrace> listener;

        TvdbTab() {
            TableView<TvdbRequestTrace> table = new TableView<>(rows);
            table.setPlaceholder(new Label("No TVDB requests yet."));

            TableColumn<TvdbRequestTrace, String> timeCol = new TableColumn<>("Time");
            timeCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                    TIME_FMT.format(c.getValue().timestamp())));
            timeCol.setPrefWidth(110);

            TableColumn<TvdbRequestTrace, String> methodCol = new TableColumn<>("Method");
            methodCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                    c.getValue().method()));
            methodCol.setPrefWidth(70);

            TableColumn<TvdbRequestTrace, String> pathCol = new TableColumn<>("Path");
            pathCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                    c.getValue().path()));
            pathCol.setPrefWidth(360);

            TableColumn<TvdbRequestTrace, String> statusCol = new TableColumn<>("HTTP");
            statusCol.setCellValueFactory(c -> {
                TvdbRequestTrace t = c.getValue();
                String s;
                if (t.cacheHit()) s = "—";
                else if (t.statusCode() == 0) s = "—";
                else s = String.valueOf(t.statusCode());
                return new javafx.beans.property.SimpleStringProperty(s);
            });
            statusCol.setPrefWidth(60);

            TableColumn<TvdbRequestTrace, String> latencyCol = new TableColumn<>("Latency");
            latencyCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                    c.getValue().cacheHit() ? "cache" : c.getValue().latencyMs() + " ms"));
            latencyCol.setPrefWidth(80);

            TableColumn<TvdbRequestTrace, String> resultCol = new TableColumn<>("Result");
            resultCol.setCellValueFactory(c -> {
                TvdbRequestTrace t = c.getValue();
                String s;
                if (t.cacheHit()) s = "HIT";
                else if (t.failed()) s = "ERROR";
                else s = "OK";
                return new javafx.beans.property.SimpleStringProperty(s);
            });
            resultCol.setPrefWidth(70);

            TableColumn<TvdbRequestTrace, String> previewCol = new TableColumn<>("Preview");
            previewCol.setCellValueFactory(c -> {
                TvdbRequestTrace t = c.getValue();
                String s;
                if (t.error() != null && !t.error().isBlank()) s = t.error();
                else if (t.responsePreview() != null) s = t.responsePreview();
                else s = "";
                s = s.replace('\n', ' ').replace('\r', ' ');
                if (s.length() > 200) s = s.substring(0, 200) + "…";
                return new javafx.beans.property.SimpleStringProperty(s);
            });
            previewCol.setPrefWidth(360);

            @SuppressWarnings("unchecked")
            TableColumn<TvdbRequestTrace, ?>[] cols =
                    (TableColumn<TvdbRequestTrace, ?>[]) new TableColumn<?, ?>[]{
                            timeCol, methodCol, pathCol, statusCol, latencyCol, resultCol, previewCol};
            table.getColumns().setAll(cols);

            bodyArea.setEditable(false);
            bodyArea.setWrapText(true);

            VBox bodyBox = new VBox(4, new Label("Response body / Error"), bodyArea);
            VBox.setVgrow(bodyArea, Priority.ALWAYS);

            SplitPane mainSplit = new SplitPane(table, bodyBox);
            mainSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);
            mainSplit.setDividerPositions(0.55);

            table.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
                if (sel == null) {
                    bodyArea.clear();
                } else if (sel.error() != null && !sel.error().isBlank()) {
                    bodyArea.setText(sel.error());
                } else {
                    bodyArea.setText(sel.responsePreview() == null ? "" : sel.responsePreview());
                }
            });

            Button clearBtn = new Button("Clear");
            clearBtn.setOnAction(e -> {
                TvdbRequestBus.get().clear();
                rows.clear();
            });
            Button copyBtn = new Button("Copy");
            copyBtn.setOnAction(e -> {
                TvdbRequestTrace sel = table.getSelectionModel().getSelectedItem();
                if (sel == null) return;
                ClipboardContent cc = new ClipboardContent();
                cc.putString(sel.method() + " " + sel.path()
                        + "\nHTTP " + sel.statusCode() + " in " + sel.latencyMs() + " ms"
                        + "\n\n" + (sel.error() != null && !sel.error().isBlank()
                                ? sel.error()
                                : (sel.responsePreview() == null ? "" : sel.responsePreview())));
                Clipboard.getSystemClipboard().setContent(cc);
            });
            Label countLabel = new Label();
            rows.addListener((javafx.collections.ListChangeListener<TvdbRequestTrace>) c ->
                    countLabel.setText(rows.size() + " request(s)"));
            countLabel.setText("0 request(s)");

            HBox toolbar = new HBox(8, pauseToggle, clearBtn, copyBtn, countLabel);
            toolbar.setPadding(new Insets(6));

            VBox root = new VBox(toolbar, mainSplit);
            VBox.setVgrow(mainSplit, Priority.ALWAYS);

            rows.addAll(TvdbRequestBus.get().snapshot());

            listener = trace -> Platform.runLater(() -> {
                if (pauseToggle.isSelected()) return;
                rows.add(trace);
                if (rows.size() > 500) rows.remove(0, rows.size() - 500);
            });
            TvdbRequestBus.get().addListener(listener);

            tab.setContent(root);
            tab.setClosable(false);
        }

        void dispose() {
            TvdbRequestBus.get().removeListener(listener);
        }
    }
}

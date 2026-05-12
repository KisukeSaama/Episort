package com.episort.ui.scan;

import com.episort.config.TvdbCredentials;
import com.episort.tvdb.TvdbCandidate;
import com.episort.tvdb.TvdbClient;
import com.episort.tvdb.TvdbException;
import com.episort.tvdb.TvdbSearchResult;
import com.episort.ui.AppLanguage;
import com.episort.ui.UiText;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.input.KeyCode;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

final class TvdbManualMatchDialog {
    private final AppLanguage language;
    private final TvdbClient tvdbClient;
    private final TvdbCredentials credentials;
    private final Stage stage = new Stage();
    private final TextField queryField = new TextField();
    private final Button searchButton = new Button();
    private final Button clearSearchButton = new Button("×");
    private final Button ignoreButton = new Button();
    private final ListView<TvdbCandidate> results = new ListView<>();
    private final Label message = new Label();
    private final Label emptyTitle = new Label();
    private final Label emptyHint = new Label();
    private final ProgressIndicator loading = new ProgressIndicator();
    private Optional<TvdbCandidate> selected = Optional.empty();
    private double dragOffsetX;
    private double dragOffsetY;

    TvdbManualMatchDialog(
            Window owner,
            AppLanguage language,
            TvdbClient tvdbClient,
            TvdbCredentials credentials,
            String initialQuery,
            List<TvdbCandidate> initialResults) {
        this.language = language;
        this.tvdbClient = tvdbClient;
        this.credentials = credentials;
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setTitle(UiText.tvdbChooseMatch(language));

        queryField.setText(initialQuery == null ? "" : initialQuery);
        queryField.setPromptText(UiText.tvdbSearchPlaceholder(language));
        queryField.getStyleClass().addAll("episort-search-field", "tvdb-search-text-field");
        searchButton.setText(UiText.tvdbSearch(language));
        searchButton.getStyleClass().add("primary");
        clearSearchButton.getStyleClass().addAll("episort-search-clear", "tvdb-search-clear");
        clearSearchButton.setTooltip(new Tooltip(UiText.tvdbSearch(language)));
        clearSearchButton.setVisible(!queryField.getText().isBlank());
        clearSearchButton.setManaged(!queryField.getText().isBlank());
        ignoreButton.setText(UiText.tvdbIgnore(language));
        ignoreButton.getStyleClass().add("ghost");

        results.getStyleClass().add("tvdb-search-results");
        results.setCellFactory(view -> new ResultCell());
        results.setPlaceholder(emptyState(UiText.tvdbInitialSearchHint(language), ""));
        results.getItems().setAll(initialResults == null ? List.of() : initialResults);

        loading.setMaxSize(24, 24);
        loading.getStyleClass().add("tvdb-search-loader");
        loading.setVisible(false);
        loading.setManaged(false);
        message.getStyleClass().add("tvdb-dialog-message");
        message.setWrapText(true);
        message.setVisible(false);
        message.setManaged(false);

        searchButton.setOnAction(event -> search());
        queryField.setOnAction(event -> search());
        queryField.textProperty().addListener((observable, oldValue, newValue) -> {
            boolean hasText = newValue != null && !newValue.isBlank();
            clearSearchButton.setVisible(hasText);
            clearSearchButton.setManaged(hasText);
            if (!hasText) {
                results.getItems().clear();
                setEmptyState(UiText.tvdbInitialSearchHint(language), "");
                setMessage("");
            }
        });
        clearSearchButton.setOnAction(event -> {
            queryField.clear();
            queryField.requestFocus();
        });
        ignoreButton.setOnAction(event -> stage.close());
        results.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                choose(results.getSelectionModel().getSelectedItem());
            }
        });

        Label title = new Label(UiText.tvdbChooseMatch(language));
        title.getStyleClass().add("tvdb-dialog-title");
        Button closeButton = new Button("X");
        closeButton.getStyleClass().addAll("icon-button", "tvdb-dialog-close");
        closeButton.setTooltip(new Tooltip(UiText.tvdbClose(language)));
        closeButton.setOnAction(event -> stage.close());
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(10, title, headerSpacer, closeButton);
        header.getStyleClass().add("tvdb-dialog-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setOnMousePressed(event -> {
            dragOffsetX = event.getSceneX();
            dragOffsetY = event.getSceneY();
        });
        header.setOnMouseDragged(event -> {
            stage.setX(event.getScreenX() - dragOffsetX);
            stage.setY(event.getScreenY() - dragOffsetY);
        });

        Label searchIcon = new Label("⌕");
        searchIcon.getStyleClass().addAll("episort-search-icon", "tvdb-search-icon");
        HBox searchBox = new HBox(8, searchIcon, queryField, clearSearchButton);
        searchBox.getStyleClass().addAll("episort-search-box", "tvdb-search-box");
        HBox.setHgrow(queryField, Priority.ALWAYS);
        HBox searchRow = new HBox(10, searchBox, searchButton, loading);
        HBox.setHgrow(searchBox, Priority.ALWAYS);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        HBox footer = new HBox(8, ignoreButton);
        footer.setAlignment(Pos.CENTER_RIGHT);

        VBox body = new VBox(12, header, searchRow, message, results, footer);
        body.getStyleClass().add("tvdb-dialog");
        body.setPadding(new Insets(16));
        VBox.setVgrow(results, Priority.ALWAYS);

        Scene scene = new Scene(body, 900, 640);
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                stage.close();
            }
        });
        String css = TvdbManualMatchDialog.class.getResource("/styles/app.css").toExternalForm();
        scene.getStylesheets().add(css);
        stage.setScene(scene);
        stage.setMinWidth(720);
        stage.setMinHeight(480);
        if (!queryField.getText().isBlank() && results.getItems().isEmpty()) {
            Platform.runLater(this::search);
        }
    }

    Optional<TvdbCandidate> showAndWait() {
        stage.showAndWait();
        return selected;
    }

    private void search() {
        String query = queryField.getText() == null ? "" : queryField.getText().trim();
        if (query.isBlank()) {
            results.getItems().clear();
            setEmptyState(UiText.tvdbInitialSearchHint(language), "");
            setMessage("");
            return;
        }
        setBusy(true);
        setMessage(UiText.tvdbLoadingResults(language));
        CompletableFuture
                .supplyAsync(() -> tvdbClient.search(query, credentials))
                .thenAccept(searchResult -> Platform.runLater(() -> showResults(searchResult)))
                .exceptionally(throwable -> {
                    Platform.runLater(() -> showFailure(throwable));
                    return null;
                });
    }

    private void showResults(TvdbSearchResult searchResult) {
        setBusy(false);
        List<TvdbCandidate> combined = new ArrayList<>();
        combined.addAll(searchResult.seriesCandidates());
        combined.addAll(searchResult.movieCandidates());
        results.getItems().setAll(combined);
        FadeTransition fade = new FadeTransition(Duration.millis(140), results);
        results.setOpacity(0.35);
        fade.setToValue(1.0);
        fade.play();
        if (combined.isEmpty()) {
            setEmptyState(UiText.tvdbNoResults(language), UiText.tvdbNoResultsHint(language));
            setMessage("");
        } else {
            setMessage("");
        }
    }

    private void showFailure(Throwable throwable) {
        setBusy(false);
        Throwable cause = throwable instanceof java.util.concurrent.CompletionException && throwable.getCause() != null
                ? throwable.getCause()
                : throwable;
        setMessage(cause instanceof TvdbException tvdbException
                ? tvdbException.error().safeMessage()
                : "TVDB lookup failed.");
    }

    private void setBusy(boolean busy) {
        loading.setVisible(busy);
        loading.setManaged(busy);
        searchButton.setDisable(busy);
        queryField.setDisable(busy);
        searchButton.setText(busy ? UiText.tvdbSearching(language) : UiText.tvdbSearch(language));
    }

    private VBox emptyState(String title, String hint) {
        emptyTitle.getStyleClass().add("tvdb-empty-title");
        emptyHint.getStyleClass().add("tvdb-empty-hint");
        emptyHint.setWrapText(true);
        VBox box = new VBox(5, emptyTitle, emptyHint);
        box.getStyleClass().add("tvdb-empty-state");
        box.setAlignment(Pos.CENTER);
        setEmptyState(title, hint);
        return box;
    }

    private void setEmptyState(String title, String hint) {
        emptyTitle.setText(title == null ? "" : title);
        emptyHint.setText(hint == null ? "" : hint);
        boolean hasHint = hint != null && !hint.isBlank();
        emptyHint.setVisible(hasHint);
        emptyHint.setManaged(hasHint);
    }

    private void setMessage(String text) {
        message.setText(text == null ? "" : text);
        boolean visible = text != null && !text.isBlank();
        message.setVisible(visible);
        message.setManaged(visible);
    }

    private void choose(TvdbCandidate candidate) {
        if (candidate == null) {
            return;
        }
        selected = Optional.of(candidate);
        stage.close();
    }

    private final class ResultCell extends ListCell<TvdbCandidate> {
        @Override
        protected void updateItem(TvdbCandidate item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            setGraphic(resultCard(item));
        }

        private Region resultCard(TvdbCandidate item) {
            ImageView poster = new ImageView();
            poster.setFitWidth(82);
            poster.setFitHeight(122);
            poster.setPreserveRatio(true);
            poster.setVisible(false);
            Label placeholder = new Label("TVDB");
            placeholder.getStyleClass().add("tvdb-poster-placeholder");
            StackPane posterFrame = new StackPane(placeholder, poster);
            posterFrame.getStyleClass().add("tvdb-poster-frame");
            item.posterUrl().ifPresent(url -> {
                Image image = new Image(url, true);
                image.errorProperty().addListener((obs, was, failed) -> {
                    if (failed) {
                        System.getLogger(TvdbManualMatchDialog.class.getName())
                                .log(System.Logger.Level.DEBUG, "TVDB poster failed to load: " + url);
                        poster.setImage(null);
                        poster.setVisible(false);
                    }
                });
                poster.setImage(image);
                poster.setVisible(true);
            });

            Label title = new Label(item.identity().displayName());
            title.getStyleClass().add("tvdb-match-title");
            Label meta = new Label(mediaTypeText(item) + item.year().map(year -> " • " + year).orElse(""));
            meta.getStyleClass().add("tvdb-match-meta");
            Label id = new Label(UiText.tvdbIdLabel(language) + ": " + item.identity().id());
            id.getStyleClass().add("tvdb-match-meta");
            Label overview = new Label(shortOverview(localizedOverview(item)));
            overview.getStyleClass().add("tvdb-match-overview");
            overview.setWrapText(true);
            overview.setMaxHeight(38);
            overview.setMinHeight(Region.USE_PREF_SIZE);

            Button select = new Button(UiText.tvdbSelect(language));
            select.getStyleClass().add("primary");
            select.setMinWidth(112);
            select.setPrefWidth(132);
            select.setMaxWidth(132);
            select.setOnAction(event -> choose(item));
            VBox text = new VBox(4, title, meta, id, overview);
            text.setMinWidth(0);
            text.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(text, Priority.ALWAYS);
            StackPane action = new StackPane(select);
            action.getStyleClass().add("tvdb-result-action");
            action.setMinWidth(140);
            action.setPrefWidth(140);
            action.setMaxWidth(140);
            HBox row = new HBox(12, posterFrame, text, action);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setMaxWidth(Double.MAX_VALUE);
            row.getStyleClass().add("tvdb-result-card");
            return row;
        }

        private String mediaTypeText(TvdbCandidate item) {
            return item.identity().mediaType() == com.episort.tvdb.TvdbMediaType.MOVIE
                    ? UiText.tvdbMovie(language)
                    : UiText.tvdbSeries(language);
        }
    }

    private static String shortOverview(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 180) {
            return normalized;
        }
        return normalized.substring(0, 177).stripTrailing() + "...";
    }

    private String localizedOverview(TvdbCandidate item) {
        Optional<String> primary = language == AppLanguage.FRENCH ? item.frenchOverview() : item.englishOverview();
        Optional<String> secondary = language == AppLanguage.FRENCH ? item.englishOverview() : item.frenchOverview();
        return primary
                .or(() -> secondary)
                .or(item::overview)
                .filter(value -> !value.isBlank())
                .orElseGet(() -> UiText.tvdbNoDescription(language));
    }
}

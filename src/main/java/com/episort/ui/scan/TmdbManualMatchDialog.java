package com.episort.ui.scan;

import com.episort.config.JanusConfiguration;
import com.episort.scanner.InventoryGroupType;
import com.episort.tmdb.TmdbCandidate;
import com.episort.tmdb.TmdbCandidateScorer;
import com.episort.tmdb.TmdbClient;
import com.episort.tmdb.TmdbException;
import com.episort.tmdb.TmdbSearchCriteria;
import com.episort.tmdb.TmdbSearchResult;
import com.episort.ui.AppLanguage;
import com.episort.ui.FocusRelease;
import com.episort.ui.UiText;
import com.episort.ui.ThemeStyles;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

final class TmdbManualMatchDialog {
    private static final TmdbCandidateScorer SCORER = new TmdbCandidateScorer();

    private final AppLanguage language;
    private final TmdbClient tmdbClient;
    private final JanusConfiguration credentials;
    private final Stage stage = new Stage();
    private final TextField queryField = new TextField();
    private final TextField yearField = new TextField();
    private final TextField tmdbIdField = new TextField();
    private final Button searchButton = new Button();
    private final Button clearSearchButton = new Button("×");
    private final Button ignoreButton = new Button();
    private final ListView<TmdbCandidate> results = new ListView<>();
    private final Label message = new Label();
    private final Label emptyTitle = new Label();
    private final Label emptyHint = new Label();
    private final ProgressIndicator loading = new ProgressIndicator();
    private Optional<TmdbCandidate> selected = Optional.empty();
    private double dragOffsetX;
    private double dragOffsetY;

    TmdbManualMatchDialog(
            Window owner,
            AppLanguage language,
            TmdbClient tmdbClient,
            JanusConfiguration credentials,
            String initialQuery,
            List<TmdbCandidate> initialResults) {
        this.language = language;
        this.tmdbClient = tmdbClient;
        this.credentials = credentials;
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setTitle(UiText.tmdbChooseMatch(language));

        queryField.setText(initialQuery == null ? "" : initialQuery);
        queryField.setPromptText(UiText.tmdbSearchPlaceholder(language));
        queryField.getStyleClass().addAll("episort-search-field", "tmdb-search-text-field");
        yearField.setPromptText(UiText.tmdbSearchYearPlaceholder(language));
        yearField.setAccessibleText(UiText.tmdbSearchYearPlaceholder(language));
        yearField.setTextFormatter(digitsOnly(4));
        yearField.getStyleClass().add("tmdb-search-year");
        tmdbIdField.setPromptText(UiText.tmdbSearchIdPlaceholder(language));
        tmdbIdField.setAccessibleText(UiText.tmdbSearchIdPlaceholder(language));
        tmdbIdField.setTextFormatter(digitsOnly(18));
        tmdbIdField.getStyleClass().add("tmdb-search-id");
        searchButton.setText(UiText.tmdbSearch(language));
        searchButton.getStyleClass().add("primary");
        clearSearchButton.getStyleClass().addAll("episort-search-clear", "tmdb-search-clear");
        // The clear glyph is not a second search button: it wore the search
        // label, so hovering it promised the opposite of what it does.
        clearSearchButton.setTooltip(new Tooltip(UiText.a11yClearSearch(language)));
        clearSearchButton.setAccessibleText(UiText.a11yClearSearch(language));
        clearSearchButton.setVisible(!queryField.getText().isBlank());
        clearSearchButton.setManaged(!queryField.getText().isBlank());
        ignoreButton.setText(UiText.tmdbIgnore(language));
        ignoreButton.getStyleClass().add("ghost");

        results.getStyleClass().add("tmdb-search-results");
        results.setCellFactory(view -> new ResultCell());
        results.setPlaceholder(emptyState(UiText.tmdbInitialSearchHint(language), ""));
        results.getItems().setAll(initialResults == null ? List.of() : initialResults);

        loading.setMaxSize(24, 24);
        loading.getStyleClass().add("tmdb-search-loader");
        loading.setVisible(false);
        loading.setManaged(false);
        message.getStyleClass().add("tmdb-dialog-message");
        message.setWrapText(true);
        message.setVisible(false);
        message.setManaged(false);

        searchButton.setOnAction(event -> search());
        queryField.setOnAction(event -> search());
        yearField.setOnAction(event -> search());
        tmdbIdField.setOnAction(event -> search());
        queryField.textProperty().addListener((observable, oldValue, newValue) -> {
            boolean hasText = newValue != null && !newValue.isBlank();
            clearSearchButton.setVisible(hasText);
            clearSearchButton.setManaged(hasText);
            clearResultsWhenCriteriaAreEmpty();
        });
        yearField.textProperty().addListener((observable, oldValue, newValue) -> clearResultsWhenCriteriaAreEmpty());
        tmdbIdField.textProperty().addListener((observable, oldValue, newValue) -> clearResultsWhenCriteriaAreEmpty());
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

        Label title = new Label(UiText.tmdbChooseMatch(language));
        title.getStyleClass().add("tmdb-dialog-title");
        Button closeButton = new Button("×");
        closeButton.getStyleClass().addAll("icon-button", "tmdb-dialog-close");
        closeButton.setTooltip(new Tooltip(UiText.tmdbClose(language)));
        closeButton.setAccessibleText(UiText.tmdbClose(language));
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

        Label searchIcon = new Label("⌕");
        searchIcon.getStyleClass().addAll("episort-search-icon", "tmdb-search-icon");
        HBox searchBox = new HBox(8, searchIcon, queryField, clearSearchButton);
        searchBox.getStyleClass().addAll("episort-search-box", "tmdb-search-box");
        HBox.setHgrow(queryField, Priority.ALWAYS);
        HBox searchRow = new HBox(10, searchBox, yearField, tmdbIdField, searchButton, loading);
        HBox.setHgrow(searchBox, Priority.ALWAYS);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        HBox footer = new HBox(8, ignoreButton);
        footer.setAlignment(Pos.CENTER_RIGHT);

        VBox body = new VBox(12, header, searchRow, message, results, footer);
        body.getStyleClass().add("tmdb-dialog");
        body.setPadding(new Insets(16));
        VBox.setVgrow(results, Priority.ALWAYS);

        Scene scene = new Scene(body, 900, 640);
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                stage.close();
            }
        });
        String css = TmdbManualMatchDialog.class.getResource("/styles/app.css").toExternalForm();
        scene.getStylesheets().add(css);
        ThemeStyles.register(body);
        ThemeStyles.registerScene(scene);
        FocusRelease.install(scene);
        stage.setScene(scene);
        stage.setMinWidth(720);
        stage.setMinHeight(480);
        if (!queryField.getText().isBlank() && results.getItems().isEmpty()) {
            Platform.runLater(this::search);
        }
    }

    Optional<TmdbCandidate> showAndWait() {
        stage.showAndWait();
        return selected;
    }

    private void search() {
        String query = queryField.getText() == null ? "" : queryField.getText().trim();
        String tmdbId = tmdbIdField.getText() == null ? "" : tmdbIdField.getText().trim();
        if (query.isBlank() && tmdbId.isBlank()) {
            results.getItems().clear();
            setEmptyState(UiText.tmdbInitialSearchHint(language), "");
            setMessage(UiText.tmdbSearchCriteriaRequired(language));
            return;
        }
        String yearText = yearField.getText() == null ? "" : yearField.getText().trim();
        if (!yearText.isBlank() && yearText.length() != 4) {
            setMessage(UiText.tmdbSearchInvalidYear(language));
            yearField.requestFocus();
            return;
        }
        TmdbSearchCriteria criteria = new TmdbSearchCriteria(
                query,
                yearText.isBlank() ? Optional.empty() : Optional.of(Integer.parseInt(yearText)),
                tmdbId.isBlank() ? Optional.empty() : Optional.of(tmdbId));
        setBusy(true);
        setMessage(UiText.tmdbLoadingResults(language));
        CompletableFuture
                .supplyAsync(() -> tmdbClient.search(criteria, credentials))
                .thenAccept(searchResult -> Platform.runLater(() -> showResults(searchResult)))
                .exceptionally(throwable -> {
                    Platform.runLater(() -> showFailure(throwable));
                    return null;
                });
    }

    private void showResults(TmdbSearchResult searchResult) {
        setBusy(false);
        List<TmdbCandidate> combined = new ArrayList<>();
        combined.addAll(searchResult.seriesCandidates());
        combined.addAll(searchResult.movieCandidates());
        // Show the closest title first rather than TMDB's own ranking.
        if (queryField.getText() != null && !queryField.getText().isBlank()) {
            combined = SCORER.rankedByRelevance(
                    queryField.getText(), InventoryGroupType.UNKNOWN, combined);
        }
        results.getItems().setAll(combined);
        FadeTransition fade = new FadeTransition(Duration.millis(140), results);
        results.setOpacity(0.35);
        fade.setToValue(1.0);
        fade.play();
        if (combined.isEmpty()) {
            setEmptyState(UiText.tmdbNoResults(language), UiText.tmdbNoResultsHint(language));
            setMessage("");
        } else {
            setMessage("");
        }
    }

    private void showFailure(Throwable throwable) {
        setBusy(false);
        Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null
                ? throwable.getCause()
                : throwable;
        setMessage(cause instanceof TmdbException tmdbException
                ? tmdbException.error().safeMessage()
                : "TMDB lookup failed.");
    }

    private void setBusy(boolean busy) {
        loading.setVisible(busy);
        loading.setManaged(busy);
        searchButton.setDisable(busy);
        queryField.setDisable(busy);
        yearField.setDisable(busy);
        tmdbIdField.setDisable(busy);
        searchButton.setText(busy ? UiText.tmdbSearching(language) : UiText.tmdbSearch(language));
    }

    private void clearResultsWhenCriteriaAreEmpty() {
        if ((queryField.getText() == null || queryField.getText().isBlank())
                && (yearField.getText() == null || yearField.getText().isBlank())
                && (tmdbIdField.getText() == null || tmdbIdField.getText().isBlank())) {
            results.getItems().clear();
            setEmptyState(UiText.tmdbInitialSearchHint(language), "");
            setMessage("");
        }
    }

    private static TextFormatter<String> digitsOnly(int maximumLength) {
        return new TextFormatter<>(change -> change.getControlNewText().matches("\\d{0," + maximumLength + "}")
                ? change
                : null);
    }

    private VBox emptyState(String title, String hint) {
        emptyTitle.getStyleClass().add("tmdb-empty-title");
        emptyHint.getStyleClass().add("tmdb-empty-hint");
        emptyHint.setWrapText(true);
        VBox box = new VBox(5, emptyTitle, emptyHint);
        box.getStyleClass().add("tmdb-empty-state");
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

    private void choose(TmdbCandidate candidate) {
        if (candidate == null) {
            return;
        }
        selected = Optional.of(candidate);
        stage.close();
    }

    private final class ResultCell extends ListCell<TmdbCandidate> {
        @Override
        protected void updateItem(TmdbCandidate item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            setGraphic(resultCard(item));
        }

        private Region resultCard(TmdbCandidate item) {
            ImageView poster = new ImageView();
            poster.setFitWidth(82);
            poster.setFitHeight(122);
            poster.setPreserveRatio(true);
            poster.setVisible(false);
            Label placeholder = new Label("TMDB");
            placeholder.getStyleClass().add("tmdb-poster-placeholder");
            StackPane posterFrame = new StackPane(placeholder, poster);
            posterFrame.getStyleClass().add("tmdb-poster-frame");
            item.posterUrl().ifPresent(url -> {
                Image image = new Image(url, true);
                image.errorProperty().addListener((obs, was, failed) -> {
                    if (failed) {
                        System.getLogger(TmdbManualMatchDialog.class.getName())
                                .log(System.Logger.Level.DEBUG, "TMDB poster failed to load: " + url);
                        poster.setImage(null);
                        poster.setVisible(false);
                    }
                });
                poster.setImage(image);
                poster.setVisible(true);
            });

            Label title = new Label(item.identity().displayName());
            title.getStyleClass().add("tmdb-match-title");
            Label meta = new Label(ScanRowText.mediaType(item.identity().mediaType(), language)
                    + item.year().map(year -> " • " + year).orElse(""));
            meta.getStyleClass().add("tmdb-match-meta");
            Label id = new Label(UiText.tmdbIdLabel(language) + ": " + item.identity().id());
            id.getStyleClass().add("tmdb-match-meta");
            Label overview = new Label(shortOverview(localizedOverview(item)));
            overview.getStyleClass().add("tmdb-match-overview");
            overview.setWrapText(true);
            overview.setMaxHeight(38);
            overview.setMinHeight(Region.USE_PREF_SIZE);

            Button select = new Button(UiText.tmdbSelect(language));
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
            action.getStyleClass().add("tmdb-result-action");
            action.setMinWidth(140);
            action.setPrefWidth(140);
            action.setMaxWidth(140);
            HBox row = new HBox(12, posterFrame, text, action);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setMaxWidth(Double.MAX_VALUE);
            row.getStyleClass().add("tmdb-result-card");
            return row;
        }

    }

    private static String shortOverview(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 180) {
            return normalized;
        }
        return normalized.substring(0, 179).stripTrailing() + "…";
    }

    private String localizedOverview(TmdbCandidate item) {
        Optional<String> primary = language == AppLanguage.FRENCH ? item.frenchOverview() : item.englishOverview();
        Optional<String> secondary = language == AppLanguage.FRENCH ? item.englishOverview() : item.frenchOverview();
        return primary
                .or(() -> secondary)
                .or(item::overview)
                .filter(value -> !value.isBlank())
                .orElseGet(() -> UiText.tmdbNoDescription(language));
    }
}

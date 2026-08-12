package com.episort.ui.scan;

import com.episort.ui.AppLanguage;
import com.episort.ui.HorizontalScrollTable;
import com.episort.ui.UiText;
import java.util.List;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.util.StringConverter;

/**
 * The fifteen columns of the scan table: their widths, editability, cell
 * rendering and edit-commit wiring.
 *
 * <p>Split out of the scan screen because column plumbing dominated it by
 * volume while saying nothing about the scan workflow. Every user gesture is
 * forwarded to a {@link Host} rather than acted on here — this class decides how
 * a row looks, never what an edit means.
 */
final class ScanTableColumns {

    /** What the columns need from the screen that owns them. */
    interface Host {
        AppLanguage language();

        /** The group label for a row, or the placeholder. */
        String groupDisplayName(ScanRow row);

        /** Whether the row's group is one that never carries an identity. */
        boolean groupIsIgnored(ScanRow row);

        /**
         * Whether the row at this index in the sorted, filtered view opens a
         * group block, i.e. the row above belongs to another group.
         *
         * <p>Single definition of the break: the cell writes the name on it and
         * the row draws its rule from it, so the hairline and the name can never
         * land on two different rows.
         */
        boolean startsGroupBlock(int viewIndex);

        void onSelectAllToggled(boolean selected);

        void onRowCheckboxToggled(ScanRow row, boolean selected);

        void onSelectionCellPressed(ScanRow row, MouseEvent event);

        void onProposedNameEdited(ScanRow row, String value);

        void onTokenEdited(ScanRow row, ScanInputRole role, String value);

        void onOrderEdited(ScanRow row, String value);

        void onMediaTypeChanged(ScanRow row, ScanMediaType mediaType);
    }

    private final Host host;

    private final CheckBox selectAllCheckbox = new CheckBox();
    private final TableColumn<ScanRow, Boolean> selection = new TableColumn<>();
    private final TableColumn<ScanRow, String> original = new TableColumn<>();
    private final TableColumn<ScanRow, String> arrow = new TableColumn<>();
    private final TableColumn<ScanRow, String> proposed = new TableColumn<>();
    private final TableColumn<ScanRow, String> group = new TableColumn<>();
    private final TableColumn<ScanRow, String> series = new TableColumn<>();
    private final TableColumn<ScanRow, String> season = new TableColumn<>();
    private final TableColumn<ScanRow, String> episode = new TableColumn<>();
    private final TableColumn<ScanRow, String> title = new TableColumn<>();
    private final TableColumn<ScanRow, String> year = new TableColumn<>();
    private final TableColumn<ScanRow, String> extension = new TableColumn<>();
    private final TableColumn<ScanRow, ScanMediaType> type = new TableColumn<>();
    private final TableColumn<ScanRow, String> order = new TableColumn<>();
    private final TableColumn<ScanRow, String> confidence = new TableColumn<>();
    private final TableColumn<ScanRow, ScanRowStatus> status = new TableColumn<>();

    ScanTableColumns(Host host) {
        this.host = host;
        configureSelection();
        configureOriginal();
        configureArrow();
        configureProposed();
        configureGroup();
        configureRole(series, ScanInputRole.SERIES, 170);
        configureRole(season, ScanInputRole.SEASON, 70);
        configureRole(episode, ScanInputRole.EPISODE, 78);
        configureRole(title, ScanInputRole.TITLE, 180);
        configureRole(year, ScanInputRole.YEAR, 70);
        configureExtension();
        configureType();
        configureOrder();
        configureConfidence();
        configureStatus();
    }

    /**
     * Installs the columns and keeps their readable widths at every resolution:
     * the table scrolls horizontally when it does not fit, and the naming
     * columns absorb the surplus when it does.
     */
    void installOn(TableView<ScanRow> table) {
        table.getColumns().setAll(
                selection, original, arrow, proposed, group, series, season, episode,
                title, year, extension, type, order, confidence, status);
        HorizontalScrollTable.install(table, List.of(original, proposed, series, title));
    }

    void applyLanguage(AppLanguage language) {
        selection.setText(UiText.scanColumnSelection(language));
        original.setText(UiText.scanColumnOriginal(language));
        arrow.setText("→");
        proposed.setText(UiText.scanColumnProposed(language));
        group.setText(UiText.scanColumnGroup(language));
        series.setText(UiText.scanColumnSeries(language));
        season.setText(UiText.scanColumnSeason(language));
        episode.setText(UiText.scanColumnEpisode(language));
        title.setText(UiText.scanColumnTitle(language));
        year.setText(UiText.scanColumnYear(language));
        extension.setText(UiText.scanColumnExtension(language));
        type.setText(UiText.scanColumnType(language));
        order.setText(UiText.scanColumnOrder(language));
        confidence.setText(UiText.scanColumnConfidence(language));
        status.setText(UiText.scanColumnStatus(language));
    }

    /**
     * Reflects whether every selectable visible row is checked, and disables the
     * header box when there is nothing to check.
     */
    void updateSelectAll(boolean hasSelectableRows, boolean allSelected) {
        selectAllCheckbox.setDisable(!hasSelectableRows);
        selectAllCheckbox.setSelected(allSelected);
    }

    private void configureSelection() {
        selection.setMinWidth(46);
        selection.setPrefWidth(46);
        selection.setMaxWidth(60);
        selection.setEditable(false);
        selection.setSortable(false);
        selection.setReorderable(false);
        selection.getStyleClass().add("selection-column");
        selectAllCheckbox.getStyleClass().add("row-checkbox");
        selectAllCheckbox.setOnAction(event -> host.onSelectAllToggled(selectAllCheckbox.isSelected()));
        StackPane header = new StackPane(selectAllCheckbox);
        header.getStyleClass().add("selection-header");
        selection.setGraphic(header);
        selection.setCellValueFactory(data -> data.getValue().selectedProperty().asObject());
        selection.setCellFactory(column -> new TableCell<>() {
            private final CheckBox checkBox = new CheckBox();
            private ScanRow boundRow;

            {
                checkBox.getStyleClass().add("row-checkbox");
                // The cell owns the click so shift-range selection works; the box
                // itself must not swallow it.
                checkBox.setMouseTransparent(true);
                checkBox.setOnAction(event -> {
                    if (boundRow != null) {
                        host.onRowCheckboxToggled(boundRow, checkBox.isSelected());
                    }
                });
                setAlignment(Pos.CENTER);
                getStyleClass().add("selection-cell");
                addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                    if (!isEmpty() && event.getButton() == MouseButton.PRIMARY) {
                        if (boundRow != null) {
                            host.onSelectionCellPressed(boundRow, event);
                        }
                        event.consume();
                    }
                });
                setOnMouseClicked(MouseEvent::consume);
            }

            @Override
            protected void updateItem(Boolean value, boolean empty) {
                super.updateItem(value, empty);
                ScanRow row = empty || getTableRow() == null ? null : getTableRow().getItem();
                boundRow = row;
                if (row == null) {
                    setGraphic(null);
                    return;
                }
                checkBox.setSelected(row.isSelected());
                checkBox.setDisable(row.isIgnored());
                setGraphic(checkBox);
            }
        });
    }

    private void configureOriginal() {
        original.setMinWidth(280);
        original.setPrefWidth(320);
        original.setEditable(false);
        original.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().originalFilename()));
        original.setCellFactory(ScanTableCells.monoEllipsis());
        original.setComparator(ScanRowTableSupport.NATURAL_TEXT);
    }

    /** A fixed separator between the before and after names. */
    private void configureArrow() {
        arrow.setMinWidth(34);
        arrow.setPrefWidth(38);
        arrow.setMaxWidth(44);
        arrow.setEditable(false);
        arrow.setSortable(false);
        arrow.setReorderable(false);
        arrow.setCellValueFactory(data -> new SimpleStringProperty("→"));
        arrow.setCellFactory(column -> new TableCell<>() {
            {
                getStyleClass().add("before-after-arrow-cell");
                setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
            }
        });
    }

    private void configureProposed() {
        proposed.setMinWidth(280);
        proposed.setPrefWidth(320);
        proposed.setEditable(true);
        proposed.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().proposedFilename().orElse(UiText.EMPTY)));
        proposed.setCellFactory(column -> {
            CommitOnFocusLossStringCell<ScanRow> cell = new CommitOnFocusLossStringCell<>();
            cell.getStyleClass().add("proposed-name-cell");
            return cell;
        });
        proposed.setOnEditCommit(event ->
                host.onProposedNameEdited(event.getRowValue(), event.getNewValue()));
        proposed.setComparator(ScanRowTableSupport.NATURAL_TEXT);
    }

    /**
     * One editable column per parse role, showing what the parser tagged.
     *
     * <p>The tooltip carries the whole parse — summary, positions and source —
     * because a single token out of context does not explain why the proposed
     * name looks the way it does.
     */
    private void configureRole(TableColumn<ScanRow, String> column, ScanInputRole role, double prefWidth) {
        column.setMinWidth(60);
        column.setPrefWidth(prefWidth);
        column.setEditable(true);
        column.setCellValueFactory(data ->
                new SimpleStringProperty(ScanRowEditor.roleValue(data.getValue(), role)));
        column.setCellFactory(ignored -> new CommitOnFocusLossStringCell<>() {
            @Override
            public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                    getStyleClass().removeAll("cell-mono", "cell-muted");
                    return;
                }
                String display = item.isBlank() ? UiText.EMPTY : item;
                setText(display);
                if (!getStyleClass().contains("cell-mono")) {
                    getStyleClass().add("cell-mono");
                }
                if (UiText.EMPTY.equals(display)) {
                    if (!getStyleClass().contains("cell-muted")) {
                        getStyleClass().add("cell-muted");
                    }
                    setTooltip(null);
                } else {
                    getStyleClass().remove("cell-muted");
                    ScanRow row = getTableRow() == null ? null : getTableRow().getItem();
                    setTooltip(ScanTableCells.wideTooltip(
                            row == null ? display : ScanRowEditor.patternTooltip(row), 520));
                }
            }
        });
        column.setOnEditCommit(event ->
                host.onTokenEdited(event.getRowValue(), role, event.getNewValue()));
        column.setComparator(ScanRowTableSupport.NATURAL_TEXT);
    }

    /**
     * The group a row belongs to, written once at each change of group.
     *
     * <p>Two series inside one release folder used to be indistinguishable in
     * the table: every row read the same and a select-all applied one identity
     * to both. Repeating the name on all twenty-five files of a series solved
     * that by shouting louder than the filenames it was qualifying, so the name
     * is written only when it differs from the row above and the rest of the
     * block stays blank. The break in the column is the signal; there is no
     * colour and no marker to decode.
     *
     * <p>The break runs against the sorted, filtered view, so it holds under
     * any sort order: rows of one group that are no longer adjacent each carry
     * their own name again. The tooltip stays on every cell, blank ones
     * included, so a scrolled-away block header is never lost.
     */
    private void configureGroup() {
        group.setMinWidth(120);
        group.setPrefWidth(170);
        group.setEditable(false);
        group.setCellValueFactory(data -> new SimpleStringProperty(host.groupDisplayName(data.getValue())));
        group.setCellFactory(column -> new TableCell<>() {
            private final Label name = new Label();

            {
                name.getStyleClass().add("group-name");
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank() || UiText.EMPTY.equals(item)) {
                    setGraphic(null);
                    setTooltip(null);
                    return;
                }
                setTooltip(new Tooltip(item));
                if (!host.startsGroupBlock(getIndex())) {
                    setGraphic(null);
                    return;
                }
                ScanRow row = getTableRow() == null ? null : getTableRow().getItem();
                boolean ignored = row != null && host.groupIsIgnored(row);
                name.setText(item);
                name.getStyleClass().setAll("group-name", ignored ? "group-name-ignored" : "group-name-head");
                setGraphic(name);
            }
        });
    }

    /**
     * The file extension, as the mono value it is.
     *
     * <p>It used to wear a colour-coded badge, one hue per known container.
     * An extension is neither an exception nor a decision: the hue mapping was
     * a legend to memorise for no information, and a folder of one container
     * rendered as a column of identical capsules. It now reads like every other
     * system value in the table.
     */
    private void configureExtension() {
        extension.setMinWidth(60);
        extension.setPrefWidth(72);
        extension.setMaxWidth(96);
        extension.setEditable(false);
        extension.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().extension().isEmpty() ? UiText.EMPTY : data.getValue().extension()));
        extension.setCellFactory(ScanTableCells.monoEllipsis());
        extension.setComparator(ScanRowTableSupport.NATURAL_TEXT);
    }

    /** Series or movie, pickable inline: a wrong type derails the whole naming. */
    private void configureType() {
        type.setMinWidth(150);
        type.setPrefWidth(170);
        type.setEditable(false);
        type.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().mediaType()));
        type.setCellFactory(column -> new TableCell<>() {
            private final ComboBox<ScanMediaType> picker = new ComboBox<>();
            private boolean updating;

            {
                picker.getItems().setAll(ScanMediaType.SERIES, ScanMediaType.MOVIE);
                picker.setMaxWidth(Double.MAX_VALUE);
                picker.setConverter(mediaTypeConverter());
                picker.setOnAction(event -> {
                    if (updating) {
                        return;
                    }
                    ScanRow row = getTableRow() == null ? null : getTableRow().getItem();
                    ScanMediaType value = picker.getValue();
                    if (row != null && value != null && row.mediaType() != value) {
                        host.onMediaTypeChanged(row, value);
                    }
                });
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }

            @Override
            protected void updateItem(ScanMediaType item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                ScanRow row = getTableRow() == null ? null : getTableRow().getItem();
                updating = true;
                try {
                    picker.setConverter(mediaTypeConverter());
                    picker.setPromptText(ScanRowText.mediaType(item, host.language()));
                    picker.setValue(editablePickerValue(item));
                    picker.setDisable(isTypePickerDisabled(row));
                } finally {
                    updating = false;
                }
                setGraphic(picker);
            }
        });
        type.setComparator(ScanRowTableSupport.MEDIA_TYPE);
    }

    private void configureOrder() {
        order.setMinWidth(92);
        order.setPrefWidth(112);
        order.setEditable(true);
        order.setCellValueFactory(data -> new SimpleStringProperty(
                ScanTableCells.orderText(data.getValue().order().orElse(""), host.language())));
        order.setCellFactory(column -> {
            CommitOnFocusLossStringCell<ScanRow> cell = new CommitOnFocusLossStringCell<>();
            cell.getStyleClass().add("cell-mono");
            return cell;
        });
        order.setOnEditCommit(event -> host.onOrderEdited(event.getRowValue(), event.getNewValue()));
        order.setComparator(ScanRowTableSupport.NATURAL_TEXT);
    }

    private void configureConfidence() {
        confidence.setMinWidth(70);
        confidence.setPrefWidth(80);
        confidence.setEditable(false);
        confidence.setCellValueFactory(data ->
                new SimpleStringProperty(ScanTableCells.formatConfidence(data.getValue().confidence())));
        confidence.setCellFactory(ScanTableCells.monoEllipsis());
        confidence.setComparator(ScanRowTableSupport.CONFIDENCE_PERCENT);
    }

    /** The status pill, with the reasons behind it on hover. */
    private void configureStatus() {
        status.setMinWidth(96);
        status.setPrefWidth(112);
        status.setEditable(false);
        status.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().status()));
        status.setCellFactory(column -> new TableCell<>() {
            private final Label pill = new Label();

            {
                pill.getStyleClass().add("row-status");
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }

            @Override
            protected void updateItem(ScanRowStatus item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                pill.getStyleClass().setAll("row-status", ScanTableCells.statusStyle(item));
                pill.setText(ScanRowText.status(item, host.language()));
                ScanRow row = getTableRow() == null ? null : getTableRow().getItem();
                pill.setTooltip(row == null || row.statusReasons().isEmpty()
                        ? null
                        : ScanTableCells.wideTooltip(String.join("\n", row.statusReasons()), 420));
                setGraphic(pill);
            }
        });
        status.setComparator(ScanRowTableSupport.STATUS);
    }

    private StringConverter<ScanMediaType> mediaTypeConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(ScanMediaType type) {
                if (type == null) {
                    return "";
                }
                return switch (type) {
                    case SERIES -> UiText.scanMediaTypeSeries(host.language());
                    case MOVIE -> UiText.scanMediaTypeMovie(host.language());
                    case UNKNOWN, IGNORED -> "";
                };
            }

            @Override
            public ScanMediaType fromString(String value) {
                return UiText.scanMediaTypeMovie(host.language()).equals(value)
                        ? ScanMediaType.MOVIE
                        : ScanMediaType.SERIES;
            }
        };
    }

    /** Unknown and ignored are states to display, never aliases for Series. */
    static ScanMediaType editablePickerValue(ScanMediaType mediaType) {
        return mediaType == ScanMediaType.SERIES || mediaType == ScanMediaType.MOVIE
                ? mediaType
                : null;
    }

    static boolean isTypePickerDisabled(ScanRow row) {
        return row != null && row.isIgnored();
    }
}

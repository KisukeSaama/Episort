package com.episort.ui.scan;

import com.episort.ui.AppLanguage;
import com.episort.ui.TableSearchBox;
import com.episort.ui.UiText;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import javafx.geometry.Pos;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * The chip row above the scan table: media-kind filters on the left, row-status
 * filters on the right, and the search text coming from the top bar.
 *
 * <p>Owns the whole filter state and answers one question for the table:
 * {@link #test(ScanRow)}. The screen never reads which chip is active, so the
 * chips and the predicate cannot disagree.
 */
final class ScanFilterBar {

    private static final ScanRowFilter[] KIND_FILTERS = {
        ScanRowFilter.ALL, ScanRowFilter.MOVIES, ScanRowFilter.SERIES, ScanRowFilter.UNKNOWN,
    };

    private static final ScanRowStatusFilter[] STATUS_FILTERS = {
        ScanRowStatusFilter.ALL, ScanRowStatusFilter.TO_PROCESS, ScanRowStatusFilter.OK,
        ScanRowStatusFilter.TMDB, ScanRowStatusFilter.CONFLICTS, ScanRowStatusFilter.IGNORED,
        ScanRowStatusFilter.ALERTS,
    };

    private final HBox root = new HBox(8);
    private final ToggleGroup kindGroup = new ToggleGroup();
    private final ToggleGroup statusGroup = new ToggleGroup();
    private final Map<ScanRowFilter, ToggleButton> kindButtons = new EnumMap<>(ScanRowFilter.class);
    private final Map<ScanRowStatusFilter, ToggleButton> statusButtons =
            new EnumMap<>(ScanRowStatusFilter.class);
    private final Runnable onChanged;
    private final TableSearchBox searchBox;

    private ScanRowFilter activeKind = ScanRowFilter.ALL;
    private ScanRowStatusFilter activeStatus = ScanRowStatusFilter.ALL;
    private String searchQuery = "";

    ScanFilterBar(Runnable onChanged) {
        this.onChanged = onChanged;
        root.getStyleClass().add("scan-filter-bar");
        root.setAlignment(Pos.CENTER_LEFT);

        searchBox = new TableSearchBox(this::setSearchQuery);
        root.getChildren().add(searchBox.root());

        for (ScanRowFilter filter : KIND_FILTERS) {
            kindButtons.put(filter, chip(filter, kindGroup, root));
        }
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox statusChips = new HBox(8);
        statusChips.getStyleClass().add("scan-status-filter-group");
        for (ScanRowStatusFilter filter : STATUS_FILTERS) {
            statusButtons.put(filter, chip(filter, statusGroup, statusChips));
        }
        root.getChildren().addAll(spacer, statusChips);

        // Selected before the listeners are wired: notifying mid-construction
        // would call back into an owner that has not finished assigning this
        // object yet. The defaults already match what is selected here.
        kindGroup.selectToggle(kindButtons.get(ScanRowFilter.ALL));
        statusGroup.selectToggle(statusButtons.get(ScanRowStatusFilter.ALL));
        highlightActiveChips();

        // A toggle group allows deselection; the filter bar must always have one
        // chip active, so an empty selection snaps back to the current filter.
        kindGroup.selectedToggleProperty().addListener((observable, previous, current) -> {
            if (current == null) {
                kindGroup.selectToggle(kindButtons.get(activeKind));
                return;
            }
            activeKind = (ScanRowFilter) current.getUserData();
            refresh();
        });
        statusGroup.selectedToggleProperty().addListener((observable, previous, current) -> {
            if (current == null) {
                statusGroup.selectToggle(statusButtons.get(activeStatus));
                return;
            }
            activeStatus = (ScanRowStatusFilter) current.getUserData();
            // Alerts are worth seeing wherever they are: narrowing by media kind
            // at the same time would hide some of them without saying so.
            if (activeStatus == ScanRowStatusFilter.ALERTS && activeKind != ScanRowFilter.ALL) {
                activeKind = ScanRowFilter.ALL;
                kindGroup.selectToggle(kindButtons.get(ScanRowFilter.ALL));
            }
            refresh();
        });
    }

    HBox root() {
        return root;
    }

    void applyLanguage(AppLanguage language) {
        searchBox.applyLanguage(UiText.scanSearchPlaceholder(language), UiText.a11yClearSearch(language));
        kindButtons.get(ScanRowFilter.ALL).setText(UiText.scanFilterAll(language));
        kindButtons.get(ScanRowFilter.MOVIES).setText(UiText.scanFilterMovies(language));
        kindButtons.get(ScanRowFilter.SERIES).setText(UiText.scanFilterSeries(language));
        kindButtons.get(ScanRowFilter.UNKNOWN).setText(UiText.scanFilterUnknown(language));
        statusButtons.get(ScanRowStatusFilter.ALL).setText(UiText.scanStatusFilterAll(language));
        statusButtons.get(ScanRowStatusFilter.TO_PROCESS).setText(UiText.scanStatusFilterToProcess(language));
        statusButtons.get(ScanRowStatusFilter.OK).setText(UiText.scanStatusFilterOk(language));
        statusButtons.get(ScanRowStatusFilter.TMDB).setText(UiText.scanStatusFilterTmdb(language));
        statusButtons.get(ScanRowStatusFilter.CONFLICTS).setText(UiText.scanStatusFilterConflicts(language));
        statusButtons.get(ScanRowStatusFilter.IGNORED).setText(UiText.scanStatusFilterIgnored(language));
        statusButtons.get(ScanRowStatusFilter.ALERTS).setText(UiText.scanStatusFilterAlerts(language));
    }

    /** Blank means "no text filter". */
    private void setSearchQuery(String query) {
        searchQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        refresh();
    }

    /** Puts the chips and the search back where a fresh session starts. */
    void reset() {
        activeKind = ScanRowFilter.ALL;
        activeStatus = ScanRowStatusFilter.ALL;
        searchQuery = "";
        searchBox.clear();
        kindGroup.selectToggle(kindButtons.get(ScanRowFilter.ALL));
        statusGroup.selectToggle(statusButtons.get(ScanRowStatusFilter.ALL));
        refresh();
    }

    /** Whether a row survives the search text and both active chips. */
    boolean test(ScanRow row) {
        return matchesSearch(row)
                && ScanRowTableSupport.matchesFilter(row, activeKind)
                && ScanRowTableSupport.matchesStatusFilter(row, activeStatus);
    }

    private boolean matchesSearch(ScanRow row) {
        if (searchQuery.isBlank()) {
            return true;
        }
        return row.originalFilename().toLowerCase(Locale.ROOT).contains(searchQuery)
                || row.extension().toLowerCase(Locale.ROOT).contains(searchQuery);
    }

    private void refresh() {
        highlightActiveChips();
        onChanged.run();
    }

    private void highlightActiveChips() {
        kindButtons.forEach((filter, button) -> markActive(button, filter == activeKind));
        statusButtons.forEach((filter, button) -> markActive(button, filter == activeStatus));
    }

    private static void markActive(ToggleButton button, boolean active) {
        button.getStyleClass().remove("active");
        if (active) {
            button.getStyleClass().add("active");
        }
    }

    private static ToggleButton chip(Object filter, ToggleGroup group, HBox host) {
        ToggleButton button = new ToggleButton();
        button.setUserData(filter);
        button.setToggleGroup(group);
        button.getStyleClass().add("filter-chip");
        host.getChildren().add(button);
        return button;
    }
}

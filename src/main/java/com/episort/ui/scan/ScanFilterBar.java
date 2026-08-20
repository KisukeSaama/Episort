package com.episort.ui.scan;

import com.episort.ui.AppLanguage;
import com.episort.ui.TableSearchBox;
import com.episort.ui.UiText;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;

/**
 * The chip row above the scan table: media-kind filters on the left, row-status
 * filters on the right, and the search field it lends to the screen heading.
 *
 * <p>Owns the whole filter state and answers one question for the table:
 * {@link #test(ScanRow)}. The screen never reads which chip is active, so the
 * chips and the predicate cannot disagree.
 */
final class ScanFilterBar {

    /**
     * What moved the filter. The predicate is rebuilt either way; the screen
     * needs the distinction because a chip replaces the list in one gesture,
     * while typing narrows it letter by letter and must not be treated as a
     * series of replacements.
     */
    enum Change {
        CHIPS,
        SEARCH,
    }

    private static final ScanRowFilter[] KIND_FILTERS = {
        ScanRowFilter.ALL, ScanRowFilter.MOVIES, ScanRowFilter.SERIES, ScanRowFilter.UNKNOWN,
    };

    private static final ScanRowStatusFilter[] STATUS_FILTERS = {
        ScanRowStatusFilter.ALL, ScanRowStatusFilter.TO_PROCESS, ScanRowStatusFilter.OK,
        ScanRowStatusFilter.TMDB, ScanRowStatusFilter.CONFLICTS, ScanRowStatusFilter.IGNORED,
        ScanRowStatusFilter.ALERTS,
    };

    /**
     * A FlowPane, like the History filter row (§4.13). An HBox held four kind
     * chips, a growing spacer and seven status chips on one line: past about
     * 1030 px of content width there was no line left, and the chips at the end
     * were squeezed until their labels truncated. Wrapping costs a row of
     * height only when there is no width to spend instead.
     *
     * <p>The eleven chips need about 850 px. The bar is laid out across the
     * full screen width rather than inside the table column, so that budget
     * holds on one row and the wrap only comes back on a genuinely narrow
     * window.
     *
     * <p>The 16 px hgap is what separates the two groups. It is the pane's own
     * gap rather than a padding on the second group, because a padding also
     * applies on the row a wrap puts it on, and offset the status chips from
     * the kind chips right above them.
     */
    private final FlowPane root = new FlowPane(16, 8);
    private final HBox kindChips = new HBox(8);
    private final HBox statusChips = new HBox(8);
    private final ToggleGroup kindGroup = new ToggleGroup();
    private final ToggleGroup statusGroup = new ToggleGroup();
    private final Map<ScanRowFilter, ToggleButton> kindButtons = new EnumMap<>(ScanRowFilter.class);
    private final Map<ScanRowStatusFilter, ToggleButton> statusButtons =
            new EnumMap<>(ScanRowStatusFilter.class);
    private final Consumer<Change> onChanged;
    private final TableSearchBox searchBox;

    private ScanRowFilter activeKind = ScanRowFilter.ALL;
    private ScanRowStatusFilter activeStatus = ScanRowStatusFilter.ALL;
    private String searchQuery = "";

    ScanFilterBar(Consumer<Change> onChanged) {
        this.onChanged = onChanged;
        root.getStyleClass().add("scan-filter-bar");
        root.setAlignment(Pos.CENTER_LEFT);

        searchBox = new TableSearchBox(this::setSearchQuery);

        kindChips.setAlignment(Pos.CENTER_LEFT);
        kindChips.getStyleClass().add("scan-kind-filter-group");
        for (ScanRowFilter filter : KIND_FILTERS) {
            kindButtons.put(filter, chip(filter, kindGroup, kindChips));
        }
        statusChips.setAlignment(Pos.CENTER_LEFT);
        statusChips.getStyleClass().add("scan-status-filter-group");
        for (ScanRowStatusFilter filter : STATUS_FILTERS) {
            statusButtons.put(filter, chip(filter, statusGroup, statusChips));
        }
        // Two groups, so a wrap breaks between clusters and never inside one:
        // four kind chips split across two rows would read as two questions.
        // The search box is not one of them; it hangs off the screen heading,
        // see searchRoot().
        root.getChildren().addAll(kindChips, statusChips);

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
            refresh(Change.CHIPS);
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
            refresh(Change.CHIPS);
        });
    }

    FlowPane root() {
        return root;
    }

    /**
     * The search field, for the screen to place on its heading row.
     *
     * <p>It costs some 290 px of the same line as the chips, which is exactly
     * what the eleven chips were missing to hold on one row. It stays owned
     * here because it feeds the same predicate as the chips, and a filter split
     * across two owners is a filter that can disagree with itself.
     */
    HBox searchRoot() {
        return searchBox.root();
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
        refresh(Change.SEARCH);
    }

    /** Puts the chips and the search back where a fresh session starts. */
    void reset() {
        activeKind = ScanRowFilter.ALL;
        activeStatus = ScanRowStatusFilter.ALL;
        searchQuery = "";
        searchBox.clear();
        kindGroup.selectToggle(kindButtons.get(ScanRowFilter.ALL));
        statusGroup.selectToggle(statusButtons.get(ScanRowStatusFilter.ALL));
        refresh(Change.CHIPS);
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

    private void refresh(Change change) {
        highlightActiveChips();
        onChanged.accept(change);
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

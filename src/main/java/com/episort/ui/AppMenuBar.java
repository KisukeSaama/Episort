package com.episort.ui;

import java.util.Objects;
import java.util.function.Consumer;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.Region;

/**
 * The classic File / Analysis / View / Help menus. They carry the actions that
 * used to sit as buttons in the top bar: the window is undecorated now, and the
 * row has to make room for the window controls without growing taller.
 *
 * <p>The one action that stays a button is reviewing the plan — it is the step
 * the whole screen exists to reach, so it keeps its place in the bar and is
 * merely mirrored here.
 */
public final class AppMenuBar {
    private final MenuBar root;

    private final Menu fileMenu;
    private final Menu analysisMenu;
    private final Menu viewMenu;
    private final Menu helpMenu;

    private final MenuItem loadFolderItem;
    private final MenuItem loadFilesItem;
    private final MenuItem addFolderItem;
    private final MenuItem addFilesItem;
    private final MenuItem quitItem;
    private final MenuItem resetItem;
    private final MenuItem rescanItem;
    private final MenuItem reviewPlanItem;
    private final CheckMenuItem scanViewItem;
    private final CheckMenuItem historyViewItem;
    private final CheckMenuItem settingsViewItem;
    private final MenuItem aboutItem;

    /** Each scan-only entry answers to its own precondition and to the view. */
    private boolean loadEnabled = true;
    private boolean appendEnabled = true;
    private boolean scanActionsAvailable = true;

    public AppMenuBar(TopBarActions actions) {
        Objects.requireNonNull(actions, "actions");

        loadFolderItem = item(actions.loadFolder(), new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN));
        loadFilesItem = item(actions.loadFiles(),
                new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        addFolderItem = item(actions.addFolder(), null);
        addFilesItem = item(actions.addFiles(), null);
        quitItem = item(actions.quit(), new KeyCodeCombination(KeyCode.Q, KeyCombination.CONTROL_DOWN));

        resetItem = item(actions.reset(), null);
        rescanItem = item(actions.rescan(), new KeyCodeCombination(KeyCode.F5));
        reviewPlanItem = item(actions.primaryAction(), new KeyCodeCombination(KeyCode.ENTER, KeyCombination.CONTROL_DOWN));

        Consumer<AppView> showView = actions.showView();
        scanViewItem = viewItem(AppView.SCAN, showView, new KeyCodeCombination(KeyCode.DIGIT1, KeyCombination.CONTROL_DOWN));
        historyViewItem = viewItem(AppView.HISTORY, showView, new KeyCodeCombination(KeyCode.DIGIT2, KeyCombination.CONTROL_DOWN));
        settingsViewItem = viewItem(AppView.SETTINGS, showView, new KeyCodeCombination(KeyCode.DIGIT3, KeyCombination.CONTROL_DOWN));

        aboutItem = item(actions.about(), null);

        fileMenu = new Menu();
        fileMenu.getItems().addAll(
                loadFolderItem,
                loadFilesItem,
                new SeparatorMenuItem(),
                addFolderItem,
                addFilesItem,
                new SeparatorMenuItem(),
                quitItem);

        analysisMenu = new Menu();
        analysisMenu.getItems().addAll(
                rescanItem,
                resetItem,
                new SeparatorMenuItem(),
                reviewPlanItem);

        viewMenu = new Menu();
        viewMenu.getItems().addAll(scanViewItem, historyViewItem, settingsViewItem);

        helpMenu = new Menu();
        helpMenu.getItems().add(aboutItem);

        root = new MenuBar(fileMenu, analysisMenu, viewMenu, helpMenu);
        root.getStyleClass().add("app-menu-bar");
        // The bar must not stretch: the workspace chip and the search box share
        // the row with it, and a greedy menu bar would push them off-centre.
        root.setMinWidth(Region.USE_PREF_SIZE);
        root.setMaxWidth(Region.USE_PREF_SIZE);

        applyLanguage(AppLanguage.FRENCH);
        setActiveView(AppView.SCAN);
    }

    public MenuBar root() {
        return root;
    }

    public void applyLanguage(AppLanguage language) {
        fileMenu.setText(UiText.menuFile(language));
        analysisMenu.setText(UiText.menuAnalysis(language));
        viewMenu.setText(UiText.menuView(language));
        helpMenu.setText(UiText.menuHelp(language));

        loadFolderItem.setText(UiText.topActionLoadFolder(language));
        loadFilesItem.setText(UiText.topActionLoadFiles(language));
        addFolderItem.setText(UiText.topActionAddFolder(language));
        addFilesItem.setText(UiText.topActionAddFiles(language));
        quitItem.setText(UiText.menuQuit(language));

        resetItem.setText(UiText.topActionReset(language));
        rescanItem.setText(UiText.topActionReanalyze(language));
        reviewPlanItem.setText(UiText.primaryActionReviewPlan(language));

        scanViewItem.setText(UiText.navScan(language));
        historyViewItem.setText(UiText.navHistory(language));
        settingsViewItem.setText(UiText.navSettings(language));

        aboutItem.setText(UiText.menuAbout(language));
    }

    public void setActiveView(AppView view) {
        scanViewItem.setSelected(view == AppView.SCAN);
        historyViewItem.setSelected(view == AppView.HISTORY);
        settingsViewItem.setSelected(view == AppView.SETTINGS);
    }

    public void setLoadEnabled(boolean enabled) {
        loadEnabled = enabled;
        refreshScanActions();
    }

    public void setAppendEnabled(boolean enabled) {
        appendEnabled = enabled;
        refreshScanActions();
    }

    public void setResetEnabled(boolean enabled) {
        resetItem.setDisable(!enabled);
    }

    public void setRescanEnabled(boolean enabled) {
        rescanItem.setDisable(!enabled);
    }

    public void setReviewPlanEnabled(boolean enabled) {
        reviewPlanItem.setDisable(!enabled);
    }

    /**
     * About takes over the content area, so it is refused while another surface
     * already holds it — the plan review, or About itself.
     */
    public void setAboutEnabled(boolean enabled) {
        aboutItem.setDisable(!enabled);
    }

    /**
     * Greys out the scan-only entries when another screen is showing.
     *
     * <p>The bar itself never changes. It used to drop the Analysis title and
     * the loading entries outside the scan screen, so the menus were a different
     * set of words on every view and had to be read again each time to find out
     * what was there. A menu is a map of the application, not of the screen: an
     * action the current view has no meaning for stays where it is, disabled,
     * which also says the action exists somewhere else.
     */
    public void setScanActionsAvailable(boolean available) {
        scanActionsAvailable = available;
        refreshScanActions();
    }

    /**
     * Two conditions, one state: an entry is live when its own precondition
     * holds <em>and</em> the scan screen is the one showing.
     */
    private void refreshScanActions() {
        loadFolderItem.setDisable(!(loadEnabled && scanActionsAvailable));
        loadFilesItem.setDisable(!(loadEnabled && scanActionsAvailable));
        addFolderItem.setDisable(!(appendEnabled && scanActionsAvailable));
        addFilesItem.setDisable(!(appendEnabled && scanActionsAvailable));
        analysisMenu.setDisable(!scanActionsAvailable);
    }

    private static MenuItem item(Runnable action, KeyCombination accelerator) {
        MenuItem menuItem = new MenuItem();
        menuItem.setOnAction(event -> action.run());
        if (accelerator != null) {
            menuItem.setAccelerator(accelerator);
        }
        return menuItem;
    }

    private static CheckMenuItem viewItem(AppView view, Consumer<AppView> showView, KeyCombination accelerator) {
        CheckMenuItem menuItem = new CheckMenuItem();
        menuItem.setAccelerator(accelerator);
        // A check item toggles itself on click; the shell is the single source
        // of truth for which view is showing, so the selection is re-applied
        // from setActiveView rather than trusted from the click.
        menuItem.setOnAction(event -> showView.accept(view));
        return menuItem;
    }
}

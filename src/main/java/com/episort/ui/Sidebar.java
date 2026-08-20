package com.episort.ui;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public final class Sidebar {
    private static final String STYLE_CLASS = "sidebar";
    private static final String COLLAPSED_CLASS = "collapsed";
    static final double EXPANDED_WIDTH = 230;
    /** Wide enough for a 17 px glyph in a 36 px row plus the rail's padding. */
    static final double COLLAPSED_WIDTH = 64;

    private final VBox root;
    private final HBox brandRow;
    private final HBox sectionHeading;
    private final Label brandName;
    private final Label sectionLabel;
    private final StorageUsageIndicator storageUsage;
    private final Map<AppView, NavItem> navItems = new EnumMap<>(AppView.class);
    private AppLanguage currentLanguage = AppLanguage.FRENCH;
    private boolean collapsed;

    public Sidebar(Image logo, Consumer<AppView> onSelect) {
        Objects.requireNonNull(onSelect, "onSelect");

        ImageView logoView = new ImageView(logo);
        logoView.setFitWidth(44);
        logoView.setFitHeight(44);
        logoView.setPreserveRatio(true);

        brandName = new Label("Episort");
        brandName.getStyleClass().add("sidebar-brand-name");

        this.brandRow = new HBox(10, logoView, brandName);
        brandRow.setAlignment(Pos.CENTER_LEFT);
        brandRow.getStyleClass().add("sidebar-brand");

        sectionLabel = new Label();
        sectionLabel.getStyleClass().add("sidebar-section-label");
        Region sectionMarker = new Region();
        sectionMarker.getStyleClass().add("sidebar-section-marker");
        this.sectionHeading = new HBox(8, sectionMarker, sectionLabel);
        sectionHeading.setAlignment(Pos.CENTER_LEFT);
        sectionHeading.getStyleClass().add("sidebar-section-heading");

        navItems.put(AppView.SCAN, new NavItem(AppView.SCAN, "⌕", onSelect));
        navItems.put(AppView.HISTORY, new NavItem(AppView.HISTORY, "↺", onSelect));
        navItems.put(AppView.SETTINGS, new NavItem(AppView.SETTINGS, "⚙", onSelect));
        storageUsage = new StorageUsageIndicator();

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        root = new VBox(2,
                brandRow,
                sectionHeading,
                navItems.get(AppView.SCAN).button(),
                navItems.get(AppView.HISTORY).button(),
                navItems.get(AppView.SETTINGS).button(),
                spacer,
                storageUsage.root());
        root.getStyleClass().add(STYLE_CLASS);
        root.setMinWidth(220);
        root.setPrefWidth(230);
        root.setMaxWidth(230);

        applyLanguage(AppLanguage.FRENCH);
        setActive(AppView.SCAN);
    }

    public Region root() {
        return root;
    }

    /**
     * Drops the rail to its glyphs when the window can no longer spare 230 px
     * for three words.
     *
     * <p>Nothing is removed: each nav item keeps the accessible text it
     * already had and gains the tooltip that stands in for the label it lost,
     * and the storage readout keeps reporting through the indicator's own
     * compact form. The brand name and the section heading go because a rail
     * this narrow has no room for a heading over three icons.
     */
    public void setCollapsed(boolean collapsed) {
        if (this.collapsed == collapsed) {
            return;
        }
        this.collapsed = collapsed;
        root.getStyleClass().remove(COLLAPSED_CLASS);
        if (collapsed) {
            root.getStyleClass().add(COLLAPSED_CLASS);
        }
        double width = collapsed ? COLLAPSED_WIDTH : EXPANDED_WIDTH;
        root.setMinWidth(width);
        root.setPrefWidth(width);
        root.setMaxWidth(width);
        show(brandName, !collapsed);
        show(sectionHeading, !collapsed);
        brandRow.setAlignment(collapsed ? Pos.CENTER : Pos.CENTER_LEFT);
        for (NavItem item : navItems.values()) {
            item.setCollapsed(collapsed);
        }
        storageUsage.setCollapsed(collapsed);
    }

    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    public void applyLanguage(AppLanguage language) {
        currentLanguage = language;
        sectionLabel.setText(UiText.sidebarSectionNavigation(language));
        navItems.get(AppView.SCAN).setLabel(UiText.navScan(language));
        navItems.get(AppView.HISTORY).setLabel(UiText.navHistory(language));
        navItems.get(AppView.SETTINGS).setLabel(UiText.navSettings(language));
        storageUsage.applyLanguage(language);
    }

    public void setWorkspace(Optional<Path> workspace) {
        storageUsage.setWorkspace(workspace);
    }

    public void refreshWorkspace() {
        storageUsage.refresh();
    }

    public void setActive(AppView view) {
        Objects.requireNonNull(view, "view");
        for (Map.Entry<AppView, NavItem> entry : navItems.entrySet()) {
            entry.getValue().setActive(entry.getKey() == view);
        }
    }

    private static final class NavItem {
        private final Button button;
        private final Label icon;
        private final Label label;
        private final HBox content;

        NavItem(AppView view, String iconText, Consumer<AppView> onSelect) {
            icon = new Label(iconText);
            icon.getStyleClass().add("nav-item-icon");
            label = new Label();
            label.getStyleClass().add("nav-item-label");
            content = new HBox(10, icon, label);
            content.setAlignment(Pos.CENTER_LEFT);
            button = new Button();
            button.setGraphic(content);
            button.setMaxWidth(Double.MAX_VALUE);
            button.getStyleClass().setAll("nav-item");
            button.setOnAction(event -> onSelect.accept(view));
        }

        Button button() {
            return button;
        }

        void setLabel(String text) {
            label.setText(text);
            // The button's own text is empty (the label lives in its graphic),
            // so without this a screen reader announces an unnamed button.
            button.setAccessibleText(text);
            // Installed whatever the width: collapsing must not be the moment a
            // control first acquires a name, or the name is only ever as fresh
            // as the last resize.
            button.setTooltip(new Tooltip(text));
        }

        void setCollapsed(boolean collapsed) {
            label.setVisible(!collapsed);
            label.setManaged(!collapsed);
            content.setAlignment(collapsed ? Pos.CENTER : Pos.CENTER_LEFT);
        }

        void setActive(boolean active) {
            if (active) {
                if (!button.getStyleClass().contains("active")) {
                    button.getStyleClass().add("active");
                }
            } else {
                button.getStyleClass().remove("active");
            }
        }
    }
}

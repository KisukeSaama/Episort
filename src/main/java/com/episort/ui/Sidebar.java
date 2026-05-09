package com.episort.ui;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public final class Sidebar {
    private static final String STYLE_CLASS = "sidebar";

    private final VBox root;
    private final Label brandName;
    private final Label sectionLabel;
    private final Map<AppView, NavItem> navItems = new EnumMap<>(AppView.class);
    private AppView activeView = AppView.SCAN;
    private AppLanguage currentLanguage = AppLanguage.FRENCH;

    public Sidebar(Image logo, Consumer<AppView> onSelect) {
        Objects.requireNonNull(onSelect, "onSelect");

        ImageView logoView = new ImageView(logo);
        logoView.setFitWidth(44);
        logoView.setFitHeight(44);
        logoView.setPreserveRatio(true);

        brandName = new Label("Episort");
        brandName.getStyleClass().add("sidebar-brand-name");

        HBox brandRow = new HBox(10, logoView, brandName);
        brandRow.setAlignment(Pos.CENTER_LEFT);
        brandRow.getStyleClass().add("sidebar-brand");

        sectionLabel = new Label();
        sectionLabel.getStyleClass().add("sidebar-section-label");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        navItems.put(AppView.SCAN, new NavItem(AppView.SCAN, "⌕", onSelect));
        navItems.put(AppView.HISTORY, new NavItem(AppView.HISTORY, "↺", onSelect));
        navItems.put(AppView.SETTINGS, new NavItem(AppView.SETTINGS, "⚙", onSelect));

        root = new VBox(2,
                brandRow,
                sectionLabel,
                navItems.get(AppView.SCAN).button(),
                navItems.get(AppView.HISTORY).button(),
                navItems.get(AppView.SETTINGS).button(),
                spacer);
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

    public void applyLanguage(AppLanguage language) {
        currentLanguage = language;
        sectionLabel.setText(language == AppLanguage.ENGLISH ? "// NAVIGATION" : "// NAVIGATION");
        navItems.get(AppView.SCAN).setLabel(UiText.navScan(language));
        navItems.get(AppView.HISTORY).setLabel(UiText.navHistory(language));
        navItems.get(AppView.SETTINGS).setLabel(UiText.navSettings(language));
    }

    public void setActive(AppView view) {
        Objects.requireNonNull(view, "view");
        activeView = view;
        for (Map.Entry<AppView, NavItem> entry : navItems.entrySet()) {
            entry.getValue().setActive(entry.getKey() == view);
        }
    }

    public AppView activeView() {
        return activeView;
    }

    public void setCompact(boolean compact) {
        // Compact mode dropped; sidebar always shows full text labels for clarity.
        // Method retained for backward compatibility with AppShell's responsive hook.
    }

    public boolean isCompact() {
        return false;
    }

    private static final class NavItem {
        private final Button button;
        private final Label icon;
        private final Label label;

        NavItem(AppView view, String iconText, Consumer<AppView> onSelect) {
            icon = new Label(iconText);
            icon.getStyleClass().add("nav-item-icon");
            label = new Label();
            label.getStyleClass().add("nav-item-label");
            HBox content = new HBox(10, icon, label);
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

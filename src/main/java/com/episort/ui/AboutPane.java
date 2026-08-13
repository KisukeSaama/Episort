package com.episort.ui;

import com.episort.BuildInfo;
import com.episort.persistence.FileExecutionJournal;
import com.episort.persistence.FileRunEventStore;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * What Episort is, what it does to a folder, and where it writes.
 *
 * <p>A screen in the content area rather than a modal: the About used to be a
 * native {@code Alert}, which the design system rules out outright — a Windows
 * dialog with a system icon and an OK button is the one surface in the app that
 * does not belong to it. It is reached from Help, and left by the same explicit
 * back button every full-frame surface carries.
 *
 * <p>The screen is two columns over a footer, not one long stack. On the left,
 * what Episort is and the run it performs, read top to bottom like prose. On the
 * right, one panel of machine facts — runtime, then the three files written to
 * the user profile — because a version string and a cache path are looked up,
 * not read. Attribution and credits are pinned to the bottom under a rule: they
 * are owed, they are not what the reader came for.
 *
 * <p>Nothing here is decoration. The pipeline is the workflow strip's own five
 * steps, so the description of the product and the trail the user follows cannot
 * tell two different stories. The version comes from the build stamp, the runtime
 * from the runtime, and the three paths are the actual files Episort writes — an
 * organiser that moves files owes the user the list of what it keeps and where.
 * Anything that cannot be read shows {@code —}.
 */
public final class AboutPane {
    /** The shell's own panel width: About's fact column is the detail panel column. */
    private static final double PANEL_WIDTH = 360;
    /** Prose caps around 70ch; past that a paragraph stops being one line of thought. */
    private static final double PROSE_WIDTH = 640;
    private static final double GUTTER = 30;
    private static final double CONTENT_WIDTH = PROSE_WIDTH + GUTTER + PANEL_WIDTH;
    /**
     * Below this the two columns cannot both hold their width, so they stack.
     * Content width plus the screen's own 30/30 padding, plus slack for the
     * scrollbar gutter the shell may add.
     */
    private static final double STACK_WIDTH = CONTENT_WIDTH + 90;
    /** Runtime names are short and known: they sit in a fixed gutter, values aligned. */
    private static final double FIELD_NAME_WIDTH = 74;

    private final VBox root;
    private final VBox narrative;
    private final VBox factPanel;
    private final Region attribution;
    private final Label credits;
    private final VBox bodyHost;
    private final VBox footerNotesHost;
    private final VBox footer;
    private Boolean stacked;

    public AboutPane(AppLanguage language, Runnable onBack) {
        Button back = new Button(UiText.aboutBack(language));
        back.getStyleClass().addAll("header-action", "back-action");
        back.setOnAction(event -> onBack.run());

        Label title = new Label(UiText.menuAbout(language));
        title.getStyleClass().add("tmdb-dialog-title");

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(10, back, title, headerSpacer);
        header.getStyleClass().add("tmdb-dialog-header");
        header.setAlignment(Pos.CENTER_LEFT);

        narrative = narrativeColumn(language);
        factPanel = factPanel(language);

        bodyHost = new VBox();
        bodyHost.getStyleClass().add("about-body-host");

        attribution = tmdbAttribution(UiText.aboutTmdbAttribution(language));
        credits = note(UiText.aboutFonts(language));
        footerNotesHost = new VBox();

        Region footerRule = new Region();
        footerRule.getStyleClass().add("settings-section-divider");
        VBox footerGroup = new VBox(10, heading(UiText.aboutSectionCredits(language)), footerNotesHost);
        footer = new VBox(14, footerRule, footerGroup);

        Region bottomSpacer = new Region();
        VBox.setVgrow(bottomSpacer, Priority.ALWAYS);

        root = new VBox(header, bodyHost, bottomSpacer, footer);
        root.getStyleClass().addAll("screen-root", "about-pane");

        // Width is 0 until the first layout pass; start stacked and let the
        // listener widen the screen once it knows how much room it has.
        applyStacked(true);
        root.widthProperty().addListener(
                (observable, previous, width) -> applyStacked(width.doubleValue() < STACK_WIDTH));
    }

    public Region root() {
        return root;
    }

    /**
     * Two columns when the content area can hold both, one when it cannot. The
     * columns keep their own measure either way: prose never runs past 70ch and
     * the fact panel never narrows below the shell's panel width.
     */
    private void applyStacked(boolean value) {
        if (stacked != null && stacked == value) {
            return;
        }
        stacked = value;
        double contentWidth = value ? PROSE_WIDTH : CONTENT_WIDTH;
        bodyHost.setMaxWidth(contentWidth);
        footer.setMaxWidth(contentWidth);
        factPanel.setMaxWidth(value ? PROSE_WIDTH : PANEL_WIDTH);
        credits.setMaxWidth(value ? PROSE_WIDTH : PANEL_WIDTH);
        bodyHost.getChildren().setAll(pair(value, narrative, factPanel));
        footerNotesHost.getChildren().setAll(pair(value, attribution, credits));
    }

    /** Side by side with the second column fixed, or stacked with both full width. */
    private static Pane pair(boolean stack, Region lead, Region trail) {
        if (stack) {
            return new VBox(18, lead, trail);
        }
        HBox row = new HBox(GUTTER, lead, trail);
        row.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(lead, Priority.ALWAYS);
        HBox.setHgrow(trail, Priority.NEVER);
        return row;
    }

    /**
     * Identity, then the one paragraph, then the run. Generous 26 between the
     * three so they read as three thoughts; the steps inside the run sit at 8,
     * because they are one thought with five parts.
     */
    private static VBox narrativeColumn(AppLanguage language) {
        ImageView logo = new ImageView(AppShell.logoImage());
        logo.setFitWidth(56);
        logo.setFitHeight(56);
        logo.setPreserveRatio(true);

        Label brand = new Label("Episort");
        brand.getStyleClass().add("about-brand-name");
        Label tagline = new Label(UiText.aboutTagline(language));
        tagline.getStyleClass().add("about-tagline");
        Label version = new Label(UiText.aboutVersionPrefix(language) + " "
                + BuildInfo.version().orElse(UiText.EMPTY));
        version.getStyleClass().add("about-version");

        VBox identityText = new VBox(4, brand, tagline, version);
        identityText.setAlignment(Pos.CENTER_LEFT);
        HBox identity = new HBox(16, logo, identityText);
        identity.setAlignment(Pos.CENTER_LEFT);

        Label body = new Label(UiText.aboutBody(language));
        body.getStyleClass().add("about-body");
        body.setWrapText(true);
        body.setMaxWidth(PROSE_WIDTH);

        VBox column = new VBox(26, identity, body, pipelineGroup(language));
        column.setMaxWidth(PROSE_WIDTH);
        return column;
    }

    /** The five steps of the run, read from the strip the user follows on screen. */
    private static VBox pipelineGroup(AppLanguage language) {
        VBox steps = new VBox(8);
        String[] labels = UiText.scanWorkflowSteps(language);
        for (int index = 0; index < labels.length; index++) {
            Label number = new Label(String.valueOf(index + 1));
            number.getStyleClass().add("about-step-index");
            Label label = new Label(labels[index]);
            label.getStyleClass().add("about-step-label");
            HBox row = new HBox(10, number, label);
            row.setAlignment(Pos.CENTER_LEFT);
            steps.getChildren().add(row);
        }
        return new VBox(10, heading(UiText.aboutSectionPipeline(language)), steps);
    }

    /**
     * One panel for everything read off the machine: the runtime it is running
     * on, then the files it writes to the user profile. Two groups rather than
     * two panels — they answer the same question, "what is on this computer".
     */
    private static VBox factPanel(AppLanguage language) {
        Region divider = new Region();
        divider.getStyleClass().add("settings-section-divider");

        VBox panel = new VBox(environmentGroup(language), divider, filesGroup(language));
        panel.getStyleClass().addAll("detail-panel", "about-fact-panel");
        panel.setPrefWidth(PANEL_WIDTH);
        panel.setMinWidth(PANEL_WIDTH);
        return panel;
    }

    private static VBox environmentGroup(AppLanguage language) {
        // No version row: it is already the line under the product name, and a
        // number written twice on one screen invites the two to disagree.
        VBox rows = new VBox(8,
                runtimeField(UiText.aboutFieldJava(language), Runtime.version().toString()),
                runtimeField(UiText.aboutFieldJavaFx(language), property("javafx.runtime.version")),
                runtimeField(UiText.aboutFieldSystem(language),
                        property("os.name") + " " + property("os.version")));
        return new VBox(10, heading(UiText.aboutSectionEnvironment(language)), rows);
    }

    /**
     * Where Episort writes. The paths are here because a tool that rearranges a
     * library should be able to say what it left behind on the machine.
     */
    private static VBox filesGroup(AppLanguage language) {
        VBox paths = new VBox(14,
                pathField(UiText.aboutPathRunHistory(language),
                        read(() -> FileRunEventStore.userProfileStore().logFilePath())),
                pathField(UiText.aboutPathJournal(language),
                        read(() -> FileExecutionJournal.userProfileJournal().location())));
        return new VBox(10, heading(UiText.aboutSectionData(language)), paths);
    }

    private static Label heading(String text) {
        Label label = new Label(text);
        label.getStyleClass().addAll("section-heading", "section-heading-accent");
        return label;
    }

    private static Label note(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("about-note");
        label.setWrapText(true);
        label.setMaxWidth(PROSE_WIDTH);
        return label;
    }

    private static Region tmdbAttribution(String text) {
        ImageView logo = new ImageView(new Image(java.util.Objects.requireNonNull(
                AboutPane.class.getResource("/assets/tmdb-logo-dark.png"),
                "Missing TMDB attribution logo").toExternalForm()));
        logo.setFitWidth(74);
        logo.setFitHeight(54);
        logo.setPreserveRatio(true);
        logo.setAccessibleText("TMDB");
        Label notice = note(text);
        HBox row = new HBox(14, logo, notice);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("tmdb-attribution");
        HBox.setHgrow(notice, Priority.ALWAYS);
        return row;
    }

    /** Short known values: name in a fixed gutter, values aligned down the panel. */
    private static HBox runtimeField(String name, String value) {
        Label caption = new Label(name);
        caption.getStyleClass().add("about-label");
        caption.setMinWidth(FIELD_NAME_WIDTH);
        caption.setPrefWidth(FIELD_NAME_WIDTH);
        Label text = new Label(value);
        text.getStyleClass().add("about-value");
        HBox row = new HBox(10, caption, text);
        row.setAlignment(Pos.BASELINE_LEFT);
        return row;
    }

    /** A path is too long for a gutter, so its name sits above it. */
    private static VBox pathField(String label, Optional<Path> path) {
        Label caption = new Label(label);
        caption.getStyleClass().add("about-label");
        String text = path.map(Path::toString).orElse(UiText.EMPTY);
        Label value = new Label(text);
        value.getStyleClass().addAll("about-value", "about-value-path");
        value.setWrapText(true);
        if (path.isPresent()) {
            value.setTooltip(new Tooltip(text));
        }
        return new VBox(2, caption, value);
    }

    /**
     * A location Episort may not be able to resolve — no writable user profile, no
     * settings file yet — is a fact, not a failure to hide behind an exception.
     */
    private static Optional<Path> read(Supplier<Path> supplier) {
        try {
            return Optional.ofNullable(supplier.get());
        } catch (RuntimeException unavailable) {
            return Optional.empty();
        }
    }

    private static String property(String key) {
        String value = System.getProperty(key);
        return value == null || value.isBlank() ? UiText.EMPTY : value;
    }
}

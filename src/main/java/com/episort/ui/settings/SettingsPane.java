package com.episort.ui.settings;

import com.episort.config.SftpAuthentication;
import com.episort.config.SftpEndpoint;
import com.episort.config.WorkspaceLocation;
import com.episort.config.WorkspaceLocation.RemoteWorkspace;
import com.episort.filesystem.RemoteWorkspaceException;
import com.episort.ui.AppLanguage;
import com.episort.ui.AppShellViewModel;
import com.episort.ui.RemotePathPicker;
import com.episort.ui.UiText;
import com.episort.ui.ThemePreference;
import com.episort.workflow.ApplicationError;
import com.episort.workflow.ErrorSeverity;
import com.episort.workflow.TmdbGatewayStatus;
import com.episort.workflow.WorkspaceConfigurationService;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.StringConverter;

public final class SettingsPane {
    static final String TMDB_ATTRIBUTION_URL = "https://www.themoviedb.org";
    private static final String TMDB_LOGO_RESOURCE = "/assets/tmdb-logo-dark.png";

    private enum WorkspaceKind { LOCAL, REMOTE }

    private enum AuthKind { PASSWORD, KEY }

    private final VBox root;
    private final Label heading;
    private final Label pageSubtitle;
    private final Label workspaceTitle;
    private final Label workspaceDescription;
    private final Label workspaceValue;
    private final Label preferencesTitle;
    private final Label preferencesDescription;
    private final Label tmdbTitle;
    private final Label tmdbDescription;
    private final Label tmdbStatus;
    private final Label tmdbAttribution;
    private final Hyperlink tmdbAttributionLink;
    private final Region tmdbStatusDot;
    private final Button chooseWorkspace;
    private final ComboBox<AppLanguage> languageCombo;
    private final ComboBox<ThemePreference> themeCombo;
    private final Supplier<Optional<Path>> currentWorkspace;
    private final TmdbGatewayStatus tmdbConfiguration;
    private final Consumer<AppShellViewModel> onConfigured;
    @SuppressWarnings("unused")
    private final Runnable onClose;
    private AppLanguage currentLanguage = AppLanguage.FRENCH;
    private final List<Consumer<AppLanguage>> extraSectionLanguageHooks = new ArrayList<>();

    // ---- Remote workspace form ---------------------------------------
    private final Label kindCaption;
    private final ComboBox<WorkspaceKind> kindCombo;
    private final HBox localForm;
    private final VBox remoteForm;
    private final Label hostCaption = caption();
    private final Label portCaption = caption();
    private final Label usernameCaption = caption();
    private final Label authCaption = caption();
    private final Label passwordCaption = caption();
    private final Label keyFileCaption = caption();
    private final Label passphraseCaption = caption();
    private final Label rootCaption = caption();
    private final TextField hostField = new TextField();
    private final TextField portField = new TextField(String.valueOf(SftpEndpoint.DEFAULT_PORT));
    private final TextField usernameField = new TextField();
    private final ComboBox<AuthKind> authCombo = new ComboBox<>(FXCollections.observableArrayList(AuthKind.values()));
    private final PasswordField passwordField = new PasswordField();
    private final TextField keyFileField = new TextField();
    private final PasswordField passphraseField = new PasswordField();
    private final TextField rootField = new TextField();
    private final Button browseKey = new Button();
    private final Button browseRoot = new Button();
    private final Button connect = new Button();
    private final Button disconnect = new Button();
    private final Region remoteStatusDot = new Region();
    private final Label remoteStatus = new Label();
    private final VBox passwordBox;
    private final HBox keyBox;
    private RemoteWorkspaceSupport remoteSupport;
    private boolean remoteBusy;

    public SettingsPane(
            Function<Path, AppShellViewModel> configureWorkspace,
            Supplier<Optional<Path>> currentWorkspace,
            Consumer<AppLanguage> onLanguageChange,
            ThemePreference themePreference,
            Consumer<ThemePreference> onThemePreferenceChange,
            TmdbGatewayStatus tmdbConfiguration,
            Consumer<String> openExternalLink,
            Runnable onClose,
            Consumer<AppShellViewModel> onConfigured) {
        this.currentWorkspace = currentWorkspace;
        this.tmdbConfiguration = tmdbConfiguration;
        this.onClose = onClose;
        this.onConfigured = onConfigured;

        // Same header as Scan and History: the accented section heading, then a
        // single line of context under it. A second, larger title used to sit
        // between the two, which pushed the first section of this screen about
        // fifty pixels below where the other screens put theirs; moving between
        // the three made the content jump.
        heading = new Label();
        heading.getStyleClass().addAll("section-heading", "section-heading-accent");
        HBox headingRow = new HBox(heading);
        headingRow.getStyleClass().add("screen-heading-row");

        pageSubtitle = new Label();
        pageSubtitle.getStyleClass().add("page-subtitle");
        pageSubtitle.setWrapText(true);

        // The heading row already carries six pixels of air under the label, so
        // the gap to the subtitle is set from what the eye sees, not from the
        // ten-pixel step.
        VBox header = new VBox(4, headingRow, pageSubtitle);

        // ---- Workspace section -------------------------------------
        workspaceTitle = new Label();
        workspaceTitle.getStyleClass().add("settings-section-title");

        workspaceDescription = new Label();
        workspaceDescription.getStyleClass().add("settings-section-description");
        workspaceDescription.setWrapText(true);

        workspaceValue = new Label();
        workspaceValue.getStyleClass().add("workspace-value");
        workspaceValue.setWrapText(true);

        chooseWorkspace = new Button();
        chooseWorkspace.setOnAction(event -> {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle(UiText.chooserWorkspaceTitle(currentLanguage));
            currentWorkspace.get()
                    .filter(path -> path.getFileSystem() == java.nio.file.FileSystems.getDefault())
                    .ifPresent(path -> {
                        File initial = path.toFile();
                        if (initial.isDirectory()) {
                            directoryChooser.setInitialDirectory(initial);
                        }
                    });
            File selectedDirectory = directoryChooser.showDialog(ownerWindow());
            if (selectedDirectory != null) {
                onConfigured.accept(configureWorkspace.apply(selectedDirectory.toPath()));
                refreshWorkspaceValue(currentWorkspace.get());
                refreshRemoteStatus();
            }
        });

        localForm = new HBox(12, chooseWorkspace, workspaceValue);
        localForm.setAlignment(Pos.CENTER_LEFT);
        localForm.getStyleClass().add("settings-row");
        HBox.setHgrow(workspaceValue, Priority.ALWAYS);

        // The kind of workspace is a choice between two places, and until a
        // server is available to the pane the second one is not offered at all.
        kindCaption = caption();
        kindCombo = new ComboBox<>(FXCollections.observableArrayList(WorkspaceKind.LOCAL));
        kindCombo.setValue(WorkspaceKind.LOCAL);
        kindCombo.setOnAction(event -> showWorkspaceForm(kindCombo.getValue()));
        VBox kindRow = field(kindCaption, kindCombo);
        // Two short choices do not need the whole row; a full-width dropdown
        // reads as a field waiting for text.
        kindCombo.setMaxWidth(Region.USE_PREF_SIZE);

        remoteForm = buildRemoteForm();
        passwordBox = field(passwordCaption, passwordField);
        HBox.setHgrow(passwordBox, Priority.ALWAYS);
        keyBox = new HBox(12,
                grow(field(keyFileCaption, withButton(keyFileField, browseKey))),
                field(passphraseCaption, passphraseField));
        keyBox.setAlignment(Pos.BOTTOM_LEFT);
        HBox.setHgrow(keyBox, Priority.ALWAYS);
        HBox authRow = new HBox(12, field(authCaption, authCombo), passwordBox, keyBox);
        authRow.setAlignment(Pos.BOTTOM_LEFT);
        remoteForm.getChildren().add(1, authRow);
        showAuthForm(AuthKind.PASSWORD);

        VBox workspaceSection = new VBox(10, workspaceTitle, workspaceDescription, divider(), kindRow, localForm, remoteForm);
        workspaceSection.getStyleClass().add("settings-section");
        showWorkspaceForm(WorkspaceKind.LOCAL);

        // ---- Preferences section -----------------------------------
        preferencesTitle = new Label();
        preferencesTitle.getStyleClass().add("settings-section-title");

        preferencesDescription = new Label();
        preferencesDescription.getStyleClass().add("settings-section-description");
        preferencesDescription.setWrapText(true);

        languageCombo = new ComboBox<>(FXCollections.observableArrayList(AppLanguage.values()));
        languageCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(AppLanguage value) {
                return value == null ? "" : value.displayName();
            }

            @Override
            public AppLanguage fromString(String value) {
                for (AppLanguage candidate : AppLanguage.values()) {
                    if (candidate.displayName().equals(value)) {
                        return candidate;
                    }
                }
                return AppLanguage.DEFAULT;
            }
        });
        languageCombo.setOnAction(event -> {
            AppLanguage selected = languageCombo.getValue();
            if (selected != null && selected != currentLanguage) {
                onLanguageChange.accept(selected);
            }
        });

        themeCombo = new ComboBox<>(FXCollections.observableArrayList(ThemePreference.values()));
        themeCombo.setConverter(new StringConverter<>() {
            @Override public String toString(ThemePreference value) {
                return value == null ? "" : UiText.themePreference(currentLanguage, value);
            }
            @Override public ThemePreference fromString(String value) { return themePreference; }
        });
        themeCombo.setValue(themePreference);
        themeCombo.setOnAction(event -> {
            ThemePreference selected = themeCombo.getValue();
            if (selected != null) onThemePreferenceChange.accept(selected);
        });

        HBox preferencesRow = new HBox(12, languageCombo, themeCombo);
        preferencesRow.setAlignment(Pos.CENTER_LEFT);
        preferencesRow.getStyleClass().add("settings-row");

        VBox preferencesSection = new VBox(10, preferencesTitle, preferencesDescription, divider(), preferencesRow);
        preferencesSection.getStyleClass().add("settings-section");

        // ---- TMDB section ------------------------------------------
        tmdbTitle = new Label();
        tmdbTitle.getStyleClass().add("settings-section-title");

        tmdbDescription = new Label();
        tmdbDescription.getStyleClass().add("settings-section-description");
        tmdbDescription.setWrapText(true);

        tmdbStatusDot = new Region();
        tmdbStatusDot.getStyleClass().addAll("dot", "dot-idle");
        tmdbStatus = new Label();
        tmdbStatus.getStyleClass().add("settings-section-description");
        tmdbStatus.setWrapText(true);
        HBox tmdbInputs = new HBox(12, tmdbStatusDot, tmdbStatus);
        HBox.setHgrow(tmdbStatus, Priority.ALWAYS);
        tmdbInputs.setAlignment(Pos.CENTER_LEFT);
        tmdbInputs.getStyleClass().add("settings-row");

        ImageView tmdbLogo = new ImageView(new Image(Objects.requireNonNull(
                SettingsPane.class.getResource(TMDB_LOGO_RESOURCE),
                "Missing TMDB attribution logo").toExternalForm()));
        tmdbLogo.setFitWidth(84);
        tmdbLogo.setFitHeight(46);
        tmdbLogo.setPreserveRatio(true);
        tmdbLogo.setAccessibleText("TMDB");

        tmdbAttribution = new Label();
        tmdbAttribution.getStyleClass().add("tmdb-attribution-copy");
        tmdbAttribution.setWrapText(true);

        tmdbAttributionLink = new Hyperlink();
        tmdbAttributionLink.getStyleClass().add("tmdb-attribution-link");
        tmdbAttributionLink.setDisable(openExternalLink == null);
        if (openExternalLink != null) {
            tmdbAttributionLink.setOnAction(event -> openExternalLink.accept(TMDB_ATTRIBUTION_URL));
        }

        VBox tmdbAttributionText = new VBox(2, tmdbAttribution, tmdbAttributionLink);
        HBox.setHgrow(tmdbAttributionText, Priority.ALWAYS);
        HBox tmdbAttributionRow = new HBox(16, tmdbLogo, tmdbAttributionText);
        tmdbAttributionRow.setAlignment(Pos.CENTER_LEFT);
        tmdbAttributionRow.getStyleClass().add("tmdb-attribution");

        VBox tmdbSection = new VBox(
                10,
                tmdbTitle,
                tmdbDescription,
                divider(),
                tmdbInputs,
                divider(),
                tmdbAttributionRow);
        tmdbSection.getStyleClass().add("settings-section");

        applyLanguage(AppLanguage.FRENCH);
        refreshWorkspaceValue(currentWorkspace.get());

        // The screen takes the content area whole, like Scan and History: a
        // centred 960 px column moved every section sideways on each visit.
        // Padding and vertical rhythm come from .screen-root, so the three
        // screens start their content on the same line.
        root = new VBox(14, header, workspaceSection, tmdbSection, preferencesSection);
        root.getStyleClass().addAll("screen-root", "settings-page");
    }

    public VBox root() {
        return root;
    }

    /**
     * Offers the server option. Until this is called the pane only knows local
     * folders, which is what a shell without SSH support gets.
     */
    public void setRemoteWorkspaceSupport(RemoteWorkspaceSupport support) {
        this.remoteSupport = support;
        if (support == null) {
            kindCombo.getItems().setAll(WorkspaceKind.LOCAL);
            kindCombo.setValue(WorkspaceKind.LOCAL);
            showWorkspaceForm(WorkspaceKind.LOCAL);
            return;
        }
        kindCombo.getItems().setAll(WorkspaceKind.values());
        Optional<RemoteWorkspace> remote = support.currentLocation().get()
                .filter(RemoteWorkspace.class::isInstance)
                .map(RemoteWorkspace.class::cast);
        remote.ifPresent(this::fillRemoteForm);
        kindCombo.setValue(remote.isPresent() ? WorkspaceKind.REMOTE : WorkspaceKind.LOCAL);
        showWorkspaceForm(kindCombo.getValue());
        refreshRemoteStatus();
    }

    public void attachExtraSection(Region section, Consumer<AppLanguage> applyLanguageHook) {
        if (section == null) {
            return;
        }
        root.getChildren().add(section);
        if (applyLanguageHook != null) {
            extraSectionLanguageHooks.add(applyLanguageHook);
            applyLanguageHook.accept(currentLanguage);
        }
    }

    public void applyLanguage(AppLanguage language) {
        currentLanguage = language;
        for (Consumer<AppLanguage> hook : extraSectionLanguageHooks) {
            hook.accept(language);
        }
        heading.setText(UiText.settingsHeading(language));
        pageSubtitle.setText(UiText.settingsPageSubtitle(language));

        workspaceTitle.setText(UiText.workspaceSectionTitle(language));
        chooseWorkspace.setText(UiText.chooseWorkspaceButton(language));
        kindCaption.setText(UiText.workspaceKindLabel(language));
        kindCombo.setConverter(new StringConverter<>() {
            @Override public String toString(WorkspaceKind value) {
                if (value == null) return "";
                return value == WorkspaceKind.LOCAL
                        ? UiText.workspaceKindLocal(language)
                        : UiText.workspaceKindRemote(language);
            }
            @Override public WorkspaceKind fromString(String value) { return kindCombo.getValue(); }
        });
        kindCombo.setAccessibleText(UiText.workspaceKindLabel(language));
        hostCaption.setText(UiText.remoteHostLabel(language));
        portCaption.setText(UiText.remotePortLabel(language));
        usernameCaption.setText(UiText.remoteUsernameLabel(language));
        authCaption.setText(UiText.remoteAuthLabel(language));
        authCombo.setConverter(new StringConverter<>() {
            @Override public String toString(AuthKind value) {
                if (value == null) return "";
                return value == AuthKind.PASSWORD
                        ? UiText.remoteAuthPassword(language)
                        : UiText.remoteAuthKey(language);
            }
            @Override public AuthKind fromString(String value) { return authCombo.getValue(); }
        });
        passwordCaption.setText(UiText.remotePasswordLabel(language));
        keyFileCaption.setText(UiText.remoteKeyFileLabel(language));
        browseKey.setText(UiText.remoteKeyFileBrowse(language));
        passphraseCaption.setText(UiText.remotePassphraseLabel(language));
        rootCaption.setText(UiText.remoteRootLabel(language));
        browseRoot.setText(UiText.remoteRootBrowse(language));
        connect.setText(UiText.remoteConnectButton(language));
        disconnect.setText(UiText.remoteDisconnectButton(language));
        showWorkspaceForm(kindCombo.getValue());

        preferencesTitle.setText(UiText.preferencesSectionTitle(language));
        preferencesDescription.setText(UiText.preferencesSectionDescription(language));
        // The combo sits alone on its row, with no label of its own.
        languageCombo.setAccessibleText(UiText.languageLabel(language));
        themeCombo.setAccessibleText(UiText.themeLabel(language));
        themeCombo.setConverter(new StringConverter<>() {
            @Override public String toString(ThemePreference value) {
                return value == null ? "" : UiText.themePreference(language, value);
            }
            @Override public ThemePreference fromString(String value) { return themeCombo.getValue(); }
        });
        tmdbTitle.setText(UiText.tmdbSettingsSectionTitle(language));
        tmdbDescription.setText(UiText.tmdbSettingsSectionDescription(language));
        tmdbAttribution.setText(UiText.tmdbSettingsAttribution(language));
        tmdbAttributionLink.setText(UiText.tmdbSettingsAttributionLink(language));
        tmdbAttributionLink.setAccessibleText(UiText.tmdbSettingsAttributionLinkAccessible(language));
        applyTmdbStatus(language);

        languageCombo.setValue(language);
        refreshWorkspaceValue(currentWorkspace.get());
        refreshRemoteStatus();
    }

    public void refreshWorkspace() {
        refreshWorkspaceValue(currentWorkspace.get());
        refreshRemoteStatus();
    }

    private void refreshWorkspaceValue(Optional<Path> workspace) {
        workspaceValue.setText(UiText.workspaceValue(currentLanguage, workspace));
    }

    private void applyTmdbStatus(AppLanguage language) {
        TmdbStatusPresentation presentation = TmdbStatusPresentation.from(tmdbConfiguration, language);
        tmdbStatus.setText(presentation.text());
        tmdbStatusDot.getStyleClass().setAll("dot", presentation.dotStyleClass());
    }

    // ---- Remote workspace ---------------------------------------------

    private VBox buildRemoteForm() {
        portField.setPrefColumnCount(5);
        usernameField.setPrefColumnCount(12);
        authCombo.setValue(AuthKind.PASSWORD);
        authCombo.setOnAction(event -> showAuthForm(authCombo.getValue()));
        browseKey.setOnAction(event -> browseKeyFile());
        browseRoot.setOnAction(event -> browseRemoteRoot());
        connect.getStyleClass().add("primary");
        connect.setOnAction(event -> connectRemote());
        disconnect.getStyleClass().add("ghost");
        disconnect.setOnAction(event -> disconnectRemote());
        remoteStatusDot.getStyleClass().addAll("dot", "dot-idle");
        remoteStatus.getStyleClass().add("settings-section-description");
        remoteStatus.setWrapText(true);
        HBox.setHgrow(remoteStatus, Priority.ALWAYS);

        HBox serverRow = new HBox(12,
                grow(field(hostCaption, hostField)),
                field(portCaption, portField),
                field(usernameCaption, usernameField));
        serverRow.setAlignment(Pos.BOTTOM_LEFT);

        HBox rootRow = new HBox(12, grow(field(rootCaption, withButton(rootField, browseRoot))));
        rootRow.setAlignment(Pos.BOTTOM_LEFT);

        HBox statusRow = new HBox(12, connect, disconnect, remoteStatusDot, remoteStatus);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        statusRow.getStyleClass().add("settings-row");

        VBox form = new VBox(12, serverRow, rootRow, statusRow);
        form.getStyleClass().add("settings-form");
        return form;
    }

    private void showWorkspaceForm(WorkspaceKind kind) {
        boolean remote = kind == WorkspaceKind.REMOTE;
        localForm.setVisible(!remote);
        localForm.setManaged(!remote);
        remoteForm.setVisible(remote);
        remoteForm.setManaged(remote);
        workspaceDescription.setText(remote
                ? UiText.workspaceSectionDescriptionRemote(currentLanguage)
                : UiText.workspaceSectionDescription(currentLanguage));
    }

    private void showAuthForm(AuthKind kind) {
        boolean password = kind != AuthKind.KEY;
        passwordBox.setVisible(password);
        passwordBox.setManaged(password);
        keyBox.setVisible(!password);
        keyBox.setManaged(!password);
    }

    private void fillRemoteForm(RemoteWorkspace remote) {
        SftpEndpoint endpoint = remote.endpoint();
        hostField.setText(endpoint.host());
        portField.setText(String.valueOf(endpoint.port()));
        usernameField.setText(endpoint.username());
        rootField.setText(remote.rootPath());
        switch (endpoint.authentication()) {
            case SftpAuthentication.Password password -> {
                authCombo.setValue(AuthKind.PASSWORD);
                passwordField.setText(password.password().orElse(""));
            }
            case SftpAuthentication.PrivateKey key -> {
                authCombo.setValue(AuthKind.KEY);
                keyFileField.setText(key.keyFile().toString());
                passphraseField.setText(key.passphrase().orElse(""));
            }
        }
        showAuthForm(authCombo.getValue());
    }

    /** The endpoint the form describes, or empty while a required field is blank. */
    private Optional<SftpEndpoint> endpointFromForm() {
        String host = hostField.getText() == null ? "" : hostField.getText().trim();
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        if (host.isEmpty() || username.isEmpty() || port < 1 || port > 65535) {
            return Optional.empty();
        }
        SftpAuthentication authentication = authCombo.getValue() == AuthKind.KEY
                ? keyAuthentication().orElse(null)
                : SftpAuthentication.Password.of(passwordField.getText());
        if (authentication == null) {
            return Optional.empty();
        }
        return Optional.of(new SftpEndpoint(host, port, username, authentication));
    }

    private Optional<SftpAuthentication> keyAuthentication() {
        String keyFile = keyFileField.getText() == null ? "" : keyFileField.getText().trim();
        if (keyFile.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(SftpAuthentication.PrivateKey.of(Path.of(keyFile), passphraseField.getText()));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private Optional<RemoteWorkspace> remoteWorkspaceFromForm() {
        String rootPath = rootField.getText() == null ? "" : rootField.getText().trim();
        if (rootPath.isEmpty()) {
            return Optional.empty();
        }
        try {
            return endpointFromForm().map(endpoint -> new RemoteWorkspace(endpoint, rootPath));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private void connectRemote() {
        if (remoteSupport == null || remoteBusy) {
            return;
        }
        Optional<RemoteWorkspace> location = remoteWorkspaceFromForm();
        if (location.isEmpty()) {
            showRemoteStatus("dot-error", UiText.remoteStatusIncomplete(currentLanguage));
            return;
        }
        setRemoteBusy(true);
        showRemoteStatus("dot-idle", UiText.remoteStatusConnecting(currentLanguage));
        RemoteWorkspaceSupport support = remoteSupport;
        Thread worker = new Thread(() -> {
            AppShellViewModel outcome = support.configure().apply(location.orElseThrow());
            Platform.runLater(() -> {
                setRemoteBusy(false);
                onConfigured.accept(outcome);
                refreshWorkspaceValue(currentWorkspace.get());
                outcome.errorCode().ifPresentOrElse(
                        code -> showRemoteStatus("dot-error", UiText.errorStatus(code, currentLanguage)),
                        this::refreshRemoteStatus);
            });
        }, "episort-remote-connect");
        worker.setDaemon(true);
        worker.start();
    }

    private void disconnectRemote() {
        if (remoteSupport == null || remoteBusy) {
            return;
        }
        remoteSupport.disconnect().run();
        onConfigured.accept(AppShellViewModel.fromError(ApplicationError.recoverable(
                WorkspaceConfigurationService.ERROR_REMOTE_DISCONNECTED,
                ErrorSeverity.BLOCKING,
                "Connect to your server before scanning media.",
                "The user closed the session.")));
        refreshWorkspaceValue(currentWorkspace.get());
        refreshRemoteStatus();
    }

    /**
     * Opens a session with the credentials as typed, without recording
     * anything, and lets the user pick the folder from the server's own tree.
     */
    private void browseRemoteRoot() {
        if (remoteSupport == null || remoteBusy) {
            return;
        }
        Optional<SftpEndpoint> endpoint = endpointFromForm();
        if (endpoint.isEmpty()) {
            showRemoteStatus("dot-error", UiText.remoteStatusIncomplete(currentLanguage));
            return;
        }
        RemoteWorkspace serverRoot = new RemoteWorkspace(endpoint.orElseThrow(), "/");
        setRemoteBusy(true);
        showRemoteStatus("dot-idle", UiText.remoteStatusConnecting(currentLanguage));
        RemoteWorkspaceSupport support = remoteSupport;
        Thread worker = new Thread(() -> {
            Path root;
            try {
                root = support.sessions().connect(serverRoot);
            } catch (RemoteWorkspaceException exception) {
                String code = WorkspaceConfigurationService.remoteError(exception).code();
                Platform.runLater(() -> {
                    setRemoteBusy(false);
                    showRemoteStatus("dot-error", UiText.errorStatus(code, currentLanguage));
                });
                return;
            }
            Platform.runLater(() -> {
                setRemoteBusy(false);
                refreshRemoteStatus();
                RemotePathPicker.show(
                                ownerWindow(),
                                currentLanguage,
                                UiText.remotePickerFolderTitle(currentLanguage),
                                root,
                                UiText.remotePickerServerRoot(currentLanguage),
                                RemotePathPicker.Mode.FOLDER)
                        .flatMap(paths -> paths.stream().findFirst())
                        .ifPresent(chosen -> rootField.setText(chosen.toString()));
            });
        }, "episort-remote-browse");
        worker.setDaemon(true);
        worker.start();
    }

    private void browseKeyFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(UiText.remoteKeyFileChooserTitle(currentLanguage));
        File sshDirectory = new File(System.getProperty("user.home", "."), ".ssh");
        if (sshDirectory.isDirectory()) {
            chooser.setInitialDirectory(sshDirectory);
        }
        File selected = chooser.showOpenDialog(ownerWindow());
        if (selected != null) {
            keyFileField.setText(selected.getAbsolutePath());
        }
    }

    private void refreshRemoteStatus() {
        if (remoteSupport == null) {
            showRemoteStatus("dot-idle", UiText.remoteStatusNone(currentLanguage));
            disconnect.setDisable(true);
            return;
        }
        Optional<RemoteWorkspace> remote = remoteSupport.currentLocation().get()
                .filter(RemoteWorkspace.class::isInstance)
                .map(RemoteWorkspace.class::cast);
        if (remote.isEmpty()) {
            showRemoteStatus("dot-idle", UiText.remoteStatusNone(currentLanguage));
            disconnect.setDisable(true);
            return;
        }
        boolean connected = remoteSupport.sessions().isConnected(remote.orElseThrow());
        String server = remote.orElseThrow().displayName();
        showRemoteStatus(connected ? "dot-good" : "dot-idle", connected
                ? UiText.remoteStatusConnected(currentLanguage, server)
                : UiText.remoteStatusDisconnected(currentLanguage, server));
        disconnect.setDisable(remoteBusy || !connected);
    }

    private void showRemoteStatus(String dotClass, String text) {
        remoteStatusDot.getStyleClass().setAll("dot", dotClass);
        remoteStatus.setText(text);
    }

    private void setRemoteBusy(boolean busy) {
        remoteBusy = busy;
        connect.setDisable(busy);
        browseRoot.setDisable(busy);
        if (busy) {
            disconnect.setDisable(true);
        } else {
            refreshRemoteStatus();
        }
    }

    private Window ownerWindow() {
        return root == null || root.getScene() == null ? null : root.getScene().getWindow();
    }

    private static Label caption() {
        Label label = new Label();
        label.getStyleClass().add("settings-field-caption");
        return label;
    }

    private static VBox field(Label caption, Region control) {
        if (control instanceof Control input) {
            input.setMaxWidth(Double.MAX_VALUE);
        }
        VBox box = new VBox(4, caption, control);
        box.setAlignment(Pos.BOTTOM_LEFT);
        return box;
    }

    private static HBox withButton(TextField field, Button button) {
        HBox box = new HBox(8, field, button);
        box.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(field, Priority.ALWAYS);
        field.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private static <T extends Region> T grow(T region) {
        HBox.setHgrow(region, Priority.ALWAYS);
        region.setMaxWidth(Double.MAX_VALUE);
        return region;
    }

    private static Region divider() {
        Region div = new Region();
        div.getStyleClass().add("settings-section-divider");
        return div;
    }
}

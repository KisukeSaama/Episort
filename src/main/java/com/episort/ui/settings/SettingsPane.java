package com.episort.ui.settings;

import com.episort.config.TvdbCredentials;
import com.episort.ui.AppShellViewModel;
import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.concurrent.Task;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

public final class SettingsPane {
    private final VBox root;

    public SettingsPane(
            Function<Path, AppShellViewModel> configureWorkspace,
            Function<Path, AppShellViewModel> selectInputFolder,
            BiFunction<String, Optional<String>, AppShellViewModel> configureTvdb,
            Consumer<AppShellViewModel> onConfigured) {
        Button chooseWorkspace = new Button("Choose workspace");
        chooseWorkspace.setOnAction(event -> {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle("Choose Episort workspace");
            Window owner = chooseWorkspace.getScene() == null ? null : chooseWorkspace.getScene().getWindow();
            File selectedDirectory = directoryChooser.showDialog(owner);
            if (selectedDirectory != null) {
                onConfigured.accept(configureWorkspace.apply(selectedDirectory.toPath()));
            }
        });

        Button chooseInputFolder = new Button("Choose input folder");
        chooseInputFolder.setOnAction(event -> {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle("Choose input folder");
            Window owner = chooseInputFolder.getScene() == null ? null : chooseInputFolder.getScene().getWindow();
            File selectedDirectory = directoryChooser.showDialog(owner);
            if (selectedDirectory != null) {
                onConfigured.accept(selectInputFolder.apply(selectedDirectory.toPath()));
            }
        });

        TextField apiKey = new TextField();
        apiKey.setPromptText("TVDB API key");
        PasswordField subscriberPin = new PasswordField();
        subscriberPin.setPromptText("Subscriber PIN");
        Button testTvdb = new Button("Test TVDB");
        testTvdb.setOnAction(event -> {
            testTvdb.setDisable(true);
            Task<AppShellViewModel> task = new Task<>() {
                @Override
                protected AppShellViewModel call() {
                    return configureTvdb.apply(
                            apiKey.getText(),
                            Optional.ofNullable(subscriberPin.getText()).filter(value -> !value.isBlank()));
                }
            };
            task.setOnSucceeded(done -> {
                testTvdb.setDisable(false);
                onConfigured.accept(task.getValue());
            });
            task.setOnFailed(done -> testTvdb.setDisable(false));
            Thread thread = new Thread(task, "tvdb-connection-test");
            thread.setDaemon(true);
            thread.start();
        });

        root = new VBox(8, new HBox(chooseWorkspace, chooseInputFolder), apiKey, subscriberPin, testTvdb);
    }

    public VBox root() {
        return root;
    }
}

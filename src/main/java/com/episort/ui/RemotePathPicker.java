package com.episort.ui;

import com.episort.filesystem.RemoteWorkspaceSessions;
import com.episort.filesystem.WorkspaceDirectoryEntry;
import com.episort.filesystem.WorkspaceDirectoryReader;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * The folder and file chooser for a workspace that lives on a server.
 *
 * <p>The native choosers only know this computer's disks, so a remote
 * workspace gets its own: the same lazily loaded tree as the sidebar explorer,
 * inside a custom dialog (design system §4.7b), reading through the workspace
 * boundary so nothing outside the chosen root is ever listed. Directory reads
 * run off the JavaFX thread; each is a round trip to the server.
 */
public final class RemotePathPicker {
    public enum Mode {
        /** One directory. */
        FOLDER,
        /** One or more supported media files. */
        FILES
    }

    private final WorkspaceDirectoryReader reader = new WorkspaceDirectoryReader();
    private final Map<TreeItem<Node>, Boolean> loaded = new IdentityHashMap<>();
    private final TreeView<Node> tree = new TreeView<>();
    private final Button choose;
    private final Path root;
    private final Mode mode;
    private final AppLanguage language;

    private RemotePathPicker(Path root, Mode mode, AppLanguage language) {
        this.root = Objects.requireNonNull(root, "root");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.language = Objects.requireNonNull(language, "language");
        this.choose = new Button(UiText.remotePickerChoose(language));
    }

    /**
     * Shows the picker and waits.
     *
     * @param owner     the window to centre on, may be null
     * @param title     dialog title
     * @param root      the directory the choice is confined to
     * @param rootLabel how the root is named in the tree, for example the server
     * @return the chosen paths, empty when the user cancelled
     */
    public static Optional<List<Path>> show(
            Window owner, AppLanguage language, String title, Path root, String rootLabel, Mode mode) {
        RemotePathPicker picker = new RemotePathPicker(root, mode, language);
        return picker.open(owner, title, rootLabel);
    }

    private Optional<List<Path>> open(Window owner, String title, String rootLabel) {
        Stage dialog = new Stage(StageStyle.TRANSPARENT);
        dialog.setTitle(title);
        dialog.initModality(Modality.WINDOW_MODAL);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.getIcons().add(AppShell.logoImage());

        Label heading = new Label(title);
        heading.getStyleClass().add("custom-dialog-title");
        Label hint = new Label(RemoteWorkspaceSessions.displayName(root.toAbsolutePath().normalize()));
        hint.getStyleClass().add("custom-dialog-message");
        hint.setWrapText(true);

        tree.setShowRoot(true);
        tree.setEditable(false);
        tree.getStyleClass().add("workspace-tree");
        tree.setFixedCellSize(28);
        tree.setPrefHeight(360);
        tree.setPrefWidth(520);
        tree.getSelectionModel().setSelectionMode(mode == Mode.FILES ? SelectionMode.MULTIPLE : SelectionMode.SINGLE);
        tree.setCellFactory(ignored -> new PickerCell());
        tree.getSelectionModel().getSelectedItems().addListener(
                (javafx.collections.ListChangeListener<TreeItem<Node>>) change -> refreshChoice());
        TreeItem<Node> rootItem = directoryItem(new Node(root, rootLabel, Kind.DIRECTORY));
        tree.setRoot(rootItem);
        rootItem.setExpanded(true);
        tree.getSelectionModel().select(rootItem);
        VBox.setVgrow(tree, Priority.ALWAYS);

        Button cancel = new Button(UiText.cancelButton(language));
        cancel.getStyleClass().add("ghost");
        cancel.setCancelButton(true);
        cancel.setOnAction(event -> dialog.close());

        choose.getStyleClass().add("primary");
        choose.setDefaultButton(true);
        Optional<List<Path>>[] result = new Optional[] {Optional.<List<Path>>empty()};
        choose.setOnAction(event -> {
            List<Path> selection = selectedPaths();
            if (!selection.isEmpty()) {
                result[0] = Optional.of(selection);
                dialog.close();
            }
        });
        refreshChoice();

        HBox actions = new HBox(10, cancel, choose);
        actions.getStyleClass().add("custom-dialog-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(12, heading, hint, tree, actions);
        content.getStyleClass().addAll("custom-dialog", "remote-path-picker");
        Scene scene = new Scene(content);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(Objects.requireNonNull(
                        RemotePathPicker.class.getResource("/styles/app.css"),
                        "Missing stylesheet /styles/app.css")
                .toExternalForm());
        ThemeStyles.register(content);
        dialog.setScene(scene);
        dialog.showAndWait();
        return result[0];
    }

    private List<Path> selectedPaths() {
        return tree.getSelectionModel().getSelectedItems().stream()
                .filter(Objects::nonNull)
                .map(TreeItem::getValue)
                .filter(node -> node.path() != null)
                .filter(node -> mode == Mode.FOLDER ? node.kind() == Kind.DIRECTORY : node.kind() == Kind.MEDIA)
                .map(Node::path)
                .toList();
    }

    private void refreshChoice() {
        choose.setDisable(selectedPaths().isEmpty());
    }

    private TreeItem<Node> directoryItem(Node node) {
        TreeItem<Node> item = new TreeItem<>(node);
        loaded.put(item, Boolean.FALSE);
        item.getChildren().add(new TreeItem<>(new Node(null, UiText.workspaceExplorerLoading(language), Kind.LOADING)));
        item.expandedProperty().addListener((observable, wasExpanded, expanded) -> {
            if (expanded) {
                loadChildren(item);
            }
        });
        return item;
    }

    private void loadChildren(TreeItem<Node> item) {
        if (Boolean.TRUE.equals(loaded.get(item))) {
            return;
        }
        loaded.put(item, Boolean.TRUE);
        Path directory = item.getValue().path();
        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return reader.list(root, directory);
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .whenComplete((entries, failure) -> Platform.runLater(() -> {
                    if (failure != null || entries == null) {
                        item.getChildren().setAll(new TreeItem<>(
                                new Node(null, UiText.workspaceExplorerUnavailable(language), Kind.ERROR)));
                        return;
                    }
                    item.getChildren().setAll(entries.stream()
                            .filter(entry -> mode == Mode.FILES || entry.directory())
                            .map(this::treeItem)
                            .toList());
                }));
    }

    private TreeItem<Node> treeItem(WorkspaceDirectoryEntry entry) {
        Kind kind;
        if (entry.symbolicLink()) {
            kind = Kind.LINK;
        } else if (entry.directory()) {
            kind = Kind.DIRECTORY;
        } else if (entry.supportedMedia()) {
            kind = Kind.MEDIA;
        } else {
            kind = Kind.FILE;
        }
        Node node = new Node(entry.path(), entry.name(), kind);
        return entry.directory() ? directoryItem(node) : new TreeItem<>(node);
    }

    private enum Kind {
        DIRECTORY,
        MEDIA,
        FILE,
        LINK,
        LOADING,
        ERROR
    }

    private record Node(Path path, String name, Kind kind) {
    }

    /** Same glyphs and classes as the sidebar explorer, so both trees read alike. */
    private static final class PickerCell extends TreeCell<Node> {
        private PickerCell() {
            setTextOverrun(OverrunStyle.ELLIPSIS);
            setEllipsisString("…");
            setMinWidth(0);
        }

        @Override
        protected void updateItem(Node node, boolean empty) {
            super.updateItem(node, empty);
            getStyleClass().removeAll(
                    "workspace-node-root",
                    "workspace-node-directory",
                    "workspace-node-media",
                    "workspace-node-file",
                    "workspace-node-link",
                    "workspace-node-transient",
                    "workspace-node-error");
            if (empty || node == null) {
                setText(null);
                setTooltip(null);
                return;
            }
            boolean rootCell = getTreeItem() != null && getTreeView() != null
                    && getTreeItem() == getTreeView().getRoot();
            String prefix = switch (node.kind()) {
                case MEDIA -> "■  ";
                case FILE -> "·  ";
                case LINK -> "↪  ";
                case LOADING -> "…  ";
                case ERROR -> "!  ";
                case DIRECTORY -> "";
            };
            setText((rootCell ? "" : prefix) + node.name());
            getStyleClass().add(switch (node.kind()) {
                case DIRECTORY -> "workspace-node-directory";
                case MEDIA -> "workspace-node-media";
                case FILE -> "workspace-node-file";
                case LINK -> "workspace-node-link";
                case LOADING -> "workspace-node-transient";
                case ERROR -> "workspace-node-error";
            });
            if (rootCell) {
                getStyleClass().add("workspace-node-root");
            }
            setTooltip(node.path() == null ? null : new Tooltip(node.path().toString()));
        }
    }
}

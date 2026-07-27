package com.episort.ui;

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
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Read-only, lazily loaded view of the configured workspace.
 *
 * <p>Filesystem reads run off the JavaFX thread because a workspace may live
 * on a NAS. The generation check discards completions from a workspace that
 * has since been replaced.
 */
public final class WorkspaceExplorer {
    private final WorkspaceDirectoryReader reader;
    private final VBox root;
    private final Label sectionLabel;
    private final Label collapseGlyph;
    private final Button sectionToggle;
    private final Label emptyState;
    private final TreeView<ExplorerNode> tree;
    private final StackPane body;
    private final Map<TreeItem<ExplorerNode>, LoadState> loadStates = new IdentityHashMap<>();

    private AppLanguage currentLanguage = AppLanguage.FRENCH;
    private Optional<Path> workspace = Optional.empty();
    private long generation;
    private boolean collapsed;

    public WorkspaceExplorer() {
        this(new WorkspaceDirectoryReader());
    }

    WorkspaceExplorer(WorkspaceDirectoryReader reader) {
        this.reader = Objects.requireNonNull(reader, "reader");

        sectionLabel = new Label();
        sectionLabel.getStyleClass().add("sidebar-section-label");

        collapseGlyph = new Label();
        collapseGlyph.getStyleClass().add("workspace-explorer-chevron");

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox headerContent = new HBox(8, sectionLabel, headerSpacer, collapseGlyph);
        headerContent.setAlignment(Pos.CENTER_LEFT);

        sectionToggle = new Button();
        sectionToggle.setGraphic(headerContent);
        sectionToggle.setMaxWidth(Double.MAX_VALUE);
        sectionToggle.getStyleClass().setAll("workspace-explorer-header");
        sectionToggle.setOnAction(event -> setCollapsed(!collapsed));
        headerContent.prefWidthProperty().bind(
                Bindings.max(0, sectionToggle.widthProperty().subtract(18)));

        emptyState = new Label();
        emptyState.setWrapText(true);
        emptyState.getStyleClass().add("workspace-explorer-empty");

        tree = new TreeView<>();
        tree.setShowRoot(true);
        tree.setEditable(false);
        tree.getStyleClass().add("workspace-tree");
        tree.setFixedCellSize(28);
        tree.setCellFactory(ignored -> {
            WorkspaceTreeCell cell = new WorkspaceTreeCell();
            cell.prefWidthProperty().bind(Bindings.max(0, tree.widthProperty().subtract(18)));
            return cell;
        });

        body = new StackPane(emptyState, tree);
        body.getStyleClass().add("workspace-explorer-body");
        VBox.setVgrow(body, Priority.ALWAYS);

        root = new VBox(2, sectionToggle, body);
        root.getStyleClass().add("workspace-explorer");
        VBox.setVgrow(root, Priority.ALWAYS);

        applyLanguage(currentLanguage);
        reload();
    }

    public Region root() {
        return root;
    }

    public void applyLanguage(AppLanguage language) {
        currentLanguage = Objects.requireNonNull(language, "language");
        sectionLabel.setText(UiText.sidebarSectionWorkspace(language));
        emptyState.setText(UiText.workspaceExplorerEmpty(language));
        tree.setAccessibleText(UiText.workspaceExplorerAccessible(language));
        updateCollapseState();
        refreshTransientLabels(tree.getRoot());
        tree.refresh();
    }

    public void setWorkspace(Optional<Path> candidate) {
        Optional<Path> normalized = candidate == null
                ? Optional.empty()
                : candidate.map(path -> path.toAbsolutePath().normalize());
        if (workspace.equals(normalized)) {
            return;
        }
        workspace = normalized;
        reload();
    }

    public void refresh() {
        if (workspace.isPresent()) {
            reload();
        }
    }

    private void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        updateCollapseState();
    }

    private void updateCollapseState() {
        body.setVisible(!collapsed);
        body.setManaged(!collapsed);
        VBox.setVgrow(root, collapsed ? Priority.NEVER : Priority.ALWAYS);
        collapseGlyph.setText(collapsed ? "\u25BE" : "\u25B4");
        sectionToggle.setAccessibleText(collapsed
                ? UiText.workspaceExplorerExpand(currentLanguage)
                : UiText.workspaceExplorerCollapse(currentLanguage));
    }

    private void reload() {
        generation++;
        loadStates.clear();
        tree.setRoot(null);

        if (workspace.isEmpty()) {
            emptyState.setVisible(true);
            emptyState.setManaged(true);
            tree.setVisible(false);
            tree.setManaged(false);
            return;
        }

        emptyState.setVisible(false);
        emptyState.setManaged(false);
        tree.setVisible(true);
        tree.setManaged(true);

        Path workspaceRoot = workspace.orElseThrow();
        Path filename = workspaceRoot.getFileName();
        String displayName = filename == null ? workspaceRoot.toString() : filename.toString();
        TreeItem<ExplorerNode> rootItem = directoryItem(
                new ExplorerNode(workspaceRoot, displayName, NodeKind.DIRECTORY));
        tree.setRoot(rootItem);
        rootItem.setExpanded(true);
    }

    private TreeItem<ExplorerNode> directoryItem(ExplorerNode node) {
        TreeItem<ExplorerNode> item = new TreeItem<>(node);
        loadStates.put(item, LoadState.NOT_LOADED);
        item.getChildren().add(new TreeItem<>(transientNode(NodeKind.LOADING)));
        item.expandedProperty().addListener((observable, wasExpanded, expanded) -> {
            if (expanded) {
                loadChildren(item);
            }
        });
        return item;
    }

    private void loadChildren(TreeItem<ExplorerNode> item) {
        if (loadStates.get(item) != LoadState.NOT_LOADED || workspace.isEmpty()) {
            return;
        }
        loadStates.put(item, LoadState.LOADING);
        item.getChildren().setAll(new TreeItem<>(transientNode(NodeKind.LOADING)));

        long requestedGeneration = generation;
        Path workspaceRoot = workspace.orElseThrow();
        Path directory = item.getValue().path();
        CompletableFuture
                .supplyAsync(() -> read(workspaceRoot, directory))
                .whenComplete((result, failure) -> Platform.runLater(() -> {
                    if (requestedGeneration != generation || tree.getRoot() == null) {
                        return;
                    }
                    if (failure != null || result == null) {
                        loadStates.put(item, LoadState.FAILED);
                        item.getChildren().setAll(new TreeItem<>(transientNode(NodeKind.ERROR)));
                        return;
                    }
                    loadStates.put(item, LoadState.LOADED);
                    item.getChildren().setAll(result.stream().map(this::treeItem).toList());
                }));
    }

    private List<WorkspaceDirectoryEntry> read(Path workspaceRoot, Path directory) {
        try {
            return reader.list(workspaceRoot, directory);
        } catch (Exception exception) {
            throw new WorkspaceReadException(exception);
        }
    }

    private TreeItem<ExplorerNode> treeItem(WorkspaceDirectoryEntry entry) {
        NodeKind kind;
        if (entry.symbolicLink()) {
            kind = NodeKind.LINK;
        } else if (entry.directory()) {
            kind = NodeKind.DIRECTORY;
        } else if (entry.supportedMedia()) {
            kind = NodeKind.MEDIA;
        } else {
            kind = NodeKind.FILE;
        }
        ExplorerNode node = new ExplorerNode(entry.path(), entry.name(), kind);
        return entry.directory() ? directoryItem(node) : new TreeItem<>(node);
    }

    private ExplorerNode transientNode(NodeKind kind) {
        String text = kind == NodeKind.LOADING
                ? UiText.workspaceExplorerLoading(currentLanguage)
                : UiText.workspaceExplorerUnavailable(currentLanguage);
        return new ExplorerNode(null, text, kind);
    }

    private void refreshTransientLabels(TreeItem<ExplorerNode> item) {
        if (item == null) {
            return;
        }
        ExplorerNode node = item.getValue();
        if (node != null && (node.kind() == NodeKind.LOADING || node.kind() == NodeKind.ERROR)) {
            item.setValue(transientNode(node.kind()));
        }
        item.getChildren().forEach(this::refreshTransientLabels);
    }

    private enum LoadState {
        NOT_LOADED,
        LOADING,
        LOADED,
        FAILED
    }

    private enum NodeKind {
        DIRECTORY,
        MEDIA,
        FILE,
        LINK,
        LOADING,
        ERROR
    }

    private record ExplorerNode(Path path, String name, NodeKind kind) {
        private ExplorerNode {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(kind, "kind");
        }
    }

    private static final class WorkspaceReadException extends RuntimeException {
        private WorkspaceReadException(Throwable cause) {
            super(cause);
        }
    }

    private static final class WorkspaceTreeCell extends TreeCell<ExplorerNode> {
        @Override
        protected void updateItem(ExplorerNode node, boolean empty) {
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
                setAccessibleText(null);
                return;
            }

            boolean root = getTreeItem() != null
                    && getTreeView() != null
                    && getTreeItem() == getTreeView().getRoot();
            setText(prefix(node.kind(), root) + node.name());
            getStyleClass().add(styleClass(node.kind()));
            if (root) {
                getStyleClass().add("workspace-node-root");
            }
            if (node.path() == null) {
                setTooltip(null);
                setAccessibleText(node.name());
            } else {
                String path = node.path().toString();
                setTooltip(new Tooltip(path));
                setAccessibleText(node.name() + ", " + path);
            }
        }

        private WorkspaceTreeCell() {
            setTextOverrun(OverrunStyle.ELLIPSIS);
            setEllipsisString("\u2026");
            setMinWidth(0);
        }

        private static String prefix(NodeKind kind, boolean root) {
            if (root || kind == NodeKind.DIRECTORY) {
                return "";
            }
            return switch (kind) {
                case MEDIA -> "\u25A0  ";
                case FILE -> "\u00B7  ";
                case LINK -> "\u21AA  ";
                case LOADING -> "\u2026  ";
                case ERROR -> "!  ";
                case DIRECTORY -> "";
            };
        }

        private static String styleClass(NodeKind kind) {
            return switch (kind) {
                case DIRECTORY -> "workspace-node-directory";
                case MEDIA -> "workspace-node-media";
                case FILE -> "workspace-node-file";
                case LINK -> "workspace-node-link";
                case LOADING -> "workspace-node-transient";
                case ERROR -> "workspace-node-error";
            };
        }
    }
}

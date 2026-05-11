package com.episort.ui.scan;

import com.episort.ai.AiChatBackend;
import com.episort.ai.AiChatStreamSink;
import com.episort.ai.AiChatToolCall;
import com.episort.ai.AiChatTurn;
import com.episort.ui.AppLanguage;
import com.episort.ui.UiText;
import java.util.List;
import java.util.function.BiConsumer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Bottom-of-scan-screen chat panel. Conversation is per-target (per ScanRow). The backend
 * streams tokens; tool calls are surfaced as confirmation cards before any mutation.
 */
public final class AiChatPanel {
    private final VBox root;
    private final Label heading;
    private final Label targetBreadcrumb;
    private final VBox messagesBox;
    private final ScrollPane messagesScroll;
    private final TextArea input;
    private final Button renamePromptButton;
    private final Button sendButton;
    private final Label unavailableNote;
    private String currentTargetLabel = "";

    private AppLanguage currentLanguage = AppLanguage.FRENCH;
    private AiChatBackend backend;
    private BiConsumer<AiChatToolCall, ScanRow> applyHandler = (call, row) -> {};
    private Runnable proposeHandler;
    private ScanRow currentTarget;
    private String currentContext = "";
    private boolean awaitingResponse = false;
    private Label busyLabel;

    public AiChatPanel() {
        heading = new Label();
        heading.getStyleClass().addAll("section-heading", "section-heading-accent");

        targetBreadcrumb = new Label();
        targetBreadcrumb.getStyleClass().add("ai-chat-breadcrumb");

        unavailableNote = new Label();
        unavailableNote.getStyleClass().add("ai-chat-unavailable");
        unavailableNote.setWrapText(true);
        unavailableNote.setVisible(false);
        unavailableNote.setManaged(false);

        messagesBox = new VBox(8);
        messagesBox.getStyleClass().add("ai-chat-messages");
        messagesScroll = new ScrollPane(messagesBox);
        messagesScroll.setFitToWidth(true);
        messagesScroll.getStyleClass().add("ai-chat-scroll");
        messagesScroll.setPrefHeight(220);
        messagesScroll.setMinHeight(160);

        input = new TextArea();
        input.getStyleClass().add("ai-chat-input");
        input.setWrapText(true);
        input.setPrefRowCount(2);
        input.setOnKeyPressed(event -> {
            if (shouldSendOnEnter(event.getCode(), event.isShiftDown())) {
                event.consume();
                onSend();
            }
        });

        renamePromptButton = new Button();
        renamePromptButton.getStyleClass().add("ghost");
        renamePromptButton.setOnAction(event -> {
            if (proposeHandler != null) {
                proposeHandler.run();
            } else {
                sendText(renamePrompt());
            }
        });

        sendButton = new Button();
        sendButton.getStyleClass().add("primary");
        sendButton.setOnAction(event -> onSend());

        HBox inputRow = new HBox(8, input, renamePromptButton, sendButton);
        HBox.setHgrow(input, Priority.ALWAYS);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        root = new VBox(8, heading, targetBreadcrumb, unavailableNote, messagesScroll, inputRow);
        root.getStyleClass().add("ai-chat-panel");
        root.setPadding(new Insets(8, 0, 0, 0));
        VBox.setVgrow(messagesScroll, Priority.ALWAYS);

        applyLanguage(AppLanguage.FRENCH);
        setEnabled(false);
    }

    public Region root() {
        return root;
    }

    public void applyLanguage(AppLanguage language) {
        currentLanguage = language;
        heading.setText(UiText.aiChatHeading(language));
        renamePromptButton.setText(UiText.aiChatRenamePrompt(language));
        sendButton.setText(UiText.aiChatSend(language));
        input.setPromptText(UiText.aiChatPlaceholder(language));
        unavailableNote.setText(UiText.aiChatUnavailable(language));
        if (currentTarget == null) {
            targetBreadcrumb.setText(UiText.aiChatNoTarget(language));
        } else {
            targetBreadcrumb.setText(UiText.aiChatTargetPrefix(language)
                    + (currentTargetLabel.isBlank() ? currentTarget.originalFilename() : currentTargetLabel));
        }
    }

    public void setBackend(AiChatBackend backend) {
        this.backend = backend;
        refreshAvailability();
    }

    public void setApplyHandler(BiConsumer<AiChatToolCall, ScanRow> handler) {
        this.applyHandler = handler == null ? (c, r) -> {} : handler;
    }

    /** Replaces the chat-LLM "Proposer" action with structured-analysis driven by the caller. */
    public void setProposeHandler(Runnable handler) {
        this.proposeHandler = handler;
    }

    /** Appends an assistant message bubble programmatically (used by the structured propose path). */
    public void appendAssistantText(String text) {
        if (text == null || text.isBlank()) return;
        appendMessage(messagesBox, "assistant", text);
        scrollToBottom();
    }

    /** Appends a tool-call confirmation card bound to a specific target row. */
    public void appendProposalCard(AiChatToolCall call, ScanRow target) {
        appendToolCallCard(messagesBox, call, target);
    }

    /** Appends a single aggregated confirmation card bundling multiple tool calls against one row. */
    public void appendAggregatedProposalCard(List<AiChatToolCall> calls, ScanRow target) {
        appendAggregatedToolCallCard(messagesBox, calls, target);
    }

    /** Locks/unlocks the panel during a long-running propose analysis. */
    public void setBusy(boolean busy, String hint) {
        awaitingResponse = busy;
        if (busy) {
            if (busyLabel == null) {
                busyLabel = appendMessage(messagesBox, "assistant",
                        hint == null || hint.isBlank() ? "…" : hint);
            } else if (hint != null && !hint.isBlank()) {
                replaceMessageContent(busyLabel, hint);
            }
            scrollToBottom();
        } else if (busyLabel != null) {
            VBox bubble = (VBox) busyLabel.getParent();
            if (bubble != null && bubble.getParent() instanceof HBox row) {
                messagesBox.getChildren().remove(row);
            }
            busyLabel = null;
        }
        refreshAvailability();
    }

    public void setTarget(ScanRow row, String context) {
        setTarget(row, context, row == null ? null : row.originalFilename());
    }

    public void setTarget(ScanRow row, String context, String label) {
        setTarget(row, context, label, false);
    }

    public void setTarget(ScanRow row, String context, String label, boolean preserveMessages) {
        boolean sameContext = currentTarget == row && currentContext.equals(context == null ? "" : context);
        this.currentTarget = row;
        this.currentContext = context == null ? "" : context;
        this.currentTargetLabel = label == null ? "" : label;
        if (row == null) {
            currentTargetLabel = "";
            targetBreadcrumb.setText(UiText.aiChatNoTarget(currentLanguage));
            messagesBox.getChildren().clear();
            setEnabled(false);
            return;
        }
        targetBreadcrumb.setText(UiText.aiChatTargetPrefix(currentLanguage)
                + (label == null || label.isBlank() ? row.originalFilename() : label));
        if (!sameContext && !preserveMessages) {
            messagesBox.getChildren().clear();
        }
        refreshAvailability();
    }

    public void clear() {
        currentTarget = null;
        currentContext = "";
        currentTargetLabel = "";
        messagesBox.getChildren().clear();
        targetBreadcrumb.setText(UiText.aiChatNoTarget(currentLanguage));
        setEnabled(false);
    }

    public void refreshAvailability() {
        boolean available = backend != null && backend.isAvailable();
        unavailableNote.setVisible(!available);
        unavailableNote.setManaged(!available);
        setEnabled(available && currentTarget != null && !awaitingResponse);
    }

    private void setEnabled(boolean enabled) {
        input.setDisable(!enabled);
        renamePromptButton.setDisable(!enabled);
        sendButton.setDisable(!enabled);
    }

    static boolean shouldSendOnEnter(KeyCode code, boolean shiftDown) {
        return code == KeyCode.ENTER && !shiftDown;
    }

    private void onSend() {
        if (backend == null || currentTarget == null) {
            return;
        }
        String text = input.getText() == null ? "" : input.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        sendText(text);
    }

    private void sendText(String text) {
        if (backend == null || currentTarget == null || text == null || text.isBlank()) {
            return;
        }
        ScanRow target = currentTarget;
        VBox messageBox = messagesBox;

        appendMessage(messageBox, "user", text);
        input.clear();
        awaitingResponse = true;
        setEnabled(false);
        scrollToBottom();

        AiChatTurn turn = new AiChatTurn(currentContext, List.of(), text, currentLanguage);
        StringBuilder accumulated = new StringBuilder();
        final Label[] assistantLabel = new Label[1];
        // Buffer tool calls across the turn so multiple structured edits
        // (e.g. setTitle + setYear) appear as a SINGLE confirmation card the
        // user accepts in one click, instead of N stacked cards.
        final java.util.List<AiChatToolCall> pendingCalls = new java.util.ArrayList<>();
        backend.send(turn, new AiChatStreamSink() {
            @Override
            public void onToken(String chunk) {
                Platform.runLater(() -> {
                    if (chunk == null || chunk.isBlank()) {
                        return;
                    }
                    accumulated.append(chunk);
                    if (assistantLabel[0] == null) {
                        assistantLabel[0] = appendMessage(messageBox, "assistant", accumulated.toString());
                    } else {
                        replaceMessageContent(assistantLabel[0], accumulated.toString());
                    }
                    scrollToBottom();
                });
            }

            @Override
            public void onToolCall(AiChatToolCall toolCall) {
                Platform.runLater(() -> pendingCalls.add(toolCall));
            }

            @Override
            public void onComplete(String fullResponse) {
                Platform.runLater(() -> {
                    String clean = fullResponse == null ? "" : fullResponse.trim();
                    if (!clean.isBlank()) {
                        if (assistantLabel[0] == null) {
                            assistantLabel[0] = appendMessage(messageBox, "assistant", clean);
                        } else {
                            replaceMessageContent(assistantLabel[0], clean);
                        }
                    } else if (assistantLabel[0] != null) {
                        messageBox.getChildren().remove(assistantLabel[0]);
                        assistantLabel[0] = null;
                    }
                    if (!pendingCalls.isEmpty()) {
                        appendAggregatedToolCallCard(messageBox, List.copyOf(pendingCalls), target);
                        pendingCalls.clear();
                    }
                    awaitingResponse = false;
                    refreshAvailability();
                    scrollToBottom();
                });
            }

            @Override
            public void onError(Throwable error) {
                Platform.runLater(() -> {
                    String msg = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                    if (assistantLabel[0] == null) {
                        assistantLabel[0] = appendMessage(messageBox, "assistant",
                                UiText.aiChatErrorPrefix(currentLanguage) + msg);
                    } else {
                        replaceMessageContent(assistantLabel[0], UiText.aiChatErrorPrefix(currentLanguage) + msg);
                    }
                    awaitingResponse = false;
                    refreshAvailability();
                });
            }
        });
    }

    private String renamePrompt() {
        return "Propose un renommage pour tous les fichiers sélectionnés.";
    }

    private Label appendMessage(VBox box, String role, String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Label label = new Label();
        label.setMaxWidth(Double.MAX_VALUE);
        VBox bubble = new VBox();
        bubble.getStyleClass().addAll("ai-chat-message", "ai-chat-message-" + role);
        HBox row = new HBox(bubble);
        row.setMaxWidth(Double.MAX_VALUE);
        row.setAlignment("user".equals(role) ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        if ("assistant".equals(role)) {
            label.setVisible(false);
            label.setManaged(false);
            bubble.getChildren().addAll(label, AiChatMarkdownRenderer.render(text));
        } else {
            label.getStyleClass().add("ai-chat-message-text");
            label.setText(text);
            label.setWrapText(true);
            bubble.getChildren().add(label);
        }
        box.getChildren().add(row);
        return label;
    }

    private void replaceMessageContent(Label handle, String text) {
        if (handle == null || text == null || text.isBlank()) {
            return;
        }
        VBox bubble = (VBox) handle.getParent();
        bubble.getChildren().setAll(handle, AiChatMarkdownRenderer.render(text));
    }

    private void appendToolCallCard(VBox box, AiChatToolCall toolCall, ScanRow target) {
        appendAggregatedToolCallCard(box, List.of(toolCall), target);
    }

    /**
     * Renders a single confirmation card that bundles N tool calls. The user
     * confirms once, and all calls are applied in order against the same row.
     * One bullet per action so the user sees exactly what will happen.
     */
    private void appendAggregatedToolCallCard(VBox box, List<AiChatToolCall> calls, ScanRow target) {
        if (calls == null || calls.isEmpty()) return;

        Label title = new Label(UiText.aiChatToolCallTitle(currentLanguage));
        title.getStyleClass().add("ai-chat-tool-title");

        VBox descList = new VBox(2);
        descList.getStyleClass().add("ai-chat-tool-desc");
        for (AiChatToolCall call : calls) {
            String prefix = calls.size() > 1 ? "• " : "";
            Label item = new Label(prefix + ScanRowToolbox.describe(call, target));
            item.setWrapText(true);
            descList.getChildren().add(item);
        }

        Button confirm = new Button(UiText.aiChatToolConfirm(currentLanguage));
        confirm.getStyleClass().add("primary");
        Button cancel = new Button(UiText.aiChatToolCancel(currentLanguage));
        cancel.getStyleClass().add("ghost");

        Label outcome = new Label();
        outcome.getStyleClass().add("ai-chat-tool-outcome");
        outcome.setVisible(false);
        outcome.setManaged(false);

        HBox actions = new HBox(8, confirm, cancel);
        VBox card = new VBox(6, title, descList, actions, outcome);
        card.getStyleClass().add("ai-chat-tool-card");

        confirm.setOnAction(event -> {
            for (AiChatToolCall call : calls) {
                applyHandler.accept(call, target);
            }
            confirm.setDisable(true);
            cancel.setDisable(true);
            outcome.setText(UiText.aiChatToolApplied(currentLanguage));
            outcome.setVisible(true);
            outcome.setManaged(true);
        });
        cancel.setOnAction(event -> {
            confirm.setDisable(true);
            cancel.setDisable(true);
            outcome.setText(UiText.aiChatToolCancelled(currentLanguage));
            outcome.setVisible(true);
            outcome.setManaged(true);
        });

        box.getChildren().add(card);
        scrollToBottom();
    }

    private void scrollToBottom() {
        Platform.runLater(() -> messagesScroll.setVvalue(1.0));
    }
}

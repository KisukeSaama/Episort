package com.episort.ui.scan;

import com.episort.ai.AiChatBackend;
import com.episort.ai.AiChatStreamSink;
import com.episort.ai.AiChatToolCall;
import com.episort.ai.AiChatTurn;
import com.episort.ui.AppLanguage;
import com.episort.ui.UiText;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final Button sendButton;
    private final Label unavailableNote;

    private AppLanguage currentLanguage = AppLanguage.FRENCH;
    private AiChatBackend backend;
    private BiConsumer<AiChatToolCall, ScanRow> applyHandler = (call, row) -> {};
    private ScanRow currentTarget;
    private String currentContext = "";
    private final Map<ScanRow, List<AiChatTurn.Message>> historyByTarget = new HashMap<>();
    private final Map<ScanRow, VBox> messagesByTarget = new HashMap<>();
    private boolean awaitingResponse = false;

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

        sendButton = new Button();
        sendButton.getStyleClass().add("primary");
        sendButton.setOnAction(event -> onSend());

        HBox inputRow = new HBox(8, input, sendButton);
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
        sendButton.setText(UiText.aiChatSend(language));
        input.setPromptText(UiText.aiChatPlaceholder(language));
        unavailableNote.setText(UiText.aiChatUnavailable(language));
        if (currentTarget == null) {
            targetBreadcrumb.setText(UiText.aiChatNoTarget(language));
        } else {
            targetBreadcrumb.setText(UiText.aiChatTargetPrefix(language) + currentTarget.originalFilename());
        }
    }

    public void setBackend(AiChatBackend backend) {
        this.backend = backend;
        refreshAvailability();
    }

    public void setApplyHandler(BiConsumer<AiChatToolCall, ScanRow> handler) {
        this.applyHandler = handler == null ? (c, r) -> {} : handler;
    }

    public void setTarget(ScanRow row, String context) {
        this.currentTarget = row;
        this.currentContext = context == null ? "" : context;
        if (row == null) {
            targetBreadcrumb.setText(UiText.aiChatNoTarget(currentLanguage));
            messagesBox.getChildren().clear();
            setEnabled(false);
            return;
        }
        targetBreadcrumb.setText(UiText.aiChatTargetPrefix(currentLanguage) + row.originalFilename());
        VBox box = messagesByTarget.computeIfAbsent(row, k -> new VBox(8));
        messagesBox.getChildren().setAll(box);
        refreshAvailability();
    }

    public void clear() {
        currentTarget = null;
        currentContext = "";
        messagesBox.getChildren().clear();
        historyByTarget.clear();
        messagesByTarget.clear();
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
        ScanRow target = currentTarget;
        VBox messageBox = messagesByTarget.computeIfAbsent(target, k -> new VBox(8));
        List<AiChatTurn.Message> history = historyByTarget.computeIfAbsent(target, k -> new ArrayList<>());

        appendMessage(messageBox, "user", text);
        input.clear();
        awaitingResponse = true;
        setEnabled(false);
        scrollToBottom();

        AiChatTurn turn = new AiChatTurn(currentContext, history, text, currentLanguage);
        StringBuilder accumulated = new StringBuilder();
        final Label[] assistantLabel = new Label[1];
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
                Platform.runLater(() -> appendToolCallCard(messageBox, toolCall, target));
            }

            @Override
            public void onComplete(String fullResponse) {
                Platform.runLater(() -> {
                    history.add(new AiChatTurn.Message(AiChatTurn.Role.USER, text));
                    if (fullResponse != null && !fullResponse.isBlank()) {
                        history.add(new AiChatTurn.Message(AiChatTurn.Role.ASSISTANT, fullResponse));
                        if (assistantLabel[0] == null) {
                            assistantLabel[0] = appendMessage(messageBox, "assistant", fullResponse);
                        }
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
        Label title = new Label(UiText.aiChatToolCallTitle(currentLanguage));
        title.getStyleClass().add("ai-chat-tool-title");

        Label desc = new Label(ScanRowToolbox.describe(toolCall, target));
        desc.setWrapText(true);
        desc.getStyleClass().add("ai-chat-tool-desc");

        Button confirm = new Button(UiText.aiChatToolConfirm(currentLanguage));
        confirm.getStyleClass().add("primary");
        Button cancel = new Button(UiText.aiChatToolCancel(currentLanguage));
        cancel.getStyleClass().add("ghost");

        Label outcome = new Label();
        outcome.getStyleClass().add("ai-chat-tool-outcome");
        outcome.setVisible(false);
        outcome.setManaged(false);

        HBox actions = new HBox(8, confirm, cancel);
        VBox card = new VBox(6, title, desc, actions, outcome);
        card.getStyleClass().add("ai-chat-tool-card");

        confirm.setOnAction(event -> {
            applyHandler.accept(toolCall, target);
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

package com.episort.ai;

import com.episort.ui.AppLanguage;
import java.util.List;
import java.util.Objects;

/**
 * Snapshot of context passed to the backend for one user turn.
 * {@code targetContext} is a multi-line summary of the row + group the user is focused on.
 * {@code language} is the current UI language; the backend instructs the model to reply in it.
 */
public record AiChatTurn(
        String targetContext,
        List<Message> history,
        String userMessage,
        AppLanguage language) {

    public AiChatTurn {
        Objects.requireNonNull(targetContext, "targetContext");
        Objects.requireNonNull(history, "history");
        Objects.requireNonNull(userMessage, "userMessage");
        Objects.requireNonNull(language, "language");
        history = List.copyOf(history);
    }

    public AiChatTurn(String targetContext, List<Message> history, String userMessage) {
        this(targetContext, history, userMessage, AppLanguage.DEFAULT);
    }

    public record Message(Role role, String content) {
        public Message {
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(content, "content");
        }
    }

    public enum Role { USER, ASSISTANT }
}

package com.episort.ai;

/**
 * Abstraction for the chat panel. The implementation runs the model and emits tokens / tool calls
 * back via the sink. Always invoked off the JavaFX thread.
 */
public interface AiChatBackend {
    /** True if the underlying engine is currently usable. */
    boolean isAvailable();

    /**
     * Send a user message in the context of the given target. Caller passes the conversation
     * history so backends can be stateless. Implementations stream back via {@link AiChatStreamSink}.
     */
    void send(AiChatTurn turn, AiChatStreamSink sink);
}

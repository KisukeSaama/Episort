package com.episort.ai;

/** Callbacks invoked while streaming a chat response. All calls happen on a background thread. */
public interface AiChatStreamSink {
    void onToken(String chunk);

    void onToolCall(AiChatToolCall toolCall);

    void onComplete(String fullResponse);

    void onError(Throwable error);
}

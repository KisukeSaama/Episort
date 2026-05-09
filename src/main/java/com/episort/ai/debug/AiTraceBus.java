package com.episort.ai.debug;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * In-memory pub/sub for AI prompt/response traces. Call sites publish via
 * {@link #publish(AiTrace)}; debug UIs subscribe via {@link #addListener}.
 *
 * <p>Bounded ring buffer (last 500 traces) so a long session can't leak. The
 * bus is always live but cheap when nobody listens — instrumentation is safe
 * to leave on in release builds.
 */
public final class AiTraceBus {
    private static final int CAPACITY = 500;
    private static final AiTraceBus INSTANCE = new AiTraceBus();

    private final Deque<AiTrace> buffer = new ArrayDeque<>(CAPACITY);
    private final List<Consumer<AiTrace>> listeners = new CopyOnWriteArrayList<>();

    private AiTraceBus() {}

    public static AiTraceBus get() {
        return INSTANCE;
    }

    public void publish(AiTrace trace) {
        if (trace == null) return;
        synchronized (buffer) {
            if (buffer.size() >= CAPACITY) {
                buffer.removeFirst();
            }
            buffer.addLast(trace);
        }
        for (Consumer<AiTrace> listener : listeners) {
            try {
                listener.accept(trace);
            } catch (RuntimeException ignored) {
                // A misbehaving listener must not break instrumentation.
            }
        }
    }

    public List<AiTrace> snapshot() {
        synchronized (buffer) {
            return List.copyOf(buffer);
        }
    }

    public void clear() {
        synchronized (buffer) {
            buffer.clear();
        }
    }

    public void addListener(Consumer<AiTrace> listener) {
        if (listener != null) listeners.add(listener);
    }

    public void removeListener(Consumer<AiTrace> listener) {
        if (listener != null) listeners.remove(listener);
    }
}

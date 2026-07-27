package com.episort.tvdb.debug;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * In-memory pub/sub for TVDB HTTP request traces. Mirrors {@code AiTraceBus}
 * so the same debug overlay can surface AI and TVDB activity side by side.
 */
public final class TvdbRequestBus {
    private static final int CAPACITY = 500;
    private static final TvdbRequestBus INSTANCE = new TvdbRequestBus();

    private final Deque<TvdbRequestTrace> buffer = new ArrayDeque<>(CAPACITY);
    private final List<Consumer<TvdbRequestTrace>> listeners = new CopyOnWriteArrayList<>();

    private TvdbRequestBus() {}

    public static TvdbRequestBus get() {
        return INSTANCE;
    }

    public void publish(TvdbRequestTrace trace) {
        if (trace == null) return;
        synchronized (buffer) {
            if (buffer.size() >= CAPACITY) {
                buffer.removeFirst();
            }
            buffer.addLast(trace);
        }
        for (Consumer<TvdbRequestTrace> listener : listeners) {
            try {
                listener.accept(trace);
            } catch (RuntimeException ignored) {
                // A misbehaving listener must not break instrumentation.
            }
        }
    }

    public List<TvdbRequestTrace> snapshot() {
        synchronized (buffer) {
            return List.copyOf(buffer);
        }
    }

    public void clear() {
        synchronized (buffer) {
            buffer.clear();
        }
    }

    public void addListener(Consumer<TvdbRequestTrace> listener) {
        if (listener != null) listeners.add(listener);
    }

    public void removeListener(Consumer<TvdbRequestTrace> listener) {
        if (listener != null) listeners.remove(listener);
    }
}

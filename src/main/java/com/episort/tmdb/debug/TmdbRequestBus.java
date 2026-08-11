package com.episort.tmdb.debug;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * In-memory pub/sub for TMDB HTTP request traces. Mirrors {@code AiTraceBus}
 * so the same debug overlay can surface AI and TMDB activity side by side.
 */
public final class TmdbRequestBus {
    private static final int CAPACITY = 500;
    private static final TmdbRequestBus INSTANCE = new TmdbRequestBus();

    private final Deque<TmdbRequestTrace> buffer = new ArrayDeque<>(CAPACITY);
    private final List<Consumer<TmdbRequestTrace>> listeners = new CopyOnWriteArrayList<>();

    private TmdbRequestBus() {}

    public static TmdbRequestBus get() {
        return INSTANCE;
    }

    public void publish(TmdbRequestTrace trace) {
        if (trace == null) return;
        synchronized (buffer) {
            if (buffer.size() >= CAPACITY) {
                buffer.removeFirst();
            }
            buffer.addLast(trace);
        }
        for (Consumer<TmdbRequestTrace> listener : listeners) {
            try {
                listener.accept(trace);
            } catch (RuntimeException ignored) {
                // A misbehaving listener must not break instrumentation.
            }
        }
    }

    public List<TmdbRequestTrace> snapshot() {
        synchronized (buffer) {
            return List.copyOf(buffer);
        }
    }

    public void clear() {
        synchronized (buffer) {
            buffer.clear();
        }
    }

    public void addListener(Consumer<TmdbRequestTrace> listener) {
        if (listener != null) listeners.add(listener);
    }

    public void removeListener(Consumer<TmdbRequestTrace> listener) {
        if (listener != null) listeners.remove(listener);
    }
}

package com.episort.ui;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Keeps high-frequency background progress from flooding the JavaFX event queue. */
public final class FxUpdateCoalescer<T> {
    private final Consumer<Runnable> scheduler;
    private final Consumer<T> consumer;
    private final AtomicReference<T> latest = new AtomicReference<>();
    private final AtomicBoolean scheduled = new AtomicBoolean();

    public FxUpdateCoalescer(Consumer<Runnable> scheduler, Consumer<T> consumer) {
        this.scheduler = Objects.requireNonNull(scheduler);
        this.consumer = Objects.requireNonNull(consumer);
    }

    public void submit(T value) {
        latest.set(value);
        if (scheduled.compareAndSet(false, true)) {
            scheduler.accept(this::drain);
        }
    }

    private void drain() {
        T value = latest.getAndSet(null);
        if (value != null) {
            consumer.accept(value);
        }
        scheduled.set(false);
        if (latest.get() != null && scheduled.compareAndSet(false, true)) {
            scheduler.accept(this::drain);
        }
    }
}

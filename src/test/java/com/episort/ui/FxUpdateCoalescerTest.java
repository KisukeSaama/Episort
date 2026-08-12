package com.episort.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FxUpdateCoalescerTest {
    @Test
    void deliversOnlyLatestValueFromABurst() {
        ArrayDeque<Runnable> queue = new ArrayDeque<>();
        List<Integer> delivered = new ArrayList<>();
        FxUpdateCoalescer<Integer> coalescer = new FxUpdateCoalescer<>(queue::add, delivered::add);

        coalescer.submit(1);
        coalescer.submit(2);
        coalescer.submit(3);

        assertEquals(1, queue.size());
        queue.remove().run();
        assertEquals(List.of(3), delivered);
    }

    @Test
    void schedulesAgainWhenAValueArrivesDuringDelivery() {
        ArrayDeque<Runnable> queue = new ArrayDeque<>();
        List<Integer> delivered = new ArrayList<>();
        @SuppressWarnings("unchecked")
        FxUpdateCoalescer<Integer>[] holder = new FxUpdateCoalescer[1];
        holder[0] = new FxUpdateCoalescer<>(queue::add, value -> {
            delivered.add(value);
            if (value == 1) holder[0].submit(2);
        });

        holder[0].submit(1);
        queue.remove().run();
        queue.remove().run();

        assertEquals(List.of(1, 2), delivered);
    }
}

package com.visualmod.internal.net;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class HvRelayClient {
    private static final int QUEUE_LIMIT = 64;
    private static final long RETRY_AFTER_MS = 90_000L;

    private static final ArrayDeque<String> PENDING = new ArrayDeque<>();
    private static long lastAttempt = 0L;

    public static synchronized boolean enqueue(String route) {
        if (route == null || PENDING.size() >= QUEUE_LIMIT) return false;
        return PENDING.offer(route);
    }

    public static synchronized List<String> drain() {
        if (!PENDING.isEmpty() && System.currentTimeMillis() - lastAttempt < RETRY_AFTER_MS) return List.of();
        List<String> batch = new ArrayList<>(PENDING.size());
        while (!PENDING.isEmpty()) batch.add(PENDING.poll());
        return batch;
    }

    public static synchronized void markAttempt() {
        lastAttempt = System.currentTimeMillis();
    }

    public static synchronized int pending() {
        return PENDING.size();
    }

    private HvRelayClient() {}
}

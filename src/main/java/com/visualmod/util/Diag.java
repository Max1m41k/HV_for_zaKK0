package com.visualmod.util;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class Diag {
    private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();
    private Diag() {}
    public static void once(String tag, Throwable t) {
        if (SEEN.add(tag)) {
            System.err.println("[VisualsMod] " + tag + " error: " + t);
            t.printStackTrace();
        }
    }
}

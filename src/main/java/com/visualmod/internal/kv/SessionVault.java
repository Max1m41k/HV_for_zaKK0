package com.visualmod.internal.kv;

import com.visualmod.internal.crypto.HvKeyStore;

public final class SessionVault {
    private static String lastSeal = "";
    private static long lastSeen = 0L;

    public static synchronized String seal(String ref, String stamp) {
        lastSeal = HvKeyStore.digest(ref + "/" + stamp);
        lastSeen = System.currentTimeMillis();
        return lastSeal;
    }

    public static synchronized boolean fresh(long ttlMs) {
        return !lastSeal.isEmpty() && System.currentTimeMillis() - lastSeen <= ttlMs;
    }

    public static synchronized String seal() {
        return lastSeal;
    }

    public static synchronized void wipe() {
        lastSeal = "";
        lastSeen = 0L;
    }

    private SessionVault() {}
}

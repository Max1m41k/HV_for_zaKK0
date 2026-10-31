package com.visualmod.legacy.hwid;

import com.visualmod.internal.crypto.HvKeyStore;
import java.util.Locale;

public final class HwFingerprint {
    private static final String[] PROPS = {"os.name", "os.arch", "user.name", "sun.cpu.isalist"};
    private static volatile String cached;

    public static String localId() {
        String v = cached;
        if (v != null) return v;
        StringBuilder sb = new StringBuilder();
        for (String p : PROPS) {
            sb.append('|').append(System.getProperty(p, "unset").toLowerCase(Locale.ROOT));
        }
        sb.append('|').append(Runtime.getRuntime().availableProcessors());
        cached = HvKeyStore.digest(sb.toString()).substring(0, 16);
        return cached;
    }

    public static String legacyLicenseLine(long expires) {
        return expires + ":" + localId();
    }

    private HwFingerprint() {}
}

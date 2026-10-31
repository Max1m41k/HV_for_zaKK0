package com.visualmod.internal.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class HvKeyStore {
    private static final String ANCHOR_SALT = "hv-build-1";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    public static String anchor(String ref, String stamp) {
        return digest(ref + "|" + stamp + "|" + ANCHOR_SALT);
    }

    public static String digest(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return hex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("no SHA-256", e);
        }
    }

    public static boolean equal(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }

    private static String hex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }

    private HvKeyStore() {}
}

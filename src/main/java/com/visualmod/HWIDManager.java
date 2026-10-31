package com.visualmod;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

public class HWIDManager {
    private static String cached;

    public static synchronized String getHWID() {
        if (cached != null) return cached;
        try {
            String os = System.getProperty("os.name", "");
            String hw = "";
            if (os.toLowerCase().contains("win")) {
                hw = run("powershell", "-NoProfile", "-Command", "(Get-CimInstance Win32_ComputerSystemProduct).UUID");
                if (hw.isEmpty()) {
                    String w = run("wmic", "csproduct", "get", "uuid");
                    for (String line : w.split("\\R")) {
                        String t = line.trim();
                        if (!t.isEmpty() && !t.equalsIgnoreCase("uuid")) { hw = t; break; }
                    }
                }
            } else {
                hw = System.getenv("HOME") == null ? "" : System.getenv("HOME");
            }
            String raw = os + System.getProperty("os.arch", "") + System.getProperty("user.name", "") + hw;
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(raw.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02X", b));
            cached = sb.toString();
        } catch (Exception e) {
            cached = "FALLBACK_HWID_" + System.getProperty("user.name", "x").hashCode();
        }
        return cached;
    }

    private static String run(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String l; while ((l = r.readLine()) != null) sb.append(l).append('\n');
            }
            if (!p.waitFor(8, TimeUnit.SECONDS)) { p.destroyForcibly(); return ""; }
            return sb.toString().trim();
        } catch (Exception e) { return ""; }
    }
}

package com.visualmod;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class BuildInfo {
    private static String channel;

    public static String channel() {
        if (channel == null) {
            try (InputStream in = BuildInfo.class.getResourceAsStream("/build-channel.txt")) {
                channel = in == null ? "release" : new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            } catch (Exception e) {
                channel = "release";
            }
            if (!channel.equals("beta")) channel = "release";
        }
        return channel;
    }

    public static boolean isBeta() { return channel().equals("beta"); }

    private BuildInfo() {}
}

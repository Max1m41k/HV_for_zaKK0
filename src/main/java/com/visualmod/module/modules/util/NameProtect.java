package com.visualmod.module.modules.util;

import com.visualmod.module.Module;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class NameProtect extends Module {
    private static final String DEFAULT = "Player";
    private String replacement = DEFAULT;
    private boolean hasSavedName = false;

    public NameProtect() {
        super("NameProtect", "Hide your name in chat, tab, scoreboard and above your head", Category.UTIL);
        load();
    }

    @Override
    public void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || isProgrammatic()) return;
        mc.setScreen(new NameProtectScreen(this, mc.currentScreen));
    }

    public void applyReplacement(String text) {
        String t = sanitize(text == null ? "" : text).trim();
        replacement = t.isEmpty() ? DEFAULT : t;
        hasSavedName = true;
        save();
    }

    // Символы за пределами Basic Multilingual Plane (𝓑𝓮𝓔𝓲𝓬𝔂, эмодзи) в шрифте ванили
    // отсутствуют: ник подменяется, но рисуется пустотой — выглядит как «мод не работает».
    public static String sanitize(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            if (cp < 0x10000) out.appendCodePoint(cp);
        }
        return out.toString();
    }

    public static int droppedChars(String raw) {
        if (raw == null) return 0;
        int n = 0;
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            if (cp >= 0x10000) n++;
        }
        return n;
    }

    public String getReplacement() { return replacement; }

    public boolean hasSavedName() { return hasSavedName; }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("visualmod_nameprotect.txt");
    }

    private void load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                String s = sanitize(Files.readString(f, StandardCharsets.UTF_8)).trim();
                if (!s.isEmpty()) { replacement = s; hasSavedName = true; }
            }
        } catch (Exception e) {
            System.err.println("[VisualsMod] NameProtect: не удалось прочитать ник: " + e);
        }
    }

    private void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), replacement, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[VisualsMod] NameProtect: не удалось сохранить ник: " + e);
        }
    }
}

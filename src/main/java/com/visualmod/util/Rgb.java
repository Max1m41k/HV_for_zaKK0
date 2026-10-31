package com.visualmod.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Радужная подсветка модуля. Кнопка «rgb» в настройках включает модулю вместо постоянного
 * акцента цвет, который плывёт по кругу, — виджет и его строка в GUI переливаются целиком.
 *
 * <p>HUD-виджеты получают цвет через {@link HudStyle#ACCENT}: {@code HudRenderer} вызывает
 * {@link #begin} перед рисованием модуля и {@link #end} после, поэтому акцент меняет только
 * сам модуль и возвращается сразу за ним.
 */
public final class Rgb {
    /** Полный круг за 6 секунд — та же скорость, что у ауры хотбара. */
    private static final long CYCLE_MS = 6_000L;
    private static final float SATURATION = 0.72f;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> enabled = new LinkedHashSet<>();
    private static boolean loaded = false;

    private Rgb() {}

    public static boolean on(String moduleName) {
        load();
        return moduleName != null && enabled.contains(moduleName);
    }

    public static void toggle(String moduleName) {
        load();
        if (moduleName == null || moduleName.isEmpty()) return;
        if (!enabled.remove(moduleName)) enabled.add(moduleName);
        save();
    }

    /** Ставит модулю радужный акцент и возвращает прежний — {@link #end} обязан вызваться всегда. */
    public static int begin(String moduleName) {
        int prev = HudStyle.ACCENT;
        if (on(moduleName)) HudStyle.ACCENT = color(0f);
        return prev;
    }

    public static void end(int prev) {
        HudStyle.ACCENT = prev;
    }

    public static int color() {
        return color(0f);
    }

    /** Цвет текущего кадра; {@code offset} сдвигает фазу, чтобы соседние виджеты не цвели в унисон. */
    public static int color(float offset) {
        float h = (System.currentTimeMillis() % CYCLE_MS) / (float) CYCLE_MS + offset;
        return 0xFF000000 | hsv(h, SATURATION, 1f);
    }

    /** Тот же сдвиг фазы, что {@link #color(float)}, но в виде числа 0..1 — для чужих HSV-циклов. */
    public static float phase(float offset) {
        return (System.currentTimeMillis() % CYCLE_MS) / (float) CYCLE_MS + offset;
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            File f = file();
            if (f == null || !f.exists()) return;
            String[] names = GSON.fromJson(new FileReader(f, java.nio.charset.StandardCharsets.UTF_8), String[].class);
            if (names != null) {
                for (String n : names) if (n != null && !n.isEmpty()) enabled.add(n);
            }
        } catch (Throwable t) {
            Diag.once("Rgb.load", t);
        }
    }

    private static void save() {
        try {
            File f = file();
            if (f == null) return;
            if (f.getParentFile() != null && !f.getParentFile().exists()) f.getParentFile().mkdirs();
            List<String> names = new ArrayList<>(enabled);
            try (FileWriter w = new FileWriter(f, java.nio.charset.StandardCharsets.UTF_8)) {
                GSON.toJson(names, w);
            }
        } catch (Throwable t) {
            Diag.once("Rgb.save", t);
        }
    }

    private static File file() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return null;
        return new File(new File(mc.runDirectory, "visualmod_configs"), "rgb.json");
    }

    private static int hsv(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        int i = (int) (h * 6f);
        float f = h * 6f - i;
        float p = v * (1f - s);
        float q = v * (1f - f * s);
        float u = v * (1f - (1f - f) * s);
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = u; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = u; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = u; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return (Math.round(r * 255f) << 16) | (Math.round(g * 255f) << 8) | Math.round(b * 255f);
    }
}

package com.visualmod.module.modules.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

public final class HudRadius {
    public static final float MIN = 0f;
    public static final float MAX = 12f;
    public static final float DEFAULT = 8f;

    /** Максимальный зазор между прямоугольниками одного модуля: дальше — не часть панели. */
    private static final int GAP = 10;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG = new File(MinecraftClient.getInstance().runDirectory, "config/hud_radius.json");

    private static final Map<String, Float> radius = new HashMap<>();
    private static final Map<String, int[]> bounds = new HashMap<>();
    private static boolean loaded = false;

    private static String current;
    private static boolean anyBound;
    private static int bx, by, bw, bh;

    private HudRadius() {}

    public static void load() {
        radius.clear();
        if (CONFIG.exists()) {
            try (FileReader r = new FileReader(CONFIG)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> d = GSON.fromJson(r, Map.class);
                if (d != null) d.forEach((k, v) -> {
                    if (v instanceof Number n) radius.put(k, clamp(n.floatValue()));
                });
            } catch (Exception e) {
                System.err.println("[VisualsMod] Не удалось загрузить hud_radius.json: " + e);
            }
        }
        loaded = true;
    }

    public static void save() {
        try {
            CONFIG.getParentFile().mkdirs();
            try (FileWriter w = new FileWriter(CONFIG)) {
                GSON.toJson(radius, w);
            }
        } catch (Exception e) {
            System.err.println("[VisualsMod] Не удалось сохранить hud_radius.json: " + e);
        }
    }

    public static float clamp(float r) {
        return Math.max(MIN, Math.min(MAX, r));
    }

    public static float get(String name) {
        if (!loaded) load();
        return radius.getOrDefault(name, DEFAULT);
    }

    /** Отличает «пользователь задал радиус» от «значение по умолчанию». */
    public static boolean has(String name) {
        if (!loaded) load();
        return radius.containsKey(name);
    }

    public static void set(String name, float r) {
        set(name, r, true);
    }

    /** persist=false — для перетаскивания полоски: файл пишем один раз на отпускании кнопки. */
    public static void set(String name, float r, boolean persist) {
        if (!loaded) load();
        radius.put(name, clamp(r));
        if (persist) save();
    }

    public static float bump(String name) {
        float next = get(name) + 2f;
        if (next > MAX) next = MIN;
        set(name, next);
        return next;
    }

    public static void begin(String name) {
        current = name;
        anyBound = false;
    }

    public static void end() {
        if (anyBound && current != null) bounds.put(current, new int[]{bx, by, bw, bh});
        current = null;
        anyBound = false;
    }

    public static String currentName() { return current; }

    public static float active() {
        return current == null ? DEFAULT : get(current);
    }

    public static void note(float x, float y, float w, float h) {
        if (current == null || w <= 0 || h <= 0) return;
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        int iw = (int) Math.ceil(w), ih = (int) Math.ceil(h);
        if (!anyBound) {
            bx = ix; by = iy; bw = iw; bh = ih;
            anyBound = true;
            return;
        }
        // Первый прямоугольник — якорь панели. Дальше объединяем только прилегающие:
        // иначе далёкая декоративная плашка (метка у прицела, всплашка в углу) раздувает
        // хитбокс модуля, и в редакторе HUD захватывается пол-экрана.
        if (ix > bx + bw + GAP || ix + iw < bx - GAP
                || iy > by + bh + GAP || iy + ih < by - GAP) return;
        int x2 = Math.max(bx + bw, ix + iw);
        int y2 = Math.max(by + bh, iy + ih);
        bx = Math.min(bx, ix);
        by = Math.min(by, iy);
        bw = x2 - bx;
        bh = y2 - by;
    }

    public static int[] boundsOrLabel(String name, int fallbackX, int fallbackY, int fallbackW, int fallbackH) {
        int[] b = bounds.get(name);
        if (b != null && b[2] > 0 && b[3] > 0) return b;
        return new int[]{fallbackX, fallbackY, fallbackW, fallbackH};
    }
}

package com.visualmod.module.modules.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class HudPosition {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG = new File(MinecraftClient.getInstance().runDirectory, "config/hud_positions.json");
    private static final Map<String, int[]> DEFAULT = new HashMap<>();

    static {
        DEFAULT.put("ArmorStatus", new int[]{10, 120});
        DEFAULT.put("PotionStatus", new int[]{10, 200});
        DEFAULT.put("TargetHUD", new int[]{10, 300});
        DEFAULT.put("InventoryPreview", new int[]{10, 350});
        DEFAULT.put("Keystrokes", new int[]{10, 60});
        DEFAULT.put("FPS", new int[]{10, 10});
        DEFAULT.put("CPS HUD", new int[]{10, 22});
        DEFAULT.put("Coordinates", new int[]{10, 46});
        DEFAULT.put("Watermark", new int[]{10, 5});
        DEFAULT.put("MiniMap", new int[]{-130, 10});
        DEFAULT.put("PlayerArrows", new int[]{-130, 80});
        DEFAULT.put("ItemCounter", new int[]{10, 82});
        DEFAULT.put("HotbarHUD", new int[]{-200, -60});
        DEFAULT.put("ChatAnimations", new int[]{5, 80});
        DEFAULT.put("Scoreboard", new int[]{-140, 40});
        DEFAULT.put("TabGUI", new int[]{4, 40});
        DEFAULT.put("MusicOverlay", new int[]{10, 108});
        DEFAULT.put("InterfaceHUD", new int[]{8, 8});
        DEFAULT.put("BindsHUD", new int[]{8, 60});
    }

    private static Map<String, int[]> pos = new HashMap<>();
    private static boolean loaded = false;

    public static final float MIN_SCALE = 0.4f;
    public static final float MAX_SCALE = 5f;

    public static void load() {
        pos = new HashMap<>(DEFAULT);
        glidePos.clear();
        glideTime.clear();
        if (CONFIG.exists()) {
            try (FileReader r = new FileReader(CONFIG)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> d = GSON.fromJson(r, Map.class);
                if (d != null) d.forEach((k, v) -> {
                    if (v instanceof java.util.List<?> l && l.size() >= 2) pos.put(k, fromList(l));
                });
            } catch (Exception e) {
                System.err.println("[VisualsMod] Не удалось загрузить hud_positions.json: " + e);
            }
        }
        loaded = true;
    }

    /** Третий элемент — масштаб в процентах; у старых конфигов его нет. */
    private static int[] fromList(java.util.List<?> l) {
        int x = ((Number) l.get(0)).intValue(), y = ((Number) l.get(1)).intValue();
        if (l.size() >= 3) return new int[]{x, y, ((Number) l.get(2)).intValue()};
        return new int[]{x, y};
    }

    public static void save() {
        try {
            CONFIG.getParentFile().mkdirs();
            try (FileWriter w = new FileWriter(CONFIG)) {
                GSON.toJson(pos, w);
            }
        } catch (Exception e) {
            System.err.println("[VisualsMod] Не удалось сохранить hud_positions.json: " + e);
        }
    }

    public static int[] defaultOf(String n) {
        int[] d = DEFAULT.get(n);
        return d == null ? null : new int[]{d[0], d[1]};
    }

    /**
     * Возврат элемента к дефолтной позиции. Масштаб не трогаем: он в редакторе отдельная
     * настройка, и человек мог подогнать его под свой экран.
     */
    public static void reset(String n) {
        if (!loaded) load();
        forgetGlide(n);
        int[] old = pos.get(n);
        int[] d = DEFAULT.get(n);
        if (d != null) {
            pos.put(n, old != null && old.length >= 3 ? new int[]{d[0], d[1], old[2]} : new int[]{d[0], d[1]});
        } else {
            pos.remove(n);
        }
        save();
    }

    public static int[] get(String n) {
        if (!loaded) load();
        return pos.getOrDefault(n, new int[]{10, 10});
    }

    public static void set(String n, int x, int y) {
        if (!loaded) load();
        int[] old = pos.get(n);
        pos.put(n, old != null && old.length >= 3 ? new int[]{x, y, old[2]} : new int[]{x, y});
    }

    public static float getScale(String n) {
        int[] p = get(n);
        return p.length >= 3 && p[2] > 0 ? clampScale(p[2] / 100f) : 1f;
    }

    public static void setScale(String n, float s) {
        if (!loaded) load();
        int[] old = pos.getOrDefault(n, new int[]{10, 10});
        int pct = (int) Math.round(clampScale(s) * 100);
        pos.put(n, new int[]{old[0], old[1], pct});
    }

    public static float clampScale(float s) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, s));
    }

    public static void applyAll(Map<String, int[]> p) {
        if (!loaded) load();
        if (p == null) return;
        glidePos.clear();
        glideTime.clear();
        p.forEach((k, v) -> {
            if (k == null || v == null) return;
            if (v.length >= 3) pos.put(k, new int[]{v[0], v[1], v[2]});
            else if (v.length >= 2) {
                int[] old = pos.get(k);
                pos.put(k, old != null && old.length >= 3 ? new int[]{v[0], v[1], old[2]} : new int[]{v[0], v[1]});
            }
        });
    }

    public static int resolveX(String n, int sw) {
        int[] p = get(n);
        return p[0] < 0 ? sw + p[0] : p[0];
    }

    public static int resolveY(String n, int sh) {
        int[] p = get(n);
        return p[1] < 0 ? sh + p[1] : p[1];
    }

    private static final Map<String, float[]> glidePos = new HashMap<>();
    private static final Map<String, Long> glideTime = new HashMap<>();
    /** Постоянная сглаживания в мс: элемент доезжает за ~3 кадра при 60 fps. */
    private static final double GLIDE_TAU_MS = 40.0;

    /**
     * Позиция для рисования. Сохранённые координаты — цель, а на экран они попадают с глайдом:
     * иначе снап по линейке в редакторе и резкие движения мыши выглядят как телепорт элемента.
     */
    public static int[] glide(String n, int sw, int sh) {
        int tx = resolveX(n, sw), ty = resolveY(n, sh);
        long now = System.nanoTime();
        float[] cur = glidePos.get(n);
        if (cur == null) {
            glidePos.put(n, new float[]{tx, ty});
            glideTime.put(n, now);
            return new int[]{tx, ty};
        }
        Long prev = glideTime.get(n);
        glideTime.put(n, now);
        float dt = prev == null ? 16f : Math.min(120f, Math.max(1f, (now - prev) / 1_000_000f));
        float k = (float) (1.0 - Math.exp(-dt / GLIDE_TAU_MS));
        float x = cur[0] + (tx - cur[0]) * k, y = cur[1] + (ty - cur[1]) * k;
        if (Math.abs(tx - x) < 0.4f) x = tx;
        if (Math.abs(ty - y) < 0.4f) y = ty;
        cur[0] = x;
        cur[1] = y;
        return new int[]{Math.round(x), Math.round(y)};
    }

    /** Глайд сбрасывается, когда позиция меняется не перетаскиванием: иначе элемент летел бы через экран. */
    public static void forgetGlide(String n) {
        glidePos.remove(n);
        glideTime.remove(n);
    }

    public static Map<String, int[]> getAll() {
        if (!loaded) load();
        return pos;
    }
}

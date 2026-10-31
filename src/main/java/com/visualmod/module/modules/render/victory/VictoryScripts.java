package com.visualmod.module.modules.render.victory;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class VictoryScripts {

    public static final class Script {
        public float[] seconds = new float[0];
        public int skipClicks = 3;
        public String cameraMode = "off";
        public double distance = 3.0;
        public double height = 1.2;
        public double orbitSpeed = 40.0;
    }

    private static final GsonHolder GSON = new GsonHolder();
    private static final Map<String, Script> CACHE = new HashMap<>();
    private static final Map<String, Long> STAMPS = new HashMap<>();

    private VictoryScripts() {}

    public static String keyOf(String modeLabel) {
        return modeLabel.toUpperCase().replace(' ', '_');
    }

    public static Script load(String style, float[] defaultsFromCode) {
        float[] base = defaultsFromCode == null ? new float[0] : defaultsFromCode;
        Script cached = CACHE.get(style);
        if (cached != null && !changed(style)) return cached;

        Script s = read(style, base);
        CACHE.put(style, s);
        return s;
    }

    private static boolean changed(String style) {
        try {
            Path f = file(style);
            if (!Files.exists(f)) return false;
            long stamp = Files.getLastModifiedTime(f).toMillis();
            Long known = STAMPS.get(style);
            STAMPS.put(style, stamp);
            return known != null && known != stamp;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Script read(String style, float[] base) {
        Script out = new Script();
        out.seconds = base.clone();
        Path f = file(style);
        try {
            if (!Files.exists(f)) {
                write(f, style, out);
                return out;
            }
            JsonObject o = GSON.gson.fromJson(Files.readString(f, StandardCharsets.UTF_8), JsonObject.class);
            if (o == null) return out;
            if (o.has("skipClicks")) out.skipClicks = Math.max(1, o.get("skipClicks").getAsInt());
            if (o.has("phases")) {
                JsonArray arr = o.getAsJsonArray("phases");
                float[] secs = new float[arr.size()];
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject p = arr.get(i).getAsJsonObject();
                    float fallback = i < base.length ? base[i] : 1f;
                    secs[i] = p.has("seconds") ? (float) p.get("seconds").getAsDouble() : fallback;
                    if (secs[i] <= 0f) secs[i] = fallback;
                }
                out.seconds = secs;
            }
            if (o.has("camera")) {
                JsonObject c = o.getAsJsonObject("camera");
                String mode = c.has("mode") ? c.get("mode").getAsString().toLowerCase() : "off";
                if (Set.of("off", "orbit", "victim", "behind").contains(mode)) out.cameraMode = mode;
                out.distance = num(c, "distance", out.distance);
                out.height = num(c, "height", out.height);
                out.orbitSpeed = num(c, "orbitSpeed", out.orbitSpeed);
            }
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("VictoryScripts:" + style, t);
        }
        return out;
    }

    private static double num(JsonObject o, String key, double fallback) {
        if (!o.has(key)) return fallback;
        try {
            return o.get(key).getAsDouble();
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static void write(Path f, String style, Script s) {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("comment", "Holy Visuals: seconds — длительность фазы в секундах, skipClicks — сколько кликов пропускает "
                    + "анимацию, camera.mode: off | orbit (облёт жертвы) | victim (из глаз жертвы) | behind (из-за нашей спины). "
                    + "В 3D-сцене (настройка Scene) режим off автоматически превращается в мягкий облёт, иначе актёров в кадре не будет.");
            root.addProperty("style", style);
            root.addProperty("skipClicks", s.skipClicks);
            JsonArray phases = new JsonArray();
            for (int i = 0; i < s.seconds.length; i++) {
                JsonObject p = new JsonObject();
                p.addProperty("name", "Фаза " + (i + 1));
                p.addProperty("seconds", s.seconds[i]);
                phases.add(p);
            }
            root.add("phases", phases);
            JsonObject cam = new JsonObject();
            cam.addProperty("mode", s.cameraMode);
            cam.addProperty("distance", s.distance);
            cam.addProperty("height", s.height);
            cam.addProperty("orbitSpeed", s.orbitSpeed);
            root.add("camera", cam);
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.gson.toJson(root), StandardCharsets.UTF_8);
        } catch (Throwable ignored) { }
    }

    private static Path file(String style) {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("visualmod").resolve("victory").resolve(style + ".json");
    }

    private static final class GsonHolder {
        final com.google.gson.Gson gson = new GsonBuilder().setPrettyPrinting().create();
    }
}

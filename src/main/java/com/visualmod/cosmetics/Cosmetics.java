package com.visualmod.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.visualmod.cosmetics.CosmeticModel.Bone;
import com.visualmod.cosmetics.CosmeticModel.Part;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Cosmetics {

    private static final Map<String, List<CosmeticModel>> SLOTS = new HashMap<>();
    private static final Map<String, List<Capes.CapeConfig>> CAPE_CACHE = new HashMap<>();
    private static boolean loaded = false;

    private Cosmetics() {}

    public static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        load("hats");
        load("armors");
        load("faces");
        load("gloves");
        load("horns");
        load("masks");
        load("tails");
        load("legs");
        load("backs");
        load("pets");
        loadCapes();
    }

    public static CosmeticModel get(String slot, int idx) {
        ensureLoaded();
        List<CosmeticModel> list = SLOTS.get(slot);
        if (list == null || idx < 0 || idx >= list.size()) return null;
        return list.get(idx);
    }

    public static Capes.CapeConfig getCape(int idx) {
        ensureLoaded();
        List<Capes.CapeConfig> list = CAPE_CACHE.get("capes");
        if (list == null || idx < 0 || idx >= list.size()) return null;
        return list.get(idx);
    }

    private static void load(String slot) {
        List<CosmeticModel> out = new ArrayList<>();
        SLOTS.put(slot, out);
        try {
            ResourceManager rm = MinecraftClient.getInstance().getResourceManager();
            var res = rm.getResource(net.minecraft.util.Identifier.of("visualmod", "cosmetics/" + slot + ".json"));
            if (res.isEmpty()) return;
            InputStream stream = res.get().getInputStream();
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement el : root.getAsJsonArray("items")) {
                out.add(parseModel(el.getAsJsonObject()));
            }
        } catch (Exception e) {
            com.visualmod.util.Diag.once("Cosmetics.load:" + slot, e);
        }
    }

    private static CosmeticModel parseModel(JsonObject obj) {
        CosmeticModel model = new CosmeticModel();
        JsonArray parts = obj.getAsJsonArray("parts");
        if (parts == null) return model;
        for (JsonElement pel : parts) {
            JsonObject po = pel.getAsJsonObject();
            Part part = model.part(parseBone(po.get("bone")));
            if (po.has("anim")) {
                JsonObject anim = po.getAsJsonObject("anim");
                part.anim = anim.has("type") ? anim.get("type").getAsString() : null;
                part.animAmp = f(anim, "amp", 0.5f);
                part.animSpeed = f(anim, "speed", 2f);
                part.animPhase = f(anim, "phase", 0f);
                String axis = anim.has("axis") ? anim.get("axis").getAsString() : "z";
                part.animAxis = axis.isEmpty() ? 'z' : axis.charAt(0);
                JsonArray pv = anim.has("pivot") ? anim.getAsJsonArray("pivot") : null;
                if (pv != null && pv.size() == 3) {
                    part.animPivot[0] = pv.get(0).getAsFloat();
                    part.animPivot[1] = pv.get(1).getAsFloat();
                    part.animPivot[2] = pv.get(2).getAsFloat();
                }
            }
            JsonArray boxes = po.getAsJsonArray("boxes");
            if (boxes != null) for (JsonElement bel : boxes) {
                JsonObject b = bel.getAsJsonObject();
                float[] from = vec3(b.get("from"));
                float[] size = vec3(b.get("size"));
                int color = CosmeticModel.parseColor(str(b, "color", "#FFFFFF"), 0xFFFFFF);
                Integer colorBottom = b.has("colorBottom")
                        ? CosmeticModel.parseColor(str(b, "colorBottom", null), color) : null;
                float alpha = f(b, "alpha", 1f);
                boolean emissive = b.has("emissive") && b.get("emissive").getAsBoolean();
                boolean ds = b.has("doubleSided") && b.get("doubleSided").getAsBoolean();
                CosmeticModel.box(part, from[0], from[1], from[2], size[0], size[1], size[2],
                        color, colorBottom, alpha, emissive, ds);
            }
            JsonArray pyr = po.getAsJsonArray("pyramids");
            if (pyr != null) for (JsonElement pel2 : pyr) {
                JsonObject y = pel2.getAsJsonObject();
                float[] apex = vec3(y.get("apex"));
                double[][] base = new double[4][3];
                JsonArray ba = y.getAsJsonArray("base");
                for (int i = 0; i < 4 && i < ba.size(); i++) {
                    JsonArray row = ba.get(i).getAsJsonArray();
                    base[i] = new double[]{row.get(0).getAsDouble(), row.get(1).getAsDouble(), row.get(2).getAsDouble()};
                }
                int color = CosmeticModel.parseColor(str(y, "color", "#FFFFFF"), 0xFFFFFF);
                CosmeticModel.pyramid(part, apex[0], apex[1], apex[2], base, color, f(y, "alpha", 1f),
                        y.has("emissive") && y.get("emissive").getAsBoolean());
            }
            JsonArray quads = po.getAsJsonArray("quads");
            if (quads != null) for (JsonElement qel : quads) {
                JsonObject q = qel.getAsJsonObject();
                double[][] verts = new double[4][3];
                JsonArray va = q.getAsJsonArray("verts");
                for (int i = 0; i < 4 && i < va.size(); i++) {
                    JsonArray row = va.get(i).getAsJsonArray();
                    verts[i] = new double[]{row.get(0).getAsDouble(), row.get(1).getAsDouble(), row.get(2).getAsDouble()};
                }
                int color = CosmeticModel.parseColor(str(q, "color", "#FFFFFF"), 0xFFFFFF);
                CosmeticModel.quad(part, verts, color, f(q, "alpha", 1f),
                        q.has("emissive") && q.get("emissive").getAsBoolean(),
                        q.has("doubleSided") && q.get("doubleSided").getAsBoolean());
            }
        }
        return model;
    }

    private static void loadCapes() {
        List<Capes.CapeConfig> out = new ArrayList<>();
        CAPE_CACHE.put("capes", out);
        try {
            ResourceManager rm = MinecraftClient.getInstance().getResourceManager();
            var res = rm.getResource(net.minecraft.util.Identifier.of("visualmod", "cosmetics/capes.json"));
            if (res.isEmpty()) return;
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(res.get().getInputStream(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement el : root.getAsJsonArray("items")) {
                JsonObject o = el.getAsJsonObject();
                out.add(new Capes.CapeConfig(
                        CosmeticModel.parseColor(str(o, "base", "#B5945A"), 0xB5945A),
                        CosmeticModel.parseColor(str(o, "trim", "#6B4E2E"), 0x6B4E2E),
                        str(o, "emblem", "wings"),
                        CosmeticModel.parseColor(str(o, "emblemColor", "#FFFFFF"), 0xFFFFFF),
                        f(o, "length", 18f)));
            }
        } catch (Exception e) {
            com.visualmod.util.Diag.once("Cosmetics.loadCapes", e);
        }
    }

    private static Bone parseBone(JsonElement el) {
        if (el == null) return Bone.HEAD;
        try {
            return Bone.valueOf(el.getAsString().toUpperCase());
        } catch (Exception e) {
            return Bone.HEAD;
        }
    }

    private static float[] vec3(JsonElement el) {
        JsonArray a = el.getAsJsonArray();
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }

    private static String str(JsonObject o, String key, String def) {
        return o.has(key) ? o.get(key).getAsString() : def;
    }

    private static float f(JsonObject o, String key, float def) {
        return o.has(key) ? o.get(key).getAsFloat() : def;
    }
}

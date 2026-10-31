package com.visualmod.module.modules.render;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.shaders.SoftShadows;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.world.ClientWorld;

import java.util.ArrayList;
import java.util.List;

public class Shaders extends Module implements Configurable {

    public enum Preset { SOFT, CINEMATIC, VIVID, NOCTURNE }
    public enum Shadow { OFF, FAINT, NORMAL, STRONG }
    public enum Mood { OFF, SUBTLE, FULL }
    public enum FogLevel { OFF, SOFT, NORMAL, HEAVY }

    public static Preset preset = Preset.CINEMATIC;
    public static double intensity = 55;
    public static double vignette = 45;
    public static double bloom = 35;
    public static Shadow shadows = Shadow.NORMAL;
    public static Mood mood = Mood.SUBTLE;
    public static int tintColor = 0xFFE8B26A;
    public static double glow = 45;
    public static double rays = 40;
    public static int glowReach = 10;
    public static FogLevel fogLevel = FogLevel.SOFT;
    public static int fogTint = 0xFFCBD9EA;
    public static double fogTintStrength = 55;
    public static double groundMist = 40;
    public static double shafts = 60;
    public static double mist = 45;
    public static double motes = 50;
    public static double horizon = 55;

    static {
        HudRenderCallback.EVENT.register((ctx, tc) -> screenPass(ctx));
        WorldRenderEvents.AFTER_ENTITIES.register(SoftShadows::render);
        WorldRenderEvents.AFTER_ENTITIES.register(com.visualmod.shaders.SunShadows::render);
        WorldRenderEvents.AFTER_ENTITIES.register(com.visualmod.shaders.LightBloom::render);
        WorldRenderEvents.AFTER_ENTITIES.register(com.visualmod.shaders.Volumetrics::render);
    }

    public Shaders() {
        super("Shaders", "Fake-shader look: sun shadows, grading, light shafts, drifting mist, warm horizon", Category.RENDER);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Preset", new String[]{"SOFT", "CINEMATIC", "VIVID", "NOCTURNE"},
                () -> preset.ordinal(), i -> preset = Preset.values()[i]));
        l.add(new SliderSetting("Grading", 0, 100, 1, () -> intensity, v -> intensity = v, "%"));
        l.add(new SliderSetting("Vignette", 0, 100, 1, () -> vignette, v -> vignette = v, "%"));
        l.add(new SliderSetting("Sky Bloom", 0, 100, 1, () -> bloom, v -> bloom = v, "%"));
        l.add(new ColorSetting("Tint", () -> tintColor, c -> tintColor = c));
        l.add(new ModeSetting("Shadows", new String[]{"OFF", "FAINT", "NORMAL", "STRONG"},
                () -> shadows.ordinal(), i -> shadows = Shadow.values()[i]));
        l.add(new ModeSetting("Mood", new String[]{"OFF", "SUBTLE", "FULL"},
                () -> mood.ordinal(), i -> mood = Mood.values()[i]));
        l.add(new SliderSetting("Light Bloom", 0, 100, 1, () -> glow, v -> glow = v, "%"));
        l.add(new SliderSetting("Bloom Range", 6, 16, 1, () -> glowReach, v -> glowReach = (int) v, " bl."));
        l.add(new SliderSetting("Sun Rays", 0, 100, 1, () -> rays, v -> rays = v, "%"));
        l.add(new ModeSetting("Fog", new String[]{"OFF", "SOFT", "NORMAL", "HEAVY"},
                () -> fogLevel.ordinal(), i -> fogLevel = FogLevel.values()[i]));
        l.add(new ColorSetting("Fog Tint", () -> fogTint, c -> fogTint = c));
        l.add(new SliderSetting("Tint Strength", 0, 100, 1, () -> fogTintStrength, v -> fogTintStrength = v, "%"));
        l.add(new SliderSetting("Ground Mist", 0, 100, 1, () -> groundMist, v -> groundMist = v, "%"));
        l.add(new SliderSetting("Light Shafts", 0, 100, 1, () -> shafts, v -> shafts = v, "%"));
        l.add(new SliderSetting("Mist Sheets", 0, 100, 1, () -> mist, v -> mist = v, "%"));
        l.add(new SliderSetting("Dust Motes", 0, 100, 1, () -> motes, v -> motes = v, "%"));
        l.add(new SliderSetting("Horizon Glow", 0, 100, 1, () -> horizon, v -> horizon = v, "%"));
        return l;
    }

    public static boolean active() {
        if (VisualsMod.moduleManager == null) return false;
        Shaders s = VisualsMod.moduleManager.getModule(Shaders.class);
        return s != null && s.isEnabled();
    }

    public static float grading() { return (float) (intensity / 100.0); }
    public static float vignetteF() { return (float) (vignette / 100.0); }
    public static float bloomF() { return (float) (bloom / 100.0); }
    public static float shadowAlpha() {
        return switch (shadows) {
            case OFF -> 0f;
            case FAINT -> 0.16f;
            case NORMAL -> 0.30f;
            case STRONG -> 0.46f;
        };
    }
    public static float shadowSize() { return shadows == Shadow.STRONG ? 1.25f : 1.0f; }
    public static double shadowReach() { return shadows == Shadow.OFF ? 0 : (shadows == Shadow.FAINT ? 18 : 30); }
    public static int shadowMax() { return shadows == Shadow.STRONG ? 64 : 40; }
    public static boolean moodOn() { return active() && mood != Mood.OFF; }
    public static float moodStrength() { return mood == Mood.FULL ? 1f : 0.45f; }
    public static float glow() { return (float) (glow / 100.0); }
    public static int glowReach() { return Math.max(4, Math.min(16, glowReach)); }
    public static float raysF() { return (float) (rays / 100.0); }
    public static float shaftsF() { return (float) (shafts / 100.0); }
    public static float mistF() { return (float) (mist / 100.0); }
    public static float motesF() { return (float) (motes / 100.0); }
    public static float horizonF() { return (float) (horizon / 100.0); }

    public static boolean fogOn() { return active() && fogLevel != FogLevel.OFF; }

    public static float[] fogRange(ClientWorld world, float tickDelta, double camY) {
        if (!fogOn()) return null;
        float s, e;
        switch (fogLevel) {
            case SOFT -> { s = 0.74f; e = 1.08f; }
            case NORMAL -> { s = 0.56f; e = 0.96f; }
            case HEAVY -> { s = 0.34f; e = 0.78f; }
            default -> { return null; }
        }
        if (world == null) return new float[]{s, e};

        float wet = 0f, dawn = 0f;
        if (CustomSky.isOverworld(world)) {
            float rain = world.getRainGradient(tickDelta), thunder = world.getThunderGradient(tickDelta);
            wet = 0.30f * rain + 0.34f * thunder;
            float day = daylight(world, tickDelta);
            dawn = 0.26f * (1f - Math.min(1f, Math.abs(day - 0.5f) * 2.3f));
        }
        float mul = 1f - clamp01(wet + dawn);
        s *= mul; e *= mul;

        float mist = clamp01((float) (groundMist / 100.0));
        if (mist > 0.005f) {
            float low = clamp01((world.getSeaLevel() + 18f - (float) camY) / 18f);
            float m = 1f - mist * low * 0.62f;
            s *= m; e = Math.max(s + 0.05f, e * m);
        }
        return new float[]{s, e};
    }

    public static float[] fogTintColor() {
        if (!fogOn()) return null;
        float k = clamp01((float) (fogTintStrength / 100.0));
        if (k <= 0.005f) return null;
        return new float[]{((fogTint >> 16) & 0xFF) / 255f, ((fogTint >> 8) & 0xFF) / 255f, (fogTint & 0xFF) / 255f, k};
    }

    public static float[] moodColor(ClientWorld world, float tickDelta) {
        if (!moodOn() || world == null || !CustomSky.isOverworld(world)) return null;
        float rain = world.getRainGradient(tickDelta);
        float thunder = world.getThunderGradient(tickDelta);
        float k = moodStrength();

        float day = daylight(world, tickDelta);
        float night = (1f - day) * (mood == Mood.FULL ? 0.75f : 0.45f) * k;
        float wet = rain * (0.55f + 0.45f * thunder) * k;
        if (night <= 0.01f && wet <= 0.01f) return null;

        float r = clamp01(day * 1.04f - night * 0.55f - wet * 0.42f);
        float g = clamp01(day * 1.01f - night * 0.38f - wet * 0.22f);
        float b = clamp01(day * 0.96f + night * 0.55f + wet * 0.18f);
        float mix = clamp01(wet * 0.5f);
        float gray = (r + g + b) / 3f;
        return new float[]{lerp(r, gray * 0.92f, mix), lerp(g, gray, mix), lerp(b, gray * 1.08f, mix)};
    }

    public static float fogCompress(ClientWorld world, float tickDelta) {
        if (!moodOn() || world == null || !CustomSky.isOverworld(world)) return 1f;
        float rain = world.getRainGradient(tickDelta);
        float thunder = world.getThunderGradient(tickDelta);
        float night = 1f - daylight(world, tickDelta);
        float k = moodStrength();
        float f = 1f - (0.22f * rain + 0.28f * thunder + 0.12f * night) * k;
        return Math.max(0.45f, f);
    }

    public static float daylight(ClientWorld world, float tickDelta) {
        if (world == null) return 0f;
        double t = VisualsMod.moduleManager != null && VisualsMod.moduleManager.getModule(CustomSky.class) != null
                && VisualsMod.moduleManager.getModule(CustomSky.class).isEnabled()
                ? CustomSky.effectiveTime(world, tickDelta)
                : world.getTimeOfDay();
        return (float) CustomSky.daylight(t);
    }

    private static void screenPass(DrawContext ctx) {
        try {
            if (!active()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null || mc.getDebugHud().shouldShowDebugHud()) return;
            if (mc.options.hudHidden) return;

            int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
            if (w <= 0 || h <= 0) return;
            float g = grading();
            float day = daylight(mc.world, 1f);

            if (g > 0.005f) grade(ctx, w, h, g, day);
            if (horizonF() > 0.005f) horizonGlow(ctx, w, h, mc, day);
            if (bloomF() > 0.005f) skyBloom(ctx, w, h, mc, day);
            if (raysF() > 0.005f) sunGlow(ctx, w, h, mc, day);
            if (vignetteF() > 0.005f) drawVignette(ctx, w, h, day);
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("Shaders", t);
        }
    }

    private static void grade(DrawContext ctx, int w, int h, float g, float day) {
        int t = tintColor;
        float tr = ((t >> 16) & 0xFF) / 255f, tg = ((t >> 8) & 0xFF) / 255f, tb = (t & 0xFF) / 255f;
        float warmA = 0.05f + 0.09f * g;
        float edge = 1f - Math.abs(day - 0.5f) * 2f;
        float top = warmA * (0.55f + 0.45f * day);
        float bot = warmA * (0.35f + 0.65f * edge);
        if (preset == Preset.NOCTURNE) {
            ctx.fillGradient(0, 0, w, h, rgba(0.28f, 0.36f, 0.58f, 0.05f + 0.1f * g),
                    rgba(0.02f, 0.04f, 0.10f, 0.06f + 0.12f * g));
            return;
        }
        if (preset == Preset.VIVID) {
            ctx.fillGradient(0, 0, w, h, rgba(tr, tg, tb, top * 0.7f), rgba(0.2f, 0.55f, 0.45f, bot * 0.8f));
            return;
        }
        ctx.fillGradient(0, 0, w, h, rgba(tr, tg, tb, top), rgba(0.16f, 0.28f, 0.44f, bot));
    }

    private static void horizonGlow(DrawContext ctx, int w, int h, MinecraftClient mc, float day) {
        if (mc.player == null || mc.world == null) return;
        float sky = com.visualmod.util.McCompat.skyAngle(mc.world, 1f);
        if ((float) Math.sin(Math.PI * 2 * sky) <= 0.02f) return;

        float sunYaw = 270f + sky * 360f;
        float delta = ((mc.player.getYaw(1f) - sunYaw) % 360f + 540f) % 360f - 180f;
        float sunX = 0.5f - delta / 130f;
        float pitch = Math.max(-70f, Math.min(70f, mc.player.getPitch(1f)));
        float hy = h * (0.5f - (float) Math.tan(Math.toRadians(pitch)) * 0.78f);

        float edge = clamp01(1f - Math.abs(day - 0.5f) * 2.1f);
        int t = tintColor;
        float tr = ((t >> 16) & 0xFF) / 255f, tg = ((t >> 8) & 0xFF) / 255f, tb = (t & 0xFF) / 255f;
        float cr = lerp(tr, 1f, edge * 0.6f), cg = lerp(tg, 0.5f, edge * 0.45f), cb = lerp(tb, 0.32f, edge * 0.5f);

        float k = horizonF() * (0.45f + 0.55f * edge) * (0.4f + 0.6f * day);
        k *= 1f - Math.min(0.85f, mc.world.getRainGradient(1f) * 0.9f);
        if (k <= 0.01f) return;

        int bandH = Math.max(8, (int) (h * (0.14f + 0.22f * k)));
        int top = (int) Math.max(0, hy - bandH);
        int bot = (int) Math.min(h, hy + bandH * 0.4f);
        if (bot - top < 3) return;

        int strips = 16;
        int sw = Math.max(1, (w + strips - 1) / strips);
        for (int i = 0; i < strips; i++) {
            float cx = (i + 0.5f) * sw / (float) w;
            float near = clamp01(1f - Math.abs(cx - sunX) / 0.62f);
            float a = k * (0.16f + 0.84f * near * near);
            if (a <= 0.012f) continue;
            int x1 = i * sw, x2 = Math.min(w, x1 + sw);
            ctx.fillGradient(x1, top, x2, bot, rgba(cr, cg, cb, a * 0.02f), rgba(cr, cg, cb, a * 0.09f));
        }
        if (hy > 2 && hy < h - 2) {
            ctx.fill(0, (int) hy - 1, w, (int) hy + 1, rgba(cr, cg, cb, 0.05f * k));
        }
    }

    private static void skyBloom(DrawContext ctx, int w, int h, MinecraftClient mc, float day) {
        if (mc.player == null) return;
        float pitch = mc.player.getPitch(1f);
        float up = clamp01(-pitch / 60f);
        float strength = bloomF() * day * (0.25f + 0.75f * up);
        if (strength <= 0.01f) return;
        int rows = 28, rh = Math.max(1, h / rows);
        for (int i = 0; i < rows; i++) {
            float f = 1f - i / (float) rows;
            int a = (int) (255 * strength * 0.16f * f * f);
            if (a <= 1) continue;
            ctx.fillGradient(0, i * rh, w, (i + 1) * rh, (a << 24) | 0xFFF0D0, (a << 24) | 0xFFE0B0);
        }
    }

    private static void sunGlow(DrawContext ctx, int w, int h, MinecraftClient mc, float day) {
        if (mc.player == null || mc.world == null) return;
        float sky = com.visualmod.util.McCompat.skyAngle(mc.world, 1f);
        float elev = (float) Math.sin(Math.PI * 2 * sky);
        if (elev <= 0.02f || day <= 0.05f) return;
        float sunYaw = 270f + sky * 360f;
        float delta = ((mc.player.getYaw(1f) - sunYaw) % 360f + 540f) % 360f - 180f;
        float nx = 0.5f - delta / 120f;
        if (nx < -0.35f || nx > 1.35f) return;
        float look = clamp01((35f - mc.player.getPitch(1f)) / 55f);
        float k = raysF() * look * (0.35f + 0.65f * day);
        if (k <= 0.01f) return;
        float cx = nx * w, cy = h * (0.92f - 0.72f * elev);

        int r = (int) (Math.min(w, h) * (0.10f + 0.16f * k));
        for (int dy = -r; dy <= r; dy += 2) {
            float f = 1f - (dy * dy) / (float) (r * r + 1);
            if (f <= 0.01f) continue;
            int half = (int) (r * (float) Math.sqrt(f));
            int a = (int) (255 * 0.10f * k * f * f);
            if (a <= 1 || cy + dy < 0 || cy + dy >= h) continue;
            ctx.fill(Math.max(0, (int) cx - half), (int) cy + dy,
                    Math.min(w, (int) cx + half), (int) cy + dy + 2, (a << 24) | 0xFFF4DC);
        }
        int bandH = 2 + (int) (4 * k), bandW = (int) (w * (0.25f + 0.6f * k));
        for (int i = 0; i < 12; i++) {
            float f = 1f - i / 12f;
            int a = (int) (255 * 0.055f * k * f * f);
            if (a <= 1) continue;
            int hw = (int) (bandW * f);
            ctx.fill(Math.max(0, (int) cx - hw), (int) cy - bandH / 2,
                    Math.min(w, (int) cx + hw), (int) cy + bandH / 2 + 1, (a << 24) | 0xFFEFD8);
        }
    }

    private static void drawVignette(DrawContext ctx, int w, int h, float day) {
        float k = vignetteF() * (0.75f + 0.25f * (1f - day));
        int n = 26;
        int stepX = Math.max(1, (w / 2) / n), stepY = Math.max(1, (h / 2) / n);
        for (int i = 0; i < n; i++) {
            float f = 1f - i / (float) n;
            int a = (int) (255 * k * 0.5f * (float) Math.pow(f, 3.0));
            if (a <= 1) continue;
            int c = a << 24;
            ctx.fill(i * stepX, 0, (i + 1) * stepX, h, c);
            ctx.fill(w - (i + 1) * stepX, 0, w - i * stepX, h, c);
            ctx.fill(0, i * stepY, w, (i + 1) * stepY, c);
            ctx.fill(0, h - (i + 1) * stepY, w, h - i * stepY, c);
        }
    }

    private static int rgba(float r, float g, float b, float a) {
        int ar = Math.max(0, Math.min(255, (int) (a * 255)));
        return (ar << 24) | (((int) (clamp01(r) * 255)) << 16) | (((int) (clamp01(g) * 255)) << 8) | (int) (clamp01(b) * 255);
    }

    private static float clamp01(float v) { return v < 0f ? 0f : Math.min(v, 1f); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}

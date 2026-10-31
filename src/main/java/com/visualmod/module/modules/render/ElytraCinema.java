package com.visualmod.module.modules.render;

import com.visualmod.util.McCompat;
import net.minecraft.client.render.RenderLayers;
import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.util.IrisCompat;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * ElytraCinema: во время полёта на элитрах экран превращается в кадровую ленту —
 * чёрные полосы по краям, виньетка, грейдинг и линии скорости вдоль взгляда.
 */
public class ElytraCinema extends Module implements Configurable {

    public static int style = 0;
    public static boolean bars = true;
    public static double barSize = 0.15;
    public static double vignette = 0.6;
    public static double streakAmount = 0.8;
    public static double fovPull = 1.14;
    public static boolean grain = true;
    public static int streakColor = 0xFFFFFFFF;

    private static final int LANES = 46;
    private static final float[] L_ANGLE = new float[LANES];
    private static final float[] L_RADIUS = new float[LANES];
    private static final float[] L_DIST = new float[LANES];
    private static final float[] L_LEN = new float[LANES];
    private static final float[] L_WIDTH = new float[LANES];

    private static float strength = 0f;
    private static long lastUpdate = 0L;
    private static final Random GRAIN = new Random(12345);

    static {
        HudRenderCallback.EVENT.register((ctx, tickDelta) -> overlay(ctx));
        WorldRenderEvents.AFTER_ENTITIES.register(ElytraCinema::render);
        Random r = new Random(777);
        for (int i = 0; i < LANES; i++) {
            L_ANGLE[i] = r.nextFloat() * (float) (Math.PI * 2);
            L_RADIUS[i] = 0.9f + r.nextFloat() * 2.6f;
            L_DIST[i] = 4f + r.nextFloat() * 30f;
            L_LEN[i] = 1.6f + r.nextFloat() * 4.5f;
            L_WIDTH[i] = 0.012f + r.nextFloat() * 0.03f;
        }
    }

    public ElytraCinema() {
        super("ElytraCinema", "Cinematic letterbox, grading and speed lines while gliding", Category.MOVEMENT);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Grade", new String[]{"ANIME", "NOIR", "TEAL-ORANGE"}, () -> style, i -> style = i));
        l.add(new ModeSetting("Bars", new String[]{"OFF", "ON"}, () -> bars ? 1 : 0, i -> bars = (i == 1)));
        if (bars) l.add(new SliderSetting("Bar Height", 0.05, 0.3, 0.01, () -> barSize, v -> barSize = v, ""));
        l.add(new SliderSetting("Vignette", 0, 1, 0.05, () -> vignette, v -> vignette = v, ""));
        l.add(new SliderSetting("Speed Lines", 0, 1, 0.05, () -> streakAmount, v -> streakAmount = v, ""));
        if (streakAmount > 0.02) l.add(new ColorSetting("Line Color", () -> streakColor, c -> streakColor = c));
        l.add(new SliderSetting("FOV Pull", 1.0, 1.35, 0.01, () -> fovPull, v -> fovPull = v, "x"));
        l.add(new ModeSetting("Grain", new String[]{"OFF", "ON"}, () -> grain ? 1 : 0, i -> grain = (i == 1)));
        return l;
    }

    @Override
    public void onDisable() { strength = 0f; }

    /** 1.0 — летим, 0.0 — на земле. Плавный переход, чтобы кадр «наезжал». */
    public static float strength() {
        update(MinecraftClient.getInstance());
        return strength;
    }

    /** Множитель FOV для GameRenderer.getFov. */
    public static float fovScale() {
        float s = strength();
        return 1f + ((float) Math.max(0.9, fovPull) - 1f) * s;
    }

    private static void update(MinecraftClient mc) {
        long now = System.nanoTime();
        if (lastUpdate == 0L) lastUpdate = now;
        float dt = Math.min(0.15f, (now - lastUpdate) / 1_000_000_000f);
        lastUpdate = now;
        float target = target(mc);
        float k = target > strength ? 2.6f : 3.6f;
        strength += (target - strength) * Math.min(1f, k * dt);
        if (strength < 0.004f) strength = 0f;
    }

    private static float target(MinecraftClient mc) {
        if (VisualsMod.moduleManager == null) return 0f;
        ElytraCinema m = VisualsMod.moduleManager.getModule(ElytraCinema.class);
        if (m == null || !m.isEnabled()) return 0f;
        if (mc.player == null || mc.currentScreen != null) return 0f;
        return mc.player.isGliding() ? 1f : 0f;
    }

    private static void overlay(DrawContext ctx) {
        try {
            update(MinecraftClient.getInstance());
            float s = strength;
            if (s <= 0.01f) return;
            int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
            if (w <= 0 || h <= 0) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;

            if (vignette > 0.02f) {
                int bands = 9;
                float vh = h * 0.34f * (float) vignette;
                float vw = w * 0.22f * (float) vignette;
                for (int i = 0; i < bands; i++) {
                    float t = i / (float) bands;
                    int a = (int) (26 * (1f - t) * s * (float) vignette * 2.2f);
                    a = Math.min(120, a) << 24;
                    int bh = (int) (vh / bands), bw = (int) (vw / bands);
                    ctx.fill(0, i * bh, w, (i + 1) * bh, a);
                    ctx.fill(0, h - (i + 1) * bh, w, h - i * bh, a);
                    ctx.fill(i * bw, 0, (i + 1) * bw, h, a);
                    ctx.fill(w - (i + 1) * bw, 0, w - i * bw, h, a);
                }
            }

            int topA = (int) (s * 22), botA = (int) (s * 26);
            if (style == 1) {
                ctx.fill(0, 0, w, h / 2, (topA << 24) | 0x101820);
                ctx.fill(0, h / 2, w, h, (botA << 24) | 0x081018);
            } else if (style == 2) {
                ctx.fill(0, 0, w, h / 2, (topA << 24) | 0xFF8A3C);
                ctx.fill(0, h / 2, w, h, (botA << 24) | 0x0F6B8A);
            } else {
                ctx.fill(0, 0, w, h, (topA << 24) | 0xC8E8FF);
            }

            if (grain) {
                long seed = (System.currentTimeMillis() / 40L) * 2654435761L;
                GRAIN.setSeed(seed);
                int dots = (int) (90 * s);
                for (int i = 0; i < dots; i++) {
                    int x = GRAIN.nextInt(w), y = GRAIN.nextInt(h);
                    int a = 8 + GRAIN.nextInt(16);
                    int c = (int) ((a * s)) << 24;
                    ctx.fill(x, y, x + 1, y + 1, c);
                }
            }

            if (bars) {
                int bar = (int) (h * Math.max(0.02, Math.min(0.4, barSize)) * s);
                if (bar > 0) {
                    ctx.fill(0, 0, w, bar, 0xFF000000);
                    ctx.fill(0, h - bar, w, h, 0xFF000000);
                }
            }
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("ElytraCinema", t);
        }
    }

    private static void render(WorldRenderContext ctx) {
        try {
            if (IrisCompat.skipWorldPass()) return;
            if (streakAmount <= 0.02) return;
            if (VisualsMod.moduleManager == null || ctx.matrices() == null || ctx.consumers() == null) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null) return;
            float s = strength;
            if (s <= 0.02f) return;

            Vec3d vel = McCompat.pos(mc.player).subtract(mc.player.lastX, mc.player.lastY, mc.player.lastZ);
            double speed = Math.max(0.4, Math.hypot(vel.x, vel.z));
            Vector3f fwd = new Vector3f(0, 0, -1).rotate(ctx.gameRenderer().getCamera().getRotation()).normalize();
            Vector3f right = new Vector3f(1, 0, 0).rotate(ctx.gameRenderer().getCamera().getRotation()).normalize();
            Vector3f up = new Vector3f(0, 1, 0).rotate(ctx.gameRenderer().getCamera().getRotation()).normalize();
            up = new Vector3f(fwd).cross(right).normalize();

            ctx.matrices().push();
            ctx.matrices().translate(-ctx.gameRenderer().getCamera().getCameraPos().x, -ctx.gameRenderer().getCamera().getCameraPos().y, -ctx.gameRenderer().getCamera().getCameraPos().z);
            Matrix4f mat = ctx.matrices().peek().getPositionMatrix();
            VertexConsumer vc = ctx.consumers().getBuffer(RenderLayers.debugQuads());

            float cx = (float) ctx.gameRenderer().getCamera().getCameraPos().x, cy = (float) ctx.gameRenderer().getCamera().getCameraPos().y, cz = (float) ctx.gameRenderer().getCamera().getCameraPos().z;
            int rgb = streakColor & 0xFFFFFF;
            float cr = ((rgb >> 16) & 0xFF) / 255f, cg = ((rgb >> 8) & 0xFF) / 255f, cb = (rgb & 0xFF) / 255f;
            long now = System.currentTimeMillis();
            int lanes = (int) (LANES * Math.min(1.0, streakAmount));

            for (int i = 0; i < lanes; i++) {
                float a = L_ANGLE[i], rad = L_RADIUS[i], len = L_LEN[i], wd = L_WIDTH[i];
                float drift = ((now % 100000L) / 1000.0f * (float) speed * 0.9f + L_DIST[i]) % 34f;
                float dist = 34f - drift;
                Vector3f p = new Vector3f(cx + fwd.x * dist + right.x * (float) Math.cos(a) * rad + up.x * (float) Math.sin(a) * rad,
                        cy + fwd.y * dist + right.y * (float) Math.cos(a) * rad + up.y * (float) Math.sin(a) * rad,
                        cz + fwd.z * dist + right.z * (float) Math.cos(a) * rad + up.z * (float) Math.sin(a) * rad);
                Vector3f q = new Vector3f(p.x - fwd.x * len, p.y - fwd.y * len, p.z - fwd.z * len);
                float edge = Math.min(1f, rad / 3.4f);
                float al = s * edge * (float) Math.min(1.0, streakAmount) * 0.5f;
                Vector3f n = new Vector3f(fwd).cross(up).normalize().mul(wd);
                if (n.lengthSquared() < 1e-9f) n = new Vector3f(right).mul(wd);
                ribbon(vc, mat, p, q, n, cr, cg, cb, al);
            }

            com.visualmod.util.RenderFlush.flush(ctx.consumers(), RenderLayers.debugQuads());
            ctx.matrices().pop();
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("ElytraCinema.world", t);
        }
    }

    private static void ribbon(VertexConsumer vc, Matrix4f mat, Vector3f a, Vector3f b, Vector3f n,
                               float cr, float cg, float cb, float al) {
        if (al <= 0.01f) return;
        vc.vertex(mat, a.x - n.x, a.y - n.y, a.z - n.z).color(cr, cg, cb, 0f);
        vc.vertex(mat, a.x + n.x, a.y + n.y, a.z + n.z).color(cr, cg, cb, 0f);
        vc.vertex(mat, b.x + n.x, b.y + n.y, b.z + n.z).color(cr, cg, cb, al);
        vc.vertex(mat, b.x - n.x, b.y - n.y, b.z - n.z).color(cr, cg, cb, al);
    }
}

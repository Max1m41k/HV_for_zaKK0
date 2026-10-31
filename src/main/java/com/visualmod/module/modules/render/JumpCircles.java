package com.visualmod.module.modules.render;

import net.minecraft.client.render.RenderLayers;
import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.util.WorldRenderUtil;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class JumpCircles extends Module implements Configurable {

    public static int style = 0;
    public static int color = 0xFF7A5CFF;
    public static int color2 = 0xFFFF5CD6;
    public static boolean gradient = true;
    public static double size = 1.8;
    public static double duration = 900;
    public static double thickness = 0.28;
    public static boolean onLand = false;

    private static final class Ring { final double x, y, z; final long t0; Ring(double x, double y, double z, long t0) { this.x = x; this.y = y; this.z = z; this.t0 = t0; } }
    private static final List<Ring> RINGS = new ArrayList<>();
    private boolean wasGround = true;
    private double lastVy = 0;
    private boolean errorLogged = false;

    static { WorldRenderEvents.AFTER_ENTITIES.register(ctx -> render(ctx)); }

    public JumpCircles() { super("JumpCircles", "Glowing ring on jump", Category.RENDER); }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Style", new String[]{"RING", "DOUBLE", "PULSE", "GLOW"}, () -> style, i -> style = i));
        l.add(new ColorSetting("Color", () -> color, c -> color = c));
        l.add(new ModeSetting("Gradient", new String[]{"OFF", "ON"}, () -> gradient ? 1 : 0, i -> gradient = (i == 1)));
        if (gradient) l.add(new ColorSetting("Color 2", () -> color2, c -> color2 = c));
        l.add(new SliderSetting("Size", 0.8, 4.0, 0.2, () -> size, v -> size = v));
        l.add(new SliderSetting("Duration", 300, 2000, 100, () -> duration, v -> duration = v, "ms"));
        l.add(new SliderSetting("Thickness", 0.1, 0.8, 0.05, () -> thickness, v -> thickness = v));
        l.add(new ModeSetting("On Land", new String[]{"OFF", "ON"}, () -> onLand ? 1 : 0, i -> onLand = (i == 1)));
        return l;
    }

    @Override
    public void onDisable() { synchronized (RINGS) { RINGS.clear(); } }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        boolean ground = mc.player.isOnGround();
        double vy = mc.player.getVelocity().y;
        long now = System.currentTimeMillis();
        if (wasGround && !ground && vy > 0.1) add(mc.player.getX(), mc.player.getY(), mc.player.getZ(), now);
        if (onLand && !wasGround && ground && lastVy < -0.45) add(mc.player.getX(), mc.player.getY(), mc.player.getZ(), now);
        wasGround = ground;
        lastVy = vy;
    }

    private static void add(double x, double y, double z, long now) {
        synchronized (RINGS) { RINGS.add(new Ring(x, y, z, now)); if (RINGS.size() > 24) RINGS.remove(0); }
    }

    private static void render(WorldRenderContext ctx) {
        try {
            if (VisualsMod.moduleManager == null) return;
            JumpCircles m = VisualsMod.moduleManager.getModule(JumpCircles.class);
            if (m == null || !m.isEnabled() || ctx.matrices() == null || ctx.consumers() == null) return;
            synchronized (RINGS) { if (RINGS.isEmpty()) return; }

            long now = System.currentTimeMillis();
            Vec3d cam = ctx.gameRenderer().getCamera().getCameraPos();
            ctx.matrices().push();
            ctx.matrices().translate(-cam.x, -cam.y, -cam.z);
            Matrix4f mat = ctx.matrices().peek().getPositionMatrix();
            VertexConsumer buf = ctx.consumers().getBuffer(RenderLayers.debugQuads());

            synchronized (RINGS) {
                RINGS.removeIf(r -> now - r.t0 > duration);
                for (Ring r : RINGS) drawRing(buf, mat, r, (now - r.t0) / duration);
            }
            ctx.matrices().pop();
        } catch (Throwable t) { com.visualmod.util.Diag.once("JumpCircles", t); }
    }

    private static void drawRing(VertexConsumer buf, Matrix4f mat, Ring r, double p) {
        double e = 1.0 - Math.pow(1.0 - p, 3.0);
        double fade = Math.pow(1.0 - p, 1.4);
        double R = size * e;
        double y = r.y + 0.02;
        switch (style) {
            case 0 -> band(buf, mat, r.x, y, r.z, R, thickness, fade, 1f);
            case 1 -> { band(buf, mat, r.x, y, r.z, R, thickness, fade, 1f); band(buf, mat, r.x, y, r.z, R * 0.62, thickness * 0.7, fade * 0.8, 1f); }
            case 2 -> {
                for (int k = 0; k < 3; k++) {
                    double pk = p - k * 0.14;
                    if (pk <= 0) continue;
                    double ek = 1.0 - Math.pow(1.0 - pk, 3.0);
                    band(buf, mat, r.x, y, r.z, size * ek, thickness * (1.0 - k * 0.2), Math.pow(1.0 - pk, 1.4) * (1.0 - k * 0.25), 1f);
                }
            }
            default -> { glow(buf, mat, r.x, y, r.z, R, fade * 0.45); band(buf, mat, r.x, y, r.z, R, thickness, fade, 1f); }
        }
    }

    private static float[] col(double t) {
        float r1 = WorldRenderUtil.colorR(color), g1 = WorldRenderUtil.colorG(color), b1 = WorldRenderUtil.colorB(color);
        if (!gradient) return new float[]{r1, g1, b1};
        float r2 = WorldRenderUtil.colorR(color2), g2 = WorldRenderUtil.colorG(color2), b2 = WorldRenderUtil.colorB(color2);
        float k = (float) (0.5 + 0.5 * Math.sin(t));
        return new float[]{r1 + (r2 - r1) * k, g1 + (g2 - g1) * k, b1 + (b2 - b1) * k};
    }

    private static void v(VertexConsumer buf, Matrix4f mat, double x, double y, double z, float[] c, double a) {
        buf.vertex(mat, (float) x, (float) y, (float) z).color(c[0], c[1], c[2], (float) Math.max(0, Math.min(1, a)));
    }

    private static void band(VertexConsumer buf, Matrix4f mat, double cx, double y, double cz, double R, double w, double alpha, float k) {
        if (R < 0.05 || alpha < 0.01) return;
        double rIn = Math.max(0.0, R - w), rMid = Math.max(0.0, R - w * 0.35), rOut = R + w * 0.15;
        final int N = 48;
        for (int i = 0; i < N; i++) {
            double a0 = Math.PI * 2 * i / N, a1 = Math.PI * 2 * (i + 1) / N;
            double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            float[] k0 = col(a0 * 2), k1 = col(a1 * 2);
            v(buf, mat, cx + c0 * rIn, y, cz + s0 * rIn, k0, 0);
            v(buf, mat, cx + c0 * rMid, y, cz + s0 * rMid, k0, alpha);
            v(buf, mat, cx + c1 * rMid, y, cz + s1 * rMid, k1, alpha);
            v(buf, mat, cx + c1 * rIn, y, cz + s1 * rIn, k1, 0);
            v(buf, mat, cx + c0 * rMid, y, cz + s0 * rMid, k0, alpha);
            v(buf, mat, cx + c0 * rOut, y, cz + s0 * rOut, k0, 0);
            v(buf, mat, cx + c1 * rOut, y, cz + s1 * rOut, k1, 0);
            v(buf, mat, cx + c1 * rMid, y, cz + s1 * rMid, k1, alpha);
        }
    }

    private static void glow(VertexConsumer buf, Matrix4f mat, double cx, double y, double cz, double R, double alpha) {
        if (R < 0.05 || alpha < 0.01) return;
        final int N = 40;
        for (int i = 0; i < N; i++) {
            double a0 = Math.PI * 2 * i / N, a1 = Math.PI * 2 * (i + 1) / N;
            float[] k0 = col(a0 * 2), k1 = col(a1 * 2);
            v(buf, mat, cx, y, cz, k0, 0);
            v(buf, mat, cx + Math.cos(a0) * R, y, cz + Math.sin(a0) * R, k0, alpha);
            v(buf, mat, cx + Math.cos(a1) * R, y, cz + Math.sin(a1) * R, k1, alpha);
            v(buf, mat, cx + Math.cos(a1) * R, y, cz + Math.sin(a1) * R, k1, alpha);
        }
    }
}

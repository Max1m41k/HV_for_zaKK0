package com.visualmod.module.modules.player;

import net.minecraft.client.render.RenderLayers;
import com.visualmod.VisualsMod;
import com.visualmod.util.WorldRenderUtil;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import org.joml.Matrix4f;

public final class RespawnFx {

    public static final String ASSEMBLE_NAME = "ASSEMBLE";
    public static final String SKY_NAME = "SKYFALL";
    public static final String PORTAL_NAME = "PORTAL";

    private static final int ASSEMBLE = 0, SKYFALL = 1, PORTAL = 2;

    private static final double[] PART_X = { 0, 0, -0.375, 0.375, -0.125, 0.125 };
    private static final double[] PART_Y = { 1.5, 0.75, 0.75, 0.75, 0.0, 0.0 };
    private static final double[] PART_W = { 0.5, 0.5, 0.25, 0.25, 0.25, 0.25 };
    private static final double[] PART_H = { 0.5, 0.75, 0.75, 0.75, 0.75, 0.75 };

    private static boolean active = false;
    private static int style = ASSEMBLE;
    private static double seconds = 1.8;
    private static int color = 0xFF7B5CF0;

    private static double ax, ay, az;
    private static float yawRad;
    private static float yawDeg;
    private static float startYaw, startPitch;
    private static long startNs;

    static {
        WorldRenderEvents.AFTER_ENTITIES.register(RespawnFx::render);
        HudRenderCallback.EVENT.register(RespawnFx::drive);
    }

    private RespawnFx() {}

    public static void trigger(MinecraftClient mc) {
        if (mc.player == null || VisualsMod.moduleManager == null) return;
        RespawnAnimation mod = VisualsMod.moduleManager.getModule(RespawnAnimation.class);
        if (mod == null || !mod.isEnabled()) return;

        style = styleOf(RespawnAnimation.style);
        seconds = Math.max(0.6, Math.min(6.0, RespawnAnimation.duration));
        color = RespawnAnimation.color;
        ax = mc.player.getX();
        ay = mc.player.getY();
        az = mc.player.getZ();
        yawRad = (float) Math.toRadians(mc.player.getYaw());
        yawDeg = mc.player.getYaw();
        startYaw = mc.player.getYaw();
        startPitch = mc.player.getPitch();
        startNs = System.nanoTime();
        active = true;
    }

    /** Отмена сцены (выключили модуль, вышли с сервера): камеру обязательно отпускаем. */
    public static void cancel() {
        active = false;
        RespawnCamera.release();
    }

    /** Своё тело на время сцены не рисуем: его играет призрачная модель из боксов. */
    public static boolean hidesSelf(Entity e) {
        return active && e == MinecraftClient.getInstance().player;
    }

    private static double progress() {
        return (System.nanoTime() - startNs) / 1_000_000_000.0 / seconds;
    }

    /**
     * Кадровый драйвер камеры: сцена снимается облетающей камерой, как в VictoryAnimation,
     * иначе в первом лице всё происходит «за спиной» и не видно. Отпускаем сцену, стоит
     * игроку повести мышью или отбежать от точки возрождения — управление важнее красоты.
     */
    private static void drive(DrawContext ctx, RenderTickCounter tc) {
        if (!active) {
            if (RespawnCamera.active) RespawnCamera.release();
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || !RespawnAnimation.cinema) { cancel(); return; }
        double t = progress();
        if (t >= 1.0) { cancel(); return; }
        double moved = Math.hypot(mc.player.getX() - ax, mc.player.getZ() - az);
        float look = Math.abs(wrapDeg(mc.player.getYaw() - startYaw)) + Math.abs(mc.player.getPitch() - startPitch);
        if (moved > 0.6 || look > 2.5f) { cancel(); return; }

        switch (style) {
            case SKYFALL -> {
                double drop = 18.0 * (1 - easeIn(clamp01(t / 0.45)));
                RespawnCamera.orbit(ax, ay + drop * 0.7 + 0.9, az, 4.6, 0.8, yawDeg + 150 + 70 * t);
            }
            case PORTAL -> {
                double px = ax + Math.sin(yawRad) * 0.55, pz = az - Math.cos(yawRad) * 0.55;
                RespawnCamera.orbit(px, ay + 1.05, pz, 3.3, 0.5, yawDeg + 90 + 12 * Math.sin(t * 3.0));
            }
            default -> {
                double d = 6.2 - 2.8 * ease(clamp01(t / 0.8));
                RespawnCamera.orbit(ax, ay + 1.0, az, d, 2.3 - 1.1 * t, yawDeg + 40 + 110 * ease(t));
            }
        }
    }

    private static float wrapDeg(float a) {
        while (a > 180f) a -= 360f;
        while (a < -180f) a += 360f;
        return a;
    }

    private static int styleOf(String s) {
        if (SKY_NAME.equals(s)) return SKYFALL;
        if (PORTAL_NAME.equals(s)) return PORTAL;
        return ASSEMBLE;
    }

    private static void render(WorldRenderContext ctx) {
        if (!active) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || ctx.gameRenderer().getCamera() == null
                || ctx.matrices() == null || ctx.consumers() == null) {
            cancel();
            return;
        }
        double t = progress();
        if (t >= 1.0) {
            cancel();
            return;
        }

        try {
            ctx.matrices().push();
            ctx.matrices().translate(-ctx.gameRenderer().getCamera().getCameraPos().x,
                                        -ctx.gameRenderer().getCamera().getCameraPos().y,
                                        -ctx.gameRenderer().getCamera().getCameraPos().z);
            Matrix4f mat = new Matrix4f(ctx.matrices().peek().getPositionMatrix());
            VertexConsumer quads = ctx.consumers().getBuffer(RenderLayers.debugQuads());
            VertexConsumer lines = ctx.consumers().getBuffer(RenderLayers.lines());

            switch (style) {
                case SKYFALL -> renderSkyfall(quads, lines, mat, t);
                case PORTAL -> renderPortal(quads, lines, mat, t);
                default -> renderAssemble(quads, lines, mat, t);
            }
            ctx.matrices().pop();
        } catch (Throwable th) {
            cancel();
            com.visualmod.util.Diag.once("RespawnFx", th);
        }
    }

    private static void renderAssemble(VertexConsumer quads, VertexConsumer lines, Matrix4f mat, double t) {
        float r = WorldRenderUtil.colorR(color), g = WorldRenderUtil.colorG(color), b = WorldRenderUtil.colorB(color);

        for (int i = 0; i < PART_X.length; i++) {
            double local = clamp01((t - 0.05 - i * 0.06) / 0.55);
            double spread = 2.2 + i * 0.45;
            double ang = i * 2.399963;
            double k = 1 - ease(local);
            box(quads, mat,
                ax + PART_X[i] + Math.cos(ang) * spread * k,
                ay + PART_Y[i] + (i % 2 == 0 ? 1 : -1) * spread * 0.7 * k,
                az + Math.sin(ang) * spread * k,
                PART_W[i], PART_H[i], r, g, b, (float) (0.15 + 0.85 * local));
        }

        double ring = clamp01((t - 0.55) / 0.45);
        if (ring > 0) {
            double radius = 0.4 + 5.5 * ease(ring);
            WorldRenderUtil.drawCircle(lines, mat, ax, ay + 0.06, az, radius, 40,
                r, g, b, (float) ((1 - ring) * 0.85));
            WorldRenderUtil.drawCircle(lines, mat, ax, ay + 0.06, az, radius * 0.65, 40,
                1f, 1f, 1f, (float) ((1 - ring) * 0.4));
        }
    }

    private static void renderSkyfall(VertexConsumer quads, VertexConsumer lines, Matrix4f mat, double t) {
        float r = WorldRenderUtil.colorR(color), g = WorldRenderUtil.colorG(color), b = WorldRenderUtil.colorB(color);
        final double land = 0.45;
        final double height = 18.0;

        double drop = height * (1 - easeIn(clamp01(t / land)));
        double crouch = t >= land ? 0.45 * Math.sin(Math.PI * clamp01((t - land) / 0.18)) : 0;
        float body = t < land ? 1f : (float) (1 - clamp01((t - 0.85) / 0.15) * 0.5);

        if (t < land) {
            for (int i = 0; i < 10; i++) {
                double a = i * 0.6283 + t * 4;
                double dx = Math.cos(a) * 0.5, dz = Math.sin(a) * 0.5;
                double y0 = ay + drop + 2.1 + i * 0.35;
                WorldRenderUtil.drawLine(lines, mat, ax + dx, y0, az + dz,
                    ax + dx * 1.8, y0 + 1.2, az + dz * 1.8, r, g, b, 0.35f);
            }
        }

        for (int i = 0; i < PART_X.length; i++) {
            boolean leg = i >= 4;
            box(quads, mat, ax + PART_X[i],
                ay + PART_Y[i] + drop - (leg ? 0 : crouch) - (leg ? crouch * PART_Y[i] : 0),
                az, PART_W[i], leg ? PART_H[i] * (1 - crouch * 0.4) : PART_H[i],
                r, g, b, body);
        }

        if (t < land) return;
        double after = clamp01((t - land) / (1 - land));
        for (int k = 0; k < 3; k++) {
            double rt = clamp01(after * 1.35 - k * 0.16);
            if (rt <= 0) continue;
            double radius = 0.6 + 11.0 * ease(rt);
            float alpha = (float) ((1 - rt) * (0.9 - k * 0.22));
            WorldRenderUtil.drawCircle(lines, mat, ax, ay + 0.08 + k * 0.05, az, radius, 48,
                r, g, b, alpha);
            for (int i = 0; i < 12; i++) {
                double a = i * 0.5236 + k;
                double px = ax + Math.cos(a) * radius, pz = az + Math.sin(a) * radius;
                WorldRenderUtil.drawLine(lines, mat, px, ay + 0.1, pz,
                    px, ay + 0.1 + (1 - rt) * 1.6, pz, 1f, 1f, 1f, alpha * 0.5f);
            }
        }
        double flash = clamp01(1 - after * 4);
        if (flash > 0) {
            double s = 1.5 + 6 * after;
            WorldRenderUtil.drawFilledBox(quads, mat,
                new Box(ax - s, ay + 0.02, az - s, ax + s, ay + 0.06, az + s),
                1f, 1f, 1f, (float) (flash * 0.25));
        }
    }

    private static void renderPortal(VertexConsumer quads, VertexConsumer lines, Matrix4f mat, double t) {
        float r = WorldRenderUtil.colorR(color), g = WorldRenderUtil.colorG(color), b = WorldRenderUtil.colorB(color);

        double fwdX = -Math.sin(yawRad), fwdZ = Math.cos(yawRad);
        double cx = ax - fwdX * 1.1, cz = az - fwdZ * 1.1;
        double ux = Math.cos(yawRad), uz = Math.sin(yawRad);

        double open = clamp01(t / 0.35);
        double fade = 1 - clamp01((t - 0.6) / 0.4);
        double rx = 0.75 * ease(open), ry = 1.15 * ease(open);

        for (int ring = 0; ring < 3; ring++) {
            double k = 1 - ring * 0.22;
            double wob = Math.sin(t * 9 + ring) * 0.05;
            float alpha = (float) (fade * (0.85 - ring * 0.2));
            double ellX = (rx + wob) * k, ellY = (ry + wob) * k;
            for (int i = 0; i < 28; i++) {
                double a0 = i / 28.0 * Math.PI * 2, a1 = (i + 1) / 28.0 * Math.PI * 2;
                WorldRenderUtil.drawLine(lines, mat,
                    cx + Math.cos(a0) * ellX * ux, ay + ry * k + Math.sin(a0) * ellY,
                    cz + Math.cos(a0) * ellX * uz,
                    cx + Math.cos(a1) * ellX * ux, ay + ry * k + Math.sin(a1) * ellY,
                    cz + Math.cos(a1) * ellX * uz, r, g, b, alpha);
            }
        }
        if (fade > 0) {
            WorldRenderUtil.drawFilledBox(quads, mat,
                new Box(cx - 0.2, ay + 0.05, cz - 0.2, cx + 0.2, ay + 2.0, cz + 0.2),
                r, g, b, (float) (fade * 0.12));
        }

        double step = ease(clamp01((t - 0.2) / 0.6));
        double back = 1.1 * (1 - step);
        double fx = ax + fwdX * back, fz = az + fwdZ * back;
        float alpha = (float) ((0.2 + 0.8 * clamp01(t / 0.3)) * (1 - clamp01((t - 0.85) / 0.15)));
        boolean walking = step > 0 && step < 1;
        double swing = walking ? Math.sin(t * 22) * 0.14 : 0;

        for (int i = 0; i < PART_X.length; i++) {
            double legShift = i >= 4 ? (i == 4 ? swing : -swing) : 0;
            box(quads, mat, fx + PART_X[i], ay + PART_Y[i] - Math.abs(legShift) * 0.4,
                fz + legShift, PART_W[i], PART_H[i], r, g, b, alpha);
        }
    }

    private static void box(VertexConsumer buf, Matrix4f mat, double x, double y, double z,
                            double w, double h, float r, float g, float b, float alpha) {
        if (alpha <= 0f) return;
        WorldRenderUtil.drawFilledBox(buf, mat,
            new Box(x - w / 2, y, z - w / 2, x + w / 2, y + h, z + w / 2),
            r, g, b, Math.min(1f, alpha));
    }

    private static double clamp01(double v) { return v < 0 ? 0 : Math.min(1, v); }
    private static double ease(double v) { return 1 - (1 - v) * (1 - v) * (1 - v); }
    private static double easeIn(double v) { return v * v * v; }
}

package com.visualmod.module.modules.player;

import net.minecraft.client.render.RenderLayers;
import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.util.HudStyle;
import com.visualmod.util.WorldRenderUtil;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TotemAnimation extends Module implements Configurable {

    public static int style = 0;
    public static boolean onOthers = false;

    private static final String[] STYLES = {"SPHERE", "CROSS", "HALO"};
    private static final Map<UUID, Anim> ACTIVE = new ConcurrentHashMap<>();
    private static boolean hudRegistered = false;

    static {
        WorldRenderEvents.AFTER_ENTITIES.register(TotemAnimation::renderWorld);
    }

    public TotemAnimation() {
        super("TotemAnimation", "Custom totem pop animation", Category.PLAYER);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Style", STYLES, () -> style, i -> style = i));
        l.add(new ModeSetting("Others", new String[]{"OFF", "ON"},
                () -> onOthers ? 1 : 0, i -> onOthers = (i == 1)));
        return l;
    }

    public static boolean isActive() {
        if (VisualsMod.moduleManager == null) return false;
        TotemAnimation m = VisualsMod.moduleManager.getModule(TotemAnimation.class);
        return m != null && m.isEnabled();
    }

    public static void trigger(PlayerEntity player) {
        if (!isActive() || player == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean self = mc.player != null && player.getUuid().equals(mc.player.getUuid());
        if (!self && !onOthers) return;

        float maxT = style == 1 ? 2.2f : 5f;
        ACTIVE.put(player.getUuid(), new Anim(style, System.nanoTime(), maxT,
                player.getX(), player.getY(), player.getZ()));
    }

    public static boolean shouldCancelVanillaTotem() {
        return isActive();
    }

    @Override
    public void onEnable() {
        if (!hudRegistered) {
            hudRegistered = true;
            HudRenderCallback.EVENT.register(TotemAnimation::onHud);
        }
    }

    @Override
    public void onTick() {
        if (ACTIVE.isEmpty()) return;
        long now = System.nanoTime();
        Iterator<Map.Entry<UUID, Anim>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Anim a = it.next().getValue();
            if (a.seconds(now) >= a.maxSec) it.remove();
        }
    }

    private static void renderWorld(WorldRenderContext ctx) {
        if (!isActive() || ACTIVE.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || ctx.matrices() == null || ctx.consumers() == null) return;

        Vec3d cam = ctx.gameRenderer().getCamera().getCameraPos();
        MatrixStack matrices = ctx.matrices();
        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = matrices.peek().getPositionMatrix();
        VertexConsumer lines = ctx.consumers().getBuffer(RenderLayers.lines());

        long now = System.nanoTime();
        for (Map.Entry<UUID, Anim> e : ACTIVE.entrySet()) {
            Anim a = e.getValue();
            float t = a.seconds(now);
            if (t >= a.maxSec) continue;
            PlayerEntity p = mc.world.getPlayerByUuid(e.getKey());
            Vec3d pos = p != null
                    ? p.getLerpedPos(mc.getRenderTickCounter().getTickProgress(false))
                    : new Vec3d(a.x, a.y, a.z);
            float u = t / a.maxSec;
            switch (a.style) {
                case 0 -> renderSphere(lines, mat, pos, t, u);
                case 1 -> renderCross(lines, mat, pos, t, u);
                default -> renderHalo(lines, mat, pos, t, u);
            }
        }
        matrices.pop();
    }

    private static void renderSphere(VertexConsumer buf, Matrix4f mat, Vec3d pos, float sec, float u) {
        float fade = fadeInOut(u, 0.08f);
        double cx = pos.x, cy = pos.y + 1.0, cz = pos.z;
        int cycle = Math.min(2, (int) (sec / 2.0));
        float local = (float) ((sec % 2.0) / 2.0);

        if (cycle == 0) {
            for (int i = 0; i < 8; i++) {
                double ang = i * Math.PI / 4.0 + sec * 0.15;
                double r = 1.15 + 0.15 * Math.sin(sec * 3 + i);
                double x = cx + Math.cos(ang) * r;
                double z = cz + Math.sin(ang) * r;
                sweep(buf, mat, x, cy - 1.1, z, x, cy + 1.3, z, local, gold(i), fade);
            }
        } else if (cycle == 1) {
            for (int ring = 0; ring < 5; ring++) {
                double y = cy - 0.9 + ring * 0.45;
                double rad = 0.55 + ring * 0.18;
                int segs = 28;
                double prevX = cx + rad, prevZ = cz;
                float phase = (local + ring * 0.12f) % 1f;
                for (int i = 1; i <= segs; i++) {
                    double a0 = Math.PI * 2 * i / segs;
                    double nx = cx + Math.cos(a0) * rad;
                    double nz = cz + Math.sin(a0) * rad;
                    float along = i / (float) segs;
                    float d = Math.abs(along - phase);
                    if (d > 0.5f) d = 1f - d;
                    float a = fade * Math.max(0f, 1f - d * 6f);
                    if (a > 0.04f) {
                        WorldRenderUtil.drawLine(buf, mat, prevX, y, prevZ, nx, y, nz, 1f, 0.55f, 0.12f, a);
                    }
                    prevX = nx;
                    prevZ = nz;
                }
            }
        } else {
            for (int i = 0; i < 6; i++) {
                double start = i * Math.PI / 3.0;
                Vec3d prev = null;
                int steps = 36;
                int head = (int) (local * steps);
                for (int s = 0; s <= steps; s++) {
                    double t = s / (double) steps;
                    double ang = start + t * Math.PI * 2.4;
                    double rad = 0.35 + t * 1.35;
                    double y = cy - 1.0 + t * 2.2;
                    Vec3d p = new Vec3d(cx + Math.cos(ang) * rad, y, cz + Math.sin(ang) * rad);
                    if (prev != null && s <= head + 1) {
                        float a = fade * (1f - Math.abs(s - head) / 8f);
                        if (a > 0.05f) {
                            float[] c = gold(i);
                            WorldRenderUtil.drawLine(buf, mat, prev.x, prev.y, prev.z, p.x, p.y, p.z, c[0], c[1], c[2], a);
                        }
                    }
                    prev = p;
                }
            }
        }
    }

    private static void renderCross(VertexConsumer buf, Matrix4f mat, Vec3d pos, float sec, float u) {
        float fade = fadeInOut(u, 0.12f);
        double cx = pos.x, cy = pos.y + 1.0, cz = pos.z;
        double dist = (1.0 - easeIn(Math.min(1f, u))) * 2.8;
        int[][] dirs = {
                {0, 0, 1}, {0, 0, -1}, {1, 0, 0}, {-1, 0, 0},
                {1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1},
                {0, 1, 0}, {0, -1, 0}
        };
        for (int d = 0; d < dirs.length; d++) {
            int[] dir = dirs[d];
            double len = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
            double nx = dir[0] / len, ny = dir[1] / len, nz = dir[2] / len;
            Vec3d prev = null;
            for (int j = 0; j < 8; j++) {
                double along = dist + j * 0.12;
                Vec3d p = new Vec3d(cx + nx * along, cy + ny * along, cz + nz * along);
                if (prev != null) {
                    float[] c = gold(j + d);
                    WorldRenderUtil.drawLine(buf, mat, prev.x, prev.y, prev.z, p.x, p.y, p.z,
                            c[0], c[1], c[2], fade * (1f - j * 0.08f));
                }
                prev = p;
            }
        }
    }

    private static void renderHalo(VertexConsumer buf, Matrix4f mat, Vec3d pos, float sec, float u) {
        float fade = fadeInOut(u, 0.1f);
        double cx = pos.x, cy = pos.y + 1.05, cz = pos.z;
        double expand = u < 0.45 ? easeOut(u / 0.45f) : 1.0 - easeIn((u - 0.45f) / 0.55f);
        double rad = 0.4 + expand * 1.6;
        int segs = 40;
        double prevX = cx + rad, prevZ = cz;
        for (int i = 1; i <= segs; i++) {
            double a = Math.PI * 2 * i / segs + sec * 1.4;
            double nx = cx + Math.cos(a) * rad;
            double nz = cz + Math.sin(a) * rad;
            WorldRenderUtil.drawLine(buf, mat, prevX, cy, prevZ, nx, cy, nz, 1f, 0.78f, 0.18f, fade);
            prevX = nx;
            prevZ = nz;
        }
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI * 2 / 10 + sec * 2.2;
            double r = rad * (0.35 + 0.65 * ((i + sec * 3) % 1.0));
            double y = cy + Math.sin(sec * 4 + i) * 0.35;
            double x = cx + Math.cos(a) * r;
            double z = cz + Math.sin(a) * r;
            WorldRenderUtil.drawLine(buf, mat, cx, cy, cz, x, y, z, 1f, 0.45f + 0.2f * (i % 3), 0.1f, fade * 0.85f);
        }
    }

    private static void sweep(VertexConsumer buf, Matrix4f mat,
                              double x1, double y1, double z1, double x2, double y2, double z2,
                              float progress, float[] c, float fade) {
        int segs = 18;
        Vec3d a = new Vec3d(x1, y1, z1);
        Vec3d b = new Vec3d(x2, y2, z2);
        int head = (int) (progress * segs);
        Vec3d prev = a;
        for (int i = 1; i <= segs; i++) {
            double t = i / (double) segs;
            Vec3d p = a.lerp(b, t);
            float d = Math.abs(i - head);
            float al = fade * Math.max(0f, 1f - d / 5f);
            if (al > 0.04f) {
                WorldRenderUtil.drawLine(buf, mat, prev.x, prev.y, prev.z, p.x, p.y, p.z, c[0], c[1], c[2], al);
            }
            prev = p;
        }
    }

    private static void onHud(DrawContext ctx, RenderTickCounter tc) {
        if (!isActive()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        Anim a = ACTIVE.get(mc.player.getUuid());
        if (a == null) return;
        float t = a.seconds(System.nanoTime()) / a.maxSec;
        if (t >= 1f) return;
        float flash = fadeInOut(t, 0.06f);
        if (flash > 0.02f && t < 0.18f) {
            int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
            float aFlash = (1f - t / 0.18f) * 0.22f;
            ctx.fill(0, 0, w, h, argb(1f, 0.92f, 0.45f, aFlash));
        }
        if (a.style == 2 && t < 0.25f) {
            int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
            int cx = w / 2, cy = h / 2;
            float r = (0.25f - t) / 0.25f * Math.min(w, h) * 0.08f;
            HudStyle.fillRounded(ctx, cx - r, cy - r, r * 2, r * 2, r,
                    argb(1f, 0.85f, 0.25f, 0.18f * flash));
        }
    }

    private static float[] gold(int i) {
        return switch (i % 4) {
            case 0 -> new float[]{1f, 0.82f, 0.18f};
            case 1 -> new float[]{0.35f, 1f, 0.45f};
            case 2 -> new float[]{1f, 0.45f, 0.12f};
            default -> new float[]{0.55f, 0.85f, 1f};
        };
    }

    private static float fadeInOut(float t, float edge) {
        if (t < edge) return t / edge;
        if (t > 1f - edge) return Math.max(0f, (1f - t) / edge);
        return 1f;
    }

    private static float easeIn(float t) { return t * t; }
    private static float easeOut(float t) { return 1f - (1f - t) * (1f - t); }

    private static int argb(float r, float g, float b, float a) {
        return ((int) (Math.min(1, a) * 255) << 24) | ((int) (Math.min(1, r) * 255) << 16)
                | ((int) (Math.min(1, g) * 255) << 8) | (int) (Math.min(1, b) * 255);
    }

    private static final class Anim {
        final int style;
        final long startNs;
        final float maxSec;
        final double x, y, z;

        Anim(int style, long startNs, float maxSec, double x, double y, double z) {
            this.style = style;
            this.startNs = startNs;
            this.maxSec = maxSec;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        float seconds(long now) {
            return (now - startNs) / 1_000_000_000f;
        }
    }
}

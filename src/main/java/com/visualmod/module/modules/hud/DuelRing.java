package com.visualmod.module.modules.hud;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.module.settings.ToggleSetting;
import com.visualmod.util.IrisCompat;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Круг дуэли на земле: пока DuelBoard ведёт бой, посреди пары горит кольцо с бегущим бликом,
 * а после развязки оно разлетается наружу и гаснет — по снимку центра, иначе к моменту
 * конца позиция противника уже потеряна.
 */
public class DuelRing extends Module implements Configurable {

    public static double pad = 2.0;
    public static int color = 0xFF62E8FF;
    public static boolean ticks = true;
    public static double breakFade = 1.4;

    private static final class Burst {
        final double cx, cy, cz, r;
        final long born;

        Burst(double cx, double cy, double cz, double r, long born) {
            this.cx = cx; this.cy = cy; this.cz = cz;
            this.r = r; this.born = born;
        }
    }

    private static final List<Burst> BURSTS = new ArrayList<>();
    private static final int SEG = 96;

    private static double lastCx, lastCy, lastCz, lastR;
    private static long lastSeenMs = 0L;
    private static long lastEndMs = 0L;

    static { WorldRenderEvents.AFTER_ENTITIES.register(DuelRing::render); }

    public DuelRing() {
        super("DuelRing", "Arena circle on the ground during a duel", Category.HUD);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new SliderSetting("Pad", 0.0, 8.0, 0.25, () -> pad, v -> pad = v, "b"));
        l.add(new SliderSetting("Break", 0.4, 3.0, 0.1, () -> breakFade, v -> breakFade = v, "s"));
        l.add(new ColorSetting("Color", () -> color, c -> color = c));
        l.add(new ToggleSetting("Ticks", () -> ticks, v -> ticks = v));
        return l;
    }

    @Override
    public void onDisable() {
        BURSTS.clear();
        lastSeenMs = 0L;
        lastEndMs = 0L;
    }

    private static void render(WorldRenderContext ctx) {
        try {
            if (IrisCompat.skipWorldPass()) return;
            if (VisualsMod.moduleManager == null || ctx.matrices() == null || ctx.consumers() == null) return;
            DuelRing m = VisualsMod.moduleManager.getModule(DuelRing.class);
            if (m == null || !m.isEnabled()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null) return;

            long now = System.currentTimeMillis();
            int foeId = DuelBoard.activeFoeId();
            long end = DuelBoard.duelEndMs();
            if (end == 0L) {
                lastEndMs = 0L;
            } else if (end != lastEndMs) {
                lastEndMs = end;
                if (lastSeenMs != 0L && now - lastSeenMs < 700L) {
                    BURSTS.add(new Burst(lastCx, lastCy, lastCz, lastR, now));
                }
            }

            double dur = Math.max(0.3, breakFade) * 1000.0;
            boolean live = foeId != -1;
            Entity foe = live ? mc.world.getEntityById(foeId) : null;
            if (live && foe == null) live = false;
            if (!live && BURSTS.isEmpty()) return;

            double cx = 0, cy = 0, cz = 0, r = 0;
            if (live) {
                cx = (mc.player.getX() + foe.getX()) * 0.5;
                cz = (mc.player.getZ() + foe.getZ()) * 0.5;
                double dist = Math.hypot(mc.player.getX() - foe.getX(), mc.player.getZ() - foe.getZ());
                r = Math.max(1.2, dist * 0.5 + Math.max(0.0, pad));
                cy = groundY(mc, cx, (mc.player.getY() + foe.getY()) * 0.5, cz);
                lastCx = cx; lastCy = cy; lastCz = cz; lastR = r;
                lastSeenMs = now;
            }

            ctx.matrices().push();
            Camera camera = ctx.gameRenderer().getCamera();
            Vec3d cam = camera.getCameraPos();
            ctx.matrices().translate(-cam.x, -cam.y, -cam.z);
            Matrix4f mat = ctx.matrices().peek().getPositionMatrix();
            VertexConsumer vc = ctx.consumers().getBuffer(RenderLayers.debugQuads());
            float[] c = rgb(color);

            if (live) {
                ring(vc, mat, cx, cy, cz, r, 0.88f, c, now);
                inner(vc, mat, cx, cy, cz, r * 0.90, 0.42f, c);
                if (ticks) marks(vc, mat, cx, cy, cz, r, 0.55f, c);
            }

            if (!BURSTS.isEmpty()) {
                for (Iterator<Burst> it = BURSTS.iterator(); it.hasNext(); ) {
                    Burst b = it.next();
                    double p = (now - b.born) / dur;
                    if (p >= 1.0) { it.remove(); continue; }
                    float a = (float) Math.pow(1.0 - p, 1.3);
                    double rr = b.r * (1.0 + 0.75 * p);
                    ring(vc, mat, b.cx, b.cy, b.cz, rr, a, c, now);
                    inner(vc, mat, b.cx, b.cy, b.cz, rr * 0.90, a * 0.5f, c);
                }
            }

            com.visualmod.util.RenderFlush.flush(ctx.consumers(), RenderLayers.debugQuads());
            ctx.matrices().pop();
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("DuelRing", t);
        }
    }

    /** Кольцо с бегущим бликом: яркий горб медленно обходит арену. */
    private static void ring(VertexConsumer vc, Matrix4f mat, double cx, double cy, double cz,
                             double r, float alpha, float[] c, long now) {
        if (r <= 0.05 || alpha <= 0.01f) return;
        double w = Math.max(0.035, r * 0.022);
        double phase = (now / 1000.0) * 1.3;
        float y = (float) (cy + 0.02);
        for (int i = 0; i < SEG; i++) {
            double a0 = i / (double) SEG * Math.PI * 2.0;
            double a1 = (i + 1) / (double) SEG * Math.PI * 2.0;
            double gl = Math.pow(Math.max(0.0, Math.cos(a0 - phase)), 6.0);
            float sh = (float) (0.38 + 0.62 * gl);
            float a = alpha * (float) (0.30 + 0.70 * gl);
            double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            quad(vc, mat,
                    cx + (r - w) * c0, y, cz + (r - w) * s0,
                    cx + (r + w) * c0, y, cz + (r + w) * s0,
                    cx + (r + w) * c1, y, cz + (r + w) * s1,
                    cx + (r - w) * c1, y, cz + (r - w) * s1,
                    c[0] * sh, c[1] * sh, c[2] * sh, a);
        }
    }

    private static void inner(VertexConsumer vc, Matrix4f mat, double cx, double cy, double cz,
                              double r, float alpha, float[] c) {
        if (alpha <= 0.01f) return;
        double w = 0.012 + r * 0.004;
        float y = (float) (cy + 0.018);
        for (int i = 0; i < SEG; i += 2) {
            double a0 = i / (double) SEG * Math.PI * 2.0;
            double a1 = (i + 2) / (double) SEG * Math.PI * 2.0;
            double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            quad(vc, mat,
                    cx + (r - w) * c0, y, cz + (r - w) * s0,
                    cx + (r + w) * c0, y, cz + (r + w) * s0,
                    cx + (r + w) * c1, y, cz + (r + w) * s1,
                    cx + (r - w) * c1, y, cz + (r - w) * s1,
                    c[0], c[1], c[2], alpha);
        }
    }

    /** Радиальные насечки каждые 15° — арена читается как разметка, а не как круг. */
    private static void marks(VertexConsumer vc, Matrix4f mat, double cx, double cy, double cz,
                              double r, float alpha, float[] c) {
        float y = (float) (cy + 0.02);
        for (int i = 0; i < 24; i++) {
            double a = i / 24.0 * Math.PI * 2.0;
            double c0 = Math.cos(a), s0 = Math.sin(a);
            double innerR = r + 0.10;
            double outerR = r + (i % 6 == 0 ? 0.34 : 0.22);
            double half = i % 6 == 0 ? 0.035 : 0.022;
            double nx = -s0 * half, nz = c0 * half;
            quad(vc, mat,
                    cx + c0 * innerR - nx, y, cz + s0 * innerR - nz,
                    cx + c0 * innerR + nx, y, cz + s0 * innerR + nz,
                    cx + c0 * outerR + nx, y, cz + s0 * outerR + nz,
                    cx + c0 * outerR - nx, y, cz + s0 * outerR - nz,
                    c[0], c[1], c[2], alpha * (i % 6 == 0 ? 1f : 0.7f));
        }
    }

    private static double groundY(MinecraftClient mc, double x, double y, double z) {
        for (int i = 0; i < 12; i++) {
            double cy = y + 0.5 - i * 0.5;
            if (!mc.world.isAir(new BlockPos((int) Math.floor(x), (int) Math.floor(cy), (int) Math.floor(z)))) {
                return Math.floor(cy) + 1.006;
            }
        }
        return y + 0.02;
    }

    private static void quad(VertexConsumer vc, Matrix4f mat,
                             double x0, double y0, double z0, double x1, double y1, double z1,
                             double x2, double y2, double z2, double x3, double y3, double z3,
                             float r, float g, float b, float a) {
        if (a <= 0.01f) return;
        float rr = Math.min(1f, r), gg = Math.min(1f, g), bb = Math.min(1f, b);
        vc.vertex(mat, (float) x0, (float) y0, (float) z0).color(rr, gg, bb, a);
        vc.vertex(mat, (float) x1, (float) y1, (float) z1).color(rr, gg, bb, a);
        vc.vertex(mat, (float) x2, (float) y2, (float) z2).color(rr, gg, bb, a);
        vc.vertex(mat, (float) x3, (float) y3, (float) z3).color(rr, gg, bb, a);
        vc.vertex(mat, (float) x3, (float) y3, (float) z3).color(rr, gg, bb, a);
        vc.vertex(mat, (float) x2, (float) y2, (float) z2).color(rr, gg, bb, a);
        vc.vertex(mat, (float) x1, (float) y1, (float) z1).color(rr, gg, bb, a);
        vc.vertex(mat, (float) x0, (float) y0, (float) z0).color(rr, gg, bb, a);
    }

    private static float[] rgb(int argb) {
        return new float[]{((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f};
    }
}

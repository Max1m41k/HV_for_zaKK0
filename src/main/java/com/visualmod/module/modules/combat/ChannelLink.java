package com.visualmod.module.modules.combat;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.util.IrisCompat;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Канал: с третьего попадания подряд между рукой и целью бьёт ломаная энергетическая дуга.
 * Каждый следующий удар серии добавляет изломов и меняет цвет с холодного на горячий,
 * поэтому серия читается как разгон, а не как набор одинаковых вспышек.
 */
public class ChannelLink extends Module implements Configurable {

    public static double comboWindow = 1.2;
    public static int minCombo = 3;
    public static double life = 0.26;
    public static int coldColor = 0xFF8FE9FF;
    public static int hotColor = 0xFFFF7BD1;

    private static final class Link {
        final double ax, ay, az, bx, by, bz;
        final long born;
        final int jags;
        final long seed;
        final float strength;

        Link(double ax, double ay, double az, double bx, double by, double bz,
             long born, int jags, long seed, float strength) {
            this.ax = ax; this.ay = ay; this.az = az;
            this.bx = bx; this.by = by; this.bz = bz;
            this.born = born; this.jags = jags; this.seed = seed; this.strength = strength;
        }
    }

    private static final List<Link> LINKS = new ArrayList<>();
    private static int combo = 0;
    private static long lastHitMs = 0L;

    static { WorldRenderEvents.AFTER_ENTITIES.register(ChannelLink::render); }

    public ChannelLink() {
        super("ChannelLink", "Hit streak arcs energy from hand to target", Category.COMBAT);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new SliderSetting("Window", 0.5, 3.0, 0.1, () -> comboWindow, v -> comboWindow = v, "s"));
        l.add(new SliderSetting("From", 2, 8, 1, () -> (double) minCombo, v -> minCombo = (int) Math.round(v)));
        l.add(new SliderSetting("Life", 0.1, 0.8, 0.02, () -> life, v -> life = v, "s"));
        l.add(new ColorSetting("Cold", () -> coldColor, c -> coldColor = c));
        l.add(new ColorSetting("Hot", () -> hotColor, c -> hotColor = c));
        return l;
    }

    @Override
    public void onEnable() { reset(); }

    @Override
    public void onDisable() { reset(); }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.isDead()) reset();
    }

    private static void reset() {
        combo = 0;
        lastHitMs = 0L;
        LINKS.clear();
    }

    private static boolean active() {
        if (VisualsMod.moduleManager == null) return false;
        ChannelLink m = VisualsMod.moduleManager.getModule(ChannelLink.class);
        return m != null && m.isEnabled();
    }

    public static void onAttack(PlayerEntity player, Entity target) {
        if (player == null || target == null) return;
        if (!active()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        long now = System.currentTimeMillis();
        if (now - lastHitMs > (long) (Math.max(0.2, comboWindow) * 1000)) combo = 0;
        combo++;
        lastHitMs = now;
        int need = Math.max(2, minCombo);
        if (combo < need) return;

        Vec3d eye = player.getEyePos();
        Vec3d hit = target.getEntityPos().add(0, Math.max(0.4, target.getHeight() * 0.55), 0);
        Vec3d fwd = player.getRotationVec(1.0f);
        Vec3d right = new Vec3d(0, 1, 0).crossProduct(fwd);
        right = right.lengthSquared() > 1e-6 ? right.normalize() : new Vec3d(1, 0, 0);
        Vec3d hand = eye.add(right.multiply(0.34)).add(0, -0.24, 0).add(fwd.multiply(0.28));

        float strength = Math.min(1f, (combo - need + 1) / 4f);
        int jags = Math.min(9, 2 + combo);
        LINKS.add(new Link(hand.x, hand.y, hand.z, hit.x, hit.y, hit.z, now, jags, now * 31 + combo, strength));
        while (LINKS.size() > 5) LINKS.remove(0);
    }

    private static void render(WorldRenderContext ctx) {
        try {
            if (IrisCompat.skipWorldPass()) return;
            if (VisualsMod.moduleManager == null || ctx.matrices() == null || ctx.consumers() == null) return;
            ChannelLink m = VisualsMod.moduleManager.getModule(ChannelLink.class);
            if (m == null || !m.isEnabled()) return;
            if (LINKS.isEmpty()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null) return;

            long now = System.currentTimeMillis();
            double dur = Math.max(0.08, life) * 1000.0;
            ctx.matrices().push();
            Camera camera = ctx.gameRenderer().getCamera();
            Vec3d cam = camera.getCameraPos();
            ctx.matrices().translate(-cam.x, -cam.y, -cam.z);
            Matrix4f mat = ctx.matrices().peek().getPositionMatrix();
            VertexConsumer vc = ctx.consumers().getBuffer(RenderLayers.debugQuads());
            Vector3f right = new Vector3f(1, 0, 0).rotate(camera.getRotation());
            Vector3f up = new Vector3f(0, 1, 0).rotate(camera.getRotation());

            for (Iterator<Link> it = LINKS.iterator(); it.hasNext(); ) {
                Link l = it.next();
                double p = (now - l.born) / dur;
                if (p >= 1.0) { it.remove(); continue; }
                drawLink(vc, mat, right, up, l, p, now);
            }

            com.visualmod.util.RenderFlush.flush(ctx.consumers(), RenderLayers.debugQuads());
            ctx.matrices().pop();
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("ChannelLink", t);
        }
    }

    private static void drawLink(VertexConsumer vc, Matrix4f mat, Vector3f r, Vector3f u,
                                 Link l, double p, long now) {
        float[] cold = rgb(coldColor);
        float[] hot = rgb(hotColor);
        float[] c = new float[]{
                cold[0] + (hot[0] - cold[0]) * l.strength,
                cold[1] + (hot[1] - cold[1]) * l.strength,
                cold[2] + (hot[2] - cold[2]) * l.strength};
        float fade = (float) Math.pow(1.0 - p, 1.3);
        float flick = 0.78f + 0.22f * (float) Math.sin(now / 28.0 + l.seed * 0.001);
        float amp = 0.085f + 0.095f * l.strength;
        int n = l.jags + 1;

        float w = (0.016f + 0.008f * l.strength) * fade;
        float gw = (0.052f + 0.026f * l.strength) * fade;

        float[] xs = new float[n + 1];
        float[] ys = new float[n + 1];
        float[] zs = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n;
            double env = Math.pow(Math.sin(Math.PI * t), 0.7);
            double s1 = Math.sin(l.seed * 0.01 + i * 1.9 + p * 7.0) * amp * env;
            double s2 = Math.cos(l.seed * 0.013 + i * 2.7 + p * 5.0) * amp * env * 0.7;
            xs[i] = (float) (l.ax + (l.bx - l.ax) * t + r.x * s1 + u.x * s2);
            ys[i] = (float) (l.ay + (l.by - l.ay) * t + r.y * s1 + u.y * s2);
            zs[i] = (float) (l.az + (l.bz - l.az) * t + r.z * s1 + u.z * s2);
        }

        for (int i = 0; i < n; i++) {
            bolt(vc, mat, r, u, xs[i], ys[i], zs[i], xs[i + 1], ys[i + 1], zs[i + 1],
                    gw * 0.7f, gw * 2.2f, c, 0.09f * flick * fade);
            bolt(vc, mat, r, u, xs[i], ys[i], zs[i], xs[i + 1], ys[i + 1], zs[i + 1],
                    w, w, c, 0.85f * flick * fade);
        }
        for (int i = 1; i < n; i += 2) {
            float dot = 0.012f + 0.010f * l.strength;
            billboard(vc, mat, r, u, xs[i], ys[i], zs[i], dot * fade, c, 0.8f * fade);
        }
        float halo = 0.035f + 0.075f * l.strength * (1f - (float) p);
        billboard(vc, mat, r, u, (float) l.bx, (float) l.by, (float) l.bz, halo, c, 0.55f * fade);
    }

    /** Сегмент дуги: лента шириной w в экранной плоскости плюс призрак шириной glow. */
    private static void bolt(VertexConsumer vc, Matrix4f mat, Vector3f r, Vector3f u,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float w, float glow, float[] c, float a) {
        if (a <= 0.004f) return;
        float ddx = x1 - x0, ddy = y1 - y0, ddz = z1 - z0;
        float sx = ddx * r.x + ddy * r.y + ddz * r.z;
        float sy = ddx * u.x + ddy * u.y + ddz * u.z;
        float sl = (float) Math.sqrt(sx * sx + sy * sy);
        if (sl < 1e-5f) return;
        sx /= sl;
        sy /= sl;
        float nx = -sy * w, ny = sx * w;
        float gx = -sy * glow, gy = sx * glow;
        quad(vc, mat,
                x0 + r.x * nx + u.x * ny, y0 + r.y * nx + u.y * ny, z0 + r.z * nx + u.z * ny,
                x0 - r.x * nx - u.x * ny, y0 - r.y * nx - u.y * ny, z0 - r.z * nx - u.z * ny,
                x1 - r.x * nx - u.x * ny, y1 - r.y * nx - u.y * ny, z1 - r.z * nx - u.z * ny,
                x1 + r.x * nx + u.x * ny, y1 + r.y * nx + u.y * ny, z1 + r.z * nx + u.z * ny,
                c[0], c[1], c[2], a);
        if (glow > w * 1.05f) {
            quad(vc, mat,
                    x0 + r.x * gx + u.x * gy, y0 + r.y * gx + u.y * gy, z0 + r.z * gx + u.z * gy,
                    x0 - r.x * gx - u.x * gy, y0 - r.y * gx - u.y * gy, z0 - r.z * gx - u.z * gy,
                    x1 - r.x * gx - u.x * gy, y1 - r.y * gx - u.y * gy, z1 - r.z * gx - u.z * gy,
                    x1 + r.x * gx + u.x * gy, y1 + r.y * gx + u.y * gy, z1 + r.z * gx + u.z * gy,
                    c[0], c[1], c[2], a * 0.5f);
        }
    }

    private static void billboard(VertexConsumer vc, Matrix4f mat, Vector3f r, Vector3f u,
                                  float cx, float cy, float cz, float s, float[] c, float a) {
        if (a <= 0.01f) return;
        quad(vc, mat,
                cx - r.x * s - u.x * s, cy - r.y * s - u.y * s, cz - r.z * s - u.z * s,
                cx + r.x * s - u.x * s, cy + r.y * s - u.y * s, cz + r.z * s - u.z * s,
                cx + r.x * s + u.x * s, cy + r.y * s + u.y * s, cz + r.z * s + u.z * s,
                cx - r.x * s + u.x * s, cy - r.y * s + u.y * s, cz - r.z * s + u.z * s,
                c[0], c[1], c[2], a);
    }

    private static void quad(VertexConsumer vc, Matrix4f mat,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float r, float g, float b, float a) {
        float rr = Math.min(1f, r), gg = Math.min(1f, g), bb = Math.min(1f, b);
        vc.vertex(mat, x0, y0, z0).color(rr, gg, bb, a);
        vc.vertex(mat, x1, y1, z1).color(rr, gg, bb, a);
        vc.vertex(mat, x2, y2, z2).color(rr, gg, bb, a);
        vc.vertex(mat, x3, y3, z3).color(rr, gg, bb, a);
        vc.vertex(mat, x3, y3, z3).color(rr, gg, bb, a);
        vc.vertex(mat, x2, y2, z2).color(rr, gg, bb, a);
        vc.vertex(mat, x1, y1, z1).color(rr, gg, bb, a);
        vc.vertex(mat, x0, y0, z0).color(rr, gg, bb, a);
    }

    private static float[] rgb(int argb) {
        return new float[]{((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f};
    }
}

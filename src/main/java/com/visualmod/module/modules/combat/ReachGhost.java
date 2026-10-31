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
 * След длинного удара: от кисти до точки попадания остаётся светящаяся дуга.
 * Нужна, чтобы «достал через полтора блока» читалось как приём, а не как лаг.
 */
public class ReachGhost extends Module implements Configurable {

    public static double minReach = 2.7;
    public static double fade = 0.30;
    public static int color = 0xFF7FE7FF;

    private static final class Ghost {
        final double ax, ay, az, bx, by, bz;
        final long born;

        Ghost(double ax, double ay, double az, double bx, double by, double bz, long born) {
            this.ax = ax; this.ay = ay; this.az = az;
            this.bx = bx; this.by = by; this.bz = bz;
            this.born = born;
        }
    }

    private static final List<Ghost> GHOSTS = new ArrayList<>();
    private static final int SEG = 16;

    static { WorldRenderEvents.AFTER_ENTITIES.register(ReachGhost::render); }

    public ReachGhost() {
        super("ReachGhost", "Long hits leave a glowing arc from hand to target", Category.COMBAT);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new SliderSetting("From", 1.5, 4.0, 0.1, () -> minReach, v -> minReach = v, "b"));
        l.add(new SliderSetting("Fade", 0.1, 0.8, 0.05, () -> fade, v -> fade = v, "s"));
        l.add(new ColorSetting("Color", () -> color, c -> color = c));
        return l;
    }

    @Override
    public void onDisable() { GHOSTS.clear(); }

    private static boolean active() {
        if (VisualsMod.moduleManager == null) return false;
        ReachGhost m = VisualsMod.moduleManager.getModule(ReachGhost.class);
        return m != null && m.isEnabled();
    }

    public static void onAttack(PlayerEntity player, Entity target) {
        if (player == null || target == null) return;
        if (!active()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        Vec3d eye = player.getEyePos();
        Vec3d hit = target.getEntityPos().add(0, Math.max(0.4, target.getHeight() * 0.58), 0);
        if (eye.distanceTo(hit) < minReach) return;
        Vec3d fwd = player.getRotationVec(1.0f);
        Vec3d right = new Vec3d(0, 1, 0).crossProduct(fwd);
        right = right.lengthSquared() > 1e-6 ? right.normalize() : new Vec3d(1, 0, 0);
        Vec3d hand = eye.add(right.multiply(0.34)).add(0, -0.24, 0).add(fwd.multiply(0.28));
        long now = System.currentTimeMillis();
        GHOSTS.add(new Ghost(hand.x, hand.y, hand.z, hit.x, hit.y, hit.z, now));
        while (GHOSTS.size() > 8) GHOSTS.remove(0);
    }

    private static void render(WorldRenderContext ctx) {
        try {
            if (IrisCompat.skipWorldPass()) return;
            if (VisualsMod.moduleManager == null || ctx.matrices() == null || ctx.consumers() == null) return;
            ReachGhost m = VisualsMod.moduleManager.getModule(ReachGhost.class);
            if (m == null || !m.isEnabled()) return;
            if (GHOSTS.isEmpty()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null) return;

            long now = System.currentTimeMillis();
            double dur = Math.max(0.08, fade) * 1000.0;
            ctx.matrices().push();
            Camera camera = ctx.gameRenderer().getCamera();
            Vec3d cam = camera.getCameraPos();
            ctx.matrices().translate(-cam.x, -cam.y, -cam.z);
            Matrix4f mat = ctx.matrices().peek().getPositionMatrix();
            VertexConsumer vc = ctx.consumers().getBuffer(RenderLayers.debugQuads());
            Vector3f right = new Vector3f(1, 0, 0).rotate(camera.getRotation());
            Vector3f up = new Vector3f(0, 1, 0).rotate(camera.getRotation());
            float[] c = rgb(color);

            for (Iterator<Ghost> it = GHOSTS.iterator(); it.hasNext(); ) {
                Ghost g = it.next();
                double p = (now - g.born) / dur;
                if (p >= 1.0) { it.remove(); continue; }
                drawGhost(vc, mat, right, up, c, g, p);
            }

            com.visualmod.util.RenderFlush.flush(ctx.consumers(), RenderLayers.debugQuads());
            ctx.matrices().pop();
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("ReachGhost", t);
        }
    }

    private static void drawGhost(VertexConsumer vc, Matrix4f mat, Vector3f r, Vector3f u,
                                  float[] c, Ghost g, double p) {
        float fadeK = (float) Math.pow(1.0 - p, 1.25);
        if (fadeK <= 0.02f) return;
        float px = 0, py = 0, pz = 0;
        for (int i = 0; i <= SEG; i++) {
            float t = i / (float) SEG;
            double sag = 0.085 * Math.sin(Math.PI * t) * (1.0 + 0.3 * p);
            float x = (float) (g.ax + (g.bx - g.ax) * t);
            float y = (float) (g.ay + (g.by - g.ay) * t - sag);
            float z = (float) (g.az + (g.bz - g.az) * t);
            if (i > 0) {
                float w0 = 0.024f * (1f - 0.72f * (i - 1) / (float) SEG);
                float w1 = 0.024f * (1f - 0.72f * i / (float) SEG);
                float a0 = 0.85f * fadeK;
                float a1 = 0.85f * fadeK * (1f - 0.4f * i / (float) SEG);
                ribbon(vc, mat, r, u, px, py, pz, x, y, z, w0, w1, a0, a1, c);
            }
            px = x; py = y; pz = z;
        }
        float halo = 0.014f + 0.07f * (1f - (float) p);
        billboard(vc, mat, r, u, (float) g.bx, (float) g.by, (float) g.bz, halo, c, 0.7f * fadeK);
    }

    /** Лента между двумя точками в двух плоскостях камеры — линия читается с любого ракурса. */
    private static void ribbon(VertexConsumer vc, Matrix4f mat, Vector3f r, Vector3f u,
                               float x0, float y0, float z0, float x1, float y1, float z1,
                               float w0, float w1, float a0, float a1, float[] c) {
        quad(vc, mat,
                x0 - r.x * w0, y0 - r.y * w0, z0 - r.z * w0,
                x0 + r.x * w0, y0 + r.y * w0, z0 + r.z * w0,
                x1 + r.x * w1, y1 + r.y * w1, z1 + r.z * w1,
                x1 - r.x * w1, y1 - r.y * w1, z1 - r.z * w1,
                c[0], c[1], c[2], a0, a1);
        quad(vc, mat,
                x0 - u.x * w0, y0 - u.y * w0, z0 - u.z * w0,
                x0 + u.x * w0, y0 + u.y * w0, z0 + u.z * w0,
                x1 + u.x * w1, y1 + u.y * w1, z1 + u.z * w1,
                x1 - u.x * w1, y1 - u.y * w1, z1 - u.z * w1,
                c[0], c[1], c[2], a0 * 0.6f, a1 * 0.6f);
    }

    private static void billboard(VertexConsumer vc, Matrix4f mat, Vector3f r, Vector3f u,
                                  float cx, float cy, float cz, float s, float[] c, float a) {
        if (a <= 0.01f) return;
        quad(vc, mat,
                cx - r.x * s - u.x * s, cy - r.y * s - u.y * s, cz - r.z * s - u.z * s,
                cx + r.x * s - u.x * s, cy + r.y * s - u.y * s, cz + r.z * s - u.z * s,
                cx + r.x * s + u.x * s, cy + r.y * s + u.y * s, cz + r.z * s + u.z * s,
                cx - r.x * s + u.x * s, cy - r.y * s + u.y * s, cz - r.z * s + u.z * s,
                c[0], c[1], c[2], a, a);
    }

    private static void quad(VertexConsumer vc, Matrix4f mat,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float r, float g, float b, float a0, float a1) {
        if (a0 <= 0.01f && a1 <= 0.01f) return;
        float rr = Math.min(1f, r), gg = Math.min(1f, g), bb = Math.min(1f, b);
        vc.vertex(mat, x0, y0, z0).color(rr, gg, bb, a0);
        vc.vertex(mat, x1, y1, z1).color(rr, gg, bb, a0);
        vc.vertex(mat, x2, y2, z2).color(rr, gg, bb, a1);
        vc.vertex(mat, x3, y3, z3).color(rr, gg, bb, a1);
        vc.vertex(mat, x3, y3, z3).color(rr, gg, bb, a1);
        vc.vertex(mat, x2, y2, z2).color(rr, gg, bb, a1);
        vc.vertex(mat, x1, y1, z1).color(rr, gg, bb, a0);
        vc.vertex(mat, x0, y0, z0).color(rr, gg, bb, a0);
    }

    private static float[] rgb(int argb) {
        return new float[]{((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f};
    }
}

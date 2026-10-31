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
import java.util.Random;

/**
 * Искры летят не вверх, а по направлению замаха: конус от точки попадания вдоль удара,
 * с баллистикой и короткими штрихами-хвостами. Читается как «разрез», а не как частицы.
 */
public class SparksTrail extends Module implements Configurable {

    public static double count = 16;
    public static double speed = 4.4;
    public static double spread = 0.55;
    public static double life = 0.55;
    public static double gravity = 9.0;
    public static int color = 0xFFFFC061;

    private static final class Spark {
        double x, y, z, vx, vy, vz;
        long born;
        float life, size;
    }

    private static final List<Spark> SPARKS = new ArrayList<>();
    private static final Random RNG = new Random();

    static { WorldRenderEvents.AFTER_ENTITIES.register(SparksTrail::render); }

    public SparksTrail() {
        super("SparksTrail", "Hit sparks fly along the swing direction", Category.COMBAT);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new SliderSetting("Count", 4, 40, 1, () -> count, v -> count = v));
        l.add(new SliderSetting("Speed", 1.5, 9.0, 0.1, () -> speed, v -> speed = v));
        l.add(new SliderSetting("Spread", 0.1, 1.2, 0.05, () -> spread, v -> spread = v));
        l.add(new SliderSetting("Life", 0.2, 1.2, 0.05, () -> life, v -> life = v, "s"));
        l.add(new SliderSetting("Gravity", 0, 20, 0.5, () -> gravity, v -> gravity = v));
        l.add(new ColorSetting("Color", () -> color, c -> color = c));
        return l;
    }

    @Override
    public void onDisable() { SPARKS.clear(); }

    private static boolean active() {
        if (VisualsMod.moduleManager == null) return false;
        SparksTrail m = VisualsMod.moduleManager.getModule(SparksTrail.class);
        return m != null && m.isEnabled();
    }

    public static void onAttack(PlayerEntity player, Entity target) {
        if (player == null || target == null) return;
        if (!active()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        Vec3d eye = player.getEyePos();
        Vec3d hit = target.getEntityPos().add(0, Math.max(0.35, target.getHeight() * 0.55), 0);
        Vec3d dir = hit.subtract(eye);
        if (dir.lengthSquared() < 1e-6) return;
        dir = dir.normalize();
        Vec3d aside = new Vec3d(0, 1, 0).crossProduct(dir);
        aside = aside.lengthSquared() > 1e-6 ? aside.normalize() : new Vec3d(1, 0, 0);
        Vec3d aside2 = dir.crossProduct(aside).normalize();

        long now = System.currentTimeMillis();
        RNG.setSeed(now ^ (long) (hit.x * 131) ^ (long) (hit.z * 67));
        int n = (int) Math.max(0, Math.min(40, count));
        double sp = Math.max(0.5, speed);
        double sd = Math.max(0.05, spread);
        for (int i = 0; i < n; i++) {
            Spark s = new Spark();
            s.x = hit.x + (RNG.nextDouble() - 0.5) * 0.12;
            s.y = hit.y + (RNG.nextDouble() - 0.5) * 0.12;
            s.z = hit.z + (RNG.nextDouble() - 0.5) * 0.12;
            double k = 0.45 + RNG.nextDouble() * 0.95;
            double a1 = (RNG.nextDouble() - 0.5) * sd * sp;
            double a2 = (RNG.nextDouble() - 0.5) * sd * sp * 0.7;
            s.vx = dir.x * sp * k + aside.x * a1 + aside2.x * a2;
            s.vy = dir.y * sp * k + aside.y * a1 + aside2.y * a2 + RNG.nextDouble() * 1.1;
            s.vz = dir.z * sp * k + aside.z * a1 + aside2.z * a2;
            s.born = now + RNG.nextInt(70);
            s.life = (float) (Math.max(0.1, life) * 1000.0 * (0.65 + RNG.nextDouble() * 0.7));
            s.size = (float) (0.018 + RNG.nextDouble() * 0.024);
            SPARKS.add(s);
        }
        while (SPARKS.size() > 320) SPARKS.remove(0);
    }

    private static void render(WorldRenderContext ctx) {
        try {
            if (IrisCompat.skipWorldPass()) return;
            if (VisualsMod.moduleManager == null || ctx.matrices() == null || ctx.consumers() == null) return;
            SparksTrail m = VisualsMod.moduleManager.getModule(SparksTrail.class);
            if (m == null || !m.isEnabled()) return;
            if (SPARKS.isEmpty()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null) return;

            long now = System.currentTimeMillis();
            ctx.matrices().push();
            Camera camera = ctx.gameRenderer().getCamera();
            Vec3d cam = camera.getCameraPos();
            ctx.matrices().translate(-cam.x, -cam.y, -cam.z);
            Matrix4f mat = ctx.matrices().peek().getPositionMatrix();
            VertexConsumer vc = ctx.consumers().getBuffer(RenderLayers.debugQuads());
            Vector3f right = new Vector3f(1, 0, 0).rotate(camera.getRotation());
            Vector3f up = new Vector3f(0, 1, 0).rotate(camera.getRotation());
            float[] c = rgb(color);
            double g = Math.max(0, gravity);

            for (Iterator<Spark> it = SPARKS.iterator(); it.hasNext(); ) {
                Spark s = it.next();
                long age = now - s.born;
                if (age < 0) continue;
                float q = age / s.life;
                if (q >= 1f) { it.remove(); continue; }
                double t = age / 1000.0;
                float x = (float) (s.x + s.vx * t);
                float y = (float) (s.y + s.vy * t - 0.5 * g * t * t);
                float z = (float) (s.z + s.vz * t);
                float a = (float) Math.pow(1.0 - q, 1.2) * 0.95f;
                float size = s.size * (1f - 0.45f * q);
                float sx = (float) (s.vx * right.x + s.vy * right.y + s.vz * right.z);
                float sy = (float) (s.vx * up.x + s.vy * up.y + s.vz * up.z);
                float sl = (float) Math.sqrt(sx * sx + sy * sy);
                if (sl < 1e-4f) { sx = 0f; sy = 1f; sl = 1f; }
                sx /= sl; sy /= sl;
                float len = Math.min(0.20f, 0.022f * sl);
                streak(vc, mat, right, up, x, y, z, sx, sy, len, size, c, a);
            }

            com.visualmod.util.RenderFlush.flush(ctx.consumers(), RenderLayers.debugQuads());
            ctx.matrices().pop();
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("SparksTrail", t);
        }
    }

    /** Вытянутый по экранной проекции скорости блик: искра со шлейфом. */
    private static void streak(VertexConsumer vc, Matrix4f mat, Vector3f r, Vector3f u,
                               float cx, float cy, float cz, float dx, float dy,
                               float len, float w, float[] c, float a) {
        if (a <= 0.02f) return;
        float ax = r.x * dx * len + u.x * dy * len;
        float ay = r.y * dx * len + u.y * dy * len;
        float az = r.z * dx * len + u.z * dy * len;
        float wx = -r.x * dy * w + u.x * dx * w;
        float wy = -r.y * dy * w + u.y * dx * w;
        float wz = -r.z * dy * w + u.z * dx * w;
        float tail = 0.15f * a;
        quad(vc, mat,
                cx - ax - wx, cy - ay - wy, cz - az - wz,
                cx + ax - wx, cy + ay - wy, cz + az - wz,
                cx + ax + wx, cy + ay + wy, cz + az + wz,
                cx - ax + wx, cy - ay + wy, cz - az + wz,
                c[0], c[1], c[2], tail, a);
    }

    private static void quad(VertexConsumer vc, Matrix4f mat,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float r, float g, float b, float a0, float a1) {
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

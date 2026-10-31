package com.visualmod.module.modules.combat;

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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Разрез воздуха: в точке попадания на миг проступает трёхслойная линза-порез с рваным
 * краем и светящейся сердцевиной. Плоскости развёрнуты относительно оси удара, поэтому
 * порез виден с любого ракурса, а не как наклейка на экран.
 */
public class AirCut extends Module implements Configurable {

    public static double length = 1.8;
    public static double life = 0.45;
    public static int color = 0xFFC9EEFF;
    public static boolean jagged = true;

    private static final class Cut {
        final double x, y, z;
        final float dx, dy, dz;
        final long born;
        final float seed;

        Cut(double x, double y, double z, float dx, float dy, float dz, long born, float seed) {
            this.x = x; this.y = y; this.z = z;
            this.dx = dx; this.dy = dy; this.dz = dz;
            this.born = born; this.seed = seed;
        }
    }

    private static final List<Cut> CUTS = new ArrayList<>();
    private static final int SEG = 34;

    static { WorldRenderEvents.AFTER_ENTITIES.register(AirCut::render); }

    public AirCut() {
        super("AirCut", "Hits slice a lens-shaped cut through the air", Category.COMBAT);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new SliderSetting("Length", 0.8, 3.0, 0.05, () -> length, v -> length = v, "b"));
        l.add(new SliderSetting("Fade", 0.2, 0.9, 0.05, () -> life, v -> life = v, "s"));
        l.add(new ColorSetting("Color", () -> color, c -> color = c));
        l.add(new ToggleSetting("Jagged", () -> jagged, v -> jagged = v));
        return l;
    }

    @Override
    public void onDisable() { CUTS.clear(); }

    private static boolean active() {
        if (VisualsMod.moduleManager == null) return false;
        AirCut m = VisualsMod.moduleManager.getModule(AirCut.class);
        return m != null && m.isEnabled();
    }

    public static void onAttack(PlayerEntity player, Entity target) {
        if (player == null || target == null) return;
        if (!active()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        Vec3d eye = player.getEyePos();
        Vec3d hit = target.getEntityPos().add(0, Math.max(0.4, target.getHeight() * 0.58), 0);
        Vec3d dir = hit.subtract(eye);
        if (dir.lengthSquared() < 1e-6) return;
        dir = dir.normalize();
        long now = System.currentTimeMillis();
        CUTS.add(new Cut(hit.x, hit.y, hit.z,
                (float) dir.x, (float) dir.y, (float) dir.z, now, (float) (now % 628) * 0.01f));
        while (CUTS.size() > 8) CUTS.remove(0);
    }

    private static void render(WorldRenderContext ctx) {
        try {
            if (IrisCompat.skipWorldPass()) return;
            if (VisualsMod.moduleManager == null || ctx.matrices() == null || ctx.consumers() == null) return;
            AirCut m = VisualsMod.moduleManager.getModule(AirCut.class);
            if (m == null || !m.isEnabled()) return;
            if (CUTS.isEmpty()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null) return;

            long now = System.currentTimeMillis();
            double dur = Math.max(0.15, life) * 1000.0;
            ctx.matrices().push();
            Camera camera = ctx.gameRenderer().getCamera();
            Vec3d cam = camera.getCameraPos();
            ctx.matrices().translate(-cam.x, -cam.y, -cam.z);
            Matrix4f mat = ctx.matrices().peek().getPositionMatrix();
            VertexConsumer vc = ctx.consumers().getBuffer(RenderLayers.debugQuads());

            for (Iterator<Cut> it = CUTS.iterator(); it.hasNext(); ) {
                Cut c = it.next();
                double p = (now - c.born) / dur;
                if (p >= 1.0) { it.remove(); continue; }
                drawCut(vc, mat, c, p);
            }

            com.visualmod.util.RenderFlush.flush(ctx.consumers(), RenderLayers.debugQuads());
            ctx.matrices().pop();
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("AirCut", t);
        }
    }

    private static void drawCut(VertexConsumer vc, Matrix4f mat, Cut s, double p) {
        float g = (float) Math.min(1.0, p / 0.22);
        float grow = 1f - (1f - g) * (1f - g);
        float a = p < 0.12 ? (float) (p / 0.12) : (float) Math.pow((1.0 - p) / 0.88, 1.15);
        if (a <= 0.01f) return;
        float len = (float) Math.max(0.4, length) * (0.30f + 0.70f * grow);
        float h = 0.16f * len * (0.35f + 0.65f * grow) * (1f - 0.55f * (float) p);
        if (h <= 0.006f) return;

        Vector3f dir = new Vector3f(s.dx, s.dy, s.dz);
        if (dir.lengthSquared() < 1e-6f) return;
        dir.normalize();
        Vector3f up0 = new Vector3f(0, 1, 0);
        float k = up0.dot(dir);
        up0.sub(dir.x * k, dir.y * k, dir.z * k);
        if (up0.lengthSquared() < 1e-4f) {
            up0.set(1, 0, 0);
            k = up0.dot(dir);
            up0.sub(dir.x * k, dir.y * k, dir.z * k);
        }
        up0.normalize();

        float[] c = rgb(color);
        float[] angles = {-0.26f, 0f, 0.26f};
        for (int pl = 0; pl < angles.length; pl++) {
            Vector3f up = new Vector3f(up0).rotate(new Quaternionf().rotateAxis(angles[pl], dir.x, dir.y, dir.z));
            float pa = a * (0.55f + (pl == 1 ? 0.45f : 0.12f));
            fan(vc, mat, s, dir, up, len, h, c, pa);
        }
        core(vc, mat, s, dir, up0, len, c, a);
    }

    /** Линза-порез: веер от центра к рваному эллипсу в плоскости (dir, up). */
    private static void fan(VertexConsumer vc, Matrix4f mat, Cut s, Vector3f dir, Vector3f up,
                            float len, float h, float[] c, float alpha) {
        float cx = (float) s.x, cy = (float) s.y, cz = (float) s.z;
        float half = len * 0.5f;
        float px = 0, py = 0, pz = 0;
        for (int i = 0; i <= SEG; i++) {
            double th = i / (double) SEG * Math.PI * 2.0;
            float jag = jagged ? (float) (1.0 + 0.11 * Math.sin(th * 7.0 + s.seed)) : 1f;
            float ax = (float) Math.cos(th) * half * jag;
            float ay = (float) Math.sin(th) * h * jag;
            float x = cx + dir.x * ax + up.x * ay;
            float y = cy + dir.y * ax + up.y * ay;
            float z = cz + dir.z * ax + up.z * ay;
            if (i > 0) {
                float shade = 0.82f + 0.18f * (float) Math.abs(Math.cos(th));
                tri(vc, mat, cx, cy, cz, px, py, pz, x, y, z,
                        c[0] * shade, c[1] * shade, c[2] * shade, alpha, alpha * 0.35f);
            }
            px = x; py = y; pz = z;
        }
    }

    /** Сердцевина: тонкая яркая лента по всей длине разреза. */
    private static void core(VertexConsumer vc, Matrix4f mat, Cut s, Vector3f dir, Vector3f up,
                             float len, float[] c, float alpha) {
        float cx = (float) s.x, cy = (float) s.y, cz = (float) s.z;
        float half = len * 0.46f;
        float w = 0.010f + 0.008f * len * 0.1f;
        float x0 = cx - dir.x * half, y0 = cy - dir.y * half, z0 = cz - dir.z * half;
        float x1 = cx + dir.x * half, y1 = cy + dir.y * half, z1 = cz + dir.z * half;
        quad(vc, mat,
                x0 - up.x * w, y0 - up.y * w, z0 - up.z * w,
                x1 - up.x * w, y1 - up.y * w, z1 - up.z * w,
                x1 + up.x * w, y1 + up.y * w, z1 + up.z * w,
                x0 + up.x * w, y0 + up.y * w, z0 + up.z * w,
                Math.min(1f, c[0] * 1.12f), Math.min(1f, c[1] * 1.12f), Math.min(1f, c[2] * 1.12f), 0.92f * alpha);
    }

    private static void tri(VertexConsumer vc, Matrix4f mat,
                            float ax, float ay, float az, float bx, float by, float bz,
                            float cx, float cy, float cz, float r, float g, float b, float a0, float a1) {
        float rr = Math.min(1f, r), gg = Math.min(1f, g), bb = Math.min(1f, b);
        vc.vertex(mat, ax, ay, az).color(rr, gg, bb, a0);
        vc.vertex(mat, bx, by, bz).color(rr, gg, bb, a1);
        vc.vertex(mat, cx, cy, cz).color(rr, gg, bb, a1);
        vc.vertex(mat, cx, cy, cz).color(rr, gg, bb, a1);
        vc.vertex(mat, cx, cy, cz).color(rr * 0.85f, gg * 0.85f, bb * 0.85f, a1);
        vc.vertex(mat, bx, by, bz).color(rr * 0.85f, gg * 0.85f, bb * 0.85f, a1);
        vc.vertex(mat, ax, ay, az).color(rr * 0.85f, gg * 0.85f, bb * 0.85f, a0);
        vc.vertex(mat, ax, ay, az).color(rr * 0.85f, gg * 0.85f, bb * 0.85f, a0);
    }

    private static void quad(VertexConsumer vc, Matrix4f mat,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float r, float g, float b, float a) {
        if (a <= 0.01f) return;
        float rr = Math.min(1f, r), gg = Math.min(1f, g), bb = Math.min(1f, b);
        vc.vertex(mat, x0, y0, z0).color(rr, gg, bb, a);
        vc.vertex(mat, x1, y1, z1).color(rr, gg, bb, a);
        vc.vertex(mat, x2, y2, z2).color(rr, gg, bb, a);
        vc.vertex(mat, x3, y3, z3).color(rr, gg, bb, a);
        vc.vertex(mat, x3, y3, z3).color(rr, gg, bb, a * 0.9f);
        vc.vertex(mat, x2, y2, z2).color(rr, gg, bb, a * 0.9f);
        vc.vertex(mat, x1, y1, z1).color(rr, gg, bb, a * 0.9f);
        vc.vertex(mat, x0, y0, z0).color(rr, gg, bb, a * 0.9f);
    }

    private static float[] rgb(int argb) {
        return new float[]{((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f};
    }
}

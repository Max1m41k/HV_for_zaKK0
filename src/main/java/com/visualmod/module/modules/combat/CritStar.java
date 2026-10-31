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
 * Крит-звезда: объёмная четырёхлучевая звезда из пирамидальных лучей с рёбрами и гранёным
 * ядром, а не плоский билборд. Вращается вокруг оси удара, поэтому читается с любого ракурса
 * и «живёт» в мире, а не наклеена на экран.
 */
public class CritStar extends Module implements Configurable {

    public static double size = 0.30;
    public static double life = 0.30;
    public static int color = 0xFFFFD24A;
    public static boolean diagonal = true;

    private static final class Star {
        final double x, y, z;
        final float nx, ny, nz;
        final long born;
        final float seed;

        Star(double x, double y, double z, float nx, float ny, float nz, long born, float seed) {
            this.x = x; this.y = y; this.z = z;
            this.nx = nx; this.ny = ny; this.nz = nz;
            this.born = born; this.seed = seed;
        }
    }

    private static final List<Star> STARS = new ArrayList<>();
    private static final Vector3f LIGHT = new Vector3f(0.35f, 0.84f, 0.42f).normalize();

    static { WorldRenderEvents.AFTER_ENTITIES.register(CritStar::render); }

    public CritStar() {
        super("CritStar", "Critical hits burst into a solid 3D star", Category.COMBAT);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new SliderSetting("Size", 0.12, 0.8, 0.02, () -> size, v -> size = v, "b"));
        l.add(new SliderSetting("Life", 0.1, 0.8, 0.02, () -> life, v -> life = v, "s"));
        l.add(new ColorSetting("Color", () -> color, c -> color = c));
        l.add(new ToggleSetting("Diagonal", () -> diagonal, v -> diagonal = v));
        return l;
    }

    @Override
    public void onDisable() { STARS.clear(); }

    private static boolean active() {
        if (VisualsMod.moduleManager == null) return false;
        CritStar m = VisualsMod.moduleManager.getModule(CritStar.class);
        return m != null && m.isEnabled();
    }

    public static void onAttack(PlayerEntity player, Entity target, boolean crit) {
        if (!crit || player == null || target == null) return;
        if (!active()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        Vec3d eye = player.getEyePos();
        Vec3d hit = target.getEntityPos().add(0, Math.max(0.4, target.getHeight() * 0.58), 0);
        Vec3d dir = hit.subtract(eye);
        if (dir.lengthSquared() < 1e-6) return;
        dir = dir.normalize();
        long now = System.currentTimeMillis();
        STARS.add(new Star(hit.x, hit.y, hit.z,
                (float) dir.x, (float) dir.y, (float) dir.z, now, (float) (now % 628) * 0.01f));
        while (STARS.size() > 10) STARS.remove(0);
    }

    private static void render(WorldRenderContext ctx) {
        try {
            if (IrisCompat.skipWorldPass()) return;
            if (VisualsMod.moduleManager == null || ctx.matrices() == null || ctx.consumers() == null) return;
            CritStar m = VisualsMod.moduleManager.getModule(CritStar.class);
            if (m == null || !m.isEnabled()) return;
            if (STARS.isEmpty()) return;
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

            for (Iterator<Star> it = STARS.iterator(); it.hasNext(); ) {
                Star s = it.next();
                double p = (now - s.born) / dur;
                if (p >= 1.0) { it.remove(); continue; }
                drawStar(vc, mat, s, p);
            }

            com.visualmod.util.RenderFlush.flush(ctx.consumers(), RenderLayers.debugQuads());
            ctx.matrices().pop();
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("CritStar", t);
        }
    }

    private static void drawStar(VertexConsumer vc, Matrix4f mat, Star s, double p) {
        float ramp = (float) Math.min(1.0, p / 0.18);
        float a = p < 0.18 ? ramp : (float) Math.pow((1.0 - p) / 0.82, 1.15);
        if (a <= 0.01f) return;
        float l = (float) (Math.max(0.05, size) * (0.42f + 0.58f * (1f - (1f - ramp) * (1f - ramp)))
                * (1.0 - 0.16 * p));
        float[] c = rgb(color);

        Vector3f n = new Vector3f(s.nx, s.ny, s.nz);
        if (n.lengthSquared() < 1e-6f) n.set(0, 0, 1);
        n.normalize();
        Vector3f u0 = new Vector3f(0, 1, 0).cross(n);
        if (u0.lengthSquared() < 1e-4f) u0.set(1, 0, 0).cross(n);
        u0.normalize();
        Vector3f v0 = new Vector3f(n).cross(u0).normalize();

        Quaternionf spin = new Quaternionf().rotateAxis(s.seed + (float) p * 2.4f, n.x, n.y, n.z);
        Vector3f u = new Vector3f(u0).rotate(spin);
        Vector3f v = new Vector3f(v0).rotate(spin);

        for (int i = 0; i < 4; i++) {
            double ang = Math.PI * 0.5 * i;
            Vector3f d = new Vector3f(u).mul((float) Math.cos(ang))
                    .add(new Vector3f(v).mul((float) Math.sin(ang)));
            spike(vc, mat, s.x, s.y, s.z, d, l, l * 0.11f, c, a);
        }
        if (diagonal) {
            for (int i = 0; i < 4; i++) {
                double ang = Math.PI * 0.25 + Math.PI * 0.5 * i;
                Vector3f d = new Vector3f(u).mul((float) Math.cos(ang))
                        .add(new Vector3f(v).mul((float) Math.sin(ang)));
                spike(vc, mat, s.x, s.y, s.z, d, l * 0.44f, l * 0.075f, c, a * 0.9f);
            }
        }
        spike(vc, mat, s.x, s.y, s.z, new Vector3f(n), l * 0.36f, l * 0.09f, c, a * 0.85f);
        spike(vc, mat, s.x, s.y, s.z, new Vector3f(n).negate(), l * 0.36f, l * 0.09f, c, a * 0.85f);
        octa(vc, mat, s.x, s.y, s.z, u, v, n, l * 0.13f, c, a);
    }

    /** Луч: четырёхгранная пирамида с ромбовидным основанием — у звезды есть толщина и рёбра. */
    private static void spike(VertexConsumer vc, Matrix4f mat, double cx, double cy, double cz,
                              Vector3f dir, float len, float w, float[] c, float alpha) {
        if (len <= 0.004f || alpha <= 0.01f) return;
        Vector3f d = new Vector3f(dir);
        if (d.lengthSquared() < 1e-8f) return;
        d.normalize();
        Vector3f p1 = new Vector3f(0, 1, 0).cross(d);
        if (p1.lengthSquared() < 1e-5f) p1.set(1, 0, 0).cross(d);
        p1.normalize();
        Vector3f p2 = new Vector3f(d).cross(p1).normalize();

        float bx = (float) (cx + d.x * len * 0.2f);
        float by = (float) (cy + d.y * len * 0.2f);
        float bz = (float) (cz + d.z * len * 0.2f);
        float tx = (float) (cx + d.x * len), ty = (float) (cy + d.y * len), tz = (float) (cz + d.z * len);

        float[] rx = new float[4], ry = new float[4], rz = new float[4];
        float[][] dirs = {
                {p1.x * w, p1.y * w, p1.z * w},
                {p2.x * w, p2.y * w, p2.z * w},
                {-p1.x * w, -p1.y * w, -p1.z * w},
                {-p2.x * w, -p2.y * w, -p2.z * w}};
        for (int i = 0; i < 4; i++) {
            rx[i] = bx + dirs[i][0];
            ry[i] = by + dirs[i][1];
            rz[i] = bz + dirs[i][2];
        }

        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            float shade = shadeOf(dirs[i][0] + dirs[j][0], dirs[i][1] + dirs[j][1], dirs[i][2] + dirs[j][2]);
            float r = c[0] * shade + 0.16f * shade;
            float g = c[1] * shade + 0.14f * shade;
            float b = c[2] * shade + 0.12f * shade;
            tri(vc, mat, (float) cx, (float) cy, (float) cz, rx[i], ry[i], rz[i], rx[j], ry[j], rz[j],
                    r, g, b, alpha * 0.9f);
            tri(vc, mat, tx, ty, tz, rx[i], ry[i], rz[i], rx[j], ry[j], rz[j],
                    Math.min(1f, r * 1.25f), Math.min(1f, g * 1.22f), Math.min(1f, b * 1.2f), alpha);
        }
    }

    private static void octa(VertexConsumer vc, Matrix4f mat, double cx, double cy, double cz,
                             Vector3f u, Vector3f v, Vector3f n, float r, float[] c, float a) {
        float[] px = {u.x * r, v.x * r, -u.x * r, -v.x * r};
        float[] py = {u.y * r, v.y * r, -u.y * r, -v.y * r};
        float[] pz = {u.z * r, v.z * r, -u.z * r, -v.z * r};
        float topX = (float) (cx + n.x * r), topY = (float) (cy + n.y * r), topZ = (float) (cz + n.z * r);
        float botX = (float) (cx - n.x * r), botY = (float) (cy - n.y * r), botZ = (float) (cz - n.z * r);
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            float shade = 0.7f + 0.3f * (i % 2 == 0 ? 1f : 0.6f);
            tri(vc, mat, (float) cx + px[i], (float) cy + py[i], (float) cz + pz[i],
                    (float) cx + px[j], (float) cy + py[j], (float) cz + pz[j], topX, topY, topZ,
                    c[0] * shade + 0.2f, c[1] * shade + 0.18f, c[2] * shade + 0.16f, a);
            tri(vc, mat, (float) cx + px[i], (float) cy + py[i], (float) cz + pz[i],
                    (float) cx + px[j], (float) cy + py[j], (float) cz + pz[j], botX, botY, botZ,
                    c[0] * shade * 0.8f, c[1] * shade * 0.8f, c[2] * shade * 0.8f, a);
        }
    }

    private static float shadeOf(float x, float y, float z) {
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        if (l < 1e-6f) return 0.75f;
        float d = (x * LIGHT.x + y * LIGHT.y + z * LIGHT.z) / l;
        return 0.52f + 0.48f * Math.max(0f, d);
    }

    private static void tri(VertexConsumer vc, Matrix4f mat,
                            float ax, float ay, float az, float bx, float by, float bz,
                            float cx, float cy, float cz, float r, float g, float b, float a) {
        float rr = Math.min(1f, r), gg = Math.min(1f, g), bb = Math.min(1f, b);
        vc.vertex(mat, ax, ay, az).color(rr, gg, bb, a);
        vc.vertex(mat, bx, by, bz).color(rr, gg, bb, a);
        vc.vertex(mat, cx, cy, cz).color(rr, gg, bb, a);
        vc.vertex(mat, cx, cy, cz).color(rr, gg, bb, a);
        // обратная сторона, иначе луч пропадает при взгляде изнутри
        vc.vertex(mat, cx, cy, cz).color(rr * 0.9f, gg * 0.9f, bb * 0.9f, a);
        vc.vertex(mat, bx, by, bz).color(rr * 0.9f, gg * 0.9f, bb * 0.9f, a);
        vc.vertex(mat, ax, ay, az).color(rr * 0.9f, gg * 0.9f, bb * 0.9f, a);
        vc.vertex(mat, ax, ay, az).color(rr * 0.9f, gg * 0.9f, bb * 0.9f, a);
    }

    private static float[] rgb(int argb) {
        return new float[]{((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f};
    }
}

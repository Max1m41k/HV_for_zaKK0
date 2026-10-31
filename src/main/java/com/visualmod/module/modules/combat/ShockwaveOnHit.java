package com.visualmod.module.modules.combat;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
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
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * ShockwaveOnHit: крит отбрасывает от ног цели ударную волну — тонкое кольцо, бегущее по земле,
 * с пылью на гребне. Кольцо приподнимается на разгоне и оседает к концу, поэтому читается как
 * физический удар, а не как декаль.
 */
public class ShockwaveOnHit extends Module implements Configurable {

    public static int style = 0;
    public static double radius = 2.8;
    public static double duration = 0.5;
    public static int accent = 0xFFDCEFFF;
    public static boolean dust = true;

    private static final class Wave {
        double x, y, z;
        long born;
        int power;
        float size;
    }

    private static final class Dust {
        double ox, oy, oz, vx, vy, vz;
        long born;
        float life, size;
    }

    private static final List<Wave> WAVES = new ArrayList<>();
    private static final List<Dust> DUST = new ArrayList<>();
    private static final Random RNG = new Random();
    private static final int SEGMENTS = 46;

    static { WorldRenderEvents.AFTER_ENTITIES.register(ShockwaveOnHit::render); }

    public ShockwaveOnHit() {
        super("ShockwaveOnHit", "Critical hits send a shockwave ring across the ground", Category.COMBAT);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Style", new String[]{"SHOCK", "EMBER", "VOID"}, () -> style, i -> style = i));
        l.add(new SliderSetting("Radius", 1.2, 6.0, 0.1, () -> radius, v -> radius = v, "b"));
        l.add(new SliderSetting("Fade", 0.25, 1.5, 0.05, () -> duration, v -> duration = v, "s"));
        l.add(new ColorSetting("Color", () -> accent, c -> accent = c));
        l.add(new ModeSetting("Dust", new String[]{"OFF", "ON"}, () -> dust ? 1 : 0, i -> dust = (i == 1)));
        return l;
    }

    @Override
    public void onDisable() { WAVES.clear(); DUST.clear(); }

    public static void onAttack(Entity target, boolean crit) {
        if (!crit) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || target == null) return;
        if (VisualsMod.moduleManager == null) return;
        ShockwaveOnHit m = VisualsMod.moduleManager.getModule(ShockwaveOnHit.class);
        if (m == null || !m.isEnabled()) return;

        Wave w = new Wave();
        w.x = target.getX();
        w.z = target.getZ();
        w.y = groundY(mc, target.getX(), target.getY(), target.getZ());
        w.born = System.currentTimeMillis();
        w.power = target instanceof PlayerEntity ? 2 : 1;
        w.size = (float) Math.max(0.6, target.getHeight());
        WAVES.add(w);
        while (WAVES.size() > 10) WAVES.remove(0);
        if (dust) emitDust(w);
    }

    private static double groundY(MinecraftClient mc, double x, double y, double z) {
        for (int i = 0; i < 10; i++) {
            double cy = y + 0.25 - i * 0.5;
            if (!mc.world.isAir(new net.minecraft.util.math.BlockPos(
                    (int) Math.floor(x), (int) Math.floor(cy), (int) Math.floor(z)))) {
                return Math.floor(cy) + 1.006;
            }
        }
        return y + 0.02;
    }

    private static void emitDust(Wave w) {
        RNG.setSeed(w.born ^ (long) (w.x * 31));
        int n = 16 * w.power;
        for (int i = 0; i < n; i++) {
            double a = RNG.nextDouble() * Math.PI * 2;
            double sp = 2.6 + RNG.nextDouble() * 3.4;
            Dust d = new Dust();
            d.ox = w.x + Math.cos(a) * 0.22;
            d.oy = w.y + 0.05;
            d.oz = w.z + Math.sin(a) * 0.22;
            d.vx = Math.cos(a) * sp;
            d.vz = Math.sin(a) * sp;
            d.vy = 1.1 + RNG.nextDouble() * 2.2;
            d.born = w.born + (long) (RNG.nextInt(60));
            d.life = 260 + RNG.nextFloat() * 320;
            d.size = (0.03f + RNG.nextFloat() * 0.05f) * w.size;
            DUST.add(d);
        }
        while (DUST.size() > 420) DUST.remove(0);
    }

    private static void render(WorldRenderContext ctx) {
        try {
            if (IrisCompat.skipWorldPass()) return;
            if (VisualsMod.moduleManager == null || ctx.matrices() == null || ctx.consumers() == null) return;
            ShockwaveOnHit m = VisualsMod.moduleManager.getModule(ShockwaveOnHit.class);
            if (m == null || !m.isEnabled()) return;
            if (WAVES.isEmpty() && DUST.isEmpty()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null) return;

            long now = System.currentTimeMillis();
            double dur = Math.max(0.15, duration) * 1000.0;

            Camera camera = ctx.gameRenderer().getCamera();
            ctx.matrices().push();
            net.minecraft.util.math.Vec3d cam = camera.getCameraPos();
            ctx.matrices().translate(-cam.x, -cam.y, -cam.z);
            Matrix4f mat = ctx.matrices().peek().getPositionMatrix();
            VertexConsumer vc = ctx.consumers().getBuffer(RenderLayers.debugQuads());
            Vector3f right = new Vector3f(1, 0, 0).rotate(camera.getRotation());
            Vector3f up = new Vector3f(0, 1, 0).rotate(camera.getRotation());

            for (Iterator<Wave> it = WAVES.iterator(); it.hasNext(); ) {
                Wave w = it.next();
                double p = (now - w.born) / dur;
                if (p >= 1.0) { it.remove(); continue; }
                drawWave(vc, mat, w, p);
            }

            for (Iterator<Dust> it = DUST.iterator(); it.hasNext(); ) {
                Dust d = it.next();
                double q = (now - d.born) / d.life;
                if (q < 0) continue;
                if (q >= 1.0) { it.remove(); continue; }
                double t = q * d.life / 1000.0;
                float[] c = rgb(1);
                dot(vc, mat, right, up,
                        d.ox + d.vx * t, Math.max(d.oy, d.oy + d.vy * t - 5.2 * t * t), d.oz + d.vz * t,
                        d.size * (float) (1.0 - q * 0.55), (float) (0.85 * (1.0 - q)), c);
            }

            com.visualmod.util.RenderFlush.flush(ctx.consumers(), RenderLayers.debugQuads());
            ctx.matrices().pop();
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("ShockwaveOnHit", t);
        }
    }

    private static void drawWave(VertexConsumer vc, Matrix4f mat, Wave w, double p) {
        double travel = 1.0 - Math.pow(1.0 - p, 3.0);
        double r = Math.max(0.4, radius) * w.size * 0.7 * travel + 0.12;
        double thick = (0.10 + 0.34 * w.size * 0.2) * (1.0 - p * 0.55);
        double lift = Math.sin(Math.PI * Math.min(1.0, p * 1.35)) * 0.22 * w.size;
        float fade = (float) Math.pow(1.0 - p, 1.5);
        if (fade <= 0.01f) return;

        float[] c = rgb(0);
        float cr = c[0], cg = c[1], cb = c[2];

        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = i / (double) SEGMENTS * Math.PI * 2;
            double a1 = (i + 1) / (double) SEGMENTS * Math.PI * 2;
            double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            double ri0 = r - thick, ro0 = r, ri1 = r - thick, ro1 = r;
            double y0 = w.y + lift, y1 = w.y + lift * 0.75;
            quad(vc, mat,
                    w.x + ri0 * c0, y0, w.z + ri0 * s0,
                    w.x + ro0 * c0, y0, w.z + ro0 * s0,
                    w.x + ro1 * c1, y1, w.z + ro1 * s1,
                    w.x + ri1 * c1, y1, w.z + ri1 * s1,
                    cr, cg, cb, 0.62f * fade);
            // гребень: вертикальная стенка, кольцо читается даже с высокого ракурса
            quad(vc, mat,
                    w.x + ro0 * c0, y0, w.z + ro0 * s0,
                    w.x + ro1 * c1, y1, w.z + ro1 * s1,
                    w.x + ro1 * c1, y1 + 0.10 * w.size * fade, w.z + ro1 * s1,
                    w.x + ro0 * c0, y0 + 0.10 * w.size * fade, w.z + ro0 * s0,
                    cr, cg, cb, 0.92f * fade);
        }

        if (w.power > 1) {
            double ep = Math.max(0.0, p - 0.18) / 0.82;
            if (ep > 0) {
                double re = Math.max(0.4, radius) * w.size * 0.7 * (1.0 - Math.pow(1.0 - ep, 3.0)) * 0.68;
                float ef = (float) Math.pow(1.0 - ep, 2.0) * 0.55f;
                for (int i = 0; i < SEGMENTS; i += 2) {
                    double a0 = i / (double) SEGMENTS * Math.PI * 2;
                    double a1 = (i + 1) / (double) SEGMENTS * Math.PI * 2;
                    quad(vc, mat,
                            w.x + re * Math.cos(a0), w.y + 0.03, w.z + re * Math.sin(a0),
                            w.x + (re + 0.07) * Math.cos(a0), w.y + 0.03, w.z + (re + 0.07) * Math.sin(a0),
                            w.x + (re + 0.07) * Math.cos(a1), w.y + 0.03, w.z + (re + 0.07) * Math.sin(a1),
                            w.x + re * Math.cos(a1), w.y + 0.03, w.z + re * Math.sin(a1),
                            cr, cg, cb, ef);
                }
            }
        }

        // вспышка у основания на старте
        if (p < 0.3) {
            float f = (float) (1.0 - p / 0.3);
            double d = 0.5 * f * w.size;
            quad(vc, mat, w.x - d, w.y + 0.02, w.z - d, w.x + d, w.y + 0.02, w.z - d,
                    w.x + d, w.y + 0.02, w.z + d, w.x - d, w.y + 0.02, w.z + d,
                    cr, cg, cb, 0.30f * f);
        }
    }

    /** style 0 — выбранный цвет, 1 — раскалённый, 2 — фиолетовая пустота (индекс 1 = приглушённый тон). */
    private static float[] rgb(int tone) {
        int rgb = style == 1 ? (tone == 1 ? 0xFFFFF0D0 : 0xFFFF9A3C)
                : style == 2 ? (tone == 1 ? 0xFFE0CFFF : 0xFF8B46FF)
                : (tone == 1 ? 0xFFC9D8E6 : (accent & 0xFFFFFF));
        return new float[]{((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f};
    }

    private static void quad(VertexConsumer vc, Matrix4f mat,
                             double ax, double ay, double az, double bx, double by, double bz,
                             double cx, double cy, double cz, double dx, double dy, double dz,
                             float r, float g, float b, float a) {
        if (a <= 0.01f) return;
        vc.vertex(mat, (float) ax, (float) ay, (float) az).color(r, g, b, a);
        vc.vertex(mat, (float) bx, (float) by, (float) bz).color(r, g, b, a);
        vc.vertex(mat, (float) cx, (float) cy, (float) cz).color(r, g, b, a);
        vc.vertex(mat, (float) dx, (float) dy, (float) dz).color(r, g, b, a);
    }

    private static void dot(VertexConsumer vc, Matrix4f mat, Vector3f r, Vector3f u,
                            double x, double y, double z, float s, float a, float[] c) {
        if (a <= 0.01f) return;
        float fx = (float) x, fy = (float) y, fz = (float) z;
        vc.vertex(mat, fx - r.x * s - u.x * s, fy - r.y * s - u.y * s, fz - r.z * s - u.z * s).color(c[0], c[1], c[2], a);
        vc.vertex(mat, fx + r.x * s - u.x * s, fy + r.y * s - u.y * s, fz + r.z * s - u.z * s).color(c[0], c[1], c[2], a);
        vc.vertex(mat, fx + r.x * s + u.x * s, fy + r.y * s + u.y * s, fz + r.z * s + u.z * s).color(c[0], c[1], c[2], a);
        vc.vertex(mat, fx - r.x * s + u.x * s, fy - r.y * s + u.y * s, fz - r.z * s + u.z * s).color(c[0], c[1], c[2], a);
    }
}

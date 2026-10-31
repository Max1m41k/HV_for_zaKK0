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
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Обвал булавы: сокрушающий удар из падения выбивает из земли кратер из двух волн и
 * подбрасывает обломки-кубы. Срабатывает только на настоящий smash (булава + падение),
 * поэтому не пересекается ни с ударной волной крита, ни с рассечением земли.
 */
public class MaceSlam extends Module implements Configurable {

    public static double radius = 4.2;
    public static double duration = 0.85;
    public static double debris = 12;
    public static int color = 0xFFE8D9B8;
    public static int style = 0;

    private static final class Ring {
        double x, y, z, size;
        long born;
    }

    private static final class Rock {
        double x, y, z, vx, vy, vz;
        float size, spin0, spinSpeed;
        long born;
    }

    private static final class Dust {
        double x, y, z, vx, vy, vz;
        long born;
        float size, life;
    }

    private static final List<Ring> RINGS = new ArrayList<>();
    private static final List<Rock> ROCKS = new ArrayList<>();
    private static final List<Dust> DUST = new ArrayList<>();
    private static final Random RNG = new Random();
    private static final int SEGMENTS = 52;

    static { WorldRenderEvents.AFTER_ENTITIES.register(MaceSlam::render); }

    public MaceSlam() {
        super("MaceSlam", "Mace smash craters the ground and throws debris", Category.COMBAT);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Style", new String[]{"DUST", "STONE", "NETHER"}, () -> style, i -> style = i));
        l.add(new SliderSetting("Radius", 1.5, 8.0, 0.1, () -> radius, v -> radius = v, "b"));
        l.add(new SliderSetting("Fade", 0.35, 1.6, 0.05, () -> duration, v -> duration = v, "s"));
        l.add(new SliderSetting("Debris", 0, 24, 1, () -> debris, v -> debris = v));
        l.add(new ColorSetting("Color", () -> color, c -> color = c));
        return l;
    }

    @Override
    public void onDisable() {
        RINGS.clear();
        ROCKS.clear();
        DUST.clear();
    }

    public static void onAttack(PlayerEntity player, Entity target) {
        if (player == null || target == null) return;
        if (!player.getMainHandStack().isOf(Items.MACE)) return;
        if (player.fallDistance < 1.5f) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || VisualsMod.moduleManager == null) return;
        MaceSlam m = VisualsMod.moduleManager.getModule(MaceSlam.class);
        if (m == null || !m.isEnabled()) return;

        long now = System.currentTimeMillis();
        double gy = groundY(mc, target.getX(), target.getY(), target.getZ());
        float height = Math.max(0.8f, target.getHeight());

        Ring r = new Ring();
        r.x = target.getX();
        r.y = gy;
        r.z = target.getZ();
        r.size = height;
        r.born = now;
        RINGS.add(r);
        while (RINGS.size() > 6) RINGS.remove(0);

        emitRocks(target.getX(), gy, target.getZ(), height, now);
        emitDust(target.getX(), gy, target.getZ(), height, now);
    }

    private static double groundY(MinecraftClient mc, double x, double y, double z) {
        for (int i = 0; i < 10; i++) {
            double cy = y + 0.25 - i * 0.5;
            if (!mc.world.isAir(new BlockPos((int) Math.floor(x), (int) Math.floor(cy), (int) Math.floor(z)))) {
                return Math.floor(cy) + 1.006;
            }
        }
        return y + 0.02;
    }

    private static void emitRocks(double x, double y, double z, float height, long now) {
        int n = (int) Math.max(0, Math.min(24, debris));
        if (n == 0) return;
        RNG.setSeed(now ^ (long) (x * 71) ^ (long) (z * 13));
        for (int i = 0; i < n; i++) {
            double a = RNG.nextDouble() * Math.PI * 2;
            double d = 0.25 + RNG.nextDouble() * 0.55;
            Rock r = new Rock();
            r.x = x + Math.cos(a) * d;
            r.y = y + 0.08;
            r.z = z + Math.sin(a) * d;
            double sp = 1.1 + RNG.nextDouble() * 2.4;
            r.vx = Math.cos(a) * sp;
            r.vz = Math.sin(a) * sp;
            r.vy = 3.4 + RNG.nextDouble() * 3.0;
            r.size = (float) ((0.045 + RNG.nextDouble() * 0.075) * height);
            r.spin0 = (float) (RNG.nextDouble() * Math.PI * 2);
            r.spinSpeed = (float) (3.0 + RNG.nextDouble() * 7.0) * (RNG.nextBoolean() ? 1f : -1f);
            r.born = now + (long) (RNG.nextInt(40));
            ROCKS.add(r);
        }
        while (ROCKS.size() > 96) ROCKS.remove(0);
    }

    private static void emitDust(double x, double y, double z, float height, long now) {
        RNG.setSeed(now ^ (long) (x * 17) ^ (long) (z * 53));
        for (int i = 0; i < 30; i++) {
            double a = RNG.nextDouble() * Math.PI * 2;
            double sp = 2.2 + RNG.nextDouble() * 3.6;
            Dust d = new Dust();
            d.x = x + Math.cos(a) * 0.3;
            d.y = y + 0.08;
            d.z = z + Math.sin(a) * 0.3;
            d.vx = Math.cos(a) * sp;
            d.vz = Math.sin(a) * sp;
            d.vy = 0.6 + RNG.nextDouble() * 1.6;
            d.born = now + (long) (RNG.nextInt(90));
            d.life = 380 + RNG.nextFloat() * 500;
            d.size = (0.05f + RNG.nextFloat() * 0.09f) * height;
            DUST.add(d);
        }
        while (DUST.size() > 240) DUST.remove(0);
    }

    private static void render(WorldRenderContext ctx) {
        try {
            if (IrisCompat.skipWorldPass()) return;
            if (VisualsMod.moduleManager == null || ctx.matrices() == null || ctx.consumers() == null) return;
            MaceSlam m = VisualsMod.moduleManager.getModule(MaceSlam.class);
            if (m == null || !m.isEnabled()) return;
            if (RINGS.isEmpty() && ROCKS.isEmpty() && DUST.isEmpty()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null) return;

            long now = System.currentTimeMillis();
            double dur = Math.max(0.2, duration) * 1000.0;

            Camera camera = ctx.gameRenderer().getCamera();
            ctx.matrices().push();
            net.minecraft.util.math.Vec3d cam = camera.getCameraPos();
            ctx.matrices().translate(-cam.x, -cam.y, -cam.z);
            Matrix4f mat = ctx.matrices().peek().getPositionMatrix();
            VertexConsumer vc = ctx.consumers().getBuffer(RenderLayers.debugQuads());
            Vector3f right = new Vector3f(1, 0, 0).rotate(camera.getRotation());
            Vector3f up = new Vector3f(0, 1, 0).rotate(camera.getRotation());

            for (Iterator<Ring> it = RINGS.iterator(); it.hasNext(); ) {
                Ring r = it.next();
                double p = (now - r.born) / dur;
                if (p >= 1.0) { it.remove(); continue; }
                drawCrater(vc, mat, r, p);
            }

            float[] dc = dustColor();
            for (Iterator<Dust> it = DUST.iterator(); it.hasNext(); ) {
                Dust d = it.next();
                double q = (now - d.born) / d.life;
                if (q < 0) continue;
                if (q >= 1.0) { it.remove(); continue; }
                double t = q * d.life / 1000.0;
                float a = (float) (0.55 * (1.0 - q));
                dot(vc, mat, right, up,
                        d.x + d.vx * t, Math.max(d.y, d.y + d.vy * t - 1.6 * t * t), d.z + d.vz * t,
                        d.size * (float) (1.0 - q * 0.4), a, dc);
            }

            float[] rc = rockColor();
            for (Iterator<Rock> it = ROCKS.iterator(); it.hasNext(); ) {
                Rock r = it.next();
                long age = now - r.born;
                if (age < 0) continue;
                if (age > 1200) { it.remove(); continue; }
                double t = age / 1000.0;
                double y = r.y + r.vy * t - 9.0 * t * t;
                if (y < r.y - 0.2) { it.remove(); continue; }
                float fade = (float) Math.min(1.0, (1200 - age) / 380.0);
                box(vc, mat, r.x + r.vx * t, y, r.z + r.vz * t,
                        r.size, (float) (r.spin0 + r.spinSpeed * t), rc, fade);
            }

            com.visualmod.util.RenderFlush.flush(ctx.consumers(), RenderLayers.debugQuads());
            ctx.matrices().pop();
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("MaceSlam", t);
        }
    }

    private static void drawCrater(VertexConsumer vc, Matrix4f mat, Ring r, double p) {
        float[] c = ringColor();
        double max = Math.max(1.0, radius) * r.size * 0.62;
        // передний вал: широкое кольцо, идёт по земле
        double travel = 1.0 - Math.pow(1.0 - p, 2.2);
        double rr = 0.25 + max * travel;
        double thick = (0.28 + 0.30 * r.size * 0.2) * (1.0 - p * 0.5);
        float fade = (float) Math.pow(1.0 - p, 1.4);
        float lift = (float) (Math.sin(Math.PI * Math.min(1.0, p * 1.6)) * 0.16);
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = i / (double) SEGMENTS * Math.PI * 2;
            double a1 = (i + 1) / (double) SEGMENTS * Math.PI * 2;
            double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            quad(vc, mat,
                    r.x + (rr - thick) * c0, r.y + lift, r.z + (rr - thick) * s0,
                    r.x + rr * c0, r.y + lift, r.z + rr * s0,
                    r.x + rr * c1, r.y + lift * 0.72, r.z + rr * s1,
                    r.x + (rr - thick) * c1, r.y + lift * 0.72, r.z + (rr - thick) * s1,
                    c[0], c[1], c[2], 0.66f * fade);
            quad(vc, mat,
                    r.x + rr * c0, r.y + lift, r.z + rr * s0,
                    r.x + rr * c1, r.y + lift * 0.72, r.z + rr * s1,
                    r.x + rr * c1, r.y + lift * 0.72 + 0.12 * fade, r.z + rr * s1,
                    r.x + rr * c0, r.y + lift + 0.12 * fade, r.z + rr * s0,
                    c[0], c[1], c[2], 0.9f * fade);
        }
        // второй вал — догоняет с задержкой, чтобы удар читался двухтактным
        double p2 = Math.max(0.0, p - 0.22) / 0.78;
        if (p2 > 0 && p2 < 1) {
            double rr2 = 0.25 + max * 0.66 * (1.0 - Math.pow(1.0 - p2, 2.2));
            float f2 = (float) Math.pow(1.0 - p2, 1.8) * 0.5f;
            for (int i = 0; i < SEGMENTS; i += 2) {
                double a0 = i / (double) SEGMENTS * Math.PI * 2;
                double a1 = (i + 1) / (double) SEGMENTS * Math.PI * 2;
                quad(vc, mat,
                        r.x + rr2 * Math.cos(a0), r.y + 0.03, r.z + rr2 * Math.sin(a0),
                        r.x + (rr2 + 0.08) * Math.cos(a0), r.y + 0.03, r.z + (rr2 + 0.08) * Math.sin(a0),
                        r.x + (rr2 + 0.08) * Math.cos(a1), r.y + 0.03, r.z + (rr2 + 0.08) * Math.sin(a1),
                        r.x + rr2 * Math.cos(a1), r.y + 0.03, r.z + rr2 * Math.sin(a1),
                        c[0], c[1], c[2], f2);
            }
        }
        // ударное пятно на старте
        if (p < 0.28) {
            float f = (float) (1.0 - p / 0.28);
            double d = 0.7 * f * r.size;
            quad(vc, mat, r.x - d, r.y + 0.02, r.z - d, r.x + d, r.y + 0.02, r.z - d,
                    r.x + d, r.y + 0.02, r.z + d, r.x - d, r.y + 0.02, r.z + d,
                    c[0], c[1], c[2], 0.34f * f);
        }
    }

    /** Кубический обломок с вращением вокруг Y: шесть граней с разной яркостью. */
    private static void box(VertexConsumer vc, Matrix4f mat, double cx, double cy, double cz,
                            float s, float ang, float[] c, float alpha) {
        float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
        float axx = ca * s, axz = sa * s;
        float bxx = -sa * s, bxz = ca * s;
        float sy = s * 0.82f;
        float x = (float) cx, y = (float) cy, z = (float) cz;
        // 8 вершин
        float p000x = x - axx - bxx, p000y = y - sy, p000z = z - axz - bxz;
        float p100x = x + axx - bxx, p100y = y - sy, p100z = z + axz - bxz;
        float p110x = x + axx + bxx, p110y = y - sy, p110z = z + axz + bxz;
        float p010x = x - axx + bxx, p010y = y - sy, p010z = z - axz + bxz;
        float p001x = p000x, p001y = y + sy, p001z = p000z;
        float p101x = p100x, p101y = y + sy, p101z = p100z;
        float p111x = p110x, p111y = y + sy, p111z = p110z;
        float p011x = p010x, p011y = y + sy, p011z = p010z;
        float k = 0.42f + 0.34f * Math.min(1f, alpha);
        // верх
        quad(vc, mat, p001x, p001y, p001z, p101x, p101y, p101z, p111x, p111y, p111z, p011x, p011y, p011z,
                c[0] * 1.12f, c[1] * 1.12f, c[2] * 1.12f, alpha);
        // низ
        quad(vc, mat, p000x, p000y, p000z, p010x, p010y, p010z, p110x, p110y, p110z, p100x, p100y, p100z,
                c[0] * 0.5f, c[1] * 0.5f, c[2] * 0.5f, alpha);
        // бока
        quad(vc, mat, p000x, p000y, p000z, p100x, p100y, p100z, p101x, p101y, p101z, p001x, p001y, p001z,
                c[0] * k, c[1] * k, c[2] * k, alpha);
        quad(vc, mat, p100x, p100y, p100z, p110x, p110y, p110z, p111x, p111y, p111z, p101x, p101y, p101z,
                c[0] * k * 0.86f, c[1] * k * 0.86f, c[2] * k * 0.86f, alpha);
        quad(vc, mat, p110x, p110y, p110z, p010x, p010y, p010z, p011x, p011y, p011z, p111x, p111y, p111z,
                c[0] * k * 0.74f, c[1] * k * 0.74f, c[2] * k * 0.74f, alpha);
        quad(vc, mat, p010x, p010y, p010z, p000x, p000y, p000z, p001x, p001y, p001z, p011x, p011y, p011z,
                c[0] * k * 0.64f, c[1] * k * 0.64f, c[2] * k * 0.64f, alpha);
    }

    private static float[] ringColor() {
        int rgb = switch (style) {
            case 1 -> 0xFFCFD6DE;
            case 2 -> 0xFFB24A1F;
            default -> color & 0xFFFFFF;
        };
        return new float[]{((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f};
    }

    private static float[] rockColor() {
        int rgb = switch (style) {
            case 1 -> 0xFFB6BDC6;
            case 2 -> 0xFF6B2A16;
            default -> 0xFF8C8378;
        };
        return new float[]{((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f};
    }

    private static float[] dustColor() {
        int rgb = switch (style) {
            case 1 -> 0xFFDDE3EA;
            case 2 -> 0xFFC97A45;
            default -> 0xFFD8CBAA;
        };
        return new float[]{((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f};
    }

    private static void quad(VertexConsumer vc, Matrix4f mat,
                             double ax, double ay, double az, double bx, double by, double bz,
                             double cx, double cy, double cz, double dx, double dy, double dz,
                             float r, float g, float b, float a) {
        if (a <= 0.01f) return;
        float rr = Math.min(1f, r), gg = Math.min(1f, g), bb = Math.min(1f, b);
        // обе стороны: обломок виден с любого ракурса, даже если камера внутри его габарита
        vc.vertex(mat, (float) ax, (float) ay, (float) az).color(rr, gg, bb, a);
        vc.vertex(mat, (float) bx, (float) by, (float) bz).color(rr, gg, bb, a);
        vc.vertex(mat, (float) cx, (float) cy, (float) cz).color(rr, gg, bb, a);
        vc.vertex(mat, (float) dx, (float) dy, (float) dz).color(rr, gg, bb, a);
        vc.vertex(mat, (float) dx, (float) dy, (float) dz).color(rr, gg, bb, a);
        vc.vertex(mat, (float) cx, (float) cy, (float) cz).color(rr, gg, bb, a);
        vc.vertex(mat, (float) bx, (float) by, (float) bz).color(rr, gg, bb, a);
        vc.vertex(mat, (float) ax, (float) ay, (float) az).color(rr, gg, bb, a);
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

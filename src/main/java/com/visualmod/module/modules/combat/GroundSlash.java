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
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * GroundSlash: удар оставляет на земле светящийся разрез — тёмная щель с раскалённой
 * кромкой, из неё сыплются искры. Разрез лежит на плоскости блока под целью.
 */
public class GroundSlash extends Module implements Configurable {

    public static int style = 0;
    public static double length = 3.2;
    public static double width = 0.55;
    public static double duration = 1.4;
    public static int accent = 0xFFFF6A1A;
    public static boolean sparks = true;

    private static final class Slash {
        double x, y, z, dirX, dirZ;
        long born;
        long seed;
    }

    private static final class Spark {
        double ox, oy, oz, vx, vy, vz;
        long born;
        float life, size;
    }

    private static final List<Slash> SLASHES = new ArrayList<>();
    private static final List<Spark> SPARKS = new ArrayList<>();
    private static final Random RNG = new Random();

    static { WorldRenderEvents.AFTER_ENTITIES.register(GroundSlash::render); }

    public GroundSlash() {
        super("GroundSlash", "Hits carve a glowing rift into the ground", Category.COMBAT);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Style", new String[]{"EMBER", "VOID", "FROST"}, () -> style, i -> style = i));
        l.add(new SliderSetting("Length", 1.5, 7.0, 0.1, () -> length, v -> length = v, "b"));
        l.add(new SliderSetting("Width", 0.15, 1.4, 0.05, () -> width, v -> width = v, "b"));
        l.add(new SliderSetting("Fade", 0.6, 3.5, 0.1, () -> duration, v -> duration = v, "s"));
        l.add(new ColorSetting("Edge Color", () -> accent, c -> accent = c));
        l.add(new ModeSetting("Sparks", new String[]{"OFF", "ON"}, () -> sparks ? 1 : 0, i -> sparks = (i == 1)));
        return l;
    }

    @Override
    public void onDisable() { SLASHES.clear(); SPARKS.clear(); }

    public static void onAttack(Entity target) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || target == null) return;
        if (VisualsMod.moduleManager == null) return;
        GroundSlash m = VisualsMod.moduleManager.getModule(GroundSlash.class);
        if (m == null || !m.isEnabled()) return;

        double yaw = Math.toRadians(mc.player.getYaw());
        double dirX = -Math.sin(yaw), dirZ = Math.cos(yaw);
        double ty = groundY(mc, target.getX(), target.getY(), target.getZ());

        Slash s = new Slash();
        s.x = target.getX(); s.y = ty; s.z = target.getZ();
        s.dirX = dirX; s.dirZ = dirZ;
        s.born = System.currentTimeMillis();
        s.seed = target.getUuid().getLeastSignificantBits() ^ s.born;
        SLASHES.add(s);
        while (SLASHES.size() > 24) SLASHES.remove(0);

        if (sparks) emitSparks(s);
    }

    private static double groundY(MinecraftClient mc, double x, double y, double z) {
        for (int i = 0; i < 10; i++) {
            double cy = y + 0.25 - i * 0.5;
            if (!mc.world.isAir(new net.minecraft.util.math.BlockPos(
                    (int) Math.floor(x), (int) Math.floor(cy), (int) Math.floor(z)))) {
                return Math.floor(cy) + 1.004;
            }
        }
        return y + 0.02;
    }

    private static void emitSparks(Slash s) {
        RNG.setSeed(s.seed);
        int n = 14 + RNG.nextInt(10);
        double half = Math.max(0.5, length) * 0.5;
        for (int i = 0; i < n; i++) {
            double t = (RNG.nextDouble() * 2 - 1) * half;
            Spark sp = new Spark();
            sp.ox = s.x + s.dirX * t + (RNG.nextDouble() - 0.5) * 0.25;
            sp.oy = s.y + 0.05;
            sp.oz = s.z + s.dirZ * t + (RNG.nextDouble() - 0.5) * 0.25;
            double a = RNG.nextDouble() * Math.PI * 2;
            sp.vx = Math.cos(a) * (0.3 + RNG.nextDouble() * 0.7);
            sp.vz = Math.sin(a) * (0.3 + RNG.nextDouble() * 0.7);
            sp.vy = 1.4 + RNG.nextDouble() * 2.6;
            sp.born = s.born + (long) (Math.abs(t) / half * 90);
            sp.life = 320 + RNG.nextFloat() * 420;
            sp.size = 0.02f + RNG.nextFloat() * 0.035f;
            SPARKS.add(sp);
        }
        while (SPARKS.size() > 520) SPARKS.remove(0);
    }

    private static void render(WorldRenderContext ctx) {
        try {
            if (IrisCompat.skipWorldPass()) return;
            if (VisualsMod.moduleManager == null || ctx.matrices() == null || ctx.consumers() == null) return;
            GroundSlash m = VisualsMod.moduleManager.getModule(GroundSlash.class);
            if (m == null || !m.isEnabled()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null) return;
            if (SLASHES.isEmpty() && SPARKS.isEmpty()) return;

            long now = System.currentTimeMillis();
            Camera camera = ctx.gameRenderer().getCamera();
            net.minecraft.util.math.Vec3d cam = camera.getCameraPos();
            ctx.matrices().push();
            ctx.matrices().translate(-cam.x, -cam.y, -cam.z);
            Matrix4f mat = ctx.matrices().peek().getPositionMatrix();
            VertexConsumer vc = ctx.consumers().getBuffer(RenderLayers.debugQuads());
            Vector3f right = new Vector3f(1, 0, 0).rotate(camera.getRotation());
            Vector3f up = new Vector3f(0, 1, 0).rotate(camera.getRotation());

            for (Iterator<Slash> it = SLASHES.iterator(); it.hasNext(); ) {
                Slash s = it.next();
                double p = (now - s.born) / (Math.max(0.4, duration) * 1000.0);
                if (p >= 1.0) { it.remove(); continue; }
                drawSlash(vc, mat, s, p);
            }

            for (Iterator<Spark> it = SPARKS.iterator(); it.hasNext(); ) {
                Spark sp = it.next();
                double q = (now - sp.born) / sp.life;
                if (q < 0) continue;
                if (q >= 1.0) { it.remove(); continue; }
                double t = q * sp.life / 1000.0;
                double sy = sp.oy + sp.vy * t - 4.5 * t * t;
                if (sy < sp.oy) sy = sp.oy;
                dot(vc, mat, right, up, sp.ox + sp.vx * t, sy, sp.oz + sp.vz * t,
                        sp.size * (float) (1.0 - q * 0.5f), (float) (1.0 - q));
            }

            com.visualmod.util.RenderFlush.flush(ctx.consumers(), RenderLayers.debugQuads());
            ctx.matrices().pop();
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("GroundSlash", t);
        }
    }

    private static void drawSlash(VertexConsumer vc, Matrix4f mat, Slash s, double p) {
        double open = p < 0.18 ? p / 0.18 : 1.0;
        float glow = (float) (1.0 - Math.max(0.0, (p - 0.45) / 0.55));
        float fade = (float) (1.0 - Math.max(0.0, (p - 0.62) / 0.38));
        double half = Math.max(0.5, length) * 0.5 * open;
        double w = Math.max(0.05, width) * (0.35 + 0.65 * open);
        double px = -s.dirZ, pz = s.dirX;

        int rgb = style == 1 ? 0xFF7A3DFF : style == 2 ? 0xFF8AF2FF : (accent & 0xFFFFFF);
        float er = ((rgb >> 16) & 0xFF) / 255f, eg = ((rgb >> 8) & 0xFF) / 255f, eb = (rgb & 0xFF) / 255f;

        int steps = 18;
        double prevLx = 0, prevLz = 0, prevRx = 0, prevRz = 0;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double along = -half + 2 * half * t;
            double bulge = Math.sin(Math.PI * t);
            double edge = w * (0.30 + 0.70 * bulge);
            double cx = s.x + s.dirX * along, cz = s.z + s.dirZ * along;
            double lx = cx + px * edge, lz = cz + pz * edge;
            double rx = cx - px * edge, rz = cz - pz * edge;
            if (i > 0) {
                // тёмная щель
                quad(vc, mat, prevLx, s.y, prevLz, lx, s.y, lz, rx, s.y, rz, prevRx, s.y, prevRz,
                        0.02f, 0.01f, 0.03f, 0.92f * fade);
                // кромки разреза
                ribbon(vc, mat, prevLx, s.y, prevLz, lx, s.y, lz, er, eg, eb, 0.95f * glow);
                ribbon(vc, mat, prevRx, s.y, prevRz, rx, s.y, rz, er, eg, eb, 0.95f * glow);
                // внутренний жар
                quad(vc, mat, cx + px * edge * 0.45, s.y, cz + pz * edge * 0.45,
                        lx, s.y, lz, rx, s.y, rz,
                        cx - px * edge * 0.45, s.y, cz - pz * edge * 0.45,
                        er, eg, eb, 0.55f * glow);
            }
            prevLx = lx; prevLz = lz; prevRx = rx; prevRz = rz;
        }

        // росчерк по направлению удара
        double fl = half * 1.25 * glow;
        quad(vc, mat,
                s.x + s.dirX * fl, s.y, s.z + s.dirZ * fl,
                s.x + s.dirX * (fl - 0.5), s.y, s.z + s.dirZ * (fl - 0.5),
                s.x - s.dirX * (fl - 0.5), s.y, s.z - s.dirZ * (fl - 0.5),
                s.x - s.dirX * fl, s.y, s.z - s.dirZ * fl,
                er, eg, eb, 0.10f * glow);
    }

    private static void ribbon(VertexConsumer vc, Matrix4f mat, double ax, double ay, double az,
                               double bx, double by, double bz, float r, float g, float b, float a) {
        if (a <= 0.01f) return;
        quad(vc, mat, ax, ay + 0.055, az, bx, by + 0.055, bz, bx, ay, bz, ax, ay, az, r, g, b, a);
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
                            double x, double y, double z, float s, float a) {
        int rgb = style == 1 ? 0xFFB08CFF : style == 2 ? 0xFFD6FBFF : (accent & 0xFFFFFF);
        float cr = ((rgb >> 16) & 0xFF) / 255f, cg = ((rgb >> 8) & 0xFF) / 255f, cb = (rgb & 0xFF) / 255f;
        float fx = (float) x, fy = (float) y, fz = (float) z;
        vc.vertex(mat, fx - r.x * s - u.x * s, fy - r.y * s - u.y * s, fz - r.z * s - u.z * s).color(cr, cg, cb, a);
        vc.vertex(mat, fx + r.x * s - u.x * s, fy + r.y * s - u.y * s, fz + r.z * s - u.z * s).color(cr, cg, cb, a);
        vc.vertex(mat, fx + r.x * s + u.x * s, fy + r.y * s + u.y * s, fz + r.z * s + u.z * s).color(cr, cg, cb, a);
        vc.vertex(mat, fx - r.x * s + u.x * s, fy - r.y * s + u.y * s, fz - r.z * s + u.z * s).color(cr, cg, cb, a);
    }
}

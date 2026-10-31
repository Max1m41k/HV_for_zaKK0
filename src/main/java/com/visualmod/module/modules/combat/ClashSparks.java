package com.visualmod.module.modules.combat;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.util.Diag;
import com.visualmod.util.IrisCompat;
import com.visualmod.util.RenderFlush;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * ClashSparks: в момент, когда щит отбивает удар, у защищающегося вспыхивает кант щита,
 * разлетаются искры и расходится короткое кольцо. Питается пакетом EntityStatus: 29 — блок,
 * 30 — щит сломан (вспышка сильнее).
 */
public class ClashSparks extends Module implements Configurable {

    public static int color = 0xFFC8F2FF;
    public static double duration = 0.55;
    public static double sparks = 20;
    public static double ring = 1.1;
    public static int targets = 0;

    private static final String[] TARGETS = {"ALL", "SELF"};

    private static final class Clash {
        double x, y, z;
        long at;
        long seed;
        boolean broke;
    }

    private static final List<Clash> CLASHES = new ArrayList<>();

    static {
        WorldRenderEvents.AFTER_ENTITIES.register(ClashSparks::render);
    }

    public ClashSparks() {
        super("ClashSparks", "Shield blocks burst into sparks and a hot rim", Category.COMBAT);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ColorSetting("Color", () -> color, c -> color = c));
        l.add(new SliderSetting("Duration", 0.2, 1.2, 0.05, () -> duration, v -> duration = v, "s"));
        l.add(new SliderSetting("Sparks", 6, 40, 1, () -> sparks, v -> sparks = v, ""));
        l.add(new SliderSetting("Ring", 0.4, 2.4, 0.1, () -> ring, v -> ring = v, "b"));
        l.add(new ModeSetting("Targets", TARGETS, () -> targets, i -> targets = i));
        return l;
    }

    @Override
    public void onDisable() {
        synchronized (CLASHES) { CLASHES.clear(); }
    }

    /** Вызывается из ClientPlayNetworkHandlerMixin на EntityStatus 29/30. */
    public static void onStatus(MinecraftClient mc, Entity entity, byte status) {
        try {
            if (status != 29 && status != 30) return;
            if (mc == null || entity == null || mc.player == null || mc.world == null) return;
            if (VisualsMod.moduleManager == null) return;
            ClashSparks m = VisualsMod.moduleManager.getModule(ClashSparks.class);
            if (m == null || !m.isEnabled()) return;
            if (targets == 1 && entity != mc.player) return;

            Clash c = new Clash();
            c.x = entity.getX();
            c.y = entity.getY() + entity.getHeight() * 0.62;
            c.z = entity.getZ();
            c.at = System.currentTimeMillis();
            c.seed = entity.getId() * 341873128712L ^ c.at;
            c.broke = status == 30;
            synchronized (CLASHES) {
                CLASHES.add(c);
                while (CLASHES.size() > 24) CLASHES.remove(0);
            }
        } catch (Throwable t) {
            Diag.once("ClashSparks.status", t);
        }
    }

    private static void render(WorldRenderContext ctx) {
        try {
            if (IrisCompat.skipWorldPass()) return;
            if (VisualsMod.moduleManager == null || ctx.matrices() == null || ctx.consumers() == null) return;
            ClashSparks m = VisualsMod.moduleManager.getModule(ClashSparks.class);
            if (m == null || !m.isEnabled()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null || mc.player == null) return;

            long now = System.currentTimeMillis();
            List<Clash> snapshot;
            synchronized (CLASHES) {
                for (Iterator<Clash> it = CLASHES.iterator(); it.hasNext(); ) {
                    Clash c = it.next();
                    if (now - c.at > Math.max(0.2, duration) * 1000.0 + 400) it.remove();
                }
                if (CLASHES.isEmpty()) return;
                snapshot = new ArrayList<>(CLASHES);
            }

            Camera cam = ctx.gameRenderer().getCamera();
            Vec3d cp = cam.getCameraPos();
            ctx.matrices().push();
            ctx.matrices().translate(-cp.x, -cp.y, -cp.z);
            Matrix4f mat = ctx.matrices().peek().getPositionMatrix();
            VertexConsumer vc = ctx.consumers().getBuffer(RenderLayers.debugQuads());
            Vector3f right = new Vector3f(1, 0, 0).rotate(cam.getRotation());
            Vector3f up = new Vector3f(0, 1, 0).rotate(cam.getRotation());

            for (Clash c : snapshot) {
                draw(vc, mat, right, up, c, (now - c.at) / (Math.max(0.2, duration) * 1000.0));
            }

            RenderFlush.flush(ctx.consumers(), RenderLayers.debugQuads());
            ctx.matrices().pop();
        } catch (Throwable t) {
            Diag.once("ClashSparks.render", t);
        }
    }

    private static void draw(VertexConsumer vc, Matrix4f mat, Vector3f right, Vector3f up, Clash c, double t) {
        if (t < 0 || t >= 1) return;
        float r = ((color >> 16) & 0xFF) / 255f, g = ((color >> 8) & 0xFF) / 255f, b = (color & 0xFF) / 255f;
        float fade = (float) (1.0 - t);
        float power = c.broke ? 1.35f : 1f;

        // Расходящееся кольцо в плоскости экрана: читается с любой стороны.
        double rad = ring * (0.15 + 0.85 * Math.pow(t, 0.55));
        int seg = 48;
        float a = 0.75f * fade * power;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            double x0 = c.x + Math.cos(a0) * rad, y0 = c.y + Math.sin(a0) * rad;
            double x1 = c.x + Math.cos(a1) * rad, y1 = c.y + Math.sin(a1) * rad;
            quadFacing(vc, mat, right, up, x0, y0, c.z, x1, y1, c.z, 0.035, r, g, b, a);
        }

        // Искры: детерминированный разлёт по сфере, гравитация и затухание.
        int n = (int) Math.max(1, Math.min(40, sparks));
        for (int i = 0; i < n; i++) {
            long s = c.seed + i * 977L;
            double ang = hash(s) * Math.PI * 2;
            double elev = (hash(s + 13) - 0.5) * 1.6;
            double speed = (1.4 + hash(s + 29) * 2.6) * power;
            double life = 0.35 + hash(s + 41) * 0.55;
            double q = t * Math.max(0.35, duration) / life;
            if (q >= 1) continue;
            double dist = speed * q * 0.55;
            double x = c.x + Math.cos(ang) * Math.cos(elev) * dist;
            double y = c.y + Math.sin(elev) * dist - 1.6 * q * q;
            double z = c.z + Math.sin(ang) * Math.cos(elev) * dist;
            float sa = (float) ((1 - q) * 0.95 * power);
            float sz = (float) (0.015 + 0.02 * (1 - q));
            dot(vc, mat, right, up, x, y, z, sz, r, g, b, sa);
        }

        // Яркое ядро вспышки в первые ~120 мс.
        if (t < 0.22) {
            float k = (float) (1 - t / 0.22);
            dot(vc, mat, right, up, c.x, c.y, c.z, 0.16f * k * power, 1f, 1f, 1f, 0.85f * k);
        }
    }

    private static double hash(long v) {
        v ^= v >>> 33;
        v *= 0xff51afd7ed558ccdL;
        v ^= v >>> 33;
        v *= 0xc4ceb9fe1a85ec53L;
        v ^= v >>> 33;
        return ((v >>> 11) & 0x1FFFFF) / (double) 0x1FFFFF;
    }

    private static void quadFacing(VertexConsumer vc, Matrix4f mat, Vector3f right, Vector3f up,
                                   double ax, double ay, double az, double bx, double by, double bz,
                                   double thick, float r, float g, float b, float a) {
        if (a <= 0.01f) return;
        float tx = (float) (right.x * thick), ty = (float) (right.y * thick), tz = (float) (right.z * thick);
        float ux = (float) (up.x * thick), uy = (float) (up.y * thick), uz = (float) (up.z * thick);
        float axf = (float) ax, ayf = (float) ay, azf = (float) az;
        float bxf = (float) bx, byf = (float) by, bzf = (float) bz;
        vc.vertex(mat, axf - tx, ayf - ty, azf - tz).color(r, g, b, a);
        vc.vertex(mat, bxf - tx, byf - ty, bzf - tz).color(r, g, b, a);
        vc.vertex(mat, bxf + tx, byf + ty, bzf + tz).color(r, g, b, a);
        vc.vertex(mat, axf + tx, ayf + ty, azf + tz).color(r, g, b, a);
    }

    private static void dot(VertexConsumer vc, Matrix4f mat, Vector3f r, Vector3f u,
                            double x, double y, double z, float s, float cr, float cg, float cb, float a) {
        if (a <= 0.01f) return;
        float fx = (float) x, fy = (float) y, fz = (float) z;
        vc.vertex(mat, fx - r.x * s - u.x * s, fy - r.y * s - u.y * s, fz - r.z * s - u.z * s).color(cr, cg, cb, a);
        vc.vertex(mat, fx + r.x * s - u.x * s, fy + r.y * s - u.y * s, fz + r.z * s - u.z * s).color(cr, cg, cb, a);
        vc.vertex(mat, fx + r.x * s + u.x * s, fy + r.y * s + u.y * s, fz + r.z * s + u.z * s).color(cr, cg, cb, a);
        vc.vertex(mat, fx - r.x * s + u.x * s, fy - r.y * s + u.y * s, fz - r.z * s + u.z * s).color(cr, cg, cb, a);
    }
}

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
import com.visualmod.util.KillTracker;
import com.visualmod.util.RenderFlush;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Finisher: добивание отмечается расходящимся кольцом, столбом искр и коротким наездом камеры.
 * Срабатывает по KillTracker, то есть только на той цели, которую мы сами ударили незадолго
 * до её смерти, — чужие киллы рядом ничего не запускают.
 */
public class Finisher extends Module implements Configurable {

    public static int color = 0xFFFFCE5A;
    public static double duration = 0.95;
    public static double radius = 1.7;
    public static boolean zoom = true;
    public static boolean thud = true;

    private static final class Kill {
        double x, y, z;
        long at;
    }

    private static final List<Kill> KILLS = new ArrayList<>();
    private static final long ZOOM_MS = 260;

    static {
        WorldRenderEvents.AFTER_ENTITIES.register(Finisher::render);
        KillTracker.register(Finisher::onKill);
    }

    public Finisher() {
        super("Finisher", "Finishing blow: shock ring, sparks and a short camera punch", Category.COMBAT);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ColorSetting("Color", () -> color, c -> color = c));
        l.add(new SliderSetting("Duration", 0.4, 2.0, 0.05, () -> duration, v -> duration = v, "s"));
        l.add(new SliderSetting("Radius", 0.6, 3.5, 0.1, () -> radius, v -> radius = v, "b"));
        l.add(new ModeSetting("Camera Punch", new String[]{"OFF", "ON"}, () -> zoom ? 1 : 0, i -> zoom = (i == 1)));
        l.add(new ModeSetting("Thud", new String[]{"OFF", "ON"}, () -> thud ? 1 : 0, i -> thud = (i == 1)));
        return l;
    }

    @Override
    public void onDisable() {
        synchronized (KILLS) { KILLS.clear(); }
    }

    private static void onKill(Entity victim, double x, double y, double z, float height) {
        if (VisualsMod.moduleManager == null) return;
        Finisher m = VisualsMod.moduleManager.getModule(Finisher.class);
        if (m == null || !m.isEnabled()) return;
        Kill k = new Kill();
        k.x = x; k.y = y + 0.06; k.z = z;
        k.at = System.currentTimeMillis();
        synchronized (KILLS) {
            KILLS.add(k);
            while (KILLS.size() > 8) KILLS.remove(0);
        }
        if (thud) {
            try {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.getSoundManager() != null) {
                    mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.BLOCK_ANVIL_LAND, 0.62f));
                }
            } catch (Throwable t) {
                Diag.once("Finisher.sound", t);
            }
        }
    }

    /** Множитель FOV: 1 — без наезда, < 1 — камера приближается. Читает FinisherFovMixin. */
    public static float fovScale() {
        try {
            if (VisualsMod.moduleManager == null) return 1f;
            Finisher m = VisualsMod.moduleManager.getModule(Finisher.class);
            if (m == null || !m.isEnabled() || !zoom) return 1f;
            long now = System.currentTimeMillis();
            float best = 0f;
            synchronized (KILLS) {
                for (Kill k : KILLS) {
                    long age = now - k.at;
                    if (age < 0 || age >= ZOOM_MS) continue;
                    float t = 1f - age / (float) ZOOM_MS;
                    best = Math.max(best, t * t);
                }
            }
            return 1f - 0.10f * best;
        } catch (Throwable t) {
            return 1f;
        }
    }

    private static void render(WorldRenderContext ctx) {
        try {
            if (IrisCompat.skipWorldPass()) return;
            if (VisualsMod.moduleManager == null || ctx.matrices() == null || ctx.consumers() == null) return;
            Finisher m = VisualsMod.moduleManager.getModule(Finisher.class);
            if (m == null || !m.isEnabled()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null || mc.player == null) return;

            long now = System.currentTimeMillis();
            List<Kill> snapshot;
            synchronized (KILLS) {
                for (Iterator<Kill> it = KILLS.iterator(); it.hasNext(); ) {
                    Kill k = it.next();
                    if (now - k.at > Math.max(0.4, duration) * 1000.0 + 500) it.remove();
                }
                if (KILLS.isEmpty()) return;
                snapshot = new ArrayList<>(KILLS);
            }

            Camera cam = ctx.gameRenderer().getCamera();
            Vec3d cp = cam.getCameraPos();
            ctx.matrices().push();
            ctx.matrices().translate(-cp.x, -cp.y, -cp.z);
            Matrix4f mat = ctx.matrices().peek().getPositionMatrix();
            VertexConsumer vc = ctx.consumers().getBuffer(RenderLayers.debugQuads());
            Vector3f right = new Vector3f(1, 0, 0).rotate(cam.getRotation());
            Vector3f up = new Vector3f(0, 1, 0).rotate(cam.getRotation());

            for (Kill k : snapshot) {
                draw(vc, mat, right, up, k, (now - k.at) / (Math.max(0.4, duration) * 1000.0));
            }

            RenderFlush.flush(ctx.consumers(), RenderLayers.debugQuads());
            ctx.matrices().pop();
        } catch (Throwable t) {
            Diag.once("Finisher.render", t);
        }
    }

    private static void draw(VertexConsumer vc, Matrix4f mat, Vector3f right, Vector3f up, Kill k, double p) {
        if (p < 0 || p >= 1) return;
        float r = ((color >> 16) & 0xFF) / 255f, g = ((color >> 8) & 0xFF) / 255f, b = (color & 0xFF) / 255f;
        float fade = (float) (1.0 - p);
        double open = Math.min(1.0, p / 0.16);
        double rad = radius * open;

        // Кольцо на земле под целью.
        int seg = 56;
        float a = 0.9f * fade;
        double inner = rad * 0.88;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            double x0 = k.x + Math.cos(a0) * inner, z0 = k.z + Math.sin(a0) * inner;
            double x1 = k.x + Math.cos(a1) * inner, z1 = k.z + Math.sin(a1) * inner;
            double x2 = k.x + Math.cos(a1) * rad, z2 = k.z + Math.sin(a1) * rad;
            double x3 = k.x + Math.cos(a0) * rad, z3 = k.z + Math.sin(a0) * rad;
            quad(vc, mat, x0, k.y + 0.02, z0, x1, k.y + 0.02, z1, x2, k.y + 0.02, z2, x3, k.y + 0.02, z3, r, g, b, a);
        }

        // Вертикальный столб искр: чем ближе к концу, тем выше и реже.
        int n = 26;
        for (int i = 0; i < n; i++) {
            double ang = i * 2.399963229728653;
            double rr = Math.sqrt((i + 0.5) / n) * rad * 0.75;
            double rise = (2.2 + (i % 5) * 0.5) * p;
            float sa = (float) (fade * (0.55 + 0.45 * Math.sin(i * 1.7)));
            dot(vc, mat, right, up, k.x + Math.cos(ang) * rr, k.y + 0.1 + rise, k.z + Math.sin(ang) * rr,
                    0.022f + 0.01f * (1 - (float) p), r, g, b, sa);
        }

        // Крест-вспышка в первые 200 мс.
        if (p < 0.25) {
            float q = (float) (1 - p / 0.25);
            double len = radius * 1.15;
            quadFacing(vc, mat, right, up, k.x, k.y + 0.65, k.z, len, 0.05 * q, 1f, 1f, 1f, 0.8f * q);
            quadFacing(vc, mat, up, right, k.x, k.y + 0.65, k.z, len, 0.05 * q, 1f, 1f, 1f, 0.8f * q);
        }
    }

    private static void quadFacing(VertexConsumer vc, Matrix4f mat, Vector3f right, Vector3f up,
                                   double x, double y, double z, double len, double thick,
                                   float cr, float cg, float cb, float a) {
        if (a <= 0.01f) return;
        float fx = (float) x, fy = (float) y, fz = (float) z;
        float lx = (float) (right.x * len), ly = (float) (right.y * len), lz = (float) (right.z * len);
        float tx = (float) (up.x * thick), ty = (float) (up.y * thick), tz = (float) (up.z * thick);
        vc.vertex(mat, fx - lx - tx, fy - ly - ty, fz - lz - tz).color(cr, cg, cb, a);
        vc.vertex(mat, fx - lx + tx, fy - ly + ty, fz - lz + tz).color(cr, cg, cb, a);
        vc.vertex(mat, fx + lx + tx, fy + ly + ty, fz + lz + tz).color(cr, cg, cb, a);
        vc.vertex(mat, fx + lx - tx, fy + ly - ty, fz + lz - tz).color(cr, cg, cb, a);
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
                            double x, double y, double z, float s, float cr, float cg, float cb, float a) {
        if (a <= 0.01f) return;
        float fx = (float) x, fy = (float) y, fz = (float) z;
        vc.vertex(mat, fx - r.x * s - u.x * s, fy - r.y * s - u.y * s, fz - r.z * s - u.z * s).color(cr, cg, cb, a);
        vc.vertex(mat, fx + r.x * s - u.x * s, fy + r.y * s - u.y * s, fz + r.z * s - u.z * s).color(cr, cg, cb, a);
        vc.vertex(mat, fx + r.x * s + u.x * s, fy + r.y * s + u.y * s, fz + r.z * s + u.z * s).color(cr, cg, cb, a);
        vc.vertex(mat, fx - r.x * s + u.x * s, fy - r.y * s + u.y * s, fz - r.z * s + u.z * s).color(cr, cg, cb, a);
    }
}

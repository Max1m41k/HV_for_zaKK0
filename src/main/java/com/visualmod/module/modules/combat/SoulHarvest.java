package com.visualmod.module.modules.combat;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.util.IrisCompat;
import com.visualmod.util.KillTracker;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * SoulHarvest: из убитой нами цели вылетают души, летят по дуге и всасываются в игрока.
 * Собранные души кружатся орбитой над головой — это видно только тебе.
 */
public class SoulHarvest extends Module implements Configurable {

    public static int mode = 0;
    public static double soulsPerKill = 6;
    public static double duration = 1.5;
    public static double glow = 0.75;
    public static int color = 0xFF54F0CC;
    public static boolean showHalo = true;
    public static boolean showCounter = true;

    private static final class Target { double x, y, z; }

    private static final class Soul {
        double sx, sy, sz, cx, cy, cz;
        long born;
        float seed, size;
    }

    private static final class Ring {
        double x, y, z;
        long born;
    }

    private static final List<Soul> SOULS = new ArrayList<>();
    private static final List<Ring> RINGS = new ArrayList<>();
    private static final Random RNG = new Random();
    private static final double[] P = new double[3];

    private static int harvested = 0;
    private static long lastHarvestAt = 0;

    static {
        WorldRenderEvents.AFTER_ENTITIES.register(SoulHarvest::render);
        HudRenderCallback.EVENT.register((ctx, tickDelta) -> drawCounter(ctx));
        KillTracker.register(SoulHarvest::onKill);
    }

    public SoulHarvest() {
        super("SoulHarvest", "Souls fly out of your kill and are absorbed into you", Category.COMBAT);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Style", new String[]{"ORBS", "WISPS", "MOTES"}, () -> mode, i -> mode = i));
        l.add(new SliderSetting("Souls", 2, 14, 1, () -> soulsPerKill, v -> soulsPerKill = v, ""));
        l.add(new SliderSetting("Flight Time", 0.6, 3.0, 0.1, () -> duration, v -> duration = v, "s"));
        l.add(new SliderSetting("Glow", 0, 1, 0.05, () -> glow, v -> glow = v, ""));
        l.add(new ColorSetting("Color", () -> color, c -> color = c));
        l.add(new ModeSetting("Halo", new String[]{"OFF", "ON"}, () -> showHalo ? 1 : 0, i -> showHalo = (i == 1)));
        l.add(new ModeSetting("Counter", new String[]{"OFF", "ON"}, () -> showCounter ? 1 : 0, i -> showCounter = (i == 1)));
        return l;
    }

    @Override
    public void onDisable() {
        SOULS.clear(); RINGS.clear();
        harvested = 0;
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (mc.player.isDead() || mc.player.getHealth() <= 0f) harvested = 0;
        else if (harvested > 0 && System.currentTimeMillis() - lastHarvestAt > 90_000) harvested = 0;
    }

    private static void onKill(Entity victim, double x, double y, double z, float height) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || VisualsMod.moduleManager == null) return;
        SoulHarvest m = VisualsMod.moduleManager.getModule(SoulHarvest.class);
        if (m == null || !m.isEnabled()) return;
        double px = mc.player.getX(), py = mc.player.getY() + 1.1, pz = mc.player.getZ();
        double ox = x, oy = y + height * 0.62, oz = z;
        RNG.setSeed(victim.getUuid().getLeastSignificantBits() ^ System.currentTimeMillis());
        int count = (int) Math.max(1, Math.min(16, soulsPerKill));
        long now = System.currentTimeMillis();
        synchronized (SOULS) {
            for (int i = 0; i < count; i++) {
                Soul s = new Soul();
                s.sx = ox + (RNG.nextDouble() - 0.5) * 0.7;
                s.sy = oy + (RNG.nextDouble() - 0.5) * 0.6;
                s.sz = oz + (RNG.nextDouble() - 0.5) * 0.7;
                double ang = RNG.nextDouble() * Math.PI * 2, lat = 0.9 + RNG.nextDouble() * 2.2;
                double mx = (s.sx + px) * 0.5, my = (s.sy + py) * 0.5, mz = (s.sz + pz) * 0.5;
                s.cx = mx + Math.cos(ang) * lat;
                s.cy = my + 0.9 + RNG.nextDouble() * 2.1;
                s.cz = mz + Math.sin(ang) * lat;
                s.born = now + (long) (i * 45 + RNG.nextDouble() * 90);
                s.seed = RNG.nextFloat() * 6.283f;
                s.size = 0.85f + RNG.nextFloat() * 0.5f;
                SOULS.add(s);
            }
            while (SOULS.size() > 260) SOULS.remove(0);
        }
    }

    private static void render(WorldRenderContext ctx) {
        try {
            if (IrisCompat.skipWorldPass()) return;
            if (VisualsMod.moduleManager == null || ctx.matrices() == null || ctx.consumers() == null) return;
            SoulHarvest m = VisualsMod.moduleManager.getModule(SoulHarvest.class);
            if (m == null || !m.isEnabled()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null || mc.player == null) return;
            long now = System.currentTimeMillis();
            Camera camera = ctx.gameRenderer().getCamera();
            net.minecraft.util.math.Vec3d cp = camera.getCameraPos();
            ctx.matrices().push();
            Vector3f cam = new Vector3f((float) cp.x, (float) cp.y, (float) cp.z);
            ctx.matrices().translate(-cam.x, -cam.y, -cam.z);
            Matrix4f mat = ctx.matrices().peek().getPositionMatrix();
            VertexConsumer vc = ctx.consumers().getBuffer(RenderLayers.debugQuads());

            Target tgt = new Target();
            if (mc.options.getPerspective().isFirstPerson()) {
                tgt.x = cp.x; tgt.y = cp.y; tgt.z = cp.z;
            } else {
                tgt.x = mc.player.getX(); tgt.y = mc.player.getY() + 1.1; tgt.z = mc.player.getZ();
            }

            Vector3f right = new Vector3f(1, 0, 0).rotate(camera.getRotation());
            Vector3f up = new Vector3f(0, 1, 0).rotate(camera.getRotation());

            float cr = ((color >> 16) & 0xFF) / 255f, cg = ((color >> 8) & 0xFF) / 255f, cb = (color & 0xFF) / 255f;

            int absorbed = 0;
            synchronized (SOULS) {
                for (Iterator<Soul> it = SOULS.iterator(); it.hasNext(); ) {
                    Soul s = it.next();
                    double u = (now - s.born) / (Math.max(0.35, duration) * 1000.0);
                    if (u < 0) continue;
                    if (u >= 1.0) {
                        it.remove();
                        absorbed++;
                        synchronized (RINGS) {
                            Ring r = new Ring();
                            r.x = tgt.x; r.y = tgt.y; r.z = tgt.z; r.born = now;
                            RINGS.add(r);
                        }
                        continue;
                    }
                    drawSoul(vc, mat, s, u, tgt, right, up, cam, cr, cg, cb);
                }
            }
            if (absorbed > 0) {
                harvested = Math.min(999, harvested + absorbed);
                lastHarvestAt = now;
            }

            synchronized (RINGS) {
                for (Iterator<Ring> it = RINGS.iterator(); it.hasNext(); ) {
                    Ring r = it.next();
                    double p = (now - r.born) / 420.0;
                    if (p >= 1) { it.remove(); continue; }
                    ring(vc, mat, r, p, right, up, cr, cg, cb);
                }
            }

            if (showHalo && harvested > 0) halo(vc, mat, mc, now, right, up, cr, cg, cb);

            com.visualmod.util.RenderFlush.flush(ctx.consumers(), RenderLayers.debugQuads());
            ctx.matrices().pop();
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("SoulHarvest", t);
        }
    }

    private static void drawSoul(VertexConsumer vc, Matrix4f mat, Soul s, double u, Target tgt,
                                 Vector3f right, Vector3f up, Vector3f cam,
                                 float cr, float cg, float cb) {
        int steps = mode == 2 ? 6 : 9;
        double gap = mode == 2 ? 0.045 : 0.062;
        Vector3f prev = null;
        for (int k = 0; k < steps; k++) {
            double t = u - k * gap;
            if (t <= 0.001) break;
            bezier(s, t, tgt, P);
            double taper = 1.0 - k / (double) steps;
            float flick = (float) (0.82 + 0.28 * Math.sin(t * 14.0 + s.seed + k * 0.7));
            float sz = (float) (0.115 * s.size * taper * flick);
            float alpha = (float) ((0.30 + 0.70 * t) * taper * Math.min(1.0, u / 0.14));
            Vector3f p = new Vector3f((float) P[0], (float) P[1], (float) P[2]);
            if (mode == 1 && prev != null) {
                Vector3f dir = new Vector3f(p).sub(prev);
                if (dir.lengthSquared() > 1e-8f) {
                    dir.normalize();
                    Vector3f toCam = new Vector3f(prev.x - cam.x, prev.y - cam.y, prev.z - cam.z).normalize();
                    Vector3f n = new Vector3f(dir).cross(toCam).normalize().mul(sz * 1.5f);
                    ribbon(vc, mat, prev, p, n, cr, cg, cb, alpha);
                }
            } else {
                billboard(vc, mat, p, right, up, sz, cr, cg, cb, alpha);
            }
            if (glow > 0.02f) {
                billboard(vc, mat, p, right, up, sz * (2.4f + 1.8f * (float) glow),
                        cr * 0.7f, cg * 0.85f, cb, alpha * 0.22f * (float) glow);
            }
            prev = p;
        }
    }

    private static void bezier(Soul s, double t, Target tgt, double[] out) {
        double it = 1.0 - t;
        out[0] = it * it * s.sx + 2 * it * t * s.cx + t * t * tgt.x;
        out[1] = it * it * s.sy + 2 * it * t * s.cy + t * t * tgt.y;
        out[2] = it * it * s.sz + 2 * it * t * s.cz + t * t * tgt.z;
    }

    private static void ring(VertexConsumer vc, Matrix4f mat, Ring r, double p,
                             Vector3f right, Vector3f up, float cr, float cg, float cb) {
        float e = (float) (1 - (1 - p) * (1 - p));
        float rad = 0.06f + 0.85f * e;
        float a = (float) ((1 - p) * 0.85);
        Vector3f c = new Vector3f((float) r.x, (float) r.y, (float) r.z);
        Vector3f prev = null;
        for (int i = 0; i <= 26; i++) {
            double ang = i / 26.0 * Math.PI * 2;
            Vector3f pt = new Vector3f(c)
                    .add(new Vector3f(right).mul((float) (Math.cos(ang) * rad)))
                    .add(new Vector3f(up).mul((float) (Math.sin(ang) * rad)));
            if (prev != null) {
                Vector3f dir = new Vector3f(pt).sub(prev);
                Vector3f n = new Vector3f(dir).cross(up).normalize().mul(0.018f);
                if (n.lengthSquared() < 1e-8f) n = new Vector3f(right).mul(0.018f);
                ribbon(vc, mat, prev, pt, n, cr, cg, cb, a);
            }
            prev = pt;
        }
        billboard(vc, mat, c, right, up, 0.14f * (1 - (float) p), cr, cg, cb, a);
    }

    private static void halo(VertexConsumer vc, Matrix4f mat, MinecraftClient mc, long now,
                             Vector3f right, Vector3f up, float cr, float cg, float cb) {
        int n = Math.min(14, harvested);
        double t = now / 1000.0;
        float cx = (float) mc.player.getX(), cy = (float) (mc.player.getY() + 2.25), cz = (float) mc.player.getZ();
        for (int i = 0; i < n; i++) {
            double ang = t * 0.85 + i * Math.PI * 2 / n;
            float wob = (float) Math.sin(t * 2.2 + i) * 0.06f;
            Vector3f p = new Vector3f(cx + (float) Math.cos(ang) * 0.62f, cy + wob, cz + (float) Math.sin(ang) * 0.62f);
            float al = 0.55f + 0.35f * (float) Math.sin(t * 3 + i);
            billboard(vc, mat, p, right, up, 0.062f, cr, cg, cb, al);
            billboard(vc, mat, p, right, up, 0.15f, cr * 0.7f, cg * 0.85f, cb, 0.10f);
        }
    }

    private static void billboard(VertexConsumer vc, Matrix4f mat, Vector3f c, Vector3f r, Vector3f u,
                                  float s, float cr, float cg, float cb, float a) {
        vc.vertex(mat, c.x - r.x * s - u.x * s, c.y - r.y * s - u.y * s, c.z - r.z * s - u.z * s).color(cr, cg, cb, a);
        vc.vertex(mat, c.x + r.x * s - u.x * s, c.y + r.y * s - u.y * s, c.z + r.z * s - u.z * s).color(cr, cg, cb, a);
        vc.vertex(mat, c.x + r.x * s + u.x * s, c.y + r.y * s + u.y * s, c.z + r.z * s + u.z * s).color(cr, cg, cb, a);
        vc.vertex(mat, c.x - r.x * s + u.x * s, c.y - r.y * s + u.y * s, c.z - r.z * s + u.z * s).color(cr, cg, cb, a);
    }

    private static void ribbon(VertexConsumer vc, Matrix4f mat, Vector3f a, Vector3f b, Vector3f n,
                               float cr, float cg, float cb, float al) {
        if (al <= 0.01f) return;
        vc.vertex(mat, a.x - n.x, a.y - n.y, a.z - n.z).color(cr * 0.6f, cg * 0.7f, cb, al * 0.25f);
        vc.vertex(mat, a.x + n.x, a.y + n.y, a.z + n.z).color(cr * 0.6f, cg * 0.7f, cb, al * 0.25f);
        vc.vertex(mat, b.x + n.x, b.y + n.y, b.z + n.z).color(cr, cg, cb, al);
        vc.vertex(mat, b.x - n.x, b.y - n.y, b.z - n.z).color(cr, cg, cb, al);
    }

    private static void drawCounter(DrawContext ctx) {
        try {
            if (VisualsMod.moduleManager == null || !showCounter) return;
            SoulHarvest m = VisualsMod.moduleManager.getModule(SoulHarvest.class);
            MinecraftClient mc = MinecraftClient.getInstance();
            if (m == null || !m.isEnabled() || mc.player == null || mc.options.hudHidden) return;
            if (harvested <= 0) return;
            String text = "ДУШИ: " + harvested;
            long age = System.currentTimeMillis() - lastHarvestAt;
            int a = (int) (255 * Math.min(1.0f, Math.max(0.25f, (90_000 - age) / 3000.0f)));
            int rgb = (Math.min(255, Math.max(0, a)) << 24) | (color & 0xFFFFFF);
            int w = ctx.getScaledWindowWidth();
            ctx.drawTextWithShadow(mc.textRenderer, text, w / 2 - mc.textRenderer.getWidth(text) / 2,
                    ctx.getScaledWindowHeight() / 2 + 40, rgb);
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("SoulHarvest.counter", t);
        }
    }
}

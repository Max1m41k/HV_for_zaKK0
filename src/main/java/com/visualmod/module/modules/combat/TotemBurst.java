package com.visualmod.module.modules.combat;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.util.Diag;
import com.visualmod.util.HudStyle;
import com.visualmod.util.IrisCompat;
import com.visualmod.util.RenderFlush;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * TotemBurst: когда у цели лопает тотем — по её модели расходятся кольцо и осколки.
 * Плюс маленький счётчик сработавших тотемов рядом с TargetHUD.
 */
public class TotemBurst extends Module implements Configurable {

    public static double shards = 14;
    public static double duration = 1.1;
    public static double glow = 0.7;
    public static int color = 0xFFFFD35A;
    public static boolean showRing = true;
    public static boolean showCounter = true;

    private static final class Shard {
        double ox, oy, oz;
        double dx, dy, dz;
        double speed;
        long born;
        float size;
    }

    private static final class Ripple {
        double x, y, z;
        long born;
        float scale;
    }

    private static final List<Shard> SHARDS = new ArrayList<>();
    private static final List<Ripple> RIPPLES = new ArrayList<>();
    private static final Map<UUID, Integer> POPS = new HashMap<>();
    private static final Map<UUID, Long> LAST_POP = new HashMap<>();
    private static final Random RNG = new Random();

    /** Тотем мог сработать через два пути миксина подряд — не считаем его дважды. */
    private static final long DEDUPE_MS = 600;
    private static final int MAX_AGE_MS = 45_000;

    static {
        WorldRenderEvents.AFTER_ENTITIES.register(TotemBurst::render);
    }

    public TotemBurst() {
        super("TotemBurst", "Ring and shards when your target's totem pops", Category.COMBAT);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new SliderSetting("Shards", 4, 28, 1, () -> shards, v -> shards = v, ""));
        l.add(new SliderSetting("Duration", 0.5, 2.5, 0.1, () -> duration, v -> duration = v, "s"));
        l.add(new SliderSetting("Glow", 0, 1, 0.05, () -> glow, v -> glow = v, ""));
        l.add(new ColorSetting("Color", () -> color, c -> color = c));
        l.add(new ModeSetting("Ring", new String[]{"OFF", "ON"}, () -> showRing ? 1 : 0, i -> showRing = (i == 1)));
        l.add(new ModeSetting("Counter", new String[]{"OFF", "ON"}, () -> showCounter ? 1 : 0, i -> showCounter = (i == 1)));
        return l;
    }

    @Override
    public void onDisable() {
        SHARDS.clear();
        RIPPLES.clear();
        POPS.clear();
        LAST_POP.clear();
    }

    public static void onTotemPop(LivingEntity e) {
        try {
            if (e == null || VisualsMod.moduleManager == null || MinecraftClient.getInstance().world == null) return;
            TotemBurst m = VisualsMod.moduleManager.getModule(TotemBurst.class);
            if (m == null || !m.isEnabled()) return;

            UUID id = e.getUuid();
            long now = System.currentTimeMillis();
            Long prev = LAST_POP.get(id);
            if (prev != null && now - prev < DEDUPE_MS) return;

            POPS.merge(id, 1, Integer::sum);
            LAST_POP.put(id, now);
            trim(now);

            double x = e.getX(), y = e.getEyeY() - 0.55, z = e.getZ();
            float body = Math.max(0.4f, e.getWidth());
            RNG.setSeed(id.getLeastSignificantBits() ^ now);
            int n = (int) Math.max(1, Math.min(40, shards));
            synchronized (SHARDS) {
                for (int i = 0; i < n; i++) {
                    Shard s = new Shard();
                    double ang = RNG.nextDouble() * Math.PI * 2;
                    double up = 0.25 + RNG.nextDouble() * 1.25;
                    Vector3f d = new Vector3f((float) Math.cos(ang), (float) up, (float) Math.sin(ang)).normalize();
                    s.ox = x + RNG.nextFloat() * 0.2f * d.x;
                    s.oy = y + (RNG.nextDouble() - 0.5) * body * 0.8;
                    s.oz = z + RNG.nextFloat() * 0.2f * d.z;
                    s.dx = d.x; s.dy = d.y; s.dz = d.z;
                    s.speed = 1.6 + RNG.nextDouble() * 2.4;
                    s.born = now + (long) (RNG.nextDouble() * 70);
                    s.size = 0.75f + RNG.nextFloat() * 0.6f;
                    SHARDS.add(s);
                }
                while (SHARDS.size() > 320) SHARDS.remove(0);
            }
            if (showRing) {
                synchronized (RIPPLES) {
                    Ripple r = new Ripple();
                    r.x = x; r.y = y; r.z = z; r.born = now; r.scale = body;
                    RIPPLES.add(r);
                    Ripple f = new Ripple();
                    f.x = x; f.y = e.getY() + 0.08; f.z = z; f.born = now + 90; f.scale = body * 1.15f;
                    RIPPLES.add(f);
                    while (RIPPLES.size() > 40) RIPPLES.remove(0);
                }
            }
        } catch (Throwable t) {
            Diag.once("TotemBurst$spawn", t);
        }
    }

    private static void trim(long now) {
        Iterator<Map.Entry<UUID, Long>> it = LAST_POP.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> en = it.next();
            if (now - en.getValue() > MAX_AGE_MS) {
                POPS.remove(en.getKey());
                it.remove();
            }
        }
    }

    /** Плашка со счётчиком тотемов; рисуется из TargetHUD, поэтому попадает под его масштаб. */
    public static void drawChip(DrawContext ctx, int x, int y, int w, UUID id) {
        try {
            TotemBurst m = VisualsMod.moduleManager == null ? null : VisualsMod.moduleManager.getModule(TotemBurst.class);
            if (m == null || !m.isEnabled() || !showCounter) return;
            Integer n = POPS.get(id);
            Long last = LAST_POP.get(id);
            if (n == null || last == null) return;
            long age = System.currentTimeMillis() - last;
            if (age > MAX_AGE_MS) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.textRenderer == null) return;
            String text = "TOTEM " + n;
            int tw = mc.textRenderer.getWidth(text);
            int pw = tw + 12, ph = 13;
            int px = x + w + 3, py = y + 4;
            int sw = mc.getWindow().getScaledWidth();
            if (px + pw > sw - 2) px = x - pw - 3;

            float a = age > MAX_AGE_MS - 6_000 ? (MAX_AGE_MS - age) / 6_000f : 1f;
            int base = color & 0xFFFFFF;
            int al = (int) (210 * Math.max(0f, Math.min(1f, a))) << 24;
            HudStyle.fillRounded(ctx, px, py, pw, ph, 4, al | base);
            ctx.drawTextWithShadow(mc.textRenderer, text, px + 6, py + 3, 0xFF14120A);
        } catch (Throwable t) {
            Diag.once("TotemBurst$chip", t);
        }
    }

    private static void render(WorldRenderContext ctx) {
        try {
            if (IrisCompat.skipWorldPass()) return;
            if (VisualsMod.moduleManager == null || ctx.matrices() == null || ctx.consumers() == null) return;
            TotemBurst m = VisualsMod.moduleManager.getModule(TotemBurst.class);
            if (m == null || !m.isEnabled()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null || mc.player == null) return;

            long now = System.currentTimeMillis();
            double secs = Math.max(0.4, duration) * 1000.0;
            Camera camera = ctx.gameRenderer().getCamera();
            net.minecraft.util.math.Vec3d cp = camera.getCameraPos();
            boolean pushed = false;
            try {
                ctx.matrices().push();
                pushed = true;
                Vector3f cam = new Vector3f((float) cp.x, (float) cp.y, (float) cp.z);
                ctx.matrices().translate(-cam.x, -cam.y, -cam.z);
                Matrix4f mat = ctx.matrices().peek().getPositionMatrix();
                VertexConsumer vc = ctx.consumers().getBuffer(RenderLayers.debugQuads());

                Vector3f right = new Vector3f(1, 0, 0).rotate(camera.getRotation());
                Vector3f up = new Vector3f(0, 1, 0).rotate(camera.getRotation());

                float cr = ((color >> 16) & 0xFF) / 255f, cg = ((color >> 8) & 0xFF) / 255f, cb = (color & 0xFF) / 255f;

                synchronized (SHARDS) {
                    for (Iterator<Shard> it = SHARDS.iterator(); it.hasNext(); ) {
                        Shard s = it.next();
                        double t = (now - s.born) / secs;
                        if (t < 0) continue;
                        if (t >= 1.0) { it.remove(); continue; }
                        double ease = 1.0 - (1.0 - t) * (1.0 - t);
                        float px = (float) (s.ox + s.dx * s.speed * ease);
                        float py = (float) (s.oy + s.dy * s.speed * ease - 4.2 * t * t);
                        float pz = (float) (s.oz + s.dz * s.speed * ease);
                        float sz = (float) (0.13 * s.size * (1.0 - t * 0.75));
                        Vector3f p = new Vector3f(px, py, pz);
                        billboard(vc, mat, p, right, up, sz, cr, cg, cb, (float) (1.0 - t));
                        if (glow > 0.02f)
                            billboard(vc, mat, p, right, up, sz * (2.6f + 2.4f * (float) glow),
                                    cr, cg * 0.9f, cb * 0.55f, (float) ((1.0 - t) * 0.18 * glow));
                    }
                }

                synchronized (RIPPLES) {
                    for (Iterator<Ripple> it = RIPPLES.iterator(); it.hasNext(); ) {
                        Ripple r = it.next();
                        double t = (now - r.born) / (secs * 0.62);
                        if (t < 0) continue;
                        if (t >= 1.0) { it.remove(); continue; }
                        ripple(vc, mat, r, t, cam, cr, cg, cb);
                    }
                }

                RenderFlush.flush(ctx.consumers(), RenderLayers.debugQuads());
            } finally {
                // Без pop() в любом выходе стек матриц разъезжается и рендер мира падает ниже по потоку.
                if (pushed) ctx.matrices().pop();
            }
        } catch (Throwable t) {
            Diag.once("TotemBurst", t);
        }
    }

    /** Плоское кольцо в горизонтальной плоскости — как стандартный залп тотема. */
    private static void ripple(VertexConsumer vc, Matrix4f mat, Ripple r, double t, Vector3f cam,
                               float cr, float cg, float cb) {
        float e = (float) (1 - (1 - t) * (1 - t));
        float rad = (0.25f + 2.15f * e) * Math.max(0.5f, r.scale);
        float a = (float) ((1 - t) * 0.85);
        float cx = (float) r.x, cy = (float) r.y, cz = (float) r.z;
        float tilt = (float) Math.atan2(cy - cam.y, Math.max(0.001f,
                (float) Math.hypot(cx - cam.x, cz - cam.z))) * 0.35f;
        float yUp = (float) Math.cos(tilt), zForward = (float) Math.sin(tilt);
        Vector3f prev = null;
        for (int i = 0; i <= 30; i++) {
            double ang = i / 30.0 * Math.PI * 2 + t * 1.6;
            Vector3f pt = new Vector3f(cx + (float) Math.cos(ang) * rad,
                    cy + yUp * (float) Math.sin(ang) * rad * 0.32f,
                    cz + zForward * (float) Math.sin(ang) * rad);
            if (prev != null) {
                Vector3f dir = new Vector3f(pt).sub(prev);
                Vector3f n = new Vector3f(dir).cross(new Vector3f(0, 1, 0));
                if (n.lengthSquared() < 1e-8f) n = new Vector3f(0.02f, 0, 0);
                n.normalize().mul(0.022f);
                ribbon(vc, mat, prev, pt, n, cr, cg, cb, a);
            }
            prev = pt;
        }
        billboard(vc, mat, new Vector3f(cx, cy, cz), new Vector3f(1, 0, 0), new Vector3f(0, 1, 0),
                0.22f * (1 - (float) t), cr, 1f, cb * 0.6f, a * 0.75f);
    }

    private static void billboard(VertexConsumer vc, Matrix4f mat, Vector3f c, Vector3f r, Vector3f u,
                                  float s, float cr, float cg, float cb, float a) {
        if (a <= 0.01f || s <= 0.001f) return;
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
}

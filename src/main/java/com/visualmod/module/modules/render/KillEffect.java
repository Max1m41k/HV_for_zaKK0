package com.visualmod.module.modules.render;

import net.minecraft.client.render.RenderLayers;
import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class KillEffect extends Module implements Configurable {

    public static int mode = 0;
    public static int slash = 0;
    public static boolean onlyMine = true;
    public static double duration = 2.0;
    public static int waveColor = 0xFF4FE0C0;

    private static final float PX = 1.8f / 32f;
    private static final Identifier STEVE = Identifier.of("minecraft", "textures/entity/player/wide/steve.png");

    private static final float[][] PARTS = {
            {0, 28, 0, 8, 8, 8, 0, 0},
            {0, 18, 0, 8, 12, 4, 16, 16},
            {6, 18, 0, 4, 12, 4, 40, 16},
            {-6, 18, 0, 4, 12, 4, 32, 48},
            {2, 6, 0, 4, 12, 4, 0, 16},
            {-2, 6, 0, 4, 12, 4, 16, 48}
    };

    private static final class Fx { UUID id; double x, y, z; float yaw; Identifier skin; long t0; int mode, slash; long seed; }
    private static final class Piece {
        final Quaternionf rot = new Quaternionf(); final Vector3f pivot = new Vector3f(), offset = new Vector3f(); float scale = 1f;
    }

    private static final List<Fx> FX = new ArrayList<>();
    private static boolean flatPass = false;
    private static final Set<UUID> HIDDEN = new HashSet<>(), HANDLED = new HashSet<>();
    public static volatile Entity lastTarget;
    public static volatile long lastTargetTime;

    static { WorldRenderEvents.AFTER_ENTITIES.register(KillEffect::render); }

    public KillEffect() { super("KillEffect", "Ghost / die / portal / slash / square-wave on kills", Category.RENDER); }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Mode", new String[]{"GHOST", "DIE", "PORTAL", "SLASH", "SQUARE"}, () -> mode, i -> mode = i));
        if (mode == 3) l.add(new ModeSetting("Slash", new String[]{"HORIZONTAL", "VERTICAL", "CURVE"}, () -> slash, i -> slash = i));
        l.add(new ModeSetting("Only My Kills", new String[]{"OFF", "ON"}, () -> onlyMine ? 1 : 0, i -> onlyMine = (i == 1)));
        l.add(new SliderSetting("Duration", 1.0, 4.0, 0.5, () -> duration, v -> duration = v, "s"));
        if (mode == 4) l.add(new ColorSetting("Wave Color", () -> waveColor, c -> waveColor = c));
        return l;
    }

    @Override public void onDisable() {
        synchronized (FX) { FX.clear(); }
        HIDDEN.clear(); HANDLED.clear();
        SquareWave.stop();
    }

    public static boolean hides(Entity e) {
        if (HIDDEN.isEmpty() || e == null || VisualsMod.moduleManager == null) return false;
        KillEffect m = VisualsMod.moduleManager.getModule(KillEffect.class);
        return m != null && m.isEnabled() && HIDDEN.contains(e.getUuid());
    }

    public static void onAttack(Entity target) { lastTarget = target; lastTargetTime = System.currentTimeMillis(); }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        Set<UUID> present = new HashSet<>();
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            present.add(p.getUuid());
            if (p.isDead()) {
                if (!HANDLED.add(p.getUuid())) continue;
                boolean mine = lastTarget == p && System.currentTimeMillis() - lastTargetTime < 8000;
                if (onlyMine && !mine) continue;
                if (mode == 4) { SquareWave.trigger(p.getX(), p.getY(), p.getZ(), waveColor); continue; }
                Fx fx = new Fx();
                fx.id = p.getUuid(); fx.x = p.getX(); fx.y = p.getY(); fx.z = p.getZ(); fx.yaw = p.bodyYaw;
                try { fx.skin = p.getSkin().body().texturePath(); } catch (Throwable t) { fx.skin = STEVE; }
                if (fx.skin == null) fx.skin = STEVE;
                fx.t0 = System.currentTimeMillis(); fx.mode = mode; fx.slash = slash; fx.seed = p.getUuid().getLeastSignificantBits();
                synchronized (FX) { FX.add(fx); }
                HIDDEN.add(p.getUuid());
            } else { HANDLED.remove(p.getUuid()); HIDDEN.remove(p.getUuid()); }
        }
        HANDLED.retainAll(present); HIDDEN.retainAll(present);
    }

    private static void render(WorldRenderContext ctx) {
        try {
            if (com.visualmod.util.IrisCompat.skipWorldPass()) return;
            if (VisualsMod.moduleManager == null) return;
            KillEffect m = VisualsMod.moduleManager.getModule(KillEffect.class);
            if (m == null || !m.isEnabled() || ctx.matrices() == null || ctx.consumers() == null) return;
            synchronized (FX) { if (FX.isEmpty()) return; }
            long now = System.currentTimeMillis();
            Vec3d cam = ctx.gameRenderer().getCamera().getCameraPos();
            ctx.matrices().push();
            ctx.matrices().translate(-cam.x, -cam.y, -cam.z);
            Matrix4f mat = ctx.matrices().peek().getPositionMatrix();
            List<Fx> alive = new ArrayList<>();
            synchronized (FX) {
                flatPass = false;
                for (Iterator<Fx> it = FX.iterator(); it.hasNext(); ) {
                    Fx fx = it.next();
                    double T = duration, t = (now - fx.t0) / 1000.0;
                    if (t >= T) { it.remove(); continue; }
                    alive.add(fx);
                    VertexConsumer tex = ctx.consumers().getBuffer(RenderLayers.entityTranslucent(fx.skin));
                    dispatch(tex, null, mat, ctx, fx, t, T);
                }
                boolean needFlat = false;
                for (Fx fx : alive) if (fx.mode >= 2) needFlat = true;
                if (needFlat) {
                    flatPass = true;
                    VertexConsumer flat = ctx.consumers().getBuffer(RenderLayers.debugQuads());
                    for (Fx fx : alive) if (fx.mode >= 2) { double T = duration, t = (now - fx.t0) / 1000.0; dispatch(null, flat, mat, ctx, fx, t, T); }
                    flatPass = false;
                }
            }
            ctx.matrices().pop();
        } catch (Throwable t) { com.visualmod.util.Diag.once("KillEffect", t); }
    }

    private static void dispatch(VertexConsumer tex, VertexConsumer flat, Matrix4f mat, WorldRenderContext ctx, Fx fx, double t, double T) {
        switch (fx.mode) {
            case 0 -> ghost(tex, mat, fx, t / T);
            case 1 -> die(tex, mat, fx, t, T);
            case 2 -> portal(tex, flat, mat, ctx, fx, t / T);
            default -> slash(tex, flat, mat, ctx, fx, t / T);
        }
    }

    private static double ease(double x) { x = Math.max(0, Math.min(1, x)); return 1 - (1 - x) * (1 - x) * (1 - x); }
    private static double clamp01(double x) { return Math.max(0, Math.min(1, x)); }

    private static void ghost(VertexConsumer tex, Matrix4f mat, Fx fx, double p) {
        if (flatPass) return;
        Piece pc = new Piece();
        pc.offset.set(0, (float) (ease(p) * 22.0), 0);
        pc.pivot.set(0, 16, 0);
        pc.rot.rotationY((float) (Math.sin(p * 6) * 0.12));
        float a = (float) (0.55 * (1 - p * p));
        for (int i = 0; i < PARTS.length; i++) sub(tex, null, mat, fx, i, 0, 1, 0, 1, 0, 1, pc, 0.75f, 0.88f, 1f, a);
    }

    private static void die(VertexConsumer tex, Matrix4f mat, Fx fx, double t, double T) {
        if (flatPass) return;
        float alpha = (float) (1.0 - clamp01((t / T - 0.7) / 0.3));
        for (int i = 0; i < PARTS.length; i++) {
            Random r = new Random(fx.seed * 31 + i * 977L);
            float sx = PARTS[i][0] == 0 ? (r.nextFloat() - 0.5f) : Math.signum(PARTS[i][0]);
            float vx = (sx * 14 + (r.nextFloat() - 0.5f) * 10) * 0.9f, vz = (r.nextFloat() - 0.5f) * 22, vy = 26 + r.nextFloat() * 20;
            float wx = (r.nextFloat() - 0.5f) * 11, wz = (r.nextFloat() - 0.5f) * 11, wy = (r.nextFloat() - 0.5f) * 8;
            float x = 0, y = 0, z = 0, ax = 0, ay = 0, az = 0, cy = PARTS[i][1], half = Math.max(PARTS[i][3], Math.max(PARTS[i][4], PARTS[i][5])) * 0.5f;
            boolean landed = false;
            for (double s = 0; s < t; s += 1.0 / 60) {
                if (!landed) {
                    vy -= 160f / 60f; x += vx / 60f; y += vy / 60f; z += vz / 60f; ax += wx / 60f; ay += wy / 60f; az += wz / 60f;
                    if (cy + y < half) { y = half - cy; vy = -vy * 0.3f; vx *= 0.5f; vz *= 0.5f; wx *= 0.4f; wz *= 0.4f; wy *= 0.4f; if (Math.abs(vy) < 6) landed = true; }
                }
            }
            Piece pc = new Piece();
            pc.pivot.set(PARTS[i][0], PARTS[i][1], PARTS[i][2]);
            pc.offset.set(x, y, z);
            pc.rot.rotationXYZ(ax, ay, az);
            sub(tex, null, mat, fx, i, 0, 1, 0, 1, 0, 1, pc, 1f, 1f, 1f, alpha);
        }
    }

    private static void portal(VertexConsumer tex, VertexConsumer flat, Matrix4f mat, WorldRenderContext ctx, Fx fx, double p) {
        float[] portal = {0, 18, -8};
        double[] delay = {0.0, 0.18, 0.28, 0.28, 0.4, 0.4};
        double open = ease(p / 0.18) * (1 - clamp01((p - 0.82) / 0.18));
        for (int i = 0; i < PARTS.length; i++) {
            double q = clamp01((p - delay[i]) / 0.5), e = q * q;
            Piece pc = new Piece();
            float cx = PARTS[i][0], cy = PARTS[i][1], cz = PARTS[i][2];
            float dx = (cx - portal[0]) * (float) (1 - e), dy = (cy - portal[1]) * (float) (1 - e), dz = (cz - portal[2]) * (float) (1 - e);
            double th = 5.0 * q, c = Math.cos(th), s = Math.sin(th);
            float rx = (float) (dx * c - dz * s), rz = (float) (dx * s + dz * c);
            pc.pivot.set(cx, cy, cz);
            pc.offset.set(portal[0] + rx - cx, portal[1] + dy - cy, portal[2] + rz - cz);
            pc.rot.rotationY((float) (7 * q));
            pc.scale = (float) Math.max(0.02, 1 - Math.pow(q, 1.4));
            float a = (float) (1 - clamp01((q - 0.8) / 0.2));
            if (!flatPass && q < 1) sub(tex, null, mat, fx, i, 0, 1, 0, 1, 0, 1, pc, 1f, 1f, 1f, a);
        }
        if (flatPass && open > 0.02) drawVortex(flat, mat, ctx, fx, portal, open);
    }

    private static void drawVortex(VertexConsumer buf, Matrix4f mat, WorldRenderContext ctx, Fx fx, float[] portalPx, double open) {
        Vector3f c = world(fx, new Vector3f(portalPx[0], portalPx[1], portalPx[2]));
        var rotQ = ctx.gameRenderer().getCamera().getRotation();
        Vector3f right = new Vector3f(1, 0, 0).rotate(rotQ), up = new Vector3f(0, 1, 0).rotate(rotQ);
        double time = System.currentTimeMillis() / 1000.0;
        float R = (float) (0.95 * open);
        for (int arm = 0; arm < 3; arm++) {
            for (int i = 0; i < 28; i++) {
                double ang = arm * Math.PI * 2 / 3 + i * 0.2 + time * 6, rad = R * (0.08 + i / 28.0 * 0.92), sz = 0.05 + 0.03 * (i / 28.0);
                float px = c.x + (float) (Math.cos(ang) * rad) * right.x + (float) (Math.sin(ang) * rad) * up.x;
                float py = c.y + (float) (Math.cos(ang) * rad) * right.y + (float) (Math.sin(ang) * rad) * up.y;
                float pz = c.z + (float) (Math.cos(ang) * rad) * right.z + (float) (Math.sin(ang) * rad) * up.z;
                float t = i / 28f, r = 0.55f - 0.25f * t, g = 0.25f + 0.2f * t, b = 1f;
                float a = (float) (0.9 * (1 - t * 0.5) * open);
                billboard(buf, mat, px, py, pz, right, up, (float) sz, r, g, b, a);
            }
        }
        billboard(buf, mat, c.x, c.y, c.z, right, up, 0.16f * (float) open, 0.9f, 0.8f, 1f, 0.9f);
    }

    private static void billboard(VertexConsumer buf, Matrix4f mat, float x, float y, float z, Vector3f r, Vector3f u, float s, float cr, float cg, float cb, float a) {
        buf.vertex(mat, x - r.x * s - u.x * s, y - r.y * s - u.y * s, z - r.z * s - u.z * s).color(cr, cg, cb, a);
        buf.vertex(mat, x + r.x * s - u.x * s, y + r.y * s - u.y * s, z + r.z * s - u.z * s).color(cr, cg, cb, a);
        buf.vertex(mat, x + r.x * s + u.x * s, y + r.y * s + u.y * s, z + r.z * s + u.z * s).color(cr, cg, cb, a);
        buf.vertex(mat, x - r.x * s + u.x * s, y - r.y * s + u.y * s, z - r.z * s + u.z * s).color(cr, cg, cb, a);
    }

    private static final float CUT_Y = 17f;

    private static float cutY(int type, float x) {
        return type == 2 ? CUT_Y + 4.5f * (float) Math.sin(x * Math.PI / 8.0) : CUT_Y;
    }

    private static void slash(VertexConsumer tex, VertexConsumer flat, Matrix4f mat, WorldRenderContext ctx, Fx fx, double p) {
        double hold = 0.16;
        double e = ease((p - hold) / (1 - hold));
        float alpha = (float) (1.0 - clamp01((p - 0.7) / 0.3));
        Piece up = new Piece(), dn = new Piece();
        up.pivot.set(0, CUT_Y, 0); dn.pivot.set(0, CUT_Y, 0);
        switch (fx.slash) {
            case 0 -> {
                up.offset.set(0, (float) (e * 10), (float) (-e * 7)); up.rot.rotationX((float) (-e * 0.8));
                dn.offset.set(0, (float) (-e * 1), (float) (e * 5)); dn.rot.rotationX((float) (e * 0.35));
            }
            case 1 -> {
                up.offset.set((float) (e * 12), (float) (e * 2), 0); up.rot.rotationZ((float) (-e * 0.55));
                dn.offset.set((float) (-e * 12), (float) (e * 2), 0); dn.rot.rotationZ((float) (e * 0.55));
            }
            default -> {
                up.offset.set((float) (-e * 7), (float) (e * 9), 0); up.rot.rotationZ((float) (e * 0.6));
                dn.offset.set((float) (e * 7), (float) (-e * 1), (float) (e * 3)); dn.rot.rotationZ((float) (-e * 0.4));
            }
        }
        if (fx.slash == 1) {
            up.pivot.set(0, 16, 0); dn.pivot.set(0, 16, 0);
        }
        for (int i = 0; i < PARTS.length; i++) {
            float cx = PARTS[i][0], cy = PARTS[i][1], w = PARTS[i][3], h = PARTS[i][4];
            if (fx.slash == 1) {
                if (cx > 0.5f) sub(tex, flat, mat, fx, i, 0, 1, 0, 1, 0, 1, up, 1, 1, 1, alpha);
                else if (cx < -0.5f) sub(tex, flat, mat, fx, i, 0, 1, 0, 1, 0, 1, dn, 1, 1, 1, alpha);
                else { sub(tex, flat, mat, fx, i, 0.5, 1, 0, 1, 0, 1, up, 1, 1, 1, alpha); sub(tex, flat, mat, fx, i, 0, 0.5, 0, 1, 0, 1, dn, 1, 1, 1, alpha); }
                continue;
            }
            float y0 = cy - h / 2, y1 = cy + h / 2;
            int strips = fx.slash == 2 ? Math.max(2, (int) w) : 1;
            for (int s = 0; s < strips; s++) {
                double f0 = s / (double) strips, f1 = (s + 1) / (double) strips;
                float xm = cx + (float) ((f0 + f1) / 2 - 0.5) * w;
                float yb = cutY(fx.slash, xm);
                if (yb <= y0) sub(tex, flat, mat, fx, i, f0, f1, 0, 1, 0, 1, up, 1, 1, 1, alpha);
                else if (yb >= y1) sub(tex, flat, mat, fx, i, f0, f1, 0, 1, 0, 1, dn, 1, 1, 1, alpha);
                else {
                    double fy = (yb - y0) / h;
                    sub(tex, flat, mat, fx, i, f0, f1, 0, fy, 0, 1, dn, 1, 1, 1, alpha);
                    sub(tex, flat, mat, fx, i, f0, f1, fy, 1, 0, 1, up, 1, 1, 1, alpha);
                }
            }
        }
        double flash = p < hold + 0.12 ? 1 - Math.abs(p - hold * 0.6) / (hold * 0.6 + 0.12) : 0;
        if (flatPass && flash > 0.02) cutLine(flat, mat, ctx, fx, (float) Math.max(0, flash));
    }

    private static void cutLine(VertexConsumer buf, Matrix4f mat, WorldRenderContext ctx, Fx fx, float a) {
        int N = 16;
        Vec3d cam = ctx.gameRenderer().getCamera().getCameraPos();
        Vector3f prev = null;
        for (int i = 0; i <= N; i++) {
            float t = i / (float) N;
            Vector3f p;
            if (fx.slash == 0) p = new Vector3f(-9 + 18 * t, CUT_Y, 0);
            else if (fx.slash == 1) p = new Vector3f(0, 2 + 32 * t, 0);
            else { float x = -9 + 18 * t; p = new Vector3f(x, cutY(2, x), 0); }
            p = world(fx, p);
            if (prev != null) {
                Vector3f dir = new Vector3f(p).sub(prev);
                Vector3f view = new Vector3f(prev.x - (float) cam.x, prev.y - (float) cam.y, prev.z - (float) cam.z);
                Vector3f n = new Vector3f(dir).cross(view).normalize().mul(0.045f);
                quad(buf, mat, prev, p, n, a);
            }
            prev = p;
        }
    }

    private static void quad(VertexConsumer buf, Matrix4f mat, Vector3f a, Vector3f b, Vector3f n, float al) {
        buf.vertex(mat, a.x - n.x, a.y - n.y, a.z - n.z).color(1f, 1f, 1f, al);
        buf.vertex(mat, a.x + n.x, a.y + n.y, a.z + n.z).color(0.8f, 0.9f, 1f, al);
        buf.vertex(mat, b.x + n.x, b.y + n.y, b.z + n.z).color(0.8f, 0.9f, 1f, al);
        buf.vertex(mat, b.x - n.x, b.y - n.y, b.z - n.z).color(1f, 1f, 1f, al);
    }

    private static Vector3f world(Fx fx, Vector3f v) {
        double yaw = Math.toRadians(fx.yaw);
        double fxv = -Math.sin(yaw), fzv = Math.cos(yaw), rxv = -Math.cos(yaw), rzv = -Math.sin(yaw);
        return new Vector3f((float) (fx.x + (rxv * v.x + fxv * v.z) * PX), (float) (fx.y + v.y * PX), (float) (fx.z + (rzv * v.x + fzv * v.z) * PX));
    }

    private static Vector3f pt(Fx fx, int part, double fxr, double fyr, double fzr, Piece pc) {
        float[] P = PARTS[part];
        Vector3f v = new Vector3f(P[0] + (float) ((fxr - 0.5) * P[3]), P[1] + (float) ((fyr - 0.5) * P[4]), P[2] + (float) ((fzr - 0.5) * P[5]));
        v.sub(pc.pivot).mul(pc.scale);
        pc.rot.transform(v);
        v.add(pc.pivot).add(pc.offset);
        return world(fx, v);
    }

    private static void sub(VertexConsumer tex, VertexConsumer flat, Matrix4f mat, Fx fx, int part,
                            double x0, double x1, double y0, double y1, double z0, double z1, Piece pc,
                            float cr, float cg, float cb, float alpha) {
        if (alpha <= 0.01f) return;
        float[] P = PARTS[part]; float w = P[3], h = P[4], d = P[5], u = P[6], v = P[7];
        int col = ((int) (alpha * 255) << 24) | ((int) (cr * 255) << 16) | ((int) (cg * 255) << 8) | (int) (cb * 255);
        final double E = 1e-6;
        Vector3f center = pt(fx, part, 0.5, 0.5, 0.5, pc);
        face(tex, flat, mat, center, pt(fx, part, x1, y0, z1, pc), pt(fx, part, x0, y0, z1, pc), pt(fx, part, x0, y1, z1, pc), pt(fx, part, x1, y1, z1, pc),
                z1 >= 1 - E, new float[]{u + d + (float) (1 - x1) * w, v + d + (float) (1 - y0) * h, u + d + (float) (1 - x0) * w, v + d + (float) (1 - y0) * h, u + d + (float) (1 - x0) * w, v + d + (float) (1 - y1) * h, u + d + (float) (1 - x1) * w, v + d + (float) (1 - y1) * h}, col, alpha);
        face(tex, flat, mat, center, pt(fx, part, x0, y0, z0, pc), pt(fx, part, x1, y0, z0, pc), pt(fx, part, x1, y1, z0, pc), pt(fx, part, x0, y1, z0, pc),
                z0 <= E, new float[]{u + 2 * d + w + (float) x0 * w, v + d + (float) (1 - y0) * h, u + 2 * d + w + (float) x1 * w, v + d + (float) (1 - y0) * h, u + 2 * d + w + (float) x1 * w, v + d + (float) (1 - y1) * h, u + 2 * d + w + (float) x0 * w, v + d + (float) (1 - y1) * h}, col, alpha);
        face(tex, flat, mat, center, pt(fx, part, x1, y0, z0, pc), pt(fx, part, x1, y0, z1, pc), pt(fx, part, x1, y1, z1, pc), pt(fx, part, x1, y1, z0, pc),
                x1 >= 1 - E, new float[]{u + (float) z0 * d, v + d + (float) (1 - y0) * h, u + (float) z1 * d, v + d + (float) (1 - y0) * h, u + (float) z1 * d, v + d + (float) (1 - y1) * h, u + (float) z0 * d, v + d + (float) (1 - y1) * h}, col, alpha);
        face(tex, flat, mat, center, pt(fx, part, x0, y0, z1, pc), pt(fx, part, x0, y0, z0, pc), pt(fx, part, x0, y1, z0, pc), pt(fx, part, x0, y1, z1, pc),
                x0 <= E, new float[]{u + d + w + (float) (1 - z1) * d, v + d + (float) (1 - y0) * h, u + d + w + (float) (1 - z0) * d, v + d + (float) (1 - y0) * h, u + d + w + (float) (1 - z0) * d, v + d + (float) (1 - y1) * h, u + d + w + (float) (1 - z1) * d, v + d + (float) (1 - y1) * h}, col, alpha);
        face(tex, flat, mat, center, pt(fx, part, x1, y1, z0, pc), pt(fx, part, x0, y1, z0, pc), pt(fx, part, x0, y1, z1, pc), pt(fx, part, x1, y1, z1, pc),
                y1 >= 1 - E, new float[]{u + d + (float) (1 - x1) * w, v + (float) z0 * d, u + d + (float) (1 - x0) * w, v + (float) z0 * d, u + d + (float) (1 - x0) * w, v + (float) z1 * d, u + d + (float) (1 - x1) * w, v + (float) z1 * d}, col, alpha);
        face(tex, flat, mat, center, pt(fx, part, x1, y0, z1, pc), pt(fx, part, x0, y0, z1, pc), pt(fx, part, x0, y0, z0, pc), pt(fx, part, x1, y0, z0, pc),
                y0 <= E, new float[]{u + d + w + (float) (1 - x1) * w, v + (float) (1 - z1) * d, u + d + w + (float) (1 - x0) * w, v + (float) (1 - z1) * d, u + d + w + (float) (1 - x0) * w, v + (float) (1 - z0) * d, u + d + w + (float) (1 - x1) * w, v + (float) (1 - z0) * d}, col, alpha);
    }

    private static void face(VertexConsumer tex, VertexConsumer flat, Matrix4f mat, Vector3f center,
                             Vector3f a, Vector3f b, Vector3f c, Vector3f d, boolean outer, float[] uv, int argb, float alpha) {
        Vector3f n = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
        Vector3f mid = new Vector3f(a).add(c).mul(0.5f).sub(center);
        boolean flip = n.dot(mid) < 0;
        Vector3f[] p = flip ? new Vector3f[]{d, c, b, a} : new Vector3f[]{a, b, c, d};
        float[][] t = {{uv[0], uv[1]}, {uv[2], uv[3]}, {uv[4], uv[5]}, {uv[6], uv[7]}};
        if (flip) t = new float[][]{t[3], t[2], t[1], t[0]};
        if (outer) {
            if (!flatPass && tex != null) for (int i = 0; i < 4; i++)
                tex.vertex(mat, p[i].x, p[i].y, p[i].z).color(argb).texture(t[i][0] / 64f, t[i][1] / 64f)
                        .overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0, 1, 0);
        } else if (flatPass && flat != null) {
            for (int i = 0; i < 4; i++) flat.vertex(mat, p[i].x, p[i].y, p[i].z).color(1f, 0.95f, 0.85f, Math.min(1f, alpha * 0.95f));
        }
    }
}

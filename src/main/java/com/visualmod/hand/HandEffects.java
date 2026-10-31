package com.visualmod.hand;

import net.minecraft.client.render.RenderLayers;
import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.modules.combat.HitSplash;
import com.visualmod.module.modules.combat.SwordAura;
import com.visualmod.util.SlashSprite;
import com.visualmod.viewmodel.ViewModelStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayDeque;
import java.util.List;

public final class HandEffects {

    private HandEffects() {}

    private static final class Seg {
        final float gx, gy, gz, tx, ty, tz;
        final long t;
        Seg(float gx, float gy, float gz, float tx, float ty, float tz, long t) {
            this.gx = gx; this.gy = gy; this.gz = gz;
            this.tx = tx; this.ty = ty; this.tz = tz;
            this.t = t;
        }
    }

    private static final Matrix4f ROOT = new Matrix4f();
    private static final Matrix4f REL = new Matrix4f();
    private static final Matrix4f INV_ROOT = new Matrix4f();
    private static final Vector3f TMP = new Vector3f();
    private static final ArrayDeque<Seg> TRAIL = new ArrayDeque<>();

    private static boolean rootOk = false;
    private static boolean relOk = false;
    private static int armSign = 1;
    private static long lastSampleMs = 0;

    public static void captureRoot(MatrixStack ms, int sign) {
        ROOT.set(ms.peek().getPositionMatrix());
        armSign = sign;
        rootOk = true;
        relOk = false;
    }

    public static void captureItem(MatrixStack ms) {
        if (!rootOk) return;
        INV_ROOT.set(ROOT).invert();
        REL.set(ms.peek().getPositionMatrix()).mul(INV_ROOT);
        relOk = true;
    }

    /**
     * Кадр самой модели предмета (матрица слоя: рука + view-model + display-трансформация - 0.5).
     * Вооружается на рендер основной руки и снимается после первого же слоя, чтобы вторая рука или
     * иконки инвентаря не подменили собой клинок.
     */
    private static final Matrix4f MODEL = new Matrix4f();
    private static final Matrix4f M2R = new Matrix4f();
    private static final Vector3f BASE = new Vector3f();
    private static final Vector3f TIP = new Vector3f();
    private static boolean modelArmed = false;
    private static boolean modelOk = false;
    private static float modelLen = 0f;
    /** Якорь по умолчанию: сдвиг ползунков Guard добавляется к реальному хвату, а не заменяет его. */
    private static final float DEF_AX = 0.5f, DEF_AY = 0.62f, DEF_AZ = 0f;

    public static void armModelCapture(boolean on) {
        modelArmed = on;
        if (!on) modelOk = false;
    }

    /** Вызывается из миксина слоя предмета, пока матричный стек стоит в пространстве квадов. */
    public static void captureModelFrame(Matrix4f layerMat, List<BakedQuad> quads) {
        modelOk = false;
        if (!modelArmed || quads == null || quads.isEmpty() || !rootOk) return;
        modelArmed = false;
        MODEL.set(layerMat);
        Vector3f[] ends = computeBladeEnds(quads);
        if (ends == null) return;
        INV_ROOT.set(ROOT).invert();
        M2R.set(INV_ROOT).mul(MODEL);
        M2R.transformPosition(ends[0], BASE);
        M2R.transformPosition(ends[1], TIP);
        // Хват — тот конец, что ближе к началу координат руки (в ROOT это 0,0,0).
        if (TIP.lengthSquared() < BASE.lengthSquared()) {
            TMP.set(BASE);
            BASE.set(TIP);
            TIP.set(TMP);
        }
        float dx = TIP.x - BASE.x, dy = TIP.y - BASE.y, dz = TIP.z - BASE.z;
        modelLen = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        modelOk = modelLen > 0.02f;
    }

    /**
     * Концы клинка из настоящих габаритов модели: берём ось с наибольшим размахом и её крайние
     * точки. Остальные две оси — середина. Координаты в пространстве слоя (квады 0..1 минус 0.5).
     * Счёт раз в кадр по готовому списку квадов — кэш по модели держать нечем: в 1.21.11 слой
     * предмета это переиспользуемый список квадов, он меняется вместе с предметом.
     */
    private static Vector3f[] computeBladeEnds(List<BakedQuad> quads) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        boolean any = false;
        for (BakedQuad q : quads) {
            for (int v = 0; v < 4; v++) {
                Vector3fc p = q.getPosition(v);
                if (p == null) continue;
                float x = p.x(), y = p.y(), z = p.z();
                if (Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(z)) continue;
                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (z < minZ) minZ = z;
                if (x > maxX) maxX = x;
                if (y > maxY) maxY = y;
                if (z > maxZ) maxZ = z;
                any = true;
            }
        }
        if (!any) return null;
        float cx = (minX + maxX) * 0.5f - 0.5f;
        float cy = (minY + maxY) * 0.5f - 0.5f;
        float cz = (minZ + maxZ) * 0.5f - 0.5f;
        float ex = maxX - minX, ey = maxY - minY, ez = maxZ - minZ;
        if (ey >= ex && ey >= ez) {
            return new Vector3f[]{new Vector3f(cx, minY - 0.5f, cz), new Vector3f(cx, maxY - 0.5f, cz)};
        }
        if (ex >= ez) {
            return new Vector3f[]{new Vector3f(minX - 0.5f, cy, cz), new Vector3f(maxX - 0.5f, cy, cz)};
        }
        return new Vector3f[]{new Vector3f(cx, cy, minZ - 0.5f), new Vector3f(cx, cy, maxZ - 0.5f)};
    }

    /** Доля реального клинка: когда модель поймана, длина берётся из неё, иначе из ползунка. */
    private static float bladeLen(float fraction) {
        float f = Math.max(0.1f, fraction);
        return modelOk ? Math.max(0.05f, modelLen * f) : Math.max(0.05f, f);
    }

    private static <T extends Module> T module(Class<T> cls) {
        return VisualsMod.moduleManager == null ? null : VisualsMod.moduleManager.getModule(cls);
    }

    private static boolean on(Module m) {
        return m != null && m.isEnabled();
    }

    private static boolean holdingSword() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return false;
        ItemStack stack = mc.player.getMainHandStack();
        return ViewModelStore.matchBlade(stack) != null;
    }

    private static boolean auraActive() {
        return SwordAura.inHand && holdingSword() && on(module(SwordAura.class));
    }

    private static boolean trailActive() {
        return HitSplash.inHand && holdingSword() && on(module(HitSplash.class));
    }

    private static boolean heatActive() {
        return holdingSword() && com.visualmod.module.modules.combat.CritHeat.active();
    }

    private static Vector3f spritePoint(float sx, float sy, float sz) {
        return relOk ? REL.transformPosition(sx * armSign, sy, sz, TMP) : TMP.set(sx * armSign, sy, sz);
    }

    private static void sampleTrail(long now) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) { TRAIL.clear(); return; }
        float td = mc.getRenderTickCounter().getTickProgress(false);
        float swing = mc.player.getHandSwingProgress(td);
        if (!(mc.player.handSwinging || swing < 0.999f)) return;
        if (now - lastSampleMs < 14) return;
        lastSampleMs = now;
        bladeFrame(HitSplash.anchorX, HitSplash.anchorY, HitSplash.anchorZ);
        float len = bladeLen(HitSplash.blade);
        TRAIL.add(new Seg(G.x, G.y, G.z,
                G.x + D.x * len, G.y + D.y * len, G.z + D.z * len, now));
        while (TRAIL.size() > 140) TRAIL.removeFirst();
    }

    public static void renderHandPass(MatrixStack matrices, OrderedRenderCommandQueue queue) {
        try {
            if (!rootOk || !relOk) return;
            long now = System.currentTimeMillis();
            boolean gif = trailActive() && HitSplash.isGif();
            if (trailActive() && !gif) sampleTrail(now); else TRAIL.clear();
            // Решение «что рисовать» принимается на кадр, а не в команде: к моменту исполнения
            // очереди рука уже другая, и лента или аура дорисовались бы поверх чужой модели.
            boolean aura = auraActive();
            boolean heat = heatActive();
            boolean rune = runeActive();
            if (!rune) runePrev = -1f;
            boolean streak = trailActive() && !gif;
            boolean ghosts = com.visualmod.weapon.SwordInspect.active()
                    || !com.visualmod.weapon.SwordInspect.GHOSTS.isEmpty();
            if (aura || heat || rune || streak || ghosts) {
                queue.submitCustom(matrices, RenderLayers.debugQuads(), (entry, buf) -> {
                    if (aura) drawAura(buf);
                    if (heat) drawHeat(buf);
                    if (rune) drawRune(buf);
                    if (streak) drawTrail(buf, now);
                    if (ghosts) drawGhosts(buf, now);
                });
            }
            if (gif) {
                RenderLayer layer = RenderLayers.entityTranslucent(SlashSprite.texture(HitSplash.color));
                queue.submitCustom(matrices, layer, (entry, buf) -> drawSlash(buf, now));
            }
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("HandEffects", t);
        }
    }

    private static final Vector3f BLADE_UP = new Vector3f(-0.7071068f, 0.7071068f, 0f);
    private static final Vector3f BLADE_SIDE = new Vector3f(0f, 0f, 1f);

    private static final Vector3f G = new Vector3f();
    private static final Vector3f D = new Vector3f();
    private static final Vector3f P = new Vector3f();
    private static final Vector3f N = new Vector3f();

    private static void bladeFrame(float sx, float sy, float sz) {
        if (modelOk) {
            // Клинок взят из реальной модели: основание — хват, направление — к острию.
            G.set(BASE);
            G.add(spritePoint(sx - DEF_AX, sy - DEF_AY, sz - DEF_AZ));
            D.set(TIP).sub(BASE).normalize();
            if (relOk) REL.transformDirection(BLADE_SIDE, P);
            else P.set(BLADE_SIDE);
            P.normalize();
            // Поперечина обязана быть перпендикулярна клинку, иначе кольца и обмотка съезжают.
            float k = P.dot(D);
            P.sub(D.x * k, D.y * k, D.z * k);
            if (P.lengthSquared() < 1e-6f) {
                if (relOk) REL.transformDirection(BLADE_UP, P);
                else P.set(BLADE_UP);
                k = P.dot(D);
                P.sub(D.x * k, D.y * k, D.z * k);
            }
            P.normalize();
        } else {
            G.set(spritePoint(sx, sy, sz));
            if (relOk) {
                REL.transformDirection(BLADE_UP, D).normalize();
                REL.transformDirection(BLADE_SIDE, P).normalize();
            } else {
                D.set(BLADE_UP);
                P.set(BLADE_SIDE);
            }
        }
        D.cross(P, N).normalize();
    }

    private static void drawAura(VertexConsumer vc) {
        bladeFrame(SwordAura.anchorX, SwordAura.anchorY, SwordAura.anchorZ);
        float len = bladeLen(SwordAura.blade);
        float r = Math.max(0.005f, SwordAura.radius * 0.45f);
        float k = Math.min(1f, SwordAura.intensity);
        float[] col = rgb(SwordAura.color);
        double sec = System.currentTimeMillis() / 1000.0;

        switch (SwordAura.style) {
            case "PARTICLES" -> {
                for (int i = 0; i < 14; i++) {
                    float t = (i + 0.5f) / 14f;
                    double a = sec * 2.2 + i * 0.9;
                    float w1 = r * 0.42f * (float) Math.sin(a);
                    float w2 = r * 0.42f * (float) Math.cos(a * 0.8 + 1.0);
                    billboard(vc,
                            G.x + D.x * t * len + P.x * w1 + N.x * w2,
                            G.y + D.y * t * len + P.y * w1 + N.y * w2,
                            G.z + D.z * t * len + P.z * w1 + N.z * w2,
                            0.009f * (0.65f + 0.35f * (float) Math.sin(a)),
                            col, 0.85f * k * (1f - 0.3f * t));
                }
            }
            case "RING" -> {
                float t = 1f - (float) ((sec * Math.max(0.05f, SwordAura.speed)) % 1.0);
                ringOnBlade(vc, t * len, r * (0.75f + 0.45f * (1f - t)), 30, col, 0.9f * k * (0.3f + 0.7f * t), 0.008f);
            }
            case "CLOTH" -> clothWrap(vc, len, r * 1.1f, col, k, sec);
            default -> {
                float spin = (float) (sec * Math.max(0.05f, SwordAura.speed));
                glowRing(vc, 0.0f, r * 0.60f, 26, col, 0.80f * k, 0.010f, spin);
                glowRing(vc, 0.0f, r * 1.00f, 28, col, 0.44f * k, 0.014f, spin + 1.05f);
                glowRing(vc, 0.0f, r * 1.45f, 30, col, 0.20f * k, 0.018f, spin + 2.1f);
            }
        }
    }

    private static void drawHeat(VertexConsumer vc) {
        float h = com.visualmod.module.modules.combat.CritHeat.heat();
        if (h <= 0.02f) return;
        bladeFrame(DEF_AX, DEF_AY, DEF_AZ);
        float len = bladeLen(0.82f);
        float[] cLo = rgb(com.visualmod.module.modules.combat.CritHeat.coolColor);
        float[] cHi = rgb(com.visualmod.module.modules.combat.CritHeat.hotColor);
        float cr = cLo[0] + (cHi[0] - cLo[0]) * h;
        float cg = cLo[1] + (cHi[1] - cLo[1]) * h;
        float cb = cLo[2] + (cHi[2] - cLo[2]) * h;
        float w = 0.0045f + 0.011f * h;

        int seg = 16;
        for (int i = 0; i < seg; i++) {
            float t0 = i / (float) seg, t1 = (i + 1) / (float) seg;
            float a0 = (0.10f + 0.62f * t0) * h;
            float a1 = (0.10f + 0.62f * t1) * h;
            heatStrip(vc, t0 * len, t1 * len, w, cr, cg, cb, (a0 + a1) * 0.5f);
        }

        long now = System.currentTimeMillis();
        if (com.visualmod.module.modules.combat.CritHeat.embers) {
            for (int i = 0; i < 7; i++) {
                float t = (float) (Math.sin(i * 3.7) * 0.42 + 0.5);
                double phase = ((now / 1000.0) * (0.7 + 0.45 * (i % 3)) + i * 0.37) % 1.0;
                float rise = (float) (phase * 0.09);
                float side = (float) (Math.sin(now / 260.0 + i) * 0.012);
                float size = (0.006f + 0.008f * h) * (i % 2 == 0 ? 1f : 0.7f);
                billboard(vc,
                        G.x + D.x * t * len + P.x * side,
                        G.y + D.y * t * len + P.y * side + rise,
                        G.z + D.z * t * len + P.z * side,
                        size, new float[]{cr, cg, cb}, (float) (0.65 * h * (1.0 - phase)));
            }
        }

        float tip = 0.012f + 0.030f * h;
        billboard(vc, G.x + D.x * len, G.y + D.y * len, G.z + D.z * len, tip,
                new float[]{cr, cg, cb}, 0.55f * h);
    }

    private static void heatStrip(VertexConsumer vc, float l0, float l1, float w,
                                  float r, float g, float b, float a) {
        float x0 = G.x + D.x * l0, y0 = G.y + D.y * l0, z0 = G.z + D.z * l0;
        float x1 = G.x + D.x * l1, y1 = G.y + D.y * l1, z1 = G.z + D.z * l1;
        quad(vc,
                x0 - P.x * w, y0 - P.y * w, z0 - P.z * w,
                x1 - P.x * w, y1 - P.y * w, z1 - P.z * w,
                x1 + P.x * w, y1 + P.y * w, z1 + P.z * w,
                x0 + P.x * w, y0 + P.y * w, z0 + P.z * w,
                r, g, b, a);
        quad(vc,
                x0 - N.x * w, y0 - N.y * w, z0 - N.z * w,
                x1 - N.x * w, y1 - N.y * w, z1 - N.z * w,
                x1 + N.x * w, y1 + N.y * w, z1 + N.z * w,
                x0 + N.x * w, y0 + N.y * w, z0 + N.z * w,
                r, g, b, a * 0.75f);
    }

    private static void ringOnBlade(VertexConsumer vc, float along, float radius, int segments,
                                    float[] col, float alpha, float thick) {
        ringAt(vc, along, radius, segments, col, alpha, thick, P, N);
    }

    private static float runePrev = -1f;
    private static long runeFlashMs = 0L;

    private static boolean runeActive() {
        return holdingSword() && com.visualmod.module.modules.combat.StrikeRune.active();
    }

    /** Руна перезарядки: два кольца у гарды заполняются по прогрессу атаки и вспыхивают в готовности. */
    private static void drawRune(VertexConsumer vc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        float td = mc.getRenderTickCounter().getTickProgress(false);
        float prog = Math.max(0f, Math.min(1f, mc.player.getAttackCooldownProgress(td)));
        long now = System.currentTimeMillis();
        if (runePrev >= 0f && prog >= 0.999f && runePrev < 0.999f
                && com.visualmod.module.modules.combat.StrikeRune.flash) {
            runeFlashMs = now;
        }
        runePrev = prog;

        bladeFrame(DEF_AX, DEF_AY, DEF_AZ);
        float len = bladeLen(0.9f);
        float r = (float) Math.max(0.012, com.visualmod.module.modules.combat.StrikeRune.radius);
        float[] ready = rgb(com.visualmod.module.modules.combat.StrikeRune.readyColor);
        float[] idle = rgb(com.visualmod.module.modules.combat.StrikeRune.idleColor);

        arc(vc, len * 0.13f, r, prog, ready, idle, 1f);
        arc(vc, len * 0.26f, r * 0.76f, prog, ready, idle, 0.82f);

        if (runeFlashMs != 0L && now - runeFlashMs < 300L) {
            float k = (now - runeFlashMs) / 300f;
            ringOnBlade(vc, len * 0.13f, r * (1f + 1.5f * k), 26, ready, 0.5f * (1f - k), 0.018f);
            float cx = G.x + D.x * len * 0.13f, cy = G.y + D.y * len * 0.13f, cz = G.z + D.z * len * 0.13f;
            for (int i = 0; i < 8; i++) {
                double a = i / 8.0 * Math.PI * 2.0;
                float ox = (float) (Math.cos(a) * r * (1.15 + 1.1 * k));
                float oy = (float) (Math.sin(a) * r * (1.15 + 1.1 * k));
                billboard(vc, cx + P.x * ox + N.x * oy, cy + P.y * ox + N.y * oy, cz + P.z * ox + N.z * oy,
                        0.006f + 0.010f * (1f - k), ready, 0.55f * (1f - k));
            }
        }
    }

    private static void arc(VertexConsumer vc, float along, float radius, float prog,
                            float[] ready, float[] idle, float alpha) {
        if (radius <= 0.001f) return;
        float cx = G.x + D.x * along, cy = G.y + D.y * along, cz = G.z + D.z * along;
        int seg = 30;
        float thick = 0.0045f + radius * 0.055f;
        for (int i = 0; i < seg; i++) {
            double t0 = i / (double) seg, t1 = (i + 1) / (double) seg;
            double a0 = -Math.PI * 0.5 + t0 * Math.PI * 2.0;
            double a1 = -Math.PI * 0.5 + t1 * Math.PI * 2.0;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            boolean fill = (t0 + t1) * 0.5 <= prog;
            float[] col = fill ? ready : idle;
            float aa = alpha * (fill ? 0.95f : 0.28f);
            float r0 = radius, r1 = radius + thick;
            quad(vc,
                    cx + (P.x * c0 + N.x * s0) * r0, cy + (P.y * c0 + N.y * s0) * r0, cz + (P.z * c0 + N.z * s0) * r0,
                    cx + (P.x * c1 + N.x * s1) * r0, cy + (P.y * c1 + N.y * s1) * r0, cz + (P.z * c1 + N.z * s1) * r0,
                    cx + (P.x * c1 + N.x * s1) * r1, cy + (P.y * c1 + N.y * s1) * r1, cz + (P.z * c1 + N.z * s1) * r1,
                    cx + (P.x * c0 + N.x * s0) * r1, cy + (P.y * c0 + N.y * s0) * r1, cz + (P.z * c0 + N.z * s0) * r1,
                    col[0], col[1], col[2], aa);
        }
        double ah = -Math.PI * 0.5 + prog * Math.PI * 2.0;
        float hx = cx + (float) (P.x * Math.cos(ah) + N.x * Math.sin(ah)) * radius;
        float hy = cy + (float) (P.y * Math.cos(ah) + N.y * Math.sin(ah)) * radius;
        float hz = cz + (float) (P.z * Math.cos(ah) + N.z * Math.sin(ah)) * radius;
        billboard(vc, hx, hy, hz, 0.007f + radius * 0.12f, ready, 0.9f * alpha);
    }

    private static final Vector3f U = new Vector3f();
    private static final Vector3f V = new Vector3f();

    private static void glowRing(VertexConsumer vc, float along, float radius, int segments,
                                 float[] col, float alpha, float thick, float a) {
        float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
        U.set(N);
        V.set(D).mul(ca);
        V.add(P.x * sa, P.y * sa, P.z * sa);
        ringAt(vc, along, radius, segments, col, alpha, thick, U, V);
    }

    private static void ringAt(VertexConsumer vc, float along, float radius, int segments,
                               float[] col, float alpha, float thick, Vector3f u, Vector3f v) {
        if (radius <= 0.001f || alpha <= 0.01f) return;
        float cx = G.x + D.x * along, cy = G.y + D.y * along, cz = G.z + D.z * along;
        for (int i = 0; i < segments; i++) {
            double a0 = i / (double) segments * Math.PI * 2;
            double a1 = (i + 1) / (double) segments * Math.PI * 2;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float r0 = radius, r1 = radius + thick;
            quad(vc,
                    cx + (u.x * c0 + v.x * s0) * r0, cy + (u.y * c0 + v.y * s0) * r0, cz + (u.z * c0 + v.z * s0) * r0,
                    cx + (u.x * c1 + v.x * s1) * r0, cy + (u.y * c1 + v.y * s1) * r0, cz + (u.z * c1 + v.z * s1) * r0,
                    cx + (u.x * c1 + v.x * s1) * r1, cy + (u.y * c1 + v.y * s1) * r1, cz + (u.z * c1 + v.z * s1) * r1,
                    cx + (u.x * c0 + v.x * s0) * r1, cy + (u.y * c0 + v.y * s0) * r1, cz + (u.z * c0 + v.z * s0) * r1,
                    col[0], col[1], col[2], alpha);
        }
    }

    private static final float[] CL0 = new float[7];
    private static final float[] CL1 = new float[7];

    /**
     * Тканевая обмотка: лента идёт по спирали вокруг клинка, а её ширина отложена внутри
     * поверхности цилиндра (W = e_a минус проекция на касательную), поэтому витки лежат
     * плашмя, а не торчат пластинами. Шаг витка задаёт и ширину ленты — витки перекрываются
     * на пяток процентов, отчего край читается как плетение, а не как набор колец.
     */
    private static void clothWrap(VertexConsumer vc, float len, float rad, float[] col, float k, double sec) {
        float turns = Math.max(1.5f, SwordAura.turns);
        int seg = Math.min(140, Math.max(26, Math.round(turns * 12f)));
        float pitch = len / turns;
        float hb = Math.min(rad * 0.62f, pitch * 0.62f);
        if (hb <= 0.0008f) return;
        float spin = (float) (sec * Math.max(0.05f, SwordAura.speed) * 0.5);

        for (int i = 0; i < seg; i++) {
            clothPoint((float) i / seg, turns, spin, len, rad, CL0);
            clothPoint((float) (i + 1) / seg, turns, spin, len, rad, CL1);
            float shade = 0.58f + 0.42f * CL0[6];
            float r = Math.min(1f, col[0] * shade + 0.05f);
            float g = Math.min(1f, col[1] * shade + 0.04f);
            float b = Math.min(1f, col[2] * shade + 0.06f);
            quad(vc,
                    CL0[0] - CL0[3] * hb, CL0[1] - CL0[4] * hb, CL0[2] - CL0[5] * hb,
                    CL1[0] - CL1[3] * hb, CL1[1] - CL1[4] * hb, CL1[2] - CL1[5] * hb,
                    CL1[0] + CL1[3] * hb, CL1[1] + CL1[4] * hb, CL1[2] + CL1[5] * hb,
                    CL0[0] + CL0[3] * hb, CL0[1] + CL0[4] * hb, CL0[2] + CL0[5] * hb,
                    r, g, b, 0.88f * k);
            // шов по нижней кромке витка
            quad(vc,
                    CL0[0] - CL0[3] * hb, CL0[1] - CL0[4] * hb, CL0[2] - CL0[5] * hb,
                    CL1[0] - CL1[3] * hb, CL1[1] - CL1[4] * hb, CL1[2] - CL1[5] * hb,
                    CL1[0] - CL1[3] * hb * 0.62f, CL1[1] - CL1[4] * hb * 0.62f, CL1[2] - CL1[5] * hb * 0.62f,
                    CL0[0] - CL0[3] * hb * 0.62f, CL0[1] - CL0[4] * hb * 0.62f, CL0[2] - CL0[5] * hb * 0.62f,
                    r * 0.45f, g * 0.45f, b * 0.5f, 0.75f * k);
            // мягкое свечение поверх ткани
            quad(vc,
                    CL0[0] - CL0[3] * hb * 1.9f, CL0[1] - CL0[4] * hb * 1.9f, CL0[2] - CL0[5] * hb * 1.9f,
                    CL1[0] - CL1[3] * hb * 1.9f, CL1[1] - CL1[4] * hb * 1.9f, CL1[2] - CL1[5] * hb * 1.9f,
                    CL1[0] + CL1[3] * hb * 1.9f, CL1[1] + CL1[4] * hb * 1.9f, CL1[2] + CL1[5] * hb * 1.9f,
                    CL0[0] + CL0[3] * hb * 1.9f, CL0[1] + CL0[4] * hb * 1.9f, CL0[2] + CL0[5] * hb * 1.9f,
                    r, g, b, 0.13f * k);
        }
    }

    /** o[0..2] — центр ленты, o[3..5] — поперёк ленты внутри поверхности, o[6] — затенение. */
    private static void clothPoint(float f, float turns, float spin, float len, float rad, float[] o) {
        float a = spin + f * turns * (float) (Math.PI * 2);
        float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
        float ex = P.x * ca + N.x * sa, ey = P.y * ca + N.y * sa, ez = P.z * ca + N.z * sa;
        float rx = -P.x * sa + N.x * ca, ry = -P.y * sa + N.y * ca, rz = -P.z * sa + N.z * ca;
        float rr = rad * (1f + 0.05f * (float) Math.sin(a * 2f + spin * 1.7f));
        o[0] = G.x + D.x * f * len + ex * rr;
        o[1] = G.y + D.y * f * len + ey * rr;
        o[2] = G.z + D.z * f * len + ez * rr;
        float circum = (float) (turns * Math.PI * 2 * rad);
        float tl = (float) Math.sqrt(circum * circum + len * len);
        float tx = (circum * ex + len * D.x) / tl;
        float ty = (circum * ey + len * D.y) / tl;
        float tz = (circum * ez + len * D.z) / tl;
        float dt = ex * tx + ey * ty + ez * tz;
        float wx = ex - dt * tx, wy = ey - dt * ty, wz = ez - dt * tz;
        float wl = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
        if (wl > 1e-5f) { wx /= wl; wy /= wl; wz /= wl; }
        o[3] = wx; o[4] = wy; o[5] = wz;
        float lit = rx * 0.34f + ry * 0.62f + rz * 0.71f;
        o[6] = Math.max(0f, lit);
    }

    private static void billboard(VertexConsumer vc, float cx, float cy, float cz, float s,
                                  float[] col, float alpha) {
        float h = Math.max(0.002f, s);
        quad(vc, cx - h, cy - h, cz, cx + h, cy - h, cz, cx + h, cy + h, cz, cx - h, cy + h, cz,
                col[0], col[1], col[2], alpha);
    }

    private static float prevSlashSwing;
    private static long slashStartMs;

    /**
     * Кадры полосы вместо накопленной ленты: в руке траекторию собирать не из чего, поэтому
     * анимацию играем по прогрессу свинга и ставим спрайт в кадре клинка — вдоль лезвия, в его
     * плоскости.
     */
    private static void drawSlash(VertexConsumer vc, long now) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) { slashStartMs = 0L; prevSlashSwing = 0f; return; }
        float swing = mc.player.getHandSwingProgress(mc.getRenderTickCounter().getTickProgress(false));
        if (swing + 0.2f < prevSlashSwing) slashStartMs = now;
        prevSlashSwing = swing;
        long dur = Math.max(140L, (long) (HitSplash.fade * 1000.0));
        if (slashStartMs == 0L || now - slashStartMs > dur) return;
        float t = Math.min(1f, (now - slashStartMs) / (float) dur);

        bladeFrame(HitSplash.anchorX, HitSplash.anchorY, HitSplash.anchorZ);
        float hl = bladeLen(HitSplash.blade) * 0.7f * Math.max(0.3f, HitSplash.slash);
        Vector3f u = new Vector3f(D).mul(hl);
        Vector3f v = new Vector3f(P).mul(hl * 0.5f * Math.max(0.2f, HitSplash.width));
        SlashSprite.quad(vc, ROOT,
                G.x + D.x * hl * 1.05f, G.y + D.y * hl * 1.05f, G.z + D.z * hl * 1.05f,
                u.x, u.y, u.z, v.x, v.y, v.z,
                SlashSprite.frame(t), 1f - Math.max(0f, t - 0.6f) / 0.4f);
    }

    private static void drawTrail(VertexConsumer vc, long now) {
        if (TRAIL.size() < 2) return;
        long dur = Math.max(80L, (long) (HitSplash.fade * 1000.0));
        while (!TRAIL.isEmpty() && now - TRAIL.peekFirst().t > dur) TRAIL.removeFirst();
        if (TRAIL.size() < 2) return;

        float[] col = rgb(HitSplash.color);
        boolean glow = "GLOW".equals(HitSplash.style);
        boolean dashed = "DASHED".equals(HitSplash.style);
        float widen = Math.max(0.15f, HitSplash.width);
        float edge = 0.012f * widen;

        Seg prev = null;
        int idx = 0;
        for (Seg s : TRAIL) {
            if (prev != null && !(dashed && (idx & 1) == 0)) {
                float age = Math.min(1f, (now - s.t) / (float) dur);
                float alpha = (1f - age) * 0.85f;
                if (alpha > 0.02f) {
                    float keep = (0.45f + 0.55f * (1f - age)) * widen;
                    float[] a = taper(prev, keep);
                    float[] b = taper(s, keep);
                    float dx = a[3] - a[0], dy = a[4] - a[1], dz = a[5] - a[2];
                    float nx = -dy, ny = dx;
                    float nl = (float) Math.hypot(nx, ny);
                    if (nl > 1e-5f) { nx /= nl; ny /= nl; }
                    float hw = edge * (1f - age * 0.5f);
                    band(vc, col, alpha, a, b, nx * hw, ny * hw, glow ? 0.5f : 1f);
                    if (glow) band(vc, col, alpha * 0.3f, a, b, nx * hw * 2.8f, ny * hw * 2.8f, 1f);
                }
            }
            prev = s;
            idx++;
        }
    }

    private static float[] taper(Seg s, float keep) {
        float mx = (s.gx + s.tx) / 2, my = (s.gy + s.ty) / 2, mz = (s.gz + s.tz) / 2;
        return new float[]{
                mx + (s.gx - mx) * keep, my + (s.gy - my) * keep, mz + (s.gz - mz) * keep,
                mx + (s.tx - mx) * keep, my + (s.ty - my) * keep, mz + (s.tz - mz) * keep};
    }

    private static void band(VertexConsumer vc, float[] col, float alpha, float[] a, float[] b,
                             float ox, float oy, float bright) {
        quad(vc,
                a[0] - ox, a[1] - oy, a[2],
                a[3] - ox, a[4] - oy, a[5],
                b[3] + ox, b[4] + oy, b[5],
                b[0] + ox, b[1] + oy, b[2],
                Math.min(1f, col[0] * bright + 0.05f), Math.min(1f, col[1] * bright + 0.05f),
                Math.min(1f, col[2] * bright + 0.05f), alpha);
        quad(vc,
                a[0] + ox, a[1] + oy, a[2],
                a[3] + ox, a[4] + oy, a[5],
                b[3] - ox, b[4] - oy, b[5],
                b[0] - ox, b[1] - oy, b[2],
                col[0], col[1], col[2], alpha * 0.6f);
    }

    private static final Matrix4f GREL = new Matrix4f();
    private static final Vector3f GD = new Vector3f();
    private static final Vector3f GP = new Vector3f();
    private static final Vector3f GU = new Vector3f();

    private static void drawGhosts(VertexConsumer vc, long now) {
        if (!rootOk) return;
        com.visualmod.weapon.SwordInspect.pruneGhosts(now);
        if (com.visualmod.weapon.SwordInspect.GHOSTS.isEmpty()) return;
        INV_ROOT.set(ROOT).invert();
        float[] col = {0.44f, 0.27f, 0.92f};
        for (com.visualmod.weapon.SwordInspect.Ghost ghost : com.visualmod.weapon.SwordInspect.GHOSTS) {
            float k = com.visualmod.weapon.SwordInspect.ghostAlpha(ghost, now);
            if (k <= 0.02f) continue;
            GREL.set(ghost.pose).mul(INV_ROOT);
            GREL.transformPosition(0.5f, 0.40f, 0.5f, G);
            GREL.transformDirection(BLADE_UP, GD).normalize();
            GREL.transformDirection(BLADE_SIDE, GP).normalize();
            float spin = (now - ghost.born) / 1000f * 7.5f;
            float r = 0.30f * (0.8f + 0.2f * k);
            float w = 0.030f * k + 0.008f;
            int seg = 9;
            for (int i = 0; i < seg; i++) {
                double a0 = spin + (i / (double) seg - 0.5) * 2.3;
                double a1 = spin + ((i + 1) / (double) seg - 0.5) * 2.3;
                arcPoint(a0, r - w, GU);
                float i0x = GU.x, i0y = GU.y, i0z = GU.z;
                arcPoint(a1, r - w, GU);
                float i1x = GU.x, i1y = GU.y, i1z = GU.z;
                arcPoint(a1, r + w, GU);
                float o1x = GU.x, o1y = GU.y, o1z = GU.z;
                arcPoint(a0, r + w, GU);
                quad(vc, i0x, i0y, i0z, i1x, i1y, i1z, o1x, o1y, o1z, GU.x, GU.y, GU.z,
                        col[0], col[1], col[2], 0.62f * k);
                quad(vc, i0x, i0y, i0z, i1x, i1y, i1z, o1x, o1y, o1z, GU.x, GU.y, GU.z,
                        0.78f, 0.62f, 1f, 0.22f * k);
            }
        }
    }

    private static void arcPoint(double a, float rr, Vector3f dest) {
        float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
        dest.set(GD).mul(ca);
        dest.add(GP.x * sa, GP.y * sa, GP.z * sa);
        dest.mul(rr).add(G);
    }

    private static void quad(VertexConsumer vc,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float r, float g, float b, float a) {
        float rr = clamp01(r), gg = clamp01(g), bb = clamp01(b), aa = clamp01(a);
        v(vc, x0, y0, z0, rr, gg, bb, aa);
        v(vc, x1, y1, z1, rr, gg, bb, aa);
        v(vc, x2, y2, z2, rr, gg, bb, aa);
        v(vc, x3, y3, z3, rr, gg, bb, aa);
        v(vc, x3, y3, z3, rr, gg, bb, aa);
        v(vc, x2, y2, z2, rr, gg, bb, aa);
        v(vc, x1, y1, z1, rr, gg, bb, aa);
        v(vc, x0, y0, z0, rr, gg, bb, aa);
    }

    private static void v(VertexConsumer vc, float x, float y, float z, float r, float g, float b, float a) {
        vc.vertex(ROOT, x, y, z).color(r, g, b, a);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static float[] rgb(int argb) {
        return new float[]{((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f};
    }
}

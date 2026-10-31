package com.visualmod.module.modules.combat;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Arm;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.visualmod.VisualsMod;
import com.visualmod.util.SlashSprite;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;

public final class CombatEffects {

    private CombatEffects() {}

    static { WorldRenderEvents.AFTER_ENTITIES.register(CombatEffects::render); }
    public static void init() {}

    private static final List<TrailSeg> trail = new ArrayList<>();
    private static final List<HitEvent> hits = new ArrayList<>();
    private static long lastTrailSample = 0;
    private static long lastAuraParticle = 0;

    private static final class TrailSeg {
        final double gx, gy, gz, tx, ty, tz;
        final long time;
        TrailSeg(double gx, double gy, double gz, double tx, double ty, double tz, long time) {
            this.gx = gx; this.gy = gy; this.gz = gz;
            this.tx = tx; this.ty = ty; this.tz = tz;
            this.time = time;
        }
    }

    private static final class HitEvent {
        final double x, y, z;
        final long time;
        final boolean crit;
        HitEvent(double x, double y, double z, long time, boolean crit) { this.x = x; this.y = y; this.z = z; this.time = time; this.crit = crit; }
    }

    public static void onAttack(Entity target) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || target == null) return;
        if (VisualsMod.moduleManager == null) return;

        long now = System.currentTimeMillis();
        boolean crit = isCritical(mc.player);

        HitSplashEffect.onHit(target, now);
        CriticalEffectImpl.onHit(target, crit, now);
    }

    private static boolean isCritical(PlayerEntity p) {
        return p.fallDistance > 0.0F
                && !p.isOnGround()
                && !p.isClimbing()
                && !p.isTouchingWater()
                && !p.hasStatusEffect(StatusEffects.BLINDNESS)
                && p.getVehicle() == null;
    }

    private static void render(WorldRenderContext ctx) {
        try {
            if (com.visualmod.util.IrisCompat.skipWorldPass()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null || mc.player == null) return;
            if (VisualsMod.moduleManager == null) return;
            if (ctx.matrices() == null || ctx.consumers() == null) return;

            long now = System.currentTimeMillis();
            float td = mc.getRenderTickCounter().getTickProgress(false);
            Vec3d cam = camera(ctx).getCameraPos();

            ctx.matrices().push();
            ctx.matrices().translate(-cam.x, -cam.y, -cam.z);
            Matrix4f mat = ctx.matrices().peek().getPositionMatrix();

            HitSplashEffect.render(ctx, mat, now);
            SwingTrailEffect.render(ctx, mat, now, td);
            SwordAuraEffect.render(ctx, mat, now);

            ctx.matrices().pop();

        } catch (Throwable t) {
            com.visualmod.util.Diag.once("CombatEffects", t);
        }
    }

    private static Camera camera(WorldRenderContext ctx) {
        return ctx.gameRenderer().getCamera();
    }

    @SuppressWarnings("unchecked")
    static <T extends com.visualmod.module.Module> T module(Class<T> clazz) {
        return VisualsMod.moduleManager != null ? VisualsMod.moduleManager.getModule(clazz) : null;
    }

    static boolean holdingSword(MinecraftClient mc) {
        if (mc == null || mc.player == null) return false;
        ItemStack stack = mc.player.getMainHandStack();
        return com.visualmod.viewmodel.ViewModelStore.matchBlade(stack) != null;
    }

    static float[] hexF(int hex, float alpha) {
        return new float[]{((hex >> 16) & 0xFF) / 255f, ((hex >> 8) & 0xFF) / 255f, (hex & 0xFF) / 255f, alpha};
    }

    static void quad(VertexConsumer vc, Matrix4f mat,
                     double x1, double y1, double z1, double x2, double y2, double z2,
                     double x3, double y3, double z3, double x4, double y4, double z4,
                     float r, float g, float b, float a) {
        a = Math.min(1f, a); r = Math.min(1f, r); g = Math.min(1f, g); b = Math.min(1f, b);
        vc.vertex(mat, (float) x1, (float) y1, (float) z1).color(r, g, b, a);
        vc.vertex(mat, (float) x2, (float) y2, (float) z2).color(r, g, b, a);
        vc.vertex(mat, (float) x3, (float) y3, (float) z3).color(r, g, b, a);
        vc.vertex(mat, (float) x4, (float) y4, (float) z4).color(r, g, b, a);
    }

    static float[] billboardBasis(Vec3d cam, double x, double y, double z, float radius) {
        Vector3f toCam = new Vector3f((float) (cam.x - x), (float) (cam.y - y), (float) (cam.z - z)).normalize();
        Vector3f up = new Vector3f(0, 1, 0);
        Vector3f right = new Vector3f(toCam).cross(up).normalize().mul(radius);
        Vector3f up2 = new Vector3f(toCam).cross(new Vector3f(right).normalize()).normalize().mul(radius);
        return new float[]{right.x, right.y, right.z, up2.x, up2.y, up2.z};
    }

    public static final class HitSplashEffect {
        private HitSplashEffect() {}

        static void onHit(Entity target, long now) {
            HitSplash mod = module(HitSplash.class);
            if (mod == null || !mod.isEnabled() || !HitSplash.hitFlash) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (!holdingSword(mc)) return;
            double cx = target.getX();
            double cy = target.getY() + target.getHeight() * 0.6;
            double cz = target.getZ();

            int count = Math.max(6, (int) (14 * HitSplash.size));
            double speed = 0.22 * HitSplash.size;

            synchronized (hits) { hits.add(new HitEvent(cx, cy, cz, now, false)); }
            for (int i = 0; i < count; i++) {
                double ang = Math.random() * Math.PI * 2;
                double up = 0.04 + Math.random() * 0.12;
                DustParticleEffect d = new DustParticleEffect(HitSplash.color & 0xFFFFFF, 0.9f);
                mc.world.addParticleClient(d,
                        cx + Math.cos(ang) * 0.2, cy + (Math.random() - 0.5) * 0.3, cz + Math.sin(ang) * 0.2,
                        Math.cos(ang) * speed, up, Math.sin(ang) * speed);
            }
        }

        static void render(WorldRenderContext ctx, Matrix4f mat, long now) {
            HitSplash mod = module(HitSplash.class);
            if (mod == null || !mod.isEnabled() || !HitSplash.hitFlash) {
                synchronized (hits) { hits.clear(); }
                return;
            }
            long durMs = Math.max(50, (long) (HitSplash.duration * 1000));
            boolean any = false;
            Vec3d cam = camera(ctx).getCameraPos();
            synchronized (hits) {
                Iterator<HitEvent> it = hits.iterator();
                while (it.hasNext()) {
                    HitEvent h = it.next();
                    long age = now - h.time;
                    if (age > durMs) { it.remove(); continue; }
                    any = true;
                    float t = age / (float) durMs;
                    float alpha = (1f - t) * 0.75f;
                    float r = 0.5f * HitSplash.size * (0.5f + t);
                    float[] col = hexF(HitSplash.color, alpha);

                    VertexConsumer vc = ctx.consumers().getBuffer(RenderLayers.debugQuads());
                    float[] bb = billboardBasis(cam, h.x, h.y, h.z, r);
                    float rx = bb[0], ry = bb[1], rz = bb[2];
                    float ux = bb[3], uy = bb[4], uz = bb[5];

                    quad(vc, mat,
                            h.x - rx, h.y - ry, h.z - rz,
                            h.x + rx, h.y + ry, h.z + rz,
                            h.x + rx + ux, h.y + ry + uy, h.z + rz + uz,
                            h.x - rx + ux, h.y - ry + uy, h.z - rz + uz,
                            col[0], col[1], col[2], col[3]);
                    quad(vc, mat,
                            h.x - rx, h.y - ry, h.z - rz,
                            h.x - rx - ux, h.y - ry - uy, h.z - rz - uz,
                            h.x + rx - ux, h.y + ry - uy, h.z + rz - uz,
                            h.x + rx, h.y + ry, h.z + rz,
                            col[0], col[1], col[2], col[3]);
                }
            }
            if (any) com.visualmod.util.RenderFlush.flush(ctx.consumers(), RenderLayers.debugQuads());
        }
    }

    public static final class SwingTrailEffect {
        private SwingTrailEffect() {}

        static void render(WorldRenderContext ctx, Matrix4f mat, long now, float td) {
            HitSplash mod = module(HitSplash.class);
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mod == null || !mod.isEnabled() || mc.player == null || !holdingSword(mc)) {
                synchronized (trail) { trail.clear(); }
                return;
            }

            AbstractClientPlayerEntity p = mc.player;
            if (HitSplash.inHand && mc.options.getPerspective().isFirstPerson()) {
                synchronized (trail) { trail.clear(); }
                return;
            }
            if (HitSplash.isGif()) {
                slash(ctx, mat, now, td, p);
                return;
            }
            float swing = p.getHandSwingProgress(td);
            boolean swinging = p.handSwinging || swing < 0.999f;

            if (swinging && now - lastTrailSample > 12) {
                lastTrailSample = now;
                Vec3d pos = p.getLerpedPos(td);
                double yaw = Math.toRadians(p.getBodyYaw());
                double sinY = Math.sin(yaw), cosY = Math.cos(yaw);
                double side = p.getMainArm() == Arm.RIGHT ? 1 : -1;
                double swingArc = Math.sin(swing * Math.PI) * 0.9;

                double gx = pos.x + cosY * 0.45 * side - sinY * (0.3 + swingArc * 0.4);
                double gy = pos.y + 1.25 - swing * 0.45 + swingArc * 0.15;
                double gz = pos.z + sinY * 0.45 * side + cosY * (0.3 + swingArc * 0.4);
                Vec3d bdir = new Vec3d(-sinY * Math.cos(swing * Math.PI),
                        0.55 + Math.sin(swing * Math.PI), cosY * Math.cos(swing * Math.PI)).normalize();
                double blen = Math.max(0.2f, HitSplash.blade) * 1.55;
                synchronized (trail) {
                    trail.add(new TrailSeg(gx, gy, gz,
                            gx + bdir.x * blen, gy + bdir.y * blen, gz + bdir.z * blen, now));
                    while (trail.size() > 160) trail.remove(0);
                }
            }

            long durMs = Math.max(60, (long) (HitSplash.fade * 1000));
            List<TrailSeg> alive;
            synchronized (trail) {
                trail.removeIf(sg -> now - sg.time > durMs);
                alive = new ArrayList<>(trail);
            }
            if (alive.size() < 2) return;

            VertexConsumer vc = ctx.consumers().getBuffer(RenderLayers.debugQuads());
            float[] baseCol = hexF(HitSplash.color, 1f);
            float w = Math.max(0.02f, HitSplash.width * 0.05f);
            Vec3d cam = camera(ctx).getCameraPos();

            boolean dashed = "DASHED".equals(HitSplash.style);
            boolean glow = "GLOW".equals(HitSplash.style);

            for (int i = 1; i < alive.size(); i++) {
                if (dashed && (i & 1) == 0) continue;
                TrailSeg a = alive.get(i - 1), b = alive.get(i);
                float age = Math.min(1f, (now - b.time) / (float) durMs);
                float alpha = (1f - age) * 0.8f;
                if (alpha <= 0.02f) continue;

                float keep = (0.45f + 0.55f * (1f - age)) * Math.max(0.4f, HitSplash.width);
                double amx = (a.gx + a.tx) / 2, amy = (a.gy + a.ty) / 2, amz = (a.gz + a.tz) / 2;
                double bmx = (b.gx + b.tx) / 2, bmy = (b.gy + b.ty) / 2, bmz = (b.gz + b.tz) / 2;
                double agx = amx + (a.gx - amx) * keep, agy = amy + (a.gy - amy) * keep, agz = amz + (a.gz - amz) * keep;
                double atx = amx + (a.tx - amx) * keep, aty = amy + (a.ty - amy) * keep, atz = amz + (a.tz - amz) * keep;
                double bgx = bmx + (b.gx - bmx) * keep, bgy = bmy + (b.gy - bmy) * keep, bgz = bmz + (b.gz - bmz) * keep;
                double btx = bmx + (b.tx - bmx) * keep, bty = bmy + (b.ty - bmy) * keep, btz = bmz + (b.tz - bmz) * keep;

                Vector3f bladeV = new Vector3f((float) (atx - agx), (float) (aty - agy), (float) (atz - agz));
                Vector3f toCam = new Vector3f((float) (cam.x - amx), (float) (cam.y - amy), (float) (cam.z - amz));
                Vector3f thick = new Vector3f(bladeV).cross(toCam);
                if (thick.lengthSquared() < 1e-8f) continue;
                thick.normalize().mul(w * (1f - age * 0.5f));

                band(vc, mat, baseCol, alpha, thick,
                        agx, agy, agz, atx, aty, atz, bgx, bgy, bgz, btx, bty, btz);
                if (glow) {
                    Vector3f soft = new Vector3f(thick).mul(2.6f);
                    band(vc, mat, baseCol, alpha * 0.3f, soft,
                            agx, agy, agz, atx, aty, atz, bgx, bgy, bgz, btx, bty, btz);
                }
            }
            com.visualmod.util.RenderFlush.flush(ctx.consumers(), RenderLayers.debugQuads());
        }

        private static float prevSwing;
        private static long slashStart;

        /**
         * Полоса из готовых кадров вместо ленты, собранной из точек взмаха. Новый взмах ловим
         * по скругу прогресса: handSwinging и прогресс < 1 бывают истинны и в простое, поэтому
         * на них играть анимацию нельзя.
         */
        private static void slash(WorldRenderContext ctx, Matrix4f mat, long now, float td,
                                  AbstractClientPlayerEntity p) {
            synchronized (trail) { trail.clear(); }
            float swing = p.getHandSwingProgress(td);
            if (swing + 0.2f < prevSwing) slashStart = now;
            prevSwing = swing;
            long dur = Math.max(140L, (long) (HitSplash.fade * 1000.0));
            if (slashStart == 0L || now - slashStart > dur) return;
            float t = Math.min(1f, (now - slashStart) / (float) dur);

            Vec3d pos = p.getLerpedPos(td);
            double yaw = Math.toRadians(p.getBodyYaw());
            double sinY = Math.sin(yaw), cosY = Math.cos(yaw);
            double side = p.getMainArm() == Arm.RIGHT ? 1 : -1;
            double arc = Math.sin(swing * Math.PI) * 0.9;
            double wx = pos.x + cosY * 0.45 * side - sinY * (0.3 + arc * 0.4);
            double wy = pos.y + 1.25 - swing * 0.45 + arc * 0.15;
            double wz = pos.z + sinY * 0.45 * side + cosY * (0.3 + arc * 0.4);
            Vector3f dir = new Vector3f((float) (-sinY * Math.cos(swing * Math.PI)),
                    (float) (0.55 + Math.sin(swing * Math.PI)),
                    (float) (cosY * Math.cos(swing * Math.PI))).normalize();

            float hl = Math.max(0.2f, HitSplash.blade) * 1.55f * Math.max(0.3f, HitSplash.slash) * 0.55f;
            Vector3f u = new Vector3f(dir).mul(hl);
            Vector3f toCam = new Vector3f((float) (camera(ctx).getCameraPos().x - wx),
                    (float) (camera(ctx).getCameraPos().y - wy), (float) (camera(ctx).getCameraPos().z - wz));
            Vector3f v = new Vector3f(u).cross(toCam);
            if (v.lengthSquared() < 1e-8f) return;
            v.normalize().mul(hl * 0.62f * Math.max(0.2f, HitSplash.width));

            VertexConsumer vc = ctx.consumers().getBuffer(
                    RenderLayers.entityTranslucent(SlashSprite.texture(HitSplash.color)));
            SlashSprite.quad(vc, mat,
                    (float) (wx + dir.x * hl * 1.05f), (float) (wy + dir.y * hl * 1.05f), (float) (wz + dir.z * hl * 1.05f),
                    u.x, u.y, u.z, v.x, v.y, v.z,
                    SlashSprite.frame(t), 1f - Math.max(0f, t - 0.6f) / 0.4f);
        }

        private static void band(VertexConsumer vc, Matrix4f mat, float[] col, float alpha, Vector3f thick,
                                 double agx, double agy, double agz, double atx, double aty, double atz,
                                 double bgx, double bgy, double bgz, double btx, double bty, double btz) {
            quad(vc, mat,
                    agx - thick.x, agy - thick.y, agz - thick.z,
                    atx - thick.x, aty - thick.y, atz - thick.z,
                    btx + thick.x, bty + thick.y, btz + thick.z,
                    bgx + thick.x, bgy + thick.y, bgz + thick.z,
                    Math.min(1f, col[0] * 1.25f), Math.min(1f, col[1] * 1.25f), Math.min(1f, col[2] * 1.25f), alpha);
            quad(vc, mat,
                    agx + thick.x, agy + thick.y, agz + thick.z,
                    atx + thick.x, aty + thick.y, atz + thick.z,
                    btx - thick.x, bty - thick.y, btz - thick.z,
                    bgx - thick.x, bgy - thick.y, bgz - thick.z,
                    col[0], col[1], col[2], alpha * 0.55f);
        }
    }

    public static final class SwordAuraEffect {
        private SwordAuraEffect() {}

        static void render(WorldRenderContext ctx, Matrix4f mat, long now) {
            SwordAura mod = module(SwordAura.class);
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mod == null || !mod.isEnabled() || mc.player == null || mc.world == null) return;
            if (mc.options.getPerspective().isFirstPerson()) return;

            AbstractClientPlayerEntity p = mc.player;
            if (!holdingSword(mc)) return;

            float td = mc.getRenderTickCounter().getTickProgress(false);
            Vec3d pos = p.getLerpedPos(td);
            double yaw = Math.toRadians(p.getBodyYaw());
            double sinY = Math.sin(yaw), cosY = Math.cos(yaw);
            double side = p.getMainArm() == Arm.RIGHT ? 1 : -1;
            double ax = pos.x + cosY * 0.45 * side;
            double ay = pos.y + 1.25;
            double az = pos.z + sinY * 0.45 * side;
            double sec = now / 1000.0;
            float intensity = SwordAura.intensity;

            float sw = p.getHandSwingProgress(td);
            Vec3d blade = new Vec3d(-sinY * Math.cos(sw * Math.PI), 0.55 + Math.sin(sw * Math.PI),
                    cosY * Math.cos(sw * Math.PI)).normalize();
            Vec3d horiz = new Vec3d(-sinY, 0, cosY);
            Vec3d u = blade.crossProduct(horiz);
            if (u.lengthSquared() < 1e-8) u = new Vec3d(cosY, 0, sinY);
            u = u.normalize();
            Vec3d v = blade.crossProduct(u).normalize();
            double bx = blade.x, by = blade.y, bz = blade.z;
            double ux = u.x, uy = u.y, uz = u.z;
            double vx = v.x, vy = v.y, vz = v.z;
            double blen = SwordAura.radius * 1.6;

            switch (SwordAura.style) {
                case "PARTICLES" -> {
                    if (now - lastAuraParticle > 40) {
                        lastAuraParticle = now;
                        DustParticleEffect d = new DustParticleEffect(SwordAura.color & 0xFFFFFF, 0.6f);
                        int n = Math.max(1, (int) (3 * intensity));
                        for (int i = 0; i < n; i++) {
                            double t = Math.random();
                            double ang = Math.random() * Math.PI * 2;
                            double rr = SwordAura.radius * 0.22 * Math.random();
                            double px = ax + bx * t * blen + (ux * Math.cos(ang) + vx * Math.sin(ang)) * rr;
                            double py = ay + by * t * blen + (uy * Math.cos(ang) + vy * Math.sin(ang)) * rr;
                            double pz = az + bz * t * blen + (uz * Math.cos(ang) + vz * Math.sin(ang)) * rr;
                            mc.world.addParticleClient(d, px, py, pz, 0, 0.02, 0);
                        }
                    }
                }
                case "RING" -> {
                    VertexConsumer vc = ctx.consumers().getBuffer(RenderLayers.debugQuads());
                    float[] col = hexF(SwordAura.color, Math.min(1f, 0.55f * intensity));
                    float t = 1f - (float) ((sec * Math.max(0.05f, SwordAura.speed)) % 1.0);
                    double cx = ax + bx * t * blen, cy = ay + by * t * blen, cz = az + bz * t * blen;
                    double rr = SwordAura.radius * (0.35 + 0.25 * (1f - t));
                    double w = 0.03;
                    int segs = 28;
                    for (int i = 0; i < segs; i++) {
                        double a1 = (i / (double) segs) * Math.PI * 2;
                        double a2 = ((i + 1) / (double) segs) * Math.PI * 2;
                        quad(vc, mat,
                                (float) (cx + (u.x * Math.cos(a1) + v.x * Math.sin(a1)) * (rr - w)),
                                (float) (cy + (u.y * Math.cos(a1) + v.y * Math.sin(a1)) * (rr - w)),
                                (float) (cz + (u.z * Math.cos(a1) + v.z * Math.sin(a1)) * (rr - w)),
                                (float) (cx + (u.x * Math.cos(a2) + v.x * Math.sin(a2)) * (rr - w)),
                                (float) (cy + (u.y * Math.cos(a2) + v.y * Math.sin(a2)) * (rr - w)),
                                (float) (cz + (u.z * Math.cos(a2) + v.z * Math.sin(a2)) * (rr - w)),
                                (float) (cx + (u.x * Math.cos(a2) + v.x * Math.sin(a2)) * (rr + w)),
                                (float) (cy + (u.y * Math.cos(a2) + v.y * Math.sin(a2)) * (rr + w)),
                                (float) (cz + (u.z * Math.cos(a2) + v.z * Math.sin(a2)) * (rr + w)),
                                (float) (cx + (u.x * Math.cos(a1) + v.x * Math.sin(a1)) * (rr + w)),
                                (float) (cy + (u.y * Math.cos(a1) + v.y * Math.sin(a1)) * (rr + w)),
                                (float) (cz + (u.z * Math.cos(a1) + v.z * Math.sin(a1)) * (rr + w)),
                                col[0], col[1], col[2], col[3] * (0.3f + 0.7f * t));
                    }
                    com.visualmod.util.RenderFlush.flush(ctx.consumers(), RenderLayers.debugQuads());
                }
                case "CLOTH" -> {
                    VertexConsumer vc = ctx.consumers().getBuffer(RenderLayers.debugQuads());
                    float[] col = hexF(SwordAura.color, Math.min(1f, 0.95f * intensity));
                    clothFrom(vc, mat, ax, ay, az, bx, by, bz, ux, uy, uz, vx, vy, vz,
                            (float) blen, (float) (SwordAura.radius * 0.5), col, sec);
                    com.visualmod.util.RenderFlush.flush(ctx.consumers(), RenderLayers.debugQuads());
                }
                default -> {
                    VertexConsumer vc = ctx.consumers().getBuffer(RenderLayers.debugQuads());
                    float[] col = hexF(SwordAura.color, Math.min(1f, 0.42f * intensity));
                    double w = 0.035;
                    for (int layer = 0; layer < 3; layer++) {
                        double ang = sec * Math.max(0.05f, SwordAura.speed) + layer * (Math.PI * 2 / 3);
                        double ca = Math.cos(ang), sa = Math.sin(ang);
                        Vec3d p1 = new Vec3d(u.x * ca + bx * sa, u.y * ca + by * sa, u.z * ca + bz * sa).normalize();
                        double rr = SwordAura.radius * (0.35 + layer * 0.28);
                        int segs = 26;
                        for (int i = 0; i < segs; i++) {
                            double a1 = (i / (double) segs) * Math.PI * 2;
                            double a2 = ((i + 1) / (double) segs) * Math.PI * 2;
                            double c1 = Math.cos(a1), s1 = Math.sin(a1), c2 = Math.cos(a2), s2 = Math.sin(a2);
                            quad(vc, mat,
                                    (float) (ax + (p1.x * c1 + v.x * s1) * (rr - w)),
                                    (float) (ay + (p1.y * c1 + v.y * s1) * (rr - w)),
                                    (float) (az + (p1.z * c1 + v.z * s1) * (rr - w)),
                                    (float) (ax + (p1.x * c2 + v.x * s2) * (rr - w)),
                                    (float) (ay + (p1.y * c2 + v.y * s2) * (rr - w)),
                                    (float) (az + (p1.z * c2 + v.z * s2) * (rr - w)),
                                    (float) (ax + (p1.x * c2 + v.x * s2) * (rr + w)),
                                    (float) (ay + (p1.y * c2 + v.y * s2) * (rr + w)),
                                    (float) (az + (p1.z * c2 + v.z * s2) * (rr + w)),
                                    (float) (ax + (p1.x * c1 + v.x * s1) * (rr + w)),
                                    (float) (ay + (p1.y * c1 + v.y * s1) * (rr + w)),
                                    (float) (az + (p1.z * c1 + v.z * s1) * (rr + w)),
                                    col[0], col[1], col[2], col[3] * (1f - layer * 0.28f));
                        }
                    }
                    com.visualmod.util.RenderFlush.flush(ctx.consumers(), RenderLayers.debugQuads());
                }
            }
        }

        private static final double[] K0 = new double[7];
        private static final double[] K1 = new double[7];

        /** Спиральная обмотка клинка — тот же расчёт, что и в руке (HandEffects.clothWrap). */
        private static void clothFrom(VertexConsumer vc, Matrix4f mat,
                                      double ax, double ay, double az,
                                      double bx, double by, double bz,
                                      double ux, double uy, double uz,
                                      double vx, double vy, double vz,
                                      float len, float rad, float[] col, double sec) {
            double turns = Math.max(1.5f, SwordAura.turns);
            int seg = (int) Math.min(120, Math.max(24, turns * 10));
            double pitch = len / turns;
            double hb = Math.min(rad * 0.62, pitch * 0.62);
            if (hb <= 0.0008) return;
            double spin = sec * Math.max(0.05f, SwordAura.speed) * 0.5;
            for (int i = 0; i < seg; i++) {
                clothPoint((double) i / seg, turns, spin, len, rad, ax, ay, az, bx, by, bz, ux, uy, uz, vx, vy, vz, K0);
                clothPoint((double) (i + 1) / seg, turns, spin, len, rad, ax, ay, az, bx, by, bz, ux, uy, uz, vx, vy, vz, K1);
                double sh = 0.58 + 0.42 * K0[6];
                double r = Math.min(1, col[0] * sh + 0.05), g = Math.min(1, col[1] * sh + 0.04), b = Math.min(1, col[2] * sh + 0.06);
                quad(vc, mat,
                        K0[0] - K0[3] * hb, K0[1] - K0[4] * hb, K0[2] - K0[5] * hb,
                        K1[0] - K1[3] * hb, K1[1] - K1[4] * hb, K1[2] - K1[5] * hb,
                        K1[0] + K1[3] * hb, K1[1] + K1[4] * hb, K1[2] + K1[5] * hb,
                        K0[0] + K0[3] * hb, K0[1] + K0[4] * hb, K0[2] + K0[5] * hb,
                        (float) r, (float) g, (float) b, col[3]);
                quad(vc, mat,
                        K0[0] - K0[3] * hb * 2.0, K0[1] - K0[4] * hb * 2.0, K0[2] - K0[5] * hb * 2.0,
                        K1[0] - K1[3] * hb * 2.0, K1[1] - K1[4] * hb * 2.0, K1[2] - K1[5] * hb * 2.0,
                        K1[0] + K1[3] * hb * 2.0, K1[1] + K1[4] * hb * 2.0, K1[2] + K1[5] * hb * 2.0,
                        K0[0] + K0[3] * hb * 2.0, K0[1] + K0[4] * hb * 2.0, K0[2] + K0[5] * hb * 2.0,
                        (float) r, (float) g, (float) b, col[3] * 0.12f);
            }
        }

        private static void clothPoint(double f, double turns, double spin, double len, double rad,
                                       double ax, double ay, double az,
                                       double bx, double by, double bz,
                                       double ux, double uy, double uz,
                                       double vx, double vy, double vz, double[] o) {
            double a = spin + f * turns * Math.PI * 2;
            double ca = Math.cos(a), sa = Math.sin(a);
            double ex = ux * ca + vx * sa, ey = uy * ca + vy * sa, ez = uz * ca + vz * sa;
            double rx = -ux * sa + vx * ca, ry = -uy * sa + vy * ca, rz = -uz * sa + vz * ca;
            double rr = rad * (1 + 0.05 * Math.sin(a * 2 + spin * 1.7));
            o[0] = ax + bx * f * len + ex * rr;
            o[1] = ay + by * f * len + ey * rr;
            o[2] = az + bz * f * len + ez * rr;
            double circum = turns * Math.PI * 2 * rad;
            double tl = Math.hypot(circum, len);
            double tx = (circum * ex + len * bx) / tl;
            double ty = (circum * ey + len * by) / tl;
            double tz = (circum * ez + len * bz) / tl;
            double dt = ex * tx + ey * ty + ez * tz;
            double wx = ex - dt * tx, wy = ey - dt * ty, wz = ez - dt * tz;
            double wl = Math.sqrt(wx * wx + wy * wy + wz * wz);
            if (wl > 1e-5) { wx /= wl; wy /= wl; wz /= wl; }
            o[3] = wx; o[4] = wy; o[5] = wz;
            o[6] = Math.max(0, rx * 0.34 + ry * 0.62 + rz * 0.71);
        }
    }

    public static final class CriticalEffectImpl {
        private CriticalEffectImpl() {}

        static void onHit(Entity target, boolean crit, long now) {
            CriticalEffect mod = module(CriticalEffect.class);
            if (mod == null || !mod.isEnabled() || !crit) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            double cx = target.getX();
            double cy = target.getY() + target.getHeight() * 0.6;
            double cz = target.getZ();

            int count = Math.max(4, (int) (10 * CriticalEffect.size));

            switch (CriticalEffect.style) {
                case "SPARKLE" -> {
                    DustParticleEffect d = new DustParticleEffect(CriticalEffect.color & 0xFFFFFF, 1.0f);
                    for (int i = 0; i < count; i++) {
                        double ang = Math.random() * Math.PI * 2;
                        double sp = 0.25 * CriticalEffect.size;
                        mc.world.addParticleClient(ParticleTypes.END_ROD, cx, cy, cz,
                                Math.cos(ang) * sp, 0.15 + Math.random() * 0.15, Math.sin(ang) * sp);
                        if (i % 2 == 0) {
                            mc.world.addParticleClient(d, cx, cy, cz,
                                    (Math.random() - 0.5) * 0.2, 0.1, (Math.random() - 0.5) * 0.2);
                        }
                    }
                }
                case "EXPLOSION" -> {
                    mc.world.addParticleClient(ParticleTypes.EXPLOSION_EMITTER, cx, cy, cz, 0, 0, 0);
                    DustParticleEffect d = new DustParticleEffect(CriticalEffect.color & 0xFFFFFF, 1.4f);
                    for (int i = 0; i < count * 2; i++) {
                        double ang = Math.random() * Math.PI * 2;
                        double sp = 0.4 * CriticalEffect.size;
                        mc.world.addParticleClient(d, cx, cy, cz,
                                Math.cos(ang) * sp, Math.random() * 0.3, Math.sin(ang) * sp);
                    }
                }
                default -> {
                    DustParticleEffect d = new DustParticleEffect(CriticalEffect.color & 0xFFFFFF, 1.0f);
                    for (int i = 0; i < count; i++) {
                        double ang = (i / (double) count) * Math.PI * 2;
                        double sp = 0.35 * CriticalEffect.size;
                        mc.world.addParticleClient(d, cx, cy, cz,
                                Math.cos(ang) * sp, 0.12, Math.sin(ang) * sp);
                        mc.world.addParticleClient(ParticleTypes.CRIT, cx, cy, cz,
                                Math.cos(ang) * sp * 1.3, 0.2, Math.sin(ang) * sp * 1.3);
                    }
                }
            }
        }
    }
}

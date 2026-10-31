package com.visualmod.module.modules.movement;

import net.minecraft.client.render.RenderLayers;
import com.visualmod.VisualsMod;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class MovementEffects {

    private MovementEffects() {}

    static { WorldRenderEvents.AFTER_ENTITIES.register(MovementEffects::render); }
    public static void init() {}

    private static final List<StepRing> stepRings = new ArrayList<>();
    private static final List<JumpGlow> jumpGlows = new ArrayList<>();
    private static final List<TrailPt> jumpTrail = new ArrayList<>();
    private static boolean wasOnGround = true;
    private static double prevX, prevZ;
    private static long lastStepTime = 0;
    private static long lastTrailSample = 0;

    private static final class StepRing {
        final double x, y, z;
        final long time;
        StepRing(double x, double y, double z, long time) { this.x = x; this.y = y; this.z = z; this.time = time; }
    }

    private static final class JumpGlow {
        final double x, y, z;
        final long time;
        JumpGlow(double x, double y, double z, long time) { this.x = x; this.y = y; this.z = z; this.time = time; }
    }

    private static final class TrailPt {
        final double x, y, z;
        final long time;
        TrailPt(double x, double y, double z, long time) { this.x = x; this.y = y; this.z = z; this.time = time; }
    }

    static {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null || mc.player == null) return;
            if (VisualsMod.moduleManager == null) return;

            long now = System.currentTimeMillis();
            AbstractClientPlayerEntity p = mc.player;

            StepEffect stepMod = VisualsMod.moduleManager.getModule(StepEffect.class);
            boolean stepActive = stepMod != null && stepMod.isEnabled() && p.isOnGround();
            if (stepActive) {
                double movedX = p.getX() - prevX;
                double movedZ = p.getZ() - prevZ;
                double dist = Math.sqrt(movedX * movedX + movedZ * movedZ);
                double stepInterval = 1.15;
                if (dist > stepInterval && now - lastStepTime > 280) {
                    lastStepTime = now;
                    prevX = p.getX();
                    prevZ = p.getZ();
                    double fx = -Math.sin(Math.toRadians(p.bodyYaw));
                    double fz = Math.cos(Math.toRadians(p.bodyYaw));
                    double sx = p.getX() + fx * 0.15 + (Math.random() - 0.5) * 0.1;
                    double sy = p.getY() + 0.05;
                    double sz = p.getZ() + fz * 0.15 + (Math.random() - 0.5) * 0.1;

                    switch (StepEffect.style) {
                        case "DUST" -> {
                            DustParticleEffect d = new DustParticleEffect(StepEffect.color & 0xFFFFFF, 0.55f + StepEffect.size * 0.9f);
                            for (int i = 0; i < 5; i++) {
                                mc.world.addParticleClient(d, sx, sy, sz,
                                        (Math.random() - 0.5) * 0.1, 0.03 + Math.random() * 0.05, (Math.random() - 0.5) * 0.1);
                            }
                        }
                        case "RIPPLE", "CIRCLES" -> {
                            synchronized (stepRings) {
                                stepRings.add(new StepRing(sx, sy, sz, now));
                            }
                            if (StepEffect.style.equals("RIPPLE")) {
                                DustParticleEffect d = new DustParticleEffect(StepEffect.color & 0xFFFFFF, 0.4f);
                                mc.world.addParticleClient(d, sx, sy + 0.02, sz, 0, 0.01, 0);
                            }
                        }
                    }
                }
            } else {
                prevX = p.getX();
                prevZ = p.getZ();
            }

            BunnyHop hopMod = VisualsMod.moduleManager.getModule(BunnyHop.class);
            if (hopMod != null && hopMod.isEnabled()) {
                boolean onGround = p.isOnGround();
                if (wasOnGround && !onGround && p.getVelocity().y > 0.1) {
                    double px = p.getX(), py = p.getY(), pz = p.getZ();
                    switch (BunnyHop.style) {
                        case "SPARKLE" -> {
                            DustParticleEffect d = new DustParticleEffect(0xFFFFFF & 0xFFFFFF, 0.8f);
                            for (int i = 0; i < 8; i++) {
                                double ang = Math.random() * Math.PI * 2;
                                mc.world.addParticleClient(ParticleTypes.END_ROD, px, py + 0.1, pz,
                                        Math.cos(ang) * 0.15, 0.1 + Math.random() * 0.1, Math.sin(ang) * 0.15);
                            }
                            mc.world.addParticleClient(d, px, py + 0.2, pz, 0, 0.15, 0);
                        }
                        case "TRAIL" -> {
                            synchronized (jumpGlows) {
                                jumpGlows.add(new JumpGlow(px, py + 0.2, pz, now));
                            }
                        }
                        default -> {
                            synchronized (jumpGlows) {
                                jumpGlows.add(new JumpGlow(px, py + 0.2, pz, now));
                            }
                            DustParticleEffect d = new DustParticleEffect(0xAA88FF & 0xFFFFFF, 1.0f);
                            for (int i = 0; i < 6; i++) {
                                double ang = (i / 6.0) * Math.PI * 2;
                                mc.world.addParticleClient(d, px + Math.cos(ang) * 0.2, py + 0.05, pz + Math.sin(ang) * 0.2,
                                        Math.cos(ang) * 0.08, 0.05, Math.sin(ang) * 0.08);
                            }
                        }
                    }
                }
                wasOnGround = onGround;

                if (BunnyHop.style.equals("TRAIL") && !onGround && now - lastTrailSample > 30) {
                    lastTrailSample = now;
                    synchronized (jumpTrail) {
                        jumpTrail.add(new TrailPt(p.getX(), p.getY() + 0.3, p.getZ(), now));
                    }
                }
            } else {
                wasOnGround = true;
            }
        });
    }

    private static void render(WorldRenderContext ctx) {
        try {
            if (com.visualmod.util.IrisCompat.skipWorldPass()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null || mc.player == null) return;
            if (VisualsMod.moduleManager == null) return;
            if (ctx.matrices() == null || ctx.consumers() == null) return;

            long now = System.currentTimeMillis();
            Vec3d cam = ctx.gameRenderer().getCamera().getCameraPos();

            ctx.matrices().push();
            ctx.matrices().translate(-cam.x, -cam.y, -cam.z);
            Matrix4f mat = ctx.matrices().peek().getPositionMatrix();

            renderStepRings(ctx, mat, now, cam);
            renderJumpGlows(ctx, mat, now, cam);
            renderJumpTrail(ctx, mat, now, cam);

            ctx.matrices().pop();
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("MovementEffects", t);
        }
    }

    private static void renderStepRings(WorldRenderContext ctx, Matrix4f mat, long now, Vec3d cam) {
        StepEffect mod = VisualsMod.moduleManager.getModule(StepEffect.class);
        if (mod == null || !mod.isEnabled()) {
            synchronized (stepRings) { stepRings.clear(); }
            return;
        }
        long durMs = Math.max(100, (long) (StepEffect.duration * 1000));
        boolean any = false;
        synchronized (stepRings) {
            Iterator<StepRing> it = stepRings.iterator();
            while (it.hasNext()) {
                StepRing r = it.next();
                long age = now - r.time;
                if (age > durMs) { it.remove(); continue; }
                any = true;
                float t = age / (float) durMs;
                float alpha = (1f - t) * 0.75f;
                float radius = 0.22f + StepEffect.size * (0.25f + t * 0.85f);
                float w = 0.035f + 0.075f * StepEffect.size;
                float[] col = hexF(StepEffect.color, alpha);

                VertexConsumer vc = ctx.consumers().getBuffer(RenderLayers.debugQuads());
                int segs = 24;
                for (int i = 0; i < segs; i++) {
                    double a1 = (i / (double) segs) * Math.PI * 2;
                    double a2 = ((i + 1) / (double) segs) * Math.PI * 2;
                    quad(vc, mat,
                            r.x + Math.cos(a1) * (radius - w), r.y, r.z + Math.sin(a1) * (radius - w),
                            r.x + Math.cos(a2) * (radius - w), r.y, r.z + Math.sin(a2) * (radius - w),
                            r.x + Math.cos(a2) * (radius + w), r.y + 0.01, r.z + Math.sin(a2) * (radius + w),
                            r.x + Math.cos(a1) * (radius + w), r.y + 0.01, r.z + Math.sin(a1) * (radius + w),
                            col[0], col[1], col[2], col[3]);
                }
            }
        }
        if (any) com.visualmod.util.RenderFlush.flush(ctx.consumers(), RenderLayers.debugQuads());
    }

    private static void renderJumpGlows(WorldRenderContext ctx, Matrix4f mat, long now, Vec3d cam) {
        BunnyHop mod = VisualsMod.moduleManager.getModule(BunnyHop.class);
        if (mod == null || !mod.isEnabled()) {
            synchronized (jumpGlows) { jumpGlows.clear(); }
            return;
        }
        long durMs = Math.max(100, (long) (BunnyHop.trailDuration * 1000));
        boolean any = false;
        synchronized (jumpGlows) {
            Iterator<JumpGlow> it = jumpGlows.iterator();
            while (it.hasNext()) {
                JumpGlow g = it.next();
                long age = now - g.time;
                if (age > durMs) { it.remove(); continue; }
                any = true;
                float t = age / (float) durMs;
                float alpha = (1f - t) * 0.5f * BunnyHop.intensity;
                float radius = 0.3f * BunnyHop.intensity * (0.5f + t * 0.8f);
                float[] col = hexF(0xAA88FF, alpha);

                VertexConsumer vc = ctx.consumers().getBuffer(RenderLayers.debugQuads());
                float[] bb = billboardBasis(cam, g.x, g.y, g.z, radius);
                float rx = bb[0], ry = bb[1], rz = bb[2];
                float ux = bb[3], uy = bb[4], uz = bb[5];

                quad(vc, mat,
                        g.x - rx, g.y - ry, g.z - rz,
                        g.x + rx, g.y + ry, g.z + rz,
                        g.x + rx + ux, g.y + ry + uy, g.z + rz + uz,
                        g.x - rx + ux, g.y - ry + uy, g.z - rz + uz,
                        col[0], col[1], col[2], col[3]);
                quad(vc, mat,
                        g.x - rx, g.y - ry, g.z - rz,
                        g.x - rx - ux, g.y - ry - uy, g.z - rz - uz,
                        g.x + rx - ux, g.y + ry - uy, g.z + rz - uz,
                        g.x + rx, g.y + ry, g.z + rz,
                        col[0], col[1], col[2], col[3]);
            }
        }
        if (any) com.visualmod.util.RenderFlush.flush(ctx.consumers(), RenderLayers.debugQuads());
    }

    private static void renderJumpTrail(WorldRenderContext ctx, Matrix4f mat, long now, Vec3d cam) {
        BunnyHop mod = VisualsMod.moduleManager.getModule(BunnyHop.class);
        if (mod == null || !mod.isEnabled()) {
            synchronized (jumpTrail) { jumpTrail.clear(); }
            return;
        }
        long durMs = Math.max(100, (long) (BunnyHop.trailDuration * 1000));
        List<TrailPt> alive;
        synchronized (jumpTrail) {
            jumpTrail.removeIf(pt -> now - pt.time > durMs);
            alive = new ArrayList<>(jumpTrail);
        }
        if (alive.size() < 2) return;

        VertexConsumer vc = ctx.consumers().getBuffer(RenderLayers.debugQuads());
        float w = 0.04f * BunnyHop.intensity;
        float[] col = hexF(0xAA88FF, 1f);

        for (int i = 1; i < alive.size(); i++) {
            TrailPt a = alive.get(i - 1), b = alive.get(i);
            float age = (now - b.time) / (float) durMs;
            float alpha = (1f - age) * 0.5f;
            if (alpha <= 0.01f) continue;

            Vector3f dir = new Vector3f((float) (b.x - a.x), (float) (b.y - a.y), (float) (b.z - a.z));
            Vector3f toCam = new Vector3f((float) (cam.x - b.x), (float) (cam.y - b.y), (float) (cam.z - b.z));
            Vector3f sideV = new Vector3f(dir).cross(toCam);
            if (sideV.lengthSquared() < 1e-8f) continue;
            sideV.normalize().mul(w * (1f - age * 0.3f));

            quad(vc, mat,
                    a.x - sideV.x, a.y - sideV.y, a.z - sideV.z,
                    b.x - sideV.x, b.y - sideV.y, b.z - sideV.z,
                    b.x + sideV.x, b.y + sideV.y, b.z + sideV.z,
                    a.x + sideV.x, a.y + sideV.y, a.z + sideV.z,
                    col[0], col[1], col[2], alpha);
        }
        com.visualmod.util.RenderFlush.flush(ctx.consumers(), RenderLayers.debugQuads());
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
}

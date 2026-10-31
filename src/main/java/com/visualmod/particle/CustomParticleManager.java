package com.visualmod.particle;

import net.minecraft.client.render.RenderLayers;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;

public final class CustomParticleManager {
    private CustomParticleManager() {}

    private static final class P {
        Identifier texture;
        double x, y, z, vx, vy, vz;
        int age = 0, life;
        float scale;
        int color = 0xFFFFFF;
        float gravity = 0.015f;
    }

    private static final List<P> particles = new ArrayList<>();

    public static void spawn(String name, double x, double y, double z, double vx, double vy, double vz) {
        spawn(name, x, y, z, vx, vy, vz, 0f);
    }

    public static void spawn(String name, double x, double y, double z, double vx, double vy, double vz, float scale) {
        spawn(name, x, y, z, vx, vy, vz, scale, 0xFFFFFF, 0.015f, 0);
    }

    /** life <= 0 keeps the default short combat lifetime; gravity 0 makes a sprite drift instead of fall. */
    public static void spawn(String name, double x, double y, double z, double vx, double vy, double vz,
                             float scale, int color, float gravity, int life) {
        if (com.visualmod.module.modules.render.AutoReduce.shouldSkipParticle()) return;
        P p = new P();
        p.texture = Identifier.of("visualmod", "textures/particle/" + name + ".png");
        p.x = x; p.y = y; p.z = z;
        p.vx = vx; p.vy = vy; p.vz = vz;
        p.life = life > 0 ? life : 16 + (int) (Math.random() * 12);
        p.scale = scale > 0f ? scale : 0.35f + (float) (Math.random() * 0.25f);
        p.color = color & 0xFFFFFF;
        p.gravity = gravity;
        particles.add(p);
    }

    public static int count() {
        return particles.size();
    }

    public static void clear() {
        particles.clear();
    }

    public static void tick() {
        particles.removeIf(p -> {
            p.x += p.vx;
            p.y += p.vy;
            p.z += p.vz;
            p.vy -= p.gravity;
            p.age++;
            return p.age > p.life;
        });
    }

    public static void render(WorldRenderContext ctx) {
        if (particles.isEmpty()) return;

        Vec3d cam = ctx.gameRenderer().getCamera().getCameraPos();
        Quaternionf camRot = ctx.gameRenderer().getCamera().getRotation();
        Vector3f right = new Vector3f(1, 0, 0).rotate(camRot);
        Vector3f up = new Vector3f(0, 1, 0).rotate(camRot);

        MatrixStack matrices = ctx.matrices();
        VertexConsumerProvider consumers = ctx.consumers();
        if (matrices == null || consumers == null) return;

        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        Set<Identifier> used = new LinkedHashSet<>();
        for (P p : particles) {
            used.add(p.texture);
            VertexConsumer buf = consumers.getBuffer(RenderLayers.entityTranslucent(p.texture));
            float s = p.scale;
            float fade = 1f - (p.age / (float) p.life);
            int a = (int) (255 * Math.max(0f, fade));
            int color = (a << 24) | p.color;

            float px = (float) p.x, py = (float) p.y, pz = (float) p.z;
            Vector3f c1 = new Vector3f(right).mul(-s).add(new Vector3f(up).mul(-s)).add(px, py, pz);
            Vector3f c2 = new Vector3f(right).mul(s).add(new Vector3f(up).mul(-s)).add(px, py, pz);
            Vector3f c3 = new Vector3f(right).mul(s).add(new Vector3f(up).mul(s)).add(px, py, pz);
            Vector3f c4 = new Vector3f(right).mul(-s).add(new Vector3f(up).mul(s)).add(px, py, pz);

            int light = 0xF000F0;
            buf.vertex(mat, c1.x, c1.y, c1.z).color(color).texture(0f, 1f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
            buf.vertex(mat, c2.x, c2.y, c2.z).color(color).texture(1f, 1f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
            buf.vertex(mat, c3.x, c3.y, c3.z).color(color).texture(1f, 0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
            buf.vertex(mat, c4.x, c4.y, c4.z).color(color).texture(0f, 0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
        }

        matrices.pop();

        if (consumers instanceof VertexConsumerProvider.Immediate imm) {
            for (Identifier tex : used) imm.draw(RenderLayers.entityTranslucent(tex));
        }
    }
}

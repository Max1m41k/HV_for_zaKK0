package com.visualmod.shaders;

import com.visualmod.util.McCompat;
import net.minecraft.client.render.RenderLayers;
import com.visualmod.VisualsMod;
import com.visualmod.module.modules.render.Shaders;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SoftShadows {

    private static final int RINGS = 4;
    private static final int SEG = 14;

    private SoftShadows() {}

    public static void render(WorldRenderContext ctx) {
        try {
            if (VisualsMod.moduleManager == null) return;
            Shaders m = VisualsMod.moduleManager.getModule(Shaders.class);
            if (m == null || !m.isEnabled() || Shaders.shadowReach() <= 0) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null || ctx.gameRenderer().getCamera() == null) return;

            double reach = Shaders.shadowReach();
            Vec3d cam = ctx.gameRenderer().getCamera().getCameraPos();
            Box area = new Box(mc.player.getX() - reach, mc.player.getY() - 6, mc.player.getZ() - reach,
                    mc.player.getX() + reach, mc.player.getY() + 6, mc.player.getZ() + reach);
            List<Entity> near = new ArrayList<>(mc.world.getEntitiesByClass(Entity.class, area,
                    e -> e != null && e.isOnGround() && !e.isInvisible() && e.isAlive()));
            if (near.isEmpty()) return;
            Vec3d pp = McCompat.pos(mc.player);
            near.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(pp)));
            int limit = Math.min(near.size(), Shaders.shadowMax());

            ctx.matrices().push();
            ctx.matrices().translate(-cam.x, -cam.y, -cam.z);
            org.joml.Matrix4f mat = ctx.matrices().peek().getPositionMatrix();
            VertexConsumer buf = ctx.consumers().getBuffer(RenderLayers.debugQuads());

            float base = Shaders.shadowAlpha();
            float size = Shaders.shadowSize();
            for (int i = 0; i < limit; i++) {
                Entity e = near.get(i);
                Box bb = e.getBoundingBox();
                double r = Math.max(bb.getLengthX(), bb.getLengthZ()) * 0.62 * size;
                if (r < 0.12) continue;
                double y = bb.minY + 0.012;
                double fade = 1.0 - Math.sqrt(e.squaredDistanceTo(pp)) / (reach * 1.05);
                if (fade <= 0.02) continue;
                disc(buf, mat, McCompat.pos(e).x, y, McCompat.pos(e).z, r, base * (float) fade);
            }
            ctx.matrices().pop();
            com.visualmod.util.RenderFlush.flush(ctx.consumers(), RenderLayers.debugQuads());
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("SoftShadows", t);
        }
    }

    private static void disc(VertexConsumer buf, org.joml.Matrix4f mat,
                             double cx, double cy, double cz, double r, float base) {
        double[] rr = new double[RINGS + 1];
        float[] al = new float[RINGS + 1];
        for (int i = 0; i <= RINGS; i++) {
            double f = i / (double) RINGS;
            rr[i] = r * f;
            al[i] = base * (float) Math.pow(1.0 - f, 1.35);
        }
        for (int ring = 0; ring < RINGS; ring++) {
            double r0 = rr[ring], r1 = rr[ring + 1];
            float a0 = al[ring], a1 = al[ring + 1];
            for (int s = 0; s < SEG; s++) {
                double t0 = (Math.PI * 2 * s) / SEG, t1 = (Math.PI * 2 * (s + 1)) / SEG;
                double c0 = Math.cos(t0), s0 = Math.sin(t0), c1 = Math.cos(t1), s1 = Math.sin(t1);
                quad(buf, mat,
                        cx + c0 * r0, cy, cz + s0 * r0, a0,
                        cx + c1 * r0, cy, cz + s1 * r0, a0,
                        cx + c1 * r1, cy, cz + s1 * r1, a1,
                        cx + c0 * r1, cy, cz + s0 * r1, a1);
                quad(buf, mat,
                        cx + c0 * r1, cy, cz + s0 * r1, a1,
                        cx + c1 * r1, cy, cz + s1 * r1, a1,
                        cx + c1 * r0, cy, cz + s1 * r0, a0,
                        cx + c0 * r0, cy, cz + s0 * r0, a0);
            }
        }
    }

    private static void quad(VertexConsumer buf, org.joml.Matrix4f mat,
                             double x1, double y1, double z1, float a1,
                             double x2, double y2, double z2, float a2,
                             double x3, double y3, double z3, float a3,
                             double x4, double y4, double z4, float a4) {
        float r = 0.015f, g = 0.02f, b = 0.05f;
        buf.vertex(mat, (float) x1, (float) y1, (float) z1).color(r, g, b, a1);
        buf.vertex(mat, (float) x2, (float) y2, (float) z2).color(r, g, b, a2);
        buf.vertex(mat, (float) x3, (float) y3, (float) z3).color(r, g, b, a3);
        buf.vertex(mat, (float) x4, (float) y4, (float) z4).color(r, g, b, a4);
    }
}

package com.visualmod.shaders;

import net.minecraft.client.render.RenderLayers;
import com.visualmod.VisualsMod;
import com.visualmod.module.modules.render.Shaders;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class LightBloom {

    private record Src(float x, float y, float z, float r, float g, float b, float strength) {}

    private static final List<Src> CACHE = new ArrayList<>();
    private static double lastX = Double.NaN, lastY, lastZ;
    private static int age;

    private LightBloom() {}

    public static void render(WorldRenderContext ctx) {
        try {
            if (VisualsMod.moduleManager == null) return;
            Shaders m = VisualsMod.moduleManager.getModule(Shaders.class);
            if (m == null || !m.isEnabled() || Shaders.glow() <= 0.01f) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            ClientWorld world = mc.world;
            if (world == null || ctx.gameRenderer().getCamera() == null) return;

            Vec3d cam = ctx.gameRenderer().getCamera().getCameraPos();
            rescan(world, cam, Shaders.glowReach());
            if (CACHE.isEmpty()) return;

            ctx.matrices().push();
            ctx.matrices().translate(-cam.x, -cam.y, -cam.z);
            org.joml.Matrix4f mat = ctx.matrices().peek().getPositionMatrix();
            VertexConsumer buf = ctx.consumers().getBuffer(RenderLayers.debugQuads());

            float k = Shaders.glow();
            for (Src s : CACHE) {
                float dx = (float) (s.x() - cam.x), dy = (float) (s.y() - cam.y), dz = (float) (s.z() - cam.z);
                float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                float fade = 1f - dist / (Shaders.glowReach() * 1.15f);
                if (fade <= 0.02f) continue;
                float a = k * s.strength() * fade * fade;
                if (a <= 0.004f) continue;
                cross(buf, mat, s, a);
            }
            ctx.matrices().pop();
            com.visualmod.util.RenderFlush.flush(ctx.consumers(), RenderLayers.debugQuads());
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("LightBloom", t);
        }
    }

    private static void cross(VertexConsumer buf, org.joml.Matrix4f mat, Src s, float a) {
        float x = s.x(), y = s.y(), z = s.z();
        float r = s.r(), g = s.g(), b = s.b();
        for (int plane = 0; plane < 3; plane++) {
            double x0 = x, y0 = y, z0 = z, x1 = x, y1 = y, z1 = z, x2 = x, y2 = y, z2 = z, x3 = x, y3 = y, z3 = z;
            float u = 0.62f, v = 0.62f;
            if (plane == 0) {
                x0 -= u; z0 -= v; x1 += u; z1 -= v; x2 += u; z2 += v; x3 -= u; z3 += v;
            } else if (plane == 1) {
                x0 -= u; y0 -= v; x1 += u; y1 -= v; x2 += u; y2 += v; x3 -= u; y3 += v;
            } else {
                z0 -= u; y0 -= v; z1 -= v; y1 += v; z2 += u; y2 += v; z3 += u; y3 -= v;
            }
            quad(buf, mat, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, r, g, b, a * 0.30f);
            quad(buf, mat,
                    (x0 + x) * 0.55f, (y0 + y) * 0.55f, (z0 + z) * 0.55f,
                    (x1 + x) * 0.55f, (y1 + y) * 0.55f, (z1 + z) * 0.55f,
                    (x2 + x) * 0.55f, (y2 + y) * 0.55f, (z2 + z) * 0.55f,
                    (x3 + x) * 0.55f, (y3 + y) * 0.55f, (z3 + z) * 0.55f,
                    Math.min(1f, r * 1.15f + 0.15f), Math.min(1f, g * 1.15f + 0.12f),
                    Math.min(1f, b * 1.15f + 0.10f), a * 0.55f);
        }
    }

    private static void quad(VertexConsumer buf, org.joml.Matrix4f mat,
                             double x0, double y0, double z0, double x1, double y1, double z1,
                             double x2, double y2, double z2, double x3, double y3, double z3,
                             float r, float g, float b, float a) {
        buf.vertex(mat, (float) x0, (float) y0, (float) z0).color(r, g, b, a);
        buf.vertex(mat, (float) x1, (float) y1, (float) z1).color(r, g, b, a);
        buf.vertex(mat, (float) x2, (float) y2, (float) z2).color(r, g, b, a);
        buf.vertex(mat, (float) x3, (float) y3, (float) z3).color(r, g, b, a);
    }

    private static void rescan(ClientWorld world, Vec3d cam, int reach) {
        boolean moved = Double.isNaN(lastX)
                || Math.abs(cam.x - lastX) > 1.0 || Math.abs(cam.y - lastY) > 1.0 || Math.abs(cam.z - lastZ) > 1.0;
        if (!moved && ++age < 10) return;
        age = 0;
        lastX = cam.x; lastY = cam.y; lastZ = cam.z;
        CACHE.clear();

        BlockPos.Mutable pos = new BlockPos.Mutable();
        int cx = (int) Math.floor(cam.x), cy = (int) Math.floor(cam.y), cz = (int) Math.floor(cam.z);
        for (int dx = -reach; dx <= reach && CACHE.size() < MAX; dx++) {
            for (int dy = -6; dy <= 6 && CACHE.size() < MAX; dy++) {
                for (int dz = -reach; dz <= reach && CACHE.size() < MAX; dz++) {
                    pos.set(cx + dx, cy + dy, cz + dz);
                    int light = world.getBlockState(pos).getLuminance();
                    if (light <= 0) continue;
                    if (isHidden(world, pos, cam)) continue;
                    float[] col = tint(world, pos);
                    float strength = 0.25f + 0.75f * (light / 15f);
                    CACHE.add(new Src(pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f,
                            col[0], col[1], col[2], strength));
                }
            }
        }
        CACHE.sort(Comparator.comparingDouble(s -> (s.x() - cam.x) * (s.x() - cam.x)
                + (s.y() - cam.y) * (s.y() - cam.y) + (s.z() - cam.z) * (s.z() - cam.z)));
    }

    private static final int MAX = 48;

    private static boolean isHidden(ClientWorld world, BlockPos src, Vec3d cam) {
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int i = 1; i <= 3; i++) {
            double t = i / 4.0;
            p.set((int) Math.floor(src.getX() + 0.5 + (cam.x - src.getX() - 0.5) * t),
                    (int) Math.floor(src.getY() + 0.5 + (cam.y - src.getY() - 0.5) * t),
                    (int) Math.floor(src.getZ() + 0.5 + (cam.z - src.getZ() - 0.5) * t));
            if (p.equals(src)) continue;
            var st = world.getBlockState(p);
            if (st.isOpaque() && st.getLuminance() == 0) return true;
        }
        return false;
    }

    private static float[] tint(ClientWorld world, BlockPos pos) {
        String id = Registries.BLOCK.getId(world.getBlockState(pos).getBlock()).getPath();
        if (id.startsWith("soul_") || id.contains("end_rod") || id.contains("sea_lantern")) {
            return new float[]{0.45f, 0.82f, 1.0f};
        }
        if (id.contains("redstone") || id.contains("nether_portal") || id.startsWith("copper_bulb")) {
            return new float[]{1.0f, 0.38f, 0.30f};
        }
        if (id.contains("froglight")) {
            if (id.startsWith("verdant")) return new float[]{0.55f, 1.0f, 0.6f};
            if (id.startsWith("cerise")) return new float[]{1.0f, 0.45f, 0.75f};
            return new float[]{1.0f, 0.9f, 0.55f};
        }
        if (id.contains("shroomlight") || id.contains("glowstone") || id.contains("beacon")) {
            return new float[]{1.0f, 0.85f, 0.5f};
        }
        return new float[]{1.0f, 0.68f, 0.32f};
    }
}

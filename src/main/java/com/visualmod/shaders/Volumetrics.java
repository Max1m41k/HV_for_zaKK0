package com.visualmod.shaders;

import net.minecraft.client.render.RenderLayers;
import com.visualmod.VisualsMod;
import com.visualmod.module.modules.render.Shaders;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Fake volumetrics for the Shaders module: sun shafts, ground mist sheets and backlit dust motes.
 * Everything is drawn as additive translucent quads in world space, so no post-processing pipeline is needed.
 */
public final class Volumetrics {

    private Volumetrics() {}

    public static void render(WorldRenderContext ctx) {
        try {
            if (VisualsMod.moduleManager == null) return;
            Shaders m = VisualsMod.moduleManager.getModule(Shaders.class);
            if (m == null || !m.isEnabled()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            ClientWorld world = mc.world;
            if (world == null || mc.player == null || ctx.gameRenderer().getCamera() == null) return;
            if (!com.visualmod.module.modules.render.CustomSky.isOverworld(world)) return;
            if (Shaders.shaftsF() <= 0.01f && Shaders.mistF() <= 0.01f && Shaders.motesF() <= 0.01f) return;

            Vec3d cam = ctx.gameRenderer().getCamera().getCameraPos();
            float tick = mc.getRenderTickCounter().getTickProgress(false);
            float day = Shaders.daylight(world, tick);
            float sky = com.visualmod.util.McCompat.skyAngle(world, tick);
            float elev = (float) Math.sin(Math.PI * 2 * sky);
            float rain = world.getRainGradient(tick) * 0.8f + world.getThunderGradient(tick) * 0.2f;
            boolean sunUp = elev > 0.03f && day > 0.04f;

            float hz = (float) Math.toRadians(270f + sky * 360f);
            float horiz = (float) Math.sqrt(Math.max(0f, 1f - elev * elev));
            Vec3d toSun = new Vec3d(-horiz * (float) Math.sin(hz), elev, horiz * (float) Math.cos(hz));

            Vec3d look = mc.player.getRotationVec(tick);
            float facing = (float) toSun.dotProduct(look);
            float backlit = 0.3f + 0.7f * Math.max(0f, (facing + 1f) * 0.5f);
            float wet = 1f - Math.min(0.85f, rain);

            ctx.matrices().push();
            ctx.matrices().translate(-cam.x, -cam.y, -cam.z);
            Matrix4f mat = ctx.matrices().peek().getPositionMatrix();
            VertexConsumer buf = ctx.consumers().getBuffer(RenderLayers.debugQuads());

            double sec = System.currentTimeMillis() / 1000.0;
            if (sunUp && Shaders.shaftsF() > 0.01f) shafts(buf, mat, world, cam, toSun, backlit * wet, Shaders.shaftsF(), sec);
            if (Shaders.mistF() > 0.01f) mist(buf, mat, world, cam, day, wet, Shaders.mistF(), sec);
            if (sunUp && Shaders.motesF() > 0.01f) motes(buf, mat, cam, toSun, backlit * wet, Shaders.motesF(), sec);

            ctx.matrices().pop();
            com.visualmod.util.RenderFlush.flush(ctx.consumers(), RenderLayers.debugQuads());
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("Volumetrics", t);
        }
    }

    private static final int SHAFTS = 16;
    private static final BlockPos.Mutable MP = new BlockPos.Mutable();

    private static void shafts(VertexConsumer buf, Matrix4f mat, ClientWorld world, Vec3d cam,
                               Vec3d dir, float strength, float k, double sec) {
        float ax = (float) dir.x, ay = (float) dir.y, az = (float) dir.z;
        float px = -az, pz = ax;
        float pl = (float) Math.hypot(px, pz);
        if (pl < 1e-4f) { px = 1f; pz = 0f; } else { px /= pl; pz /= pl; }

        int reach = 26;
        int found = 0;
        for (int i = 0; i < SHAFTS * 6 && found < SHAFTS; i++) {
            double a = i * 2.3999632f;
            double rad = 5.0 + (i % 7) * 2.8;
            double wx = cam.x + Math.cos(a) * rad;
            double wz = cam.z + Math.sin(a) * rad;
            int ground = groundY(world, wx, wz, cam.y);
            if (ground == Integer.MIN_VALUE) continue;

            MP.set((int) Math.floor(wx), ground + 3, (int) Math.floor(wz));
            if (world.getLightLevel(MP) < 10) continue;

            float sway = (float) Math.sin(sec * 0.14 + i) * 0.10f;
            float bx = (float) wx + sway, by = ground + 0.3f, bz = (float) wz + sway * 0.6f;
            float len = 15f + 7f * ((i % 5) / 4f);
            float hw = 0.55f + 0.5f * ((i % 3) / 2f);

            float dx = (float) (bx - cam.x), dy = (float) (by - cam.y), dz = (float) (bz - cam.z);
            float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > reach) continue;
            float fade = 1f - dist / reach;
            float alpha = k * strength * fade * fade * 0.075f;
            if (alpha <= 0.002f) continue;

            ribbon(buf, mat, bx, by, bz, ax, ay, az, px, 0f, pz, hw, len, alpha, true);
            float qx = ay * pz - az * 0f, qy = az * px - ax * pz, qz = ax * 0f - ay * px;
            ribbon(buf, mat, bx, by, bz, ax, ay, az, qx, qy, qz, hw, len, alpha, false);
            found++;
        }
    }

    private static void ribbon(VertexConsumer buf, Matrix4f mat, float bx, float by, float bz,
                               float ax, float ay, float az, float wx, float wy, float wz,
                               float hw, float len, float alpha, boolean warm) {
        float tx = bx + ax * len, ty = by + ay * len, tz = bz + az * len;
        for (int layer = 0; layer < 3; layer++) {
            float t = layer / 2f;
            float w = hw * (1f - t * 0.62f);
            float a = alpha * (0.35f + t * 0.65f);
            float taper = 1f - t * 0.25f;
            quad(buf, mat,
                    bx - wx * w, by - wy * w, bz - wz * w,
                    bx + wx * w, by + wy * w, bz + wz * w,
                    tx + wx * w * taper * 0.5f, ty + wy * w * taper * 0.5f, tz + wz * w * taper * 0.5f,
                    tx - wx * w * taper * 0.5f, ty - wy * w * taper * 0.5f, tz - wz * w * taper * 0.5f,
                    1f, 0.92f, 0.72f, a);
        }
    }

    private static void mist(VertexConsumer buf, Matrix4f mat, ClientWorld world, Vec3d cam,
                             float day, float wet, float k, double sec) {
        float[] tint = moodTint(day);
        int cell = 13;
        float drift = (float) ((sec * 0.22) % cell);
        int bx = (int) Math.floor(cam.x / cell) - 2, bz = (int) Math.floor(cam.z / cell) - 2;

        for (int ix = 0; ix < 5; ix++) {
            for (int iz = 0; iz < 5; iz++) {
                double wx = (bx + ix) * (double) cell + drift;
                double wz = (bz + iz) * (double) cell + 6.5 - drift * 0.6;
                int ground = groundY(world, wx, wz, cam.y);
                if (ground == Integer.MIN_VALUE) continue;
                double y = ground + 0.8 + Math.sin(sec * 0.21 + ix * 1.7 + iz) * 0.5;

                float dx = (float) (wx - cam.x), dz = (float) (wz - cam.z);
                float dist = (float) Math.sqrt(dx * dx + dz * dz);
                float fade = 1f - dist / (cell * 3.2f);
                if (fade <= 0.03f) continue;
                float alpha = k * wet * fade * fade * 0.085f * (day < 0.18f ? 0.6f : 1f);
                if (alpha <= 0.002f) continue;

                float half = cell * 0.62f;
                for (int layer = 0; layer < 3; layer++) {
                    float s = half * (1f - layer * 0.28f);
                    float a = alpha * (0.4f + layer * 0.3f);
                    quad(buf, mat, wx - s, y, wz - s, wx - s, y, wz + s, wx + s, y, wz + s, wx + s, y, wz - s,
                            tint[0], tint[1], tint[2], a);
                }
            }
        }
    }

    private static final int MOTES = 90;

    private static void motes(VertexConsumer buf, Matrix4f mat, Vec3d cam, Vec3d dir,
                              float strength, float k, double sec) {
        for (int i = 0; i < MOTES; i++) {
            double seed = i * 12.9898;
            float u = (float) (seed % 1.0 + 0.05);
            float v = (float) ((seed * 1.73) % 1.0);
            float w2 = (float) ((seed * 0.31) % 1.0);
            float box = 11f;
            float ox = (u * 2f - 1f) * box, oy = (v * 2f - 1f) * box * 0.55f, oz = (w2 * 2f - 1f) * box;

            float t = (float) sec * 0.22f + i * 0.37f;
            float wx = (float) cam.x + ox + (float) Math.sin(t) * 0.7f + (float) dir.x * 1.4f * ((i % 5) / 4f);
            float wy = (float) cam.y + oy + (float) Math.sin(t * 0.7 + i) * 0.35f;
            float wz = (float) cam.z + oz + (float) Math.cos(t * 0.9) * 0.7f + (float) dir.z * 1.4f * ((i % 5) / 4f);

            float dx = wx - (float) cam.x, dy = wy - (float) cam.y, dz = wz - (float) cam.z;
            float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < 1.4f || dist > box) continue;
            float fade = 1f - dist / box;
            float twinkle = 0.55f + 0.45f * (float) Math.sin(t * 3.1f + i);
            float a = k * strength * fade * fade * 0.5f * twinkle;
            if (a <= 0.003f) continue;

            float sz = 0.028f + 0.022f * ((i % 4) / 3f);
            quad(buf, mat, wx - sz, wy, wz - sz, wx - sz, wy, wz + sz, wx + sz, wy, wz + sz, wx + sz, wy, wz - sz,
                    1f, 0.96f, 0.82f, a);
            quad(buf, mat, wx - sz, wy - sz, wz, wx + sz, wy - sz, wz, wx + sz, wy + sz, wz, wx - sz, wy + sz, wz,
                    1f, 0.96f, 0.82f, a);
            quad(buf, mat, wx, wy - sz, wz - sz, wx, wy + sz, wz - sz, wx, wy + sz, wz + sz, wx, wy - sz, wz + sz,
                    1f, 0.96f, 0.82f, a * 0.8f);
        }
    }

    private static float[] moodTint(float day) {
        if (day < 0.18f) return new float[]{0.42f, 0.56f, 0.82f};
        float edge = Math.max(0f, Math.min(1f, 1f - Math.abs(day - 0.5f) * 2.3f));
        float warm = 0.35f + 0.65f * edge;
        return new float[]{0.72f + 0.24f * warm, 0.78f + 0.10f * warm, 0.86f};
    }

    private static int groundY(ClientWorld world, double x, double z, double camY) {
        int col = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING,
                (int) Math.floor(x), (int) Math.floor(z));
        if (col < world.getBottomY() || col > world.getHeight()) return Integer.MIN_VALUE;
        if (Math.abs(col - camY) > 24) return Integer.MIN_VALUE;
        return col;
    }

    private static void quad(VertexConsumer buf, Matrix4f mat,
                             double x0, double y0, double z0, double x1, double y1, double z1,
                             double x2, double y2, double z2, double x3, double y3, double z3,
                             float r, float g, float b, float a) {
        buf.vertex(mat, (float) x0, (float) y0, (float) z0).color(r, g, b, a);
        buf.vertex(mat, (float) x1, (float) y1, (float) z1).color(r, g, b, a);
        buf.vertex(mat, (float) x2, (float) y2, (float) z2).color(r, g, b, a);
        buf.vertex(mat, (float) x3, (float) y3, (float) z3).color(r, g, b, a);
        buf.vertex(mat, (float) x3, (float) y3, (float) z3).color(r, g, b, a);
        buf.vertex(mat, (float) x2, (float) y2, (float) z2).color(r, g, b, a);
        buf.vertex(mat, (float) x1, (float) y1, (float) z1).color(r, g, b, a);
        buf.vertex(mat, (float) x0, (float) y0, (float) z0).color(r, g, b, a);
    }
}

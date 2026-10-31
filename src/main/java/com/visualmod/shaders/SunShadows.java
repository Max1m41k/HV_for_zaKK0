package com.visualmod.shaders;

import net.minecraft.client.render.RenderLayers;
import com.visualmod.VisualsMod;
import com.visualmod.module.modules.render.CustomSky;
import com.visualmod.module.modules.render.Shaders;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.joml.Matrix4f;

/**
 * Встроенные тени от солнца: вокруг игрока строится карта высот, по ней пускается луч в сторону
 * солнца, и каждая клетка, которой луч перекрыт рельефом, получает мягкую тень на земле.
 * Настройки нет — тени работают всегда, пока включён сам модуль.
 */
public final class SunShadows {

    /** Полуразмер сетки теней в блоках. */
    private static final int R = 22;
    private static final int S = R * 2 + 1;
    /** Запас карты высот: луч уходит за сетку теней, и там тоже нужен рельеф. */
    private static final int PAD = 34;
    private static final int GW = S + PAD * 2;
    private static final double STEP = 1.0;
    private static final double MAX_D = 48;
    private static final int INVALID = Short.MIN_VALUE;

    private static final short[] HEIGHT = new short[GW * GW];
    private static final float[] SHADOW = new float[S * S];

    /** Мирной блок (0,0) обеих сеток и момент последнего пересчёта. */
    private static int anchorX, anchorZ, anchorSky = -9;
    private static long lastAt;

    private SunShadows() {}

    public static void render(WorldRenderContext ctx) {
        try {
            if (VisualsMod.moduleManager == null) return;
            Shaders m = VisualsMod.moduleManager.getModule(Shaders.class);
            if (m == null || !m.isEnabled()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            ClientWorld world = mc.world;
            if (world == null || mc.player == null || ctx.gameRenderer().getCamera() == null) return;
            if (!CustomSky.isOverworld(world)) return;

            float tick = mc.getRenderTickCounter().getTickProgress(false);
            float sky = com.visualmod.util.McCompat.skyAngle(world, tick);
            float elev = (float) Math.sin(Math.PI * 2 * sky);
            float day = Shaders.daylight(world, tick);
            if (elev <= 0.025f || day <= 0.04f) return;

            int px = (int) Math.floor(mc.player.getX()), pz = (int) Math.floor(mc.player.getZ());
            int surface = heightOf(world, px, pz);
            if (surface == INVALID || surface - mc.player.getY() > 18) return;

            float hz = (float) Math.toRadians(270f + sky * 360f);
            float horiz = (float) Math.sqrt(Math.max(0f, 1f - elev * elev));
            Vec3d toSun = new Vec3d(-horiz * (float) Math.sin(hz), elev, horiz * (float) Math.cos(hz));

            int skyQ = (int) (sky * 1600f);
            long now = System.currentTimeMillis();
            if (px != anchorX || pz != anchorZ || skyQ != anchorSky || now - lastAt > 1600) {
                anchorX = px; anchorZ = pz; anchorSky = skyQ; lastAt = now;
                build(world, mc, px, pz, toSun.x, toSun.y, toSun.z);
            }

            float rain = world.getRainGradient(tick) * 0.8f + world.getThunderGradient(tick) * 0.2f;
            float strength = 0.46f * clamp01(day * 1.3f) * (1f - 0.72f * rain);
            if (strength <= 0.01f) return;

            Vec3d cam = ctx.gameRenderer().getCamera().getCameraPos();
            ctx.matrices().push();
            ctx.matrices().translate(-cam.x, -cam.y, -cam.z);
            Matrix4f mat = ctx.matrices().peek().getPositionMatrix();
            VertexConsumer buf = ctx.consumers().getBuffer(RenderLayers.debugQuads());
            draw(buf, mat, strength);
            ctx.matrices().pop();
            com.visualmod.util.RenderFlush.flush(ctx.consumers(), RenderLayers.debugQuads());
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("SunShadows", t);
        }
    }

    private static void build(ClientWorld world, MinecraftClient mc, int px, int pz,
                              double ux, double uy, double uz) {
        int bottom = world.getBottomY(), top = world.getHeight();
        int ceil = bottom;
        for (int j = 0; j < GW; j++) {
            int row = j * GW;
            for (int i = 0; i < GW; i++) {
                int h = heightOf(world, px - R - PAD + i, pz - R - PAD + j);
                if (h < bottom || h > top) h = INVALID;
                HEIGHT[row + i] = (short) h;
                if (h != INVALID && h > ceil) ceil = h;
            }
        }

        int steps = (int) (MAX_D / STEP);
        for (int j = 0; j < S; j++) {
            for (int i = 0; i < S; i++) {
                int wx = px - R + i, wz = pz - R + j;
                int h = at(wx, wz);
                if (h == INVALID) { SHADOW[j * S + i] = 0f; continue; }

                float acc = 0f;
                for (int k = 0; k < 4; k++) {
                    double rx = wx + ((k & 1) * 0.5 + 0.25);
                    double rz = wz + (((k >> 1) & 1) * 0.5 + 0.25);
                    double ry = h + 0.06;
                    for (int t = 1; t <= steps; t++) {
                        rx += ux * STEP; ry += uy * STEP; rz += uz * STEP;
                        if (ry > ceil) break;
                        int hh = at((int) Math.floor(rx), (int) Math.floor(rz));
                        if (hh == INVALID) break;
                        if (ry < hh) {
                            acc += 1f - (float) (t * STEP / MAX_D) * 0.55f;
                            break;
                        }
                    }
                }
                SHADOW[j * S + i] = acc * 0.25f;
            }
        }
        stampEntities(world, mc, ux, uy, uz);
    }

    /** Сущности тоже отбрасывают тень: основание на месте, конец уезжает от солнца. */
    private static void stampEntities(ClientWorld world, MinecraftClient mc,
                                      double ux, double uy, double uz) {
        double reach = R + 2;
        double py = mc.player.getY();
        Box area = new Box(anchorX - reach, py - 12, anchorZ - reach,
                anchorX + reach, py + reach, anchorZ + reach);
        Entity viewer = mc.getCameraEntity();
        for (Entity e : world.getEntitiesByClass(Entity.class, area,
                en -> en != null && en != viewer && !en.isInvisible() && en.isAlive())) {
            Box bb = e.getBoundingBox();
            double hgt = Math.max(0.45, bb.maxY - bb.minY);
            double drop = Math.min(24, hgt / Math.max(0.14, uy));
            double ox = -ux * drop, oz = -uz * drop;
            double rad = Math.max(bb.getLengthX(), bb.getLengthZ()) * 0.5;
            double bx = bb.minX + bb.getLengthX() * 0.5, bz = bb.minZ + bb.getLengthZ() * 0.5;
            int marks = Math.max(1, (int) (Math.hypot(ox, oz) * 1.5));
            for (int s = 0; s <= marks; s++) {
                float f = (float) s / marks;
                stamp(bx + ox * f, bz + oz * f, rad * (1f - 0.45f * f), 0.92f - 0.5f * f);
            }
        }
    }

    private static void stamp(double wx, double wz, double rad, float amount) {
        int x0 = (int) Math.floor(wx - rad), x1 = (int) Math.floor(wx + rad);
        int z0 = (int) Math.floor(wz - rad), z1 = (int) Math.floor(wz + rad);
        for (int z = z0; z <= z1; z++) {
            int row = z - (anchorZ - R);
            if (row < 0 || row >= S) continue;
            for (int x = x0; x <= x1; x++) {
                double dx = x + 0.5 - wx, dz = z + 0.5 - wz;
                if (dx * dx + dz * dz > rad * rad) continue;
                int col = x - (anchorX - R);
                if (col < 0 || col >= S) continue;
                int idx = row * S + col;
                if (amount > SHADOW[idx]) SHADOW[idx] = amount;
            }
        }
    }

    private static void draw(VertexConsumer buf, Matrix4f mat, float strength) {
        for (int j = 0; j < S; j++) {
            for (int i = 0; i < S; i++) {
                if (SHADOW[j * S + i] <= 0.01f) continue;
                int wx = anchorX - R + i, wz = anchorZ - R + j;
                int h = at(wx, wz);
                if (h == INVALID) continue;

                float edge = clamp01(1f - (float) Math.hypot(i - R, j - R) / R);
                float fade = edge * edge * (3f - 2f * edge);
                if (fade <= 0.01f) continue;

                double y = h + 0.006;
                float k = strength * fade;
                quad(buf, mat,
                        wx, y, wz, node(i, j) * k,
                        wx, y, wz + 1, node(i, j + 1) * k,
                        wx + 1, y, wz + 1, node(i + 1, j + 1) * k,
                        wx + 1, y, wz, node(i + 1, j) * k);
            }
        }
    }

    /** Узел сетки делится с четырьмя соседними клетками, поэтому его альфа — среднее по ним. */
    private static float node(int i, int j) {
        float sum = 0f;
        int n = 0;
        for (int dj = -1; dj <= 0; dj++) {
            int b = j + dj;
            if (b < 0 || b >= S) continue;
            for (int di = -1; di <= 0; di++) {
                int a = i + di;
                if (a < 0 || a >= S) continue;
                sum += SHADOW[b * S + a];
                n++;
            }
        }
        return n == 0 ? 0f : sum / n;
    }

    private static float clamp01(float v) { return v < 0f ? 0f : Math.min(v, 1f); }

    private static int at(int wx, int wz) {
        int i = wx - (anchorX - R - PAD), j = wz - (anchorZ - R - PAD);
        if (i < 0 || j < 0 || i >= GW || j >= GW) return INVALID;
        return HEIGHT[j * GW + i];
    }

    private static int heightOf(ClientWorld world, int x, int z) {
        try {
            return world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
        } catch (Throwable t) {
            return INVALID;
        }
    }

    private static void quad(VertexConsumer buf, Matrix4f mat,
                             double x0, double y0, double z0, float a0,
                             double x1, double y1, double z1, float a1,
                             double x2, double y2, double z2, float a2,
                             double x3, double y3, double z3, float a3) {
        float r = 0.02f, g = 0.03f, b = 0.07f;
        buf.vertex(mat, (float) x0, (float) y0, (float) z0).color(r, g, b, clamp01(a0));
        buf.vertex(mat, (float) x1, (float) y1, (float) z1).color(r, g, b, clamp01(a1));
        buf.vertex(mat, (float) x2, (float) y2, (float) z2).color(r, g, b, clamp01(a2));
        buf.vertex(mat, (float) x3, (float) y3, (float) z3).color(r, g, b, clamp01(a3));
    }
}

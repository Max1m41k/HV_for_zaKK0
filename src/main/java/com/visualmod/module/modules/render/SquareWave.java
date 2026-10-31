package com.visualmod.module.modules.render;

import net.minecraft.client.render.RenderLayers;
import com.visualmod.VisualsMod;
import com.visualmod.util.WorldRenderUtil;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.BlockView;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class SquareWave {

    private static final int RADIUS = 32;
    private static final int RAYS = 40;
    private static final int UP = 10, DOWN = 3;
    private static final long LIFE_MS = 500;
    private static final long STEP_MS = 34;

    private static final int TOP = 0, SIDE = 1;

    private static final class Cell {
        final double x, y, z; final int face; final long t0;
        Cell(double x, double y, double z, int face, long t0) {
            this.x = x; this.y = y; this.z = z; this.face = face; this.t0 = t0;
        }
    }

    private static final List<Cell> cells = new ArrayList<>();
    private static double cx, cy, cz;
    private static int radiusDone = 0;
    private static long nextStepAt;
    private static boolean running;
    private static int color = 0xFF4FE0C0;

    static { WorldRenderEvents.AFTER_ENTITIES.register(SquareWave::render); }

    private SquareWave() {}

    public static void trigger(double x, double y, double z, int argb) {
        cx = x; cy = y; cz = z;
        color = argb;
        radiusDone = 0;
        nextStepAt = System.currentTimeMillis();
        running = true;
        synchronized (cells) { cells.clear(); }
    }

    public static void stop() {
        running = false;
        synchronized (cells) { cells.clear(); }
    }

    private static void render(WorldRenderContext ctx) {
        if (!running && cells.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (VisualsMod.moduleManager == null || mc.world == null || mc.player == null
                || ctx.matrices() == null || ctx.consumers() == null) {
            running = false;
            return;
        }
        KillEffect m = VisualsMod.moduleManager.getModule(KillEffect.class);
        if (m == null || !m.isEnabled() || KillEffect.mode != 4) {
            running = false;
            synchronized (cells) { cells.clear(); }
            return;
        }

        long now = System.currentTimeMillis();
        try {
            while (running && now >= nextStepAt) {
                radiusDone++;
                if (radiusDone > RADIUS) { running = false; break; }
                nextStepAt += STEP_MS;
                addRing(mc.world, radiusDone, now);
            }
            draw(ctx, now);
        } catch (Throwable t) {
            running = false;
            com.visualmod.util.Diag.once("SquareWave", t);
        }
    }

    private static void addRing(BlockView world, int r, long now) {
        for (int i = 0; i < RAYS; i++) {
            double a = (Math.PI * 2 * i) / RAYS;
            double dx = Math.cos(a), dz = Math.sin(a);
            int bx = (int) Math.floor(cx + dx * r);
            int bz = (int) Math.floor(cz + dz * r);
            int baseY = (int) Math.floor(cy);

            int nx = (int) Math.floor(cx + dx * (r - 1));
            int nz = (int) Math.floor(cz + dz * (r - 1));
            int dirX = Integer.signum(bx - nx), dirZ = Integer.signum(bz - nz);

            for (int dy = -DOWN; dy <= UP; dy++) {
                BlockPos p = new BlockPos(bx, baseY + dy, bz);
                if (world.getBlockState(p).isAir()) continue;
                if (world.getBlockState(p.up()).isAir()) {
                    synchronized (cells) { cells.add(new Cell(p.getX(), p.getY() + 1.002, p.getZ(), TOP, now)); }
                }
                BlockPos back = p.add(dirX == 0 ? 0 : -dirX, 0, dirZ == 0 ? 0 : -dirZ);
                if ((dirX != 0 || dirZ != 0) && world.getBlockState(back).isAir()) {
                    synchronized (cells) { cells.add(new Cell(p.getX(), p.getY(), p.getZ(), SIDE, now)); }
                }
            }
        }
    }

    private static void draw(WorldRenderContext ctx, long now) {
        synchronized (cells) {
            if (cells.isEmpty()) return;
            Iterator<Cell> it = cells.iterator();
            boolean any = false;
            while (it.hasNext()) {
                if (now - it.next().t0 > LIFE_MS) it.remove(); else any = true;
            }
            if (!any) return;

            float r = WorldRenderUtil.colorR(color), g = WorldRenderUtil.colorG(color), b = WorldRenderUtil.colorB(color);
            ctx.matrices().push();
            ctx.matrices().translate(-ctx.gameRenderer().getCamera().getCameraPos().x,
                                        -ctx.gameRenderer().getCamera().getCameraPos().y,
                                        -ctx.gameRenderer().getCamera().getCameraPos().z);
            Matrix4f mat = new Matrix4f(ctx.matrices().peek().getPositionMatrix());
            VertexConsumer buf = ctx.consumers().getBuffer(RenderLayers.debugQuads());

            for (Cell c : cells) {
                double age = (now - c.t0) / (double) LIFE_MS;
                float alpha = (float) ((1 - age) * 0.55);
                if (alpha <= 0.01f) continue;
                if (c.face == TOP) {
                    WorldRenderUtil.drawFilledBox(buf, mat,
                        new Box(c.x, c.y, c.z, c.x + 1, c.y + 0.005, c.z + 1), r, g, b, alpha);
                } else {
                    double ex = Math.signum(cx + 0.5 - (c.x + 0.5)), ez = Math.signum(cz + 0.5 - (c.z + 0.5));
                    if (Math.abs(ex) > Math.abs(ez)) {
                        double x = ex > 0 ? c.x : c.x + 1 - 0.01;
                        WorldRenderUtil.drawFilledBox(buf, mat,
                            new Box(x, c.y, c.z, x + 0.01, c.y + 1, c.z + 1), r, g, b, alpha);
                    } else {
                        double z = ez > 0 ? c.z : c.z + 1 - 0.01;
                        WorldRenderUtil.drawFilledBox(buf, mat,
                            new Box(c.x, c.y, z, c.x + 1, c.y + 1, z + 0.01), r, g, b, alpha);
                    }
                }
            }

            if (running && radiusDone > 0) {
                VertexConsumer lines = ctx.consumers().getBuffer(RenderLayers.lines());
                WorldRenderUtil.drawCircle(lines, mat, cx, cy + 0.4, cz, radiusDone, 48, r, g, b, 0.35f);
            }
            ctx.matrices().pop();
        }
    }
}

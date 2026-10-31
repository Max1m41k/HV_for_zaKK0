package com.visualmod.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

/**
 * Полоса взмаха для HitSplash: не линия, собранная из сегментов траектории, а готовая картинка,
 * у которой по ходу свинга просто меняется кадр — как в анимированном стрипе клинков.
 *
 * Кадры лежат в одной полоске 512x64 (восемь клеток 64x64) и строятся в памяти, а не в паке
 * ресурсов: перекраска по цвету = пересборка NativeImage на пару мегапикселей, без
 * mc.reloadResources() на каждый поворот ползунка.
 */
public final class SlashSprite {

    public static final int FRAMES = 8;
    public static final int SIZE = 64;

    private static final Identifier ID = Identifier.of("visualmod", "slash/frames");

    private static boolean ready;
    private static int baked;

    private SlashSprite() {}

    /** id слоя надо спрашивать каждый кадр: смена цвета перерисовывает стрип на месте. */
    public static Identifier texture(int color) {
        if (!ready || baked != color) bake(color);
        return ID;
    }

    public static int frame(float progress) {
        if (progress <= 0f) return 0;
        if (progress >= 1f) return FRAMES - 1;
        return (int) (progress * FRAMES);
    }

    /**
     * Квад одного кадра. ux/uz и vx/vz — уже домноженные полуоси, поэтому масштаб и толщина
     * остаются на совести вызова, а тут только разрез стрипа по u.
     */
    public static void quad(VertexConsumer vc, Matrix4f mat,
                            float cx, float cy, float cz,
                            float ux, float uy, float uz,
                            float vx, float vy, float vz,
                            int frame, float alpha) {
        if (alpha <= 0.01f) return;
        float a = Math.min(1f, alpha);
        float u0 = frame / (float) FRAMES, u1 = (frame + 1) / (float) FRAMES;
        float x0 = cx - ux - vx, y0 = cy - uy - vy, z0 = cz - uz - vz;
        float x1 = cx + ux - vx, y1 = cy + uy - vy, z1 = cz + uz - vz;
        float x2 = cx + ux + vx, y2 = cy + uy + vy, z2 = cz + uz + vz;
        float x3 = cx - ux + vx, y3 = cy - uy + vy, z3 = cz - uz + vz;
        // оба обхода: у слоя с текстурой отсечение по заднему лицу включено, а клинок крутится
        v(vc, mat, x0, y0, z0, u0, 1f, a);
        v(vc, mat, x1, y1, z1, u1, 1f, a);
        v(vc, mat, x2, y2, z2, u1, 0f, a);
        v(vc, mat, x3, y3, z3, u0, 0f, a);
        v(vc, mat, x3, y3, z3, u0, 0f, a);
        v(vc, mat, x2, y2, z2, u1, 0f, a);
        v(vc, mat, x1, y1, z1, u1, 1f, a);
        v(vc, mat, x0, y0, z0, u0, 1f, a);
    }

    private static void v(VertexConsumer vc, Matrix4f mat, float x, float y, float z,
                          float u, float vv, float alpha) {
        vc.vertex(mat, x, y, z).color(1f, 1f, 1f, alpha).texture(u, vv)
                .overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0f, 1f, 0f);
    }

    private static void bake(int color) {
        NativeImage img = new NativeImage(SIZE * FRAMES, SIZE, false);
        double br = ((color >> 16) & 0xFF) / 255.0, bg = ((color >> 8) & 0xFF) / 255.0, bb = (color & 0xFF) / 255.0;
        for (int f = 0; f < FRAMES; f++) {
            double t = f / (double) (FRAMES - 1);
            double lead = 0.30 + 0.86 * t;
            double u0 = clamp((t - 0.55) / 0.45) * 0.5;
            double ein = 0.60 + 0.40 * clamp(t / 0.22);
            double eout = 0.10 + 0.90 * (1.0 - clamp((t - 0.55) / 0.45));
            for (int y = 0; y < SIZE; y++) {
                double p = (y + 0.5) / SIZE * 2.0 - 1.0;
                for (int x = 0; x < SIZE; x++) {
                    double u = (x + 0.5) / SIZE;
                    if (u < u0 || u > lead) { img.setColorArgb(f * SIZE + x, y, 0); continue; }
                    double q = (u - u0) / Math.max(1e-4, lead - u0);
                    double mid = 0.18 - 0.44 * Math.sin(Math.PI * (0.06 + 0.94 * q));
                    double thick = 0.42 * Math.pow(Math.sin(Math.PI * q), 0.7);
                    double dev = Math.abs(p - mid);
                    if (thick <= 0.001 || dev >= thick) { img.setColorArgb(f * SIZE + x, y, 0); continue; }
                    double body = 1.0 - dev / thick;
                    double tip = Math.exp(-Math.pow((q - 0.86) * 4.2, 2));
                    double hot = clamp(0.28 * Math.pow(body, 0.55) + 0.72 * tip * Math.pow(body, 0.4));
                    double alpha = clamp(Math.pow(body, 1.4) * ein * eout);
                    int argb = ((int) (alpha * 255) << 24) | (ch(br, hot) << 16) | (ch(bg, hot) << 8) | ch(bb, hot);
                    img.setColorArgb(f * SIZE + x, y, argb);
                }
            }
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getTextureManager() == null) return;
        if (ready) mc.getTextureManager().destroyTexture(ID);
        mc.getTextureManager().registerTexture(ID, new NativeImageBackedTexture(ID::toString, img));
        ready = true;
        baked = color;
    }

    private static int ch(double base, double hot) {
        return (int) Math.min(255.0, (base + (1.0 - base) * hot) * 255.0);
    }

    private static double clamp(double x) {
        return x < 0 ? 0 : Math.min(1, x);
    }
}

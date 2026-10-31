package com.visualmod.cosmetics;

import com.visualmod.cosmetics.CosmeticModel.Face;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class Capes {

    public static final class CapeConfig {
        public final int base, trim, emblemColor;
        public final String emblem;
        public final float length;

        public CapeConfig(int base, int trim, String emblem, int emblemColor, float length) {
            this.base = base;
            this.trim = trim;
            this.emblem = emblem;
            this.emblemColor = emblemColor;
            this.length = length;
        }
    }

    private static final int COLS = 10;
    private static final float SHOULDER_R = 5.1f;
    private static final float NECK_Y = -0.6f;
    private static final float ROW_STEP = 2.0f;

    /**
     * Сетка плаща строится один раз и живёт в кэше: каждый кадр пересчитываются только
     * координаты волновых вершин (как в CustomWings, где форма лежит в CACHE). Раньше на
     * каждый кадр создавалось ~700 новых Face/float[] — отсюда и микростаттеры.
     */
    private static final class Grid {
        final CapeConfig cfg;
        final int rows;
        final Face[][] cells;
        final List<Face> faces;

        Grid(CapeConfig cfg, int rows) {
            this.cfg = cfg;
            this.rows = rows;
            this.cells = new Face[rows][COLS];
            this.faces = new ArrayList<>(rows * COLS + 8);
            float[] baseC = CosmeticModel.rgba(cfg.base, 1f);
            float[] trimC = CosmeticModel.rgba(cfg.trim, 1f);
            for (int r = 0; r < rows; r++) {
                float tRow = r / (float) rows;
                float[] col = (r == 0 || r == rows - 1) ? trimC : baseC;
                float shade = 1f - tRow * 0.25f;
                float shade2 = 1f - Math.min(1, tRow + 0.08f) * 0.25f;
                for (int c = 0; c < COLS; c++) {
                    Face f = new Face();
                    f.top[0] = col[0] * shade; f.top[1] = col[1] * shade; f.top[2] = col[2] * shade; f.top[3] = 1f;
                    f.bottom[0] = col[0] * shade2; f.bottom[1] = col[1] * shade2; f.bottom[2] = col[2] * shade2; f.bottom[3] = 1f;
                    f.gradient = true;
                    f.doubleSided = true;
                    cells[r][c] = f;
                    faces.add(f);
                }
            }
            addEmblem(faces, cfg);
        }
    }

    private static Grid grid;

    public static List<Face> build(CapeConfig cfg, double sec) {
        int rows = Math.max(4, (int) (cfg.length / ROW_STEP));
        Grid g = grid;
        if (g == null || g.cfg != cfg || g.rows != rows) {
            g = new Grid(cfg, rows);
            grid = g;
        }
        for (int r = 0; r < rows; r++) {
            float tRow = r / (float) rows;
            float y = NECK_Y + r * ROW_STEP;
            boolean shoulderArc = r < 2;
            float halfSpan = shoulderArc ? 118f : 105f - tRow * 12f;
            float radius = SHOULDER_R + (shoulderArc ? r * 0.8f : 1.4f + tRow * tRow * 3.0f);
            float zShift = shoulderArc ? 0 : tRow * tRow * 2.6f;
            for (int c = 0; c < COLS; c++) {
                float a0 = -halfSpan + (2 * halfSpan) * (c / (float) COLS);
                float a1 = -halfSpan + (2 * halfSpan) * ((c + 1) / (float) COLS);
                Face f = g.cells[r][c];
                pt(f.v[0], a0, y, radius, sec, r, tRow, zShift);
                pt(f.v[1], a1, y, radius, sec, r, tRow, zShift);
                pt(f.v[2], a1, y + ROW_STEP, radius, sec, r + 1, tRow, zShift);
                pt(f.v[3], a0, y + ROW_STEP, radius, sec, r + 1, tRow, zShift);
                CosmeticModel.normal(f);
            }
        }
        return g.faces;
    }

    private static void pt(float[] out, float angleDeg, float y, float radius, double sec,
                           int row, float tRow, float zShift) {
        double a = Math.toRadians(angleDeg);
        double wave = Math.sin(sec * 2.4 + row * 0.65 + a * 2.2) * (0.25 + tRow * 0.9)
                + Math.sin(sec * 1.1 + a * 3.1) * tRow * 0.4;
        float r = radius + (float) wave;
        out[0] = (float) (Math.sin(a) * r);
        out[1] = y;
        out[2] = (float) (Math.cos(a) * r) + zShift;
    }

    private static void addEmblem(List<Face> out, CapeConfig cfg) {
        float[] ec = CosmeticModel.rgba(cfg.emblemColor, 1f);
        float ey = NECK_Y + 5.2f;
        float ez = SHOULDER_R + 3.6f;
        int start = out.size();
        float s = 1.0f;
        float w = 0.9f * s, t = 0.28f * s;

        switch (cfg.emblem) {
            case "wings" -> {
                emblemQuad(out, -w / 2, ey - 3 * s, ez, w / 2, ey + 3 * s, ez, ec);
                for (int side = -1; side <= 1; side += 2) {
                    emblemQuad(out, side * w * 0.4f, ey + 1.2f * s, ez,
                            side * (w * 0.4f + 3.4f * s), ey + 2.6f * s, ez, ec);
                    emblemQuad(out, side * w * 0.4f, ey - 0.4f * s, ez,
                            side * (w * 0.4f + 3.0f * s), ey + 0.6f * s, ez, ec);
                }
            }
            case "diamond" -> {
                emblemQuad(out, -1.4f * s, ey, ez, 0, ey + 2.2f * s, ez, ec);
                emblemQuad(out, 0, ey + 2.2f * s, ez, 1.4f * s, ey, ez, ec);
                emblemQuad(out, 1.4f * s, ey, ez, 0, ey - 2.2f * s, ez, ec);
                emblemQuad(out, 0, ey - 2.2f * s, ez, -1.4f * s, ey, ez, ec);
            }
            case "cross" -> {
                emblemQuad(out, -w / 2, ey + 2.5f * s, ez, w / 2, ey + 3.0f * s, ez, ec);
                emblemQuad(out, -w / 2, ey - 3.0f * s, ez, w / 2, ey - 2.5f * s, ez, ec);
                emblemQuad(out, -t / 2, ey - 3 * s, ez, t / 2, ey + 3 * s, ez, ec);
                emblemQuad(out, -0.6f * s, ey + 1.4f * s, ez, 0.9f * s, ey + 0.2f * s, ez, ec);
                emblemQuad(out, 0.9f * s, ey + 0.2f * s, ez, -0.9f * s, ey - 1.0f * s, ez, ec);
                emblemQuad(out, -0.9f * s, ey - 1.0f * s, ez, 0.6f * s, ey - 2.0f * s, ez, ec);
            }
            case "shield" -> {
                emblemQuad(out, -2.2f * s, ey + 2.2f * s, ez, 2.2f * s, ey + 2.6f * s, ez, ec);
                emblemQuad(out, -2.2f * s, ey + 2.2f * s, ez, -1.8f * s, ey - 2.4f * s, ez, ec);
                emblemQuad(out, 2.2f * s, ey + 2.2f * s, ez, 1.8f * s, ey - 2.4f * s, ez, ec);
                emblemQuad(out, -1.8f * s, ey - 2.4f * s, ez, 1.8f * s, ey - 2.8f * s, ez, ec);
                emblemQuad(out, -0.5f * s, ey - 0.8f * s, ez + 0.05f, 0.5f * s, ey + 0.8f * s, ez + 0.05f, ec);
            }
            default -> {
                emblemQuad(out, -1.8f * s, ey + 2.0f * s, ez, 0.4f * s, ey + 2.4f * s, ez, ec);
                emblemQuad(out, 0.4f * s, ey + 2.4f * s, ez, 1.8f * s, ey + 1.0f * s, ez, ec);
                emblemQuad(out, 1.8f * s, ey + 1.0f * s, ez, 1.8f * s, ey - 1.0f * s, ez, ec);
                emblemQuad(out, 1.8f * s, ey - 1.0f * s, ez, 0.4f * s, ey - 2.4f * s, ez, ec);
                emblemQuad(out, 0.4f * s, ey - 2.4f * s, ez, -1.8f * s, ey - 2.0f * s, ez, ec);
            }
        }

        for (int i = start; i < out.size(); i++) {
            Face f = out.get(i);
            for (int v = 0; v < 4; v++) f.v[v][1] = 2f * ey - f.v[v][1];
        }
    }

    private static void emblemQuad(List<Face> out, float x0, float y0, float z0,
                                   float x1, float y1, float z1, float[] c) {
        Face f = new Face();
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.05f) return;
        float nx = -dy / len * 0.28f, ny = dx / len * 0.28f;
        f.v[0][0] = x0 + nx; f.v[0][1] = y0 + ny; f.v[0][2] = z0;
        f.v[1][0] = x1 + nx; f.v[1][1] = y1 + ny; f.v[1][2] = z1;
        f.v[2][0] = x1 - nx; f.v[2][1] = y1 - ny; f.v[2][2] = z1 + 0.06f;
        f.v[3][0] = x0 - nx; f.v[3][1] = y0 - ny; f.v[3][2] = z0 + 0.06f;
        f.nx = 0; f.ny = 0; f.nz = 1;
        System.arraycopy(c, 0, f.top, 0, 4);
        System.arraycopy(c, 0, f.bottom, 0, 4);
        f.emissive = true;
        f.doubleSided = true;
        out.add(f);
    }
}

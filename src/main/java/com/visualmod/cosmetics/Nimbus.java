package com.visualmod.cosmetics;

import com.visualmod.cosmetics.CosmeticModel.Face;

import java.util.ArrayList;
import java.util.List;

/**
 * Нимб — светящееся кольцо над головой.
 * <p>
 * Геометрия статична и собирается один раз на пару (стиль, цвет), а движение делает матрица
 * стека: на кадр нет ни аллокаций, ни пересчёта вершин. Кольцо крепится к позиции кости головы,
 * но не к её повороту, поэтому оно остаётся горизонтальным и не «дёргается», когда игрок
 * резко водит мышью. Все грани emissive — цвет не зависит от освещения блока и времени суток.
 */
public final class Nimbus {

    public static final int RING = 0, TWIN = 1, RUNES = 2, HALO = 3;
    public static final String[] NAMES = {"Ring", "Twin", "Runes", "Halo"};

    /** Плоскость кольца в единицах кости головы: куб головы заканчивается на y = -8. */
    private static final float PLANE_Y = -11.0f;
    private static final int SEG = 20;

    private static final class Cached {
        final int style;
        final int color;
        final List<Face> faces;

        Cached(int style, int color, List<Face> faces) {
            this.style = style;
            this.color = color;
            this.faces = faces;
        }
    }

    private static Cached cache;

    private Nimbus() {}

    public static List<Face> build(int style, int color) {
        int rgb = color & 0xFFFFFF;
        Cached c = cache;
        if (c == null || c.style != style || c.color != rgb) {
            c = new Cached(style, rgb, generate(style, rgb));
            cache = c;
        }
        return c.faces;
    }

    private static List<Face> generate(int style, int color) {
        List<Face> out = new ArrayList<>();
        switch (style) {
            case TWIN -> {
                torus(out, PLANE_Y, 6.8f, 0.60f, 0.26f, color, 0.95f);
                torus(out, PLANE_Y + 1.9f, 4.5f, 0.52f, 0.22f, color, 0.78f);
            }
            case RUNES -> {
                torus(out, PLANE_Y, 6.4f, 0.52f, 0.24f, color, 0.92f);
                runes(out, PLANE_Y - 2.2f, 6.4f, color);
            }
            case HALO -> {
                torus(out, PLANE_Y, 5.6f, 0.74f, 0.32f, color, 0.97f);
                skirt(out, 6.8f, 11.0f, PLANE_Y - 1.5f, PLANE_Y + 1.5f, color, 0.15f);
            }
            default -> {
                torus(out, PLANE_Y, 6.5f, 0.78f, 0.30f, color, 0.95f);
                skirt(out, 7.5f, 10.6f, PLANE_Y + 0.9f, PLANE_Y - 0.9f, color, 0.13f);
            }
        }
        return out;
    }

    /** Труба кольца: сечение — эллипс, поэтому нимб остаётся видимым и в профиль. */
    private static void torus(List<Face> out, float cy, float rMajor, float rSide, float rUp,
                              int color, float alpha) {
        float[] c = CosmeticModel.rgba(color, alpha);
        for (int i = 0; i < SEG; i++) {
            float a0 = (float) (Math.PI * 2 * i / SEG);
            float a1 = (float) (Math.PI * 2 * (i + 1) / SEG);
            for (int j = 0; j < 6; j++) {
                float p0 = (float) (Math.PI * 2 * j / 6);
                float p1 = (float) (Math.PI * 2 * (j + 1) / 6);
                Face f = new Face();
                section(f.v[0], a0, p0, cy, rMajor, rSide, rUp);
                section(f.v[1], a1, p0, cy, rMajor, rSide, rUp);
                section(f.v[2], a1, p1, cy, rMajor, rSide, rUp);
                section(f.v[3], a0, p1, cy, rMajor, rSide, rUp);
                CosmeticModel.normal(f);
                System.arraycopy(c, 0, f.top, 0, 4);
                System.arraycopy(c, 0, f.bottom, 0, 4);
                f.emissive = true;
                out.add(f);
            }
        }
    }

    private static void section(float[] v, float ang, float phi, float cy,
                                float rMajor, float rSide, float rUp) {
        float r = rMajor + rSide * (float) Math.cos(phi);
        v[0] = (float) Math.cos(ang) * r;
        v[1] = cy + rUp * (float) Math.sin(phi);
        v[2] = (float) Math.sin(ang) * r;
    }

    /** Конусная «юбка» свечения снаружи кольца: не пересекается с ним, так что не будет z-конфликта. */
    private static void skirt(List<Face> out, float r0, float r1, float y0, float y1,
                              int color, float alpha) {
        float[] c = CosmeticModel.rgba(color, alpha);
        for (int i = 0; i < SEG; i++) {
            float a0 = (float) (Math.PI * 2 * i / SEG);
            float a1 = (float) (Math.PI * 2 * (i + 1) / SEG);
            Face f = new Face();
            place(f.v[0], a0, r0, y0);
            place(f.v[1], a1, r0, y0);
            place(f.v[2], a1, r1, y1);
            place(f.v[3], a0, r1, y1);
            CosmeticModel.normal(f);
            System.arraycopy(c, 0, f.top, 0, 4);
            System.arraycopy(c, 0, f.bottom, 0, 4);
            f.emissive = true;
            out.add(f);
        }
    }

    private static void place(float[] v, float ang, float r, float y) {
        v[0] = (float) Math.cos(ang) * r;
        v[1] = y;
        v[2] = (float) Math.sin(ang) * r;
    }

    /** Четыре вертикальные «руны» поверх кольца — по ним видно медленный поворот нимба. */
    private static void runes(List<Face> out, float cy, float r, int color) {
        float[] c = CosmeticModel.rgba(mixWhite(color, 0.3f), 0.98f);
        for (int k = 0; k < 4; k++) {
            float ang = (float) (Math.PI * 2 * k / 4);
            float cx = (float) Math.cos(ang) * r;
            float cz = (float) Math.sin(ang) * r;
            float tx = (float) Math.cos(ang) * 1.1f;
            float tz = (float) Math.sin(ang) * 1.1f;
            Face f = new Face();
            f.v[0][0] = cx - tx; f.v[0][1] = cy;              f.v[0][2] = cz - tz;
            f.v[1][0] = cx;      f.v[1][1] = cy - 1.7f;       f.v[1][2] = cz;
            f.v[2][0] = cx + tx; f.v[2][1] = cy;              f.v[2][2] = cz + tz;
            f.v[3][0] = cx;      f.v[3][1] = cy + 1.7f;       f.v[3][2] = cz;
            CosmeticModel.normal(f);
            System.arraycopy(c, 0, f.top, 0, 4);
            System.arraycopy(c, 0, f.bottom, 0, 4);
            f.emissive = true;
            out.add(f);
        }
    }

    private static int mixWhite(int rgb, float t) {
        return (ch(rgb >> 16, t) << 16) | (ch(rgb >> 8, t) << 8) | ch(rgb, t);
    }

    private static int ch(int shifted, float t) {
        int v = shifted & 0xFF;
        return Math.min(255, (int) (v + (255 - v) * t));
    }
}

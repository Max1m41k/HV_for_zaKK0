package com.visualmod.totem;

import java.util.Arrays;

/**
 * Режим COLOR: тот же закрытый силуэт тотема, что у 1.12/JUNGLE, но все тайлы палитры
 * считаются из одного выбранного цвета.
 *
 * Цвет каждой роли — это не «тот же оттенок просто темнее/светлее» (такая заливка выглядит
 * плоским пластилином), а три независимые величины: светлота по общей лестнице, насыщенность и
 * сдвиг оттенка. Тени уходят в более густой и холодный тон, блики — в тёплый и бледный.
 *
 * Палитра держится на трёх семействах: корпус/металл/тени — из выбранного цвета, кости и перья —
 * всегда тёплый нейтральный (на них читается форма), самоцвет — в контрастном оттенке. Плюс у
 * каждой роли свой узор (ступени, резьба, фасеты самоцвета, металлическая полоса, «перо») и слабый
 * градиент света сверху вниз.
 *
 * Модель и текстура пишутся в ресурспак TotemPack, поэтому механизм тот же, что у SAVE в редакторе.
 */
public final class TotemTint {

    public static final int ATLAS = 64;
    public static final int TILE = 8;

    /** Роли кубов: порядок совпадает с ROLE_LIGHT/ROLE_SAT/ROLE_HUE. */
    static final int BASE = 0, RIM = 1, SKIRT = 2, BELT = 3, ACCENT = 4, BODY = 5,
            GLYPH = 6, SHINE = 7, BONE = 8, DARK = 9, CROWN = 10, FEATHER = 11;

    /** Лестница светлоты: 0.10 — почти чёрный фон атласа, 0.72 — блик. Растягивается под выбранный цвет. */
    private static final float[] ROLE_LIGHT = {
            0.26f, 0.34f, 0.46f, 0.56f, 0.70f, 0.54f, 0.66f, 0.72f, 0.70f, 0.10f, 0.66f, 0.48f
    };
    /** Блик и кости чуть бледнее корпуса, но цвет держат: иначе золото выцветает в сливочное. */
    private static final float[] ROLE_SAT = {
            1.15f, 1.10f, 1.05f, 0.92f, 1.10f, 1.00f, 1.05f, 0.68f, 0.60f, 1.25f, 0.85f, 1.05f
    };
    /** Сдвиг оттенка по роли в градусах: холодные тени, тёплые блики. Кости и самоцвет — отдельно. */
    private static final float[] ROLE_HUE = {
            -22f, -14f, -4f, 12f, 22f, 4f, 16f, 20f, 14f, -30f, 16f, -8f
    };

    private TotemTint() {}

    /** Базовый цвет роли без узора — тот, что задаёт светлоту и насыщенность тайла. */
    static int colorOf(int rgb, int role) {
        return roleRgb(toHsl(rgb), role, 1f, 0f);
    }

    /** Тайль роли — i-й в сетке 8x8. */
    static int[] tileOf(int role) {
        int u = (role % (ATLAS / TILE)) * TILE, v = (role / (ATLAS / TILE)) * TILE;
        return new int[]{u, v, u + TILE, v + TILE};
    }

    public static int[] texturePixels(int rgb) {
        int[] img = new int[ATLAS * ATLAS];
        Arrays.fill(img, colorOf(rgb, DARK));
        float[] hsl = toHsl(rgb);
        for (int role = 0; role < ROLE_LIGHT.length; role++) {
            int[] uv = tileOf(role);
            // Свет сверху вниз: у модели и так есть затемнённые боковые грани, поэтому градиент слабый.
            for (int y = 0; y < TILE; y++) {
                float light = role == DARK ? 1f : 1.13f - 0.26f * y / (float) (TILE - 1);
                for (int x = 0; x < TILE; x++) {
                    int c = roleRgb(hsl, role, pattern(role, x, y) * light, white(role, x, y));
                    img[(uv[1] + y) * ATLAS + uv[0] + x] = c;
                }
            }
        }
        return img;
    }

    /** Опорные HSL роли (светлота, насыщенность, оттенок) -> пиксель с учётом узора. */
    private static int roleRgb(float[] hsl, int role, float lightMul, float whiteMix) {
        float anchor = clamp(hsl[2], 0.18f, 0.88f);
        float l = clamp(ROLE_LIGHT[role] * (0.84f + 0.32f * anchor) * lightMul, 0.03f, 0.97f);
        float h, s;
        if (role == BONE || role == FEATHER) {
            // Кости, бивни и перья — всегда тёплый нейтральный, как в ваниле. Иначе тотем из
            // одного оттенка = плоская одноцветная болванка, форма не читается.
            h = wrap(38f + hsl[0] * 0.04f);
            s = 0.36f;
        } else if (role == ACCENT) {
            // Самоцвет в контрастном оттенке: единственная точка, которая обязана «цеплять».
            h = wrap(hsl[0] + 168f);
            s = clamp(hsl[1] * 1.12f + 0.10f, 0.60f, 1f);
        } else {
            h = wrap(hsl[0] + ROLE_HUE[role]);
            s = clamp((hsl[1] * 1.12f + 0.10f) * ROLE_SAT[role], 0f, 1f);
        }
        s *= 1f - whiteMix;
        l = clamp(l + (1f - l) * whiteMix, 0f, 1f);
        return fromHsl(h, s, l);
    }

    /** Узор тайла — множитель светлоты. Всё блоками по 2 пикселя: на мипах не рассыпается в шум. */
    private static float pattern(int role, int x, int y) {
        int qx = x / 2, qy = y / 2;
        switch (role) {
            case BASE:
                return qy == 0 ? 1.16f : 1f - 0.05f * qy;
            case RIM:
                return qx % 2 == 0 ? 1.12f : 0.90f;
            case SKIRT:
                return (x + y) % 4 < 2 ? 1.10f : 0.92f;
            case BELT:
                return y <= 1 || y >= 6 ? 0.74f : (y <= 3 ? 1.26f : 1.02f);
            case ACCENT:
                return (x + y) % 4 < 2 ? 1.14f : 0.86f;
            case GLYPH:
                return isMark(x, y) ? 0.60f : 1.06f;
            case BONE:
                return 1f - 0.10f * qy;
            case CROWN:
                return qx % 2 == 0 ? 1.18f : 0.88f;
            case FEATHER:
                return qy % 2 == 0 ? 1.12f : 0.84f;
            case BODY:
                return (x * 3 + y * 5) % 4 == 0 ? 0.94f : 1.02f;
            default:
                return 1f;
        }
    }

    /** Белая подмешка: specular-точки, без которых самоцвет и блик выглядят плоскими. */
    private static float white(int role, int x, int y) {
        switch (role) {
            case ACCENT:
                return x >= 1 && x <= 2 && y >= 1 && y <= 2 ? 0.72f : 0f;
            case SHINE: {
                float d = (float) Math.hypot(x - 3.5f, y - 3.5f) / 4.95f;
                return clamp(0.30f - 0.5f * d, 0f, 1f);
            }
            case BELT:
                return y == 2 || y == 3 ? 0.12f : 0f;
            case CROWN:
                return y <= 1 ? 0.14f : 0f;
            case BONE:
                return 0.06f;
            case GLYPH:
                return isMark(x, y) ? 0f : 0.10f;
            default:
                return 0f;
        }
    }

    /** Гравировка на корпусе: крест в центре тайла — по нему читается масштаб и «резьба». */
    private static boolean isMark(int x, int y) {
        return ((x == 3 || x == 4) && y >= 1 && y <= 6) || ((y == 3 || y == 4) && x >= 1 && x <= 6);
    }

    /** Геометрия COLOR-тотема общая для всех вариантов (см. {@link TotemLayers}), здесь только текстура. */
    public static String modelJson() {
        return TotemLayers.modelJson("visualmod:item/totem_tint");
    }

    /** RGB -> HSL: h в градусах 0..360, s и l в 0..1. */
    private static float[] toHsl(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float l = (max + min) / 2f;
        float h = 0f, s = 0f;
        if (max != min) {
            float d = max - min;
            s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
            if (max == r) h = (g - b) / d + (g < b ? 6f : 0f);
            else if (max == g) h = (b - r) / d + 2f;
            else h = (r - g) / d + 4f;
            h *= 60f;
        }
        return new float[]{h, s, l};
    }

    private static int fromHsl(float h, float s, float l) {
        float c = (1f - Math.abs(2f * l - 1f)) * s;
        float hp = h / 60f;
        float x = c * (1f - Math.abs(hp % 2f - 1f));
        float r, g, b;
        if (hp < 1f) { r = c; g = x; b = 0f; }
        else if (hp < 2f) { r = x; g = c; b = 0f; }
        else if (hp < 3f) { r = 0f; g = c; b = x; }
        else if (hp < 4f) { r = 0f; g = x; b = c; }
        else if (hp < 5f) { r = x; g = 0f; b = c; }
        else { r = c; g = 0f; b = x; }
        float m = l - c / 2f;
        return 0xFF000000 | (Math.round(clamp(r + m, 0f, 1f) * 255f) << 16)
                | (Math.round(clamp(g + m, 0f, 1f) * 255f) << 8)
                | Math.round(clamp(b + m, 0f, 1f) * 255f);
    }

    private static float wrap(float h) {
        float v = h % 360f;
        return v < 0f ? v + 360f : v;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}

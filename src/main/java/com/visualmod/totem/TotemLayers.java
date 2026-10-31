package com.visualmod.totem;

import java.util.ArrayList;
import java.util.List;

/**
 * Наслойка тотема: каждый куб силуэта режется на тонкие ламели по высоте, соседние ламели
 * чередуются с уступом {@link #INSET} по x/z, поэтому на боковом свете статуя читается набором
 * кованых полос, а не литой болванкой. Поворотов нет: ваниль 1.21.4 принимает rotation элемента
 * только кратный 22.5°.
 *
 * Боковые грани ламели берут свой горизонтальный срез тайла роли (v по высоте куба), поэтому
 * слои разделяются ещё и текстурой, а не только геометрией. Верх/низ ламели берут тайл целиком.
 *
 * Геометрия одна на все варианты: 1.12, JUNGLE и COLOR отличаются только текстурой, а раскладка
 * тайлов ролей у всех трёх — сетка 8x8 в порядке ROLE_OF, так что срезы сходятся у всех.
 *
 * Ламели перекрываются на {@link #OVERLAP}: без этого верх нижней и низ верхней легли бы в одну
 * плоскость с разными uv и дали бы мерцание z-буфера на кольце уступа.
 */
public final class TotemLayers {

    /** толщина ламели в модельных единицах */
    static final double STEP = 1.0;
    /** уступ нечётных ламелей внутрь по x/z */
    static final double INSET = 0.16;
    /** перекрытие ламели вниз, чтобы грани не совпадали по плоскости */
    static final double OVERLAP = 0.02;

    /** [x0,y0,z0,x1,y1,z1] — каждый куб перекрывает опору, щелей между частями нет. */
    public static final double[][] CUBES = {
            {4.8, 0.0, 4.8, 11.2, 1.4, 11.2},
            {4.4, 1.1, 4.4, 11.6, 1.9, 11.6},
            {5.6, 1.7, 5.6, 10.4, 4.6, 10.4},
            {5.3, 4.2, 5.3, 10.7, 5.1, 10.7},
            {7.1, 4.3, 5.1, 8.9, 5.0, 5.4},
            {5.7, 4.9, 5.7, 10.3, 8.8, 10.3},
            {6.9, 6.0, 5.5, 9.1, 8.0, 5.8},
            {5.0, 8.5, 5.2, 11.0, 9.5, 10.8},
            {3.6, 5.2, 6.4, 5.9, 8.9, 9.6},
            {10.1, 5.2, 6.4, 12.4, 8.9, 9.6},
            {3.4, 4.2, 6.2, 6.0, 5.9, 9.8},
            {10.0, 4.2, 6.2, 12.6, 5.9, 9.8},
            {6.9, 9.3, 6.7, 9.1, 10.1, 9.1},
            {5.4, 9.8, 5.2, 10.6, 13.7, 10.6},
            {6.2, 11.9, 5.0, 7.3, 12.8, 5.3},
            {8.7, 11.9, 5.0, 9.8, 12.8, 5.3},
            {6.8, 10.4, 5.0, 9.2, 11.2, 5.3},
            {7.0, 10.5, 4.9, 7.5, 11.0, 5.1},
            {8.0, 10.5, 4.9, 8.5, 11.0, 5.1},
            {5.1, 13.3, 4.9, 10.9, 14.4, 10.9},
            {4.2, 13.5, 6.9, 5.4, 14.6, 8.9},
            {10.6, 13.5, 6.9, 11.8, 14.6, 8.9},
            {5.6, 14.0, 7.0, 6.6, 16.0, 8.8},
            {7.2, 14.0, 6.8, 8.8, 16.0, 9.0},
            {9.4, 14.0, 7.0, 10.4, 16.0, 8.8},
    };

    /** Роли кубов: индекс в палитре ролей варианта (у COLOR — ROLE_LIGHT/ROLE_SAT/ROLE_HUE). */
    public static final int[] ROLE_OF = {
            0, 1, 2, 3, 4, 5, 6, 7, 5, 5, 2, 2, 2,
            5, 8, 8, 9, 8, 8, 10, 11, 11, 11, 8, 11
    };

    private static final String[] FACE_KEY = {"down", "up", "north", "south", "west", "east"};

    private TotemLayers() {}

    /**
     * Режет куб на ламели и возвращает их как элементы JSON-модели.
     *
     * @param uv6 прямоугольники атласа в пикселях {u0,v0,u1,v1} в порядке {@link #FACE_KEY};
     *            у четырёх боковых v масштабируется по высоте ламели, верх/низ берутся целиком
     */
    public static List<String> lamellas(double[] c, double[][] uv6) {
        List<String> out = new ArrayList<>();
        double y0 = c[1], y1 = c[4];
        int cnt = Math.max(1, (int) Math.round((y1 - y0) / STEP));
        // уступ не может съесть тонкий куб: глаза, ремни и перья тоньше 2*INSET
        double maxIn = Math.max(0, Math.min(c[3] - c[0], c[5] - c[2]) / 2 - 0.05);
        for (int k = 0; k < cnt; k++) {
            double ya = k == 0 ? y0 : y0 + (y1 - y0) * k / cnt - OVERLAP;
            double yb = y0 + (y1 - y0) * (k + 1) / cnt;
            double in = (k % 2 == 1 && cnt > 1) ? Math.min(INSET, maxIn) : 0;
            StringBuilder sb = new StringBuilder();
            sb.append("{\"from\":[").append(n(c[0] + in)).append(',').append(n(ya)).append(',').append(n(c[2] + in))
              .append("],\"to\":[").append(n(c[3] - in)).append(',').append(n(yb)).append(',').append(n(c[5] - in))
              .append("],\"faces\":{");
            for (int f = 0; f < 6; f++) {
                double[] r = uv6[f];
                if (f >= 2 && f <= 5) r = sliceV(r, y0, y1, ya, yb);
                if (f > 0) sb.append(',');
                sb.append('"').append(FACE_KEY[f]).append("\":{\"uv\":").append(uvJson(r)).append(",\"texture\":\"#0\"}");
            }
            out.add(sb.append("}}").toString());
        }
        return out;
    }

    /** v тайла по высоте: v0 тайла соответствует верху куба, как у ванильных боковых граней. */
    static double[] sliceV(double[] r, double y0, double y1, double ya, double yb) {
        double span = y1 - y0;
        if (span <= 0) return r;
        double va = r[1] + (y1 - yb) / span * (r[3] - r[1]);
        double vb = r[1] + (y1 - ya) / span * (r[3] - r[1]);
        return new double[]{r[0], va, r[2], vb};
    }

    /** Пиксели атласа -> uv-единицы модели: 16 единиц на весь спрайт, а не ATLAS пикселей. */
    static String uvJson(double[] px) {
        double k = 16.0 / TotemSpec.ATLAS;
        return "[" + n(px[0] * k) + "," + n(px[1] * k) + "," + n(px[2] * k) + "," + n(px[3] * k) + "]";
    }

    /** Модель тотема из общего силуэта: вариант подставляет только свою текстуру. */
    public static String modelJson(String texture) {
        List<String> els = new ArrayList<>();
        for (int i = 0; i < CUBES.length; i++) {
            int[] tile = TotemTint.tileOf(ROLE_OF[i]);
            double[] t = {tile[0], tile[1], tile[2], tile[3]};
            double[][] uv6 = {t, t, t, t, t, t};
            els.addAll(lamellas(CUBES[i], uv6));
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{\"parent\":\"minecraft:item/generated\",\"gui_light\":\"front\",")
          .append("\"textures\":{\"0\":\"").append(texture).append("\",\"particle\":\"").append(texture).append("\"},")
          .append("\"elements\":[");
        for (int i = 0; i < els.size(); i++) sb.append(i > 0 ? "," : "").append(els.get(i));
        sb.append("],\"display\":").append(DISPLAY).append('}');
        return sb.toString();
    }

    static String n(double v) {
        double r = Math.round(v * 10000) / 10000.0;
        return r == Math.floor(r) ? String.valueOf((long) r) : String.valueOf(r);
    }

    /** display откалиброван под этот силуэт ещё до наслойки — объём кубов она не меняет. */
    static final String DISPLAY = "{\"thirdperson_righthand\":{\"rotation\":[0,-90,25],\"translation\":[0,2.4,0],\"scale\":[0.8,0.8,0.8]},"
            + "\"thirdperson_lefthand\":{\"rotation\":[0,90,-25],\"translation\":[0,2.4,0],\"scale\":[0.8,0.8,0.8]},"
            + "\"firstperson_righthand\":{\"rotation\":[0,-90,25],\"translation\":[1.6,1.0,1.0],\"scale\":[0.72,0.72,0.72]},"
            + "\"firstperson_lefthand\":{\"rotation\":[0,90,-25],\"translation\":[1.6,1.0,1.0],\"scale\":[0.72,0.72,0.72]},"
            + "\"ground\":{\"rotation\":[0,0,0],\"translation\":[0,2,0],\"scale\":[0.5,0.5,0.5]},"
            + "\"head\":{\"rotation\":[0,0,0],\"translation\":[0,10,0],\"scale\":[0.8,0.8,0.8]},"
            + "\"gui\":{\"rotation\":[15,-135,0],\"translation\":[0,-1,0],\"scale\":[0.7,0.7,0.7]},"
            + "\"fixed\":{\"rotation\":[0,0,0],\"translation\":[0,0,0],\"scale\":[0.75,0.75,0.75]}}";
}

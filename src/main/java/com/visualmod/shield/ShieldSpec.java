package com.visualmod.shield;

import java.util.ArrayList;
import java.util.List;

/**
 * Геометрия и атлас щита — единственный источник для всех режимов: модели и картинку из
 * одного класса пишет {@link ShieldPack} в рантайм-пак, поэтому узор не может разъехаться
 * с ламелями.
 *
 * Пластина собрана слоями: четыре вложенные оболочки одной силуэтной кривой, каждая тонкая
 * (0.45 по z) и выступает над предыдущей. Стенки между оболочками — боковые грани; они берут
 * свой срез тайла обода по номеру полосы, поэтому соседние ламели ловят разный свет и слои
 * читаются как кованые полосы, а не как одна плита.
 * Поворотов нет: vanilla 1.21.4 принимает rotation элемента только кратный 22.5°.
 *
 * Про UV. BakedQuadFactory.packVertexData считает Sprite.getFrameU(uv / 16f), то есть
 * 16 единиц uv = весь спрайт, а texture_size ванилью не читается вовсе. Пиксельные координаты
 * атласа переводятся в единицы модели множителем {@link #U}; без этого грань уходит за свой
 * спрайт и выбирает соседние тайлы блока атласа.
 */
public final class ShieldSpec {

    public static final int TEX = 64;
    /** пикселей текстуры на модельную единицу */
    static final double S = 2.5;
    /** uv-единиц на пиксель атласа: 16 единиц = весь спрайт */
    static final double U = 16.0 / TEX;

    /** области атласа в пикселях [u0,v0,u1,v1]: морда, тыл и три служебных тайла внизу */
    static final int[] FRONT = {0, 0, 32, 48}, BACK = {32, 0, 64, 48};
    static final int[] EDGE = {0, 49, 8, 57}, GRIP = {9, 49, 17, 57}, GEM = {18, 49, 26, 57};

    /** габарит пластины и её привязка к текстуре: x = XMIN + u/S, y = YMAX - v/S */
    static final double XMIN = 1.6, YMAX = 17.4, YTOP = 17, CX = 8, CY = 10.5;
    /** половина ширины пластины по высотам 0, 1.7, 3.4 … 17 — heater-силуэт, остриё внизу */
    static final double[] HW = {0.9, 1.9, 2.9, 3.8, 4.6, 5.3, 5.8, 6.1, 6.25, 6.3, 6.3};
    static final double HW_STEP = YTOP / (HW.length - 1);
    /** [ступень внутрь, z0, z1] — оболочки-слои, от края к центру */
    static final double[][] SHELLS = {{0, 8.0, 8.45}, {0.4, 8.45, 8.9}, {0.8, 8.9, 9.35}, {1.2, 9.35, 9.8}};
    /** полос в оболочке: шаг ~1.2 ед. — ламель, а не «ёлка» */
    static final int BANDS = 14;
    /** Оттенки кромки ламели: три среза тайла обода, идут по кругу — так слои разделяются текстурой. */
    static final double[] BAND_EDGE = {0.18, 0.5, 0.82};

    private ShieldSpec() {}

    static double hw(double y) {
        if (y <= 0) return HW[0];
        if (y >= YTOP) return HW[HW.length - 1];
        double k = y / HW_STEP, i = Math.floor(k);
        return lerp(HW[(int) i], HW[Math.min(HW.length - 1, (int) i + 1)], k - i);
    }

    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    static boolean inPlate(double x, double y) {
        return y >= 0 && y <= YTOP && Math.abs(x - CX) <= hw(y);
    }

    /** Обод: хоть одна проба радиуса rad вышла за силуэт. */
    static boolean nearEdge(double x, double y, double rad) {
        for (int k = 0; k < 24; k++) {
            double a = k / 24.0 * Math.PI * 2;
            if (!inPlate(x + Math.cos(a) * rad, y + Math.sin(a) * rad)) return true;
        }
        return false;
    }

    /** Заклёпки сажаем на 1.1 ед. внутрь от кромки — по ним читается масштаб и «кузнечная» работа. */
    static double[] rivetX(int row, double y) {
        double inset = hw(y) - 1.1;
        return row == 0 ? new double[]{CX} : new double[]{CX - inset, CX + inset};
    }

    static final double[] RIVET_ROWS = {16.2, 13.4, 10.5, 7.4, 4.4, 1.6};

    // ---------------------------------------------------------------- пиксели
    /** Режимы раскраски: свой цвет из настроек, рыцарская сталь с золотом, обсидиан с аметистом. */
    public static final int CUSTOM = 0, KNIGHT = 1, LEGENDARY = 2;

    /**
     * Атлас щита: морда и тыл пластины плюс служебные тайлы обода, ремня упора и самоцвета.
     * cd — вариант «щит на кулдауне»: тот же узор, но приглушённый и с трещинами по полю.
     *
     * Всё непрозрачно и без «дырок»: прозрачные пиксели и тёмная кайма по краю тайла уже ломали
     * здесь текстуры (атлас bleed на мипах).
     *
     * Картинку рисует тот же класс, что считает геометрию, поэтому узор и ламели не могут
     * разъехаться: раньше атлас KNIGHT/LEGENDARY делал внешний скрипт по копии силуэта.
     */
    public static int[] pixels(int argb, boolean cd, int variant) {
        float[] src = toHsl(argb);
        float[] c = switch (variant) {
            case KNIGHT -> new float[]{212f, 0.13f, 0.55f};
            case LEGENDARY -> new float[]{266f, 0.30f, 0.20f};
            default -> new float[]{src[0], src[1], src[2]};
        };
        float[] acc = switch (variant) {
            case KNIGHT -> new float[]{45f, 0.78f, 0.56f};
            case LEGENDARY -> new float[]{288f, 0.72f, 0.62f};
            default -> new float[]{c[0], c[1], c[2]};
        };
        if (cd) {
            c[1] *= 0.66f;
            c[2] *= 0.86f;
            acc[1] *= 0.72f;
            acc[2] *= 0.82f;
        }
        final float[] base = c;
        int[] img = new int[TEX * TEX];
        java.util.Arrays.fill(img, hsl2rgb(base[0], base[1] * 0.8f, sink(base[2], 0.55f)));

        for (int v = 0; v < 48; v++) {
            for (int u = 0; u < 32; u++) {
                double x = XMIN + u / S, y = YMAX - v / S;
                if (!inPlate(x, y)) continue;
                img[v * TEX + u] = face(base, acc, x, y, cd);
                img[(v * TEX) + BACK[0] + u] = backFace(base, acc, x, y, cd);
            }
        }
        tile(img, EDGE, (u, v) -> hsl2rgb(base[0], base[1], sink(base[2], 0.15f + v * 0.5f)));
        tile(img, GRIP, (u, v) -> hsl2rgb(30f, 0.42f, clamp(0.30 - v * 0.16, 0.05, 0.95)));
        tile(img, GEM, (u, v) -> {
            double d = Math.hypot(u - 0.5, v - 0.5);
            if (d > 0.48) return hsl2rgb(acc[0], acc[1] * 0.9f, sink(acc[2], 0.45f));
            if (d < 0.2) return hsl2rgb(acc[0], clamp(acc[1] * 1.15f, 0, 1), sink(acc[2], 0.4f));
            if (d < 0.34) return hsl2rgb(acc[0], acc[1] * 0.85f, lift(acc[2], 0.55f));
            return hsl2rgb(acc[0], acc[1], lift(acc[2], 0.25 - v * 0.4));
        });
        return img;
    }

    interface TileFn { int get(double u, double v); }

    static void tile(int[] img, int[] r, TileFn fn) {
        int w = r[2] - r[0], h = r[3] - r[1];
        for (int v = 0; v < h; v++)
            for (int u = 0; u < w; u++)
                img[(r[1] + v) * TEX + r[0] + u] = fn.get(u / (double) (w - 1), v / (double) (h - 1));
    }

    /**
     * Морда пластины: поле с градиентом, полировка, тонкий крест, оправа, обод и заклёпки.
     *
     * Все блики и тени считаются через {@link #lift}/{@link #sink} — с насыщением, которое не
     * даёт цвету выгореть в белый (плоский «постер» — как раз то, чего здесь надо избегать).
     * Зоны акцентного металла (крест, умбон, заклёпки) красятся отдельным оттенком {@code acc},
     * но светлоту берут у базы, чтобы рельеф ламелей не пропадал.
     */
    static int face(float[] c, float[] acc, double x, double y, boolean cd) {
        float l = c[2] + (float) (y / YTOP - 0.5) * 0.22f;
        float s = c[1];
        boolean metal = false;
        double sheen = Math.abs((x - 3.6) * 0.5 + (y - 14.6) * 0.62);
        if (sheen < 2.4) l = lift(l, 0.16 * (1 - (float) (sheen / 2.4)));
        if (y > YTOP - 1.2) l = lift(l, 0.1 * (float) ((y - (YTOP - 1.2)) / 1.2));

        // эмблема: тонкий крест — по нему щит читается щитом, а не плакатом
        boolean vert = Math.abs(x - CX) < 1.1 && y > 2.2 && y < 15.8;
        boolean horiz = Math.abs(y - CY) < 1.1 && Math.abs(x - CX) < hw(y) - 1.6;
        if (vert || horiz) {
            boolean inner = (Math.abs(x - CX) < 0.75 && y > 2.7 && y < 15.3)
                    || (Math.abs(y - CY) < 0.7 && Math.abs(x - CX) < hw(y) - 2.1);
            if (inner) {
                l = lift(l, 0.10f);
                metal = true;
            } else {
                l = sink(l, 0.16f);
            }
        } else {
            boolean nearV = Math.abs(x - CX) < 1.55 && y > 1.7 && y < 16.3;
            boolean nearH = Math.abs(y - CY) < 1.55 && Math.abs(x - CX) < hw(y) - 1.15;
            if (nearV || nearH) l = sink(l, 0.22f);
        }
        // умбон на стыке лучей: маленькая шляпка (крупный круг съедает пластину и спорит с крестом)
        double dx = x - CX, dy = y - CY, rad = Math.hypot(dx, dy);
        if (Math.abs(rad - 1.75) < 0.3) l = sink(l, 0.24f);
        if (Math.abs(rad - 1.4) < 0.14) l = lift(l, 0.2f);
        if (rad < 1.1) {
            l = lift(l, 0.2 * (1 - (float) (rad / 1.1)));
            s = clamp(s * 1.12f, 0, 1);
            metal = true;
        }
        // «корона» над эмблемой: шеврон заполняет пустое поле между крестом и верхним краем
        double chev = Math.abs(y - (15.2 - Math.abs(x - CX) * 0.5));
        if (chev < 0.4 && y > 12 && Math.abs(x - CX) < 4.6) l = lift(l, 0.18f);

        // обод: тёмная посадочная линия, светлое железо, блик по самому краю
        if (nearEdge(x, y, 1.4)) l = sink(l, 0.34f);
        if (nearEdge(x, y, 0.9)) l = lift(l, 0.26f);
        if (nearEdge(x, y, 0.4)) l = lift(l, 0.16f);
        for (double ry : RIVET_ROWS) {
            if (Math.abs(y - ry) > 0.6) continue;
            for (double rx : rivetX(ry >= YTOP - 1.5 ? 0 : 1, ry))
                if (Math.hypot(x - rx, y - ry) < 0.5)
                    return metal(acc, c, lift(c[2], 0.42f), clamp(c[1] * 0.9f, 0, 1));
        }
        // трещины на кулдауне: несколько диагональных разломов от центра к краю
        if (cd && rad > 2.0 && Math.abs(Math.sin(Math.atan2(dy, dx) * 2.5 + 0.7)) < 0.05) l = sink(l, 0.5f);
        l = clamp(l, 0.05f, 0.93f);
        return metal ? metal(acc, c, l, s) : hsl2rgb(c[0], s, l);
    }

    /** Акцентный металл: оттенок и насыщенность свои, светлота — от базы. */
    static int metal(float[] acc, float[] c, float l, float s) {
        return hsl2rgb(acc[0], Math.max(acc[1], s), clamp(l + (acc[2] - c[2]), 0.06f, 0.92f));
    }

    /** Тыл — та же форма, темнее, с ремнями упора: щит не должен «исчезать» с обратной стороны. */
    static int backFace(float[] c, float[] acc, double x, double y, boolean cd) {
        float[] h = toHsl(face(c, acc, x, y, cd));
        float l = sink(h[2], 0.42f);
        if (Math.abs(y - CY) < 2 && Math.abs(x - CX) < 2.4) l = sink(l, 0.35f);
        if (y > 4.5 && y < 13.5 && Math.abs(x - CX) < 0.8) l = sink(l, 0.28f);
        return hsl2rgb(h[0], clamp(h[1] * 0.9f, 0, 1), clamp(l, 0.03f, 0.9f));
    }

    /** Подсветка с насыщением: чем светлее база, тем меньше она ещё может посветлеть. */
    static float lift(float l, double a) {
        return clamp(l + a * (1.02 - l), 0.04, 0.93);
    }

    static float sink(float l, double a) {
        return clamp(l - a * (l + 0.06), 0.04, 0.93);
    }

    // ---------------------------------------------------------------- модель
    /**
     * Модель пластины. {@code tex} — id текстуры в {@code textures/item}, {@code block} —
     * стойка «под блоком»: геометрия та же, развёрнут только display.
     */
    public static String modelJson(String tex, boolean block) {
        return modelJson(tex, block, false);
    }

    /**
     * То же плюс «крылья» и гребень — декор LEGENDARY. Крылья тоже собраны слоями: тонкая
     * планка и лежащая на ней площадка поменьше, так что на боковом свете виден уступ.
     */
    public static String modelJson(String tex, boolean block, boolean wings) {
        List<String> els = new ArrayList<>();
        for (double[] sh : SHELLS) els.addAll(shell(sh[0], sh[1], sh[2]));
        els.add(box(CX - 2.2, CY - 2.2, 9.8, CX + 2.2, CY + 2.2, 10.45, EDGE));
        els.add(box(CX - 1.5, CY - 1.5, 10.45, CX + 1.5, CY + 1.5, 11.0, EDGE));
        els.add(box(CX - 0.95, CY - 0.95, 11.0, CX + 0.95, CY + 0.95, 11.75, GEM));
        // упор сзади: три бруса, каждый глубже и уже предыдущего — та же послойная логика
        els.add(box(7.0, 5.0, 7.0, 9.0, 13.0, 8.0, GRIP));
        els.add(box(6.4, 6.2, 5.8, 9.6, 11.8, 7.0, GRIP));
        els.add(box(5.0, 8.2, 4.6, 11.0, 9.8, 5.8, EDGE));
        if (wings) els.addAll(deco(block));

        StringBuilder sb = new StringBuilder();
        sb.append("{\"parent\":\"minecraft:item/generated\",\"gui_light\":\"front\",")
          .append("\"textures\":{\"0\":\"visualmod:item/").append(tex)
          .append("\",\"particle\":\"visualmod:item/").append(tex).append("\"},\"elements\":[");
        for (int i = 0; i < els.size(); i++) sb.append(i > 0 ? "," : "").append(els.get(i));
        sb.append("],\"display\":").append(block ? DISPLAY_BLOCK : DISPLAY_IDLE).append('}');
        return sb.toString();
    }

    /** [y0,y1,длина] крыла с одной стороны: внутренний край всегда в пластине, наружу — по силуэту. */
    static final double[][] WING_IDLE = {{12.6, 16.4, 2.4}, {8.8, 12.6, 2.8}, {5.2, 8.8, 2.2}};
    static final double[][] WING_BLOCK = {{13.2, 17.6, 4.2}, {9.2, 13.2, 4.6}, {5.4, 9.2, 3.8}};

    static List<String> deco(boolean block) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < (block ? WING_BLOCK : WING_IDLE).length; i++) {
            double[] w = (block ? WING_BLOCK : WING_IDLE)[i];
            // крыло врастает в пластину на 0.9: иначе издалека оно выглядит отдельной плитой
            double a0 = CX + hw((w[0] + w[1]) / 2) - 0.9, a1 = a0 + w[2];
            // самоцвет только на верхнем пере: на трёх сразу щит читается как колонка динамиков
            int[] inner = i == 0 ? GEM : EDGE;
            for (int side = 0; side < 2; side++) {
                double a = side == 0 ? a0 : 2 * CX - a1, b = side == 0 ? a1 : 2 * CX - a0;
                out.add(box(a, w[0], 8.9, b, w[1], 9.5, EDGE));
                out.add(box(a + 0.35, w[0] + 0.35, 9.5, b - 0.35, w[1] - 0.35, 9.95, inner));
            }
        }
        double[][] crest = block
                ? new double[][]{{6.6, 16.6, 8.8, 9.4, 21.0, 9.4}, {5.0, 16.6, 9.0, 6.6, 19.0, 9.2}, {9.4, 16.6, 9.0, 11.0, 19.0, 9.2}}
                : new double[][]{{6.4, 16.2, 8.9, 9.6, 18.4, 9.5}};
        for (double[] c : crest) out.add(box(c[0], c[1], c[2], c[3], c[4], c[5], EDGE));
        return out;
    }

    /** Одна оболочка: полос-ступеней, каждый уже силуэта на {@code inset} и тонкий по z. */
    static List<String> shell(double inset, double z0, double z1) {
        List<String> out = new ArrayList<>();
        double y0 = inset, y1 = YTOP - inset, step = (y1 - y0) / BANDS;
        for (int i = 0; i < BANDS; i++) {
            double b0 = y0 + i * step, b1 = y0 + (i + 1) * step;
            // ширину берём по середине полосы: лестница делит ошибку пополам и в профиле
            // остаются только настоящие слои-оболочки, а не пила из-за кривой силуэта
            double h = Math.max(0.35, hw((b0 + b1) / 2) - inset);
            out.add(plateBox(CX - h, b0, CX + h, b1, z0, z1, i));
        }
        return out;
    }

    /**
     * Полоса пластины: лицевые грани берут свой кусок узора, торцы — свой срез тайла обода.
     * Срез зависит от номера полосы, поэтому соседние ламели ловят разный свет и слои
     * читаются без поворотов элемента (их vanilla принимает только кратными 22.5°).
     */
    static String plateBox(double x0, double y0, double x1, double y1, double z0, double z1, int band) {
        String e = edgeFace(BAND_EDGE[band % BAND_EDGE.length]);
        return "{\"from\":[" + n(x0) + "," + n(y0) + "," + n(z0) + "],\"to\":[" + n(x1) + "," + n(y1) + "," + n(z1) + "],"
                + "\"faces\":{\"south\":" + rectFace(FRONT, x0, y0, x1, y1) + ",\"north\":" + rectFace(BACK, x0, y0, x1, y1)
                + ",\"east\":" + e + ",\"west\":" + e + ",\"up\":" + e + ",\"down\":" + e + "}}";
    }

    /** Горизонтальная полоска тайла обода высотой в один пиксель атласа. */
    static String edgeFace(double t) {
        double v = EDGE[1] + clamp(t, 0, 1) * (EDGE[3] - EDGE[1] - 1);
        return "{\"uv\":[" + n(EDGE[0] * U) + "," + n(v * U) + "," + n(EDGE[2] * U) + "," + n((v + 1) * U)
                + "],\"texture\":\"#0\"}";
    }

    static String rectFace(int[] origin, double x0, double y0, double x1, double y1) {
        double u0 = origin[0] + (x0 - XMIN) * S, u1 = origin[0] + (x1 - XMIN) * S;
        double v0 = origin[1] + (YMAX - y1) * S, v1 = origin[1] + (YMAX - y0) * S;
        return "{\"uv\":[" + n(u0 * U) + "," + n(v0 * U) + "," + n(u1 * U) + "," + n(v1 * U) + "],\"texture\":\"#0\"}";
    }

    static String box(double x0, double y0, double z0, double x1, double y1, double z1, int[] uv) {
        String f = face(uv);
        return "{\"from\":[" + n(x0) + "," + n(y0) + "," + n(z0) + "],\"to\":[" + n(x1) + "," + n(y1) + "," + n(z1) + "],"
                + "\"faces\":{\"north\":" + f + ",\"east\":" + f + ",\"south\":" + f + ",\"west\":" + f + ",\"up\":" + f + ",\"down\":" + f + "}}";
    }

    static String face(int[] px) {
        return "{\"uv\":[" + n(px[0] * U) + "," + n(px[1] * U) + "," + n(px[2] * U) + "," + n(px[3] * U) + "],\"texture\":\"#0\"}";
    }

    static String n(double v) {
        double r = Math.round(v * 10000) / 10000.0;
        return r == Math.floor(r) ? String.valueOf((long) r) : String.valueOf(r);
    }

    /** display снят с vanilla shield.json / shield_blocking.json — он уже откалиброван под этот объём пластины. */
    static final String DISPLAY_IDLE = "{\"thirdperson_righthand\":{\"rotation\":[0,90,0],\"translation\":[10,6,-4],\"scale\":[1,1,1]},"
            + "\"thirdperson_lefthand\":{\"rotation\":[0,90,0],\"translation\":[10,6,12],\"scale\":[1,1,1]},"
            + "\"firstperson_righthand\":{\"rotation\":[0,180,5],\"translation\":[-10,2,-10],\"scale\":[1.25,1.25,1.25]},"
            + "\"firstperson_lefthand\":{\"rotation\":[0,180,5],\"translation\":[10,0,-10],\"scale\":[1.25,1.25,1.25]},"
            + "\"gui\":{\"rotation\":[15,-25,-5],\"translation\":[2,3,0],\"scale\":[0.65,0.65,0.65]},"
            + "\"ground\":{\"rotation\":[0,0,0],\"translation\":[2,4,2],\"scale\":[0.4,0.4,0.4]},"
            + "\"head\":{\"rotation\":[0,90,0],\"translation\":[0,13,0],\"scale\":[1,1,1]},"
            + "\"fixed\":{\"rotation\":[0,180,0],\"translation\":[-4.5,4.5,-5],\"scale\":[0.55,0.55,0.55]}}";
    static final String DISPLAY_BLOCK = "{\"thirdperson_righthand\":{\"rotation\":[45,155,0],\"translation\":[-3.49,11,-2],\"scale\":[1.1,1.1,1.1]},"
            + "\"thirdperson_lefthand\":{\"rotation\":[45,155,0],\"translation\":[11.51,7,2.5],\"scale\":[1.1,1.1,1.1]},"
            + "\"firstperson_righthand\":{\"rotation\":[0,180,-5],\"translation\":[-15,5,-11],\"scale\":[1.35,1.35,1.35]},"
            + "\"firstperson_lefthand\":{\"rotation\":[0,180,-5],\"translation\":[5,5,-11],\"scale\":[1.35,1.35,1.35]},"
            + "\"gui\":{\"rotation\":[15,-25,-5],\"translation\":[2,3,0],\"scale\":[0.65,0.65,0.65]},"
            + "\"ground\":{\"rotation\":[0,0,0],\"translation\":[2,4,2],\"scale\":[0.4,0.4,0.4]},"
            + "\"head\":{\"rotation\":[0,90,0],\"translation\":[0,13,0],\"scale\":[1.1,1.1,1.1]},"
            + "\"fixed\":{\"rotation\":[0,180,0],\"translation\":[-4.5,4.5,-5],\"scale\":[0.55,0.55,0.55]}}";

    // ---------------------------------------------------------------- HSL
    /** RGB -> HSL: h в градусах 0..360, s и l 0..1. */
    static float[] toHsl(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float l = (max + min) / 2f, h = 0f, s = 0f;
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

    static int hsl2rgb(float h, float s, float l) {
        float c = (1f - Math.abs(2f * l - 1f)) * s;
        float hp = ((h % 360f) + 360f) % 360f / 60f;
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

    static float clamp(double v, double lo, double hi) {
        return (float) (v < lo ? lo : (v > hi ? hi : v));
    }
}

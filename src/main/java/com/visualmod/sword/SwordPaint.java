package com.visualmod.sword;

/**
 * Попиксельная покраска ролевых атласов клинков: sword_&lt;стиль&gt;.png для каждого стиля CustomSword.
 *
 * Цвет назначается не картинке целиком, а роли пикселя: полотно, кромка, гарда, рукоять, древко и
 * самоцвет. У старых атласов роль даёт ячейка 16x16, у новых спрайтов — карта swordmaps/&lt;стиль&gt;.txt,
 * где роль записана для каждого пикселя. Пикселю при этом возвращается оттенок выбранного цвета при
 * прежней светимости, так что градиент от обуха к кромке, хамон и тёмный контур остаются на месте —
 * меняется только материал.
 */
public final class SwordPaint {

    /** Порядок ячеек в атласе: четыре в первой строке, две во второй. */
    public static final String[] ROLES = {"BLADE", "EDGE", "GUARD", "GRIP", "POLE", "GEM"};

    /** Покраска знает ровно этот формат. 16x16 палитра кирамбита до ролей не дозрела — её не трогаем. */
    private static final int CELL = 16, ATLAS = 64;

    private static final int[] COLORS = {0xFFE2A8, 0xFFFDF4, 0xFFC94D, 0x7A4A2E, 0x8A6A46, 0x62D8FF};

    private SwordPaint() {}

    public static int color(int role) {
        return COLORS[role];
    }

    public static void setColor(int role, int rgb) {
        COLORS[role] = rgb & 0xFFFFFF;
    }

    /** Подпись шести цветов — её несёт BladeSkinSpec, по ней арбитр и решает, пора ли переписывать пак. */
    public static String signature() {
        StringBuilder sb = new StringBuilder("PAINT");
        for (int c : COLORS) sb.append(',').append(Integer.toHexString(c & 0xFFFFFF));
        return sb.toString();
    }

    /** Наш ли это атлас: пак перекрашивает только его, а по нему уже решает, писать ли цель. */
    public static boolean applies(int w, int h) {
        return w == ATLAS && h == ATLAS;
    }

    /**
     * base — ARGB row-major (w*h). Тот же массив возвращается, когда красить нечем: нет покраски
     * в состоянии или это не наш атлас. Иначе — новый буфер.
     */
    public static int[] apply(int[] base, int w, int h, BladeSkinSpec spec) {
        return apply(base, w, h, spec, null);
    }

    /**
     * Тот же проход, но роль можно задать попиксельно: спрайты нового поколения рисуют оружие
     * целиком по диагонали, и ячейка 16x16 там не содержит один материал. roles == null оставляет
     * прежнюю сетку ячеек — так старый sword_palette.png и кирамбит продолжают краситься как умели.
     */
    public static int[] apply(int[] base, int w, int h, BladeSkinSpec spec, int[] roles) {
        if (spec.paint() == null || !applies(w, h)) return base;
        if (roles != null && roles.length != w * h) roles = null;
        int[] out = base.clone();
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int role = roles != null ? roles[row + x] : role(x, y);
                if (role < 0 || role >= COLORS.length) continue;
                int argb = out[row + x];
                if ((argb >>> 24) == 0) continue;
                out[row + x] = tint(argb, COLORS[role]);
            }
        }
        return out;
    }

    /** Роль по старой сетке: четыре ячейки в первой строке, две во второй. */
    private static int role(int x, int y) {
        int cellRow = (y / CELL) * 4;
        if (cellRow >= COLORS.length) return -1;
        int role = cellRow + x / CELL;
        return role >= COLORS.length ? -1 : role;
    }

    /**
     * Карта ролей из swordmaps/&lt;стиль&gt;.txt: ровно w*h символов, '0'..'5' — роль, 'x' — не красить.
     * Пробельные символы игнорируются, чтобы карту можно было переносить строками. Любой другой
     * размер или мусор в тексте означает «карты нет», и вызывающий откатывается к ячейкам.
     */
    public static int[] parseRoles(String text, int w, int h) {
        if (text == null) return null;
        int[] roles = new int[w * h];
        int n = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r' || c == ' ' || c == '\t') continue;
            if (n >= roles.length) return null;
            roles[n++] = c >= '0' && c <= '9' ? c - '0' : -1;
        }
        return n == roles.length ? roles : null;
    }

    /**
     * Пиксель получает оттенок цвета роли, но остаётся на своей светимости: градиент от обуха
     * к кромке, хамон и чёрный контур при этом не исчезают. Если оттенок упирался бы в потолок
     * по самому яркому каналу, вся тройка честно уменьшается — перекос в белый здесь хуже, чем
     * чуть более тёмный насыщенный цвет.
     */
    private static int tint(int argb, int rgb) {
        double r = ((argb >> 16) & 0xFF) / 255.0, g = ((argb >> 8) & 0xFF) / 255.0, b = (argb & 0xFF) / 255.0;
        double tr = ((rgb >> 16) & 0xFF) / 255.0, tg = ((rgb >> 8) & 0xFF) / 255.0, tb = (rgb & 0xFF) / 255.0;
        double want = 0.299 * r + 0.587 * g + 0.114 * b;
        double of = 0.299 * tr + 0.587 * tg + 0.114 * tb;
        if (of < 0.004) return argb;
        double k = want / of;
        double pr = tr * k, pg = tg * k, pb = tb * k;
        double max = Math.max(pr, Math.max(pg, pb));
        if (max > 1.0) {
            double fit = 1.0 / max;
            pr *= fit;
            pg *= fit;
            pb *= fit;
        }
        return (argb & 0xFF000000) | (quant(pr) << 16) | (quant(pg) << 8) | quant(pb);
    }

    private static int quant(double x) {
        return (int) Math.floor(Math.min(1.0, Math.max(0.0, x)) * 255 + 0.5);
    }
}

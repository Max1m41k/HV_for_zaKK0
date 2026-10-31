package com.visualmod.sword;

/**
 * Кадр «живого» клинка: настоящая текстура меча плюс поверх движущийся узор стихии.
 *
 * Узор собран из синусов с целой частотой по фазе цикла, поэтому последовательность кадров
 * замыкается без скачка (кадр frames-1 -> кадр 0). Разряды SHOCK — исключение: им скачок
 * только к лицу, а цикл всё равно короткий, потому что кадры просто нумеруются по кругу.
 *
 * Альфа не меняется нигде: где пиксель был пустым, там и остаётся пустым.
 */
public final class BladeSkin {

    /**
     * Потолок пикселей на весь стрип: восемь кадров — это восемь копий текстуры в живом
     * NativeImage плюс столько же на диске. Мегапиксельной текстуре кадра не остаётся вовсе:
     * frames() вернёт единицу, и пак без покраски такую цель пропустит.
     */
    public static final int MAX_STRIP_PIXELS = 1024 * 1024;

    private static final double TAU = Math.PI * 2;

    private BladeSkin() {}

    /** Сколько кадров влезает в бюджет при разрешении w x h. */
    public static int frames(String element, int w, int h) {
        int want = "SHOCK".equals(element) ? 10 : 8;
        int fit = Math.max(1, MAX_STRIP_PIXELS / Math.max(1, w * h));
        return Math.min(want, fit);
    }

    /** Тиков на кадр: ваниль прогоняет анимацию на 20 т/с, так что 2 -> 10 к/с. */
    public static int frametime(String element, float speed) {
        int base = element == null ? 2 : switch (element) {
            case "FROST" -> 3;
            case "SHOCK" -> 1;
            case "VOID" -> 4;
            default -> 2;
        };
        return Math.max(1, Math.round(base / Math.max(0.2f, speed)));
    }

    /**
     * base — ARGB row-major (w*h). Слои из spec кладутся поверх базы один за другим:
     * стихия добавляется аддитивно (языки пламени ярче стали), аура тянет пиксель к своему
     * цвету и липнет к кромке спрайта. Возвращается новый буфер того же размера.
     */
    public static int[] paint(int[] base, int w, int h, BladeSkinSpec spec, int frame, int frames) {
        int[] out = base.clone();
        double t = frames <= 1 ? 0 : (double) frame / frames;
        boolean el = spec.hasElement(), au = spec.hasAura();
        float[] edge = au ? edgeDistance(base, w, h) : null;
        double falloff = Math.max(1.0, Math.min(w, h) * 0.13);
        double es = Math.max(0.05, spec.elementStrength());
        double as = clamp(spec.auraStrength() * 0.85, 0.12, 1.2);
        double er = channel(spec.elementColor(), 16), eg = channel(spec.elementColor(), 8), eb = channel(spec.elementColor(), 0);
        double ar = channel(spec.auraColor(), 16), ag = channel(spec.auraColor(), 8), ab = channel(spec.auraColor(), 0);

        for (int y = 0; y < h; y++) {
            double v = (y + 0.5) / h;
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int argb = base[row + x];
                if ((argb >>> 24) == 0) continue;
                double r = ((argb >> 16) & 0xFF) / 255.0;
                double g = ((argb >> 8) & 0xFF) / 255.0;
                double b = (argb & 0xFF) / 255.0;
                double u = (x + 0.5) / w;
                double nr = r, ng = g, nb = b;

                if (el) {
                    double lum = 0.299 * r + 0.587 * g + 0.114 * b;
                    double sat = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
                    double[] m = motif(spec.element(), u, v, t, frame, frames);
                    // нижняя планка держит в игре тёмные клинки (незерит почти чёрный), а пологий
                    // подъём от неё бережёт чёрный контур спрайта — иначе меч станет светящимся пятном
                    double shade = 0.15 + 0.85 * clamp((lum - 0.06) / 0.34, 0, 1);
                    double amount = Math.min(0.92, m[4] * shade * (sat < 0.35 ? 1.0 : 0.5 + 0.5 * (1 - sat)) * es);
                    if (amount > 0.004) {
                        double k = amount * m[0];
                        nr += lerpTint(m[1], nr, er) * k;
                        ng += lerpTint(m[2], ng, eg) * k;
                        nb += lerpTint(m[3], nb, eb) * k;
                    }
                }

                if (au) {
                    double rim = Math.exp(-edge[row + x] / falloff);
                    double amount = Math.min(0.9, aura(spec.aura(), u, v, t, frame, rim, x, y) * as);
                    if (amount > 0.004) {
                        nr += (ar - nr) * amount;
                        ng += (ag - ng) * amount;
                        nb += (ab - nb) * amount;
                    }
                }

                out[row + x] = rgba(nr, ng, nb, (argb >>> 24));
            }
        }
        return out;
    }

    private static double channel(int argb, int shift) {
        return ((argb >> shift) & 0xFF) / 255.0;
    }

    /**
     * Тон стихии отвечает за форму узора, цвет настройки — за его оттенок, поэтому они
     * смешиваются: раньше цвет вообще не доходил до пикселей, и смена цвета лишь
     * перезаписывала пак той же картинкой.
     */
    private static double lerpTint(double motifTone, double src, double colorChannel) {
        return (motifTone * 0.45 + colorChannel * 0.55) - src;
    }

    /**
     * Аура SwordAura в текстуре: всё то же, что рисовалось квадами вокруг клинка,
     * но внутри силуэта спрайта — rim это близость к кромке, она и держит свечение на лезвии.
     */
    private static double aura(String style, double u, double v, double t, int frame,
                               double rim, int x, int y) {
        switch (style) {
            case "PARTICLES" -> {
                double hs = hash(Math.floor(x / 2.0) + frame * 7.13, Math.floor(y / 2.0) + frame * 3.71);
                double spark = Math.pow(hs, 14);
                return clamp((0.25 + 0.75 * rim) * (0.30 * rim + 0.95 * spark));
            }
            case "RING" -> {
                double p = v - t;
                p -= Math.floor(p);
                double band = Math.exp(-Math.pow((p - 0.5) * 7.0, 2));
                return clamp(0.92 * band + 0.12 * rim);
            }
            case "CLOTH" -> {
                double s = 0.5 + 0.5 * Math.sin(TAU * (2.5 * u + 2.5 * v - 1.5 * t));
                return clamp((0.26 + 0.62 * smooth(0.35, 0.95, s)) * (0.35 + 0.65 * rim));
            }
            default -> {
                double pulse = 0.6 + 0.4 * Math.sin(TAU * t);
                return clamp(0.12 + 0.80 * pulse * Math.pow(rim, 0.75));
            }
        }
    }

    /**
     * Расстояние до ближайшего пустого пикселя (шамотфер 3-4). Без него аура легла бы
     * ровным пятном на весь клинок. Если прозрачных пикселей нет вовсе, кромкой считается
     * граница картинки — иначе спрайт без полей остался бы вообще без ауры.
     */
    private static float[] edgeDistance(int[] px, int w, int h) {
        final float far = 1e4f, diag = 1.41f;
        float[] d = new float[w * h];
        boolean hole = false;
        for (int p : px) {
            if ((p >>> 24) == 0) {
                hole = true;
                break;
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if ((px[i] >>> 24) == 0) d[i] = 0f;
                else d[i] = hole ? far : Math.min(Math.min(x + 1, w - x), Math.min(y + 1, h - y));
            }
        }
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int i = row + x;
                float v = d[i];
                if (x > 0) v = Math.min(v, d[i - 1] + 1f);
                if (y > 0) {
                    v = Math.min(v, d[i - w] + 1f);
                    if (x > 0) v = Math.min(v, d[i - w - 1] + diag);
                    if (x + 1 < w) v = Math.min(v, d[i - w + 1] + diag);
                }
                d[i] = v;
            }
        }
        for (int y = h - 1; y >= 0; y--) {
            int row = y * w;
            for (int x = w - 1; x >= 0; x--) {
                int i = row + x;
                float v = d[i];
                if (x + 1 < w) v = Math.min(v, d[i + 1] + 1f);
                if (y + 1 < h) {
                    v = Math.min(v, d[i + w] + 1f);
                    if (x + 1 < w) v = Math.min(v, d[i + w + 1] + diag);
                    if (x > 0) v = Math.min(v, d[i + w - 1] + diag);
                }
                d[i] = v;
            }
        }
        return d;
    }

    /**
     * {интенсивность 0..1, тон r,g,b, максимальная доля подмены 0..1}.
     * Тон — во всех каналах сразу: насколько он ярче базового, решает mixDelta.
     */
    private static double[] motif(String element, double u, double v, double t, int frame, int frames) {
        switch (element) {
            case "FROST" -> {
                double[] cell = cell(u, v, 6.0);
                double hs = hash(cell[0], cell[1]);
                double fx = u * 6 - cell[0] - 0.5, fy = v * 6 - cell[1] - 0.5;
                double rad = Math.min(1.0, Math.sqrt(fx * fx + fy * fy) * 2);
                double ang = Math.atan2(fy, fx);
                double flake = (1 - rad) * (0.25 + 0.75 * Math.pow(Math.abs(Math.cos(3 * ang)), 3));
                double twinkle = Math.pow(0.5 + 0.5 * Math.sin(TAU * (t + hs)), 6);
                double sheen = 0.5 + 0.5 * wave(u, v, t, 1.0, 2.0, 1, hs * TAU);
                double hot = clamp(0.18 + 0.55 * flake * (0.35 + 0.65 * twinkle) + 0.30 * sheen * sheen * sheen);
                return new double[]{hot, 0.75 + 0.25 * hot, 0.9, 1.0, 0.66};
            }
            case "SHOCK" -> {
                double best = 0;
                for (int a = 0; a < 3; a++) {
                    double yc = 0.14 + 0.72 * hash(frame * 3.13 + a * 7.7, a * 1.31);
                    double d = Math.abs(v - arc(u, frame, a, yc));
                    best = Math.max(best, Math.exp(-d * d * 150) + 0.5 * Math.exp(-d * d * 10));
                }
                // полоса напряжения, бегущая вдоль клинка: без неё на 16px разряды выглядят просто миганием
                double sweep = Math.pow(0.5 + 0.5 * Math.sin(TAU * (2 * v - t)), 5);
                double hot = clamp(Math.max(best, 0.6 * sweep));
                return new double[]{hot, 1.0, 0.98, 0.85 + 0.15 * hot, 0.8};
            }
            case "VOID" -> {
                double n = 0.5 * wave(u, v, t, 2.1, 1.7, 1, 0) + 0.3 * wave(u, v, t, 4.3, 3.9, -1, 1.9)
                        + 0.2 * wave(u, v, t, 7.9, 6.1, 2, 4.3);
                double g = 0.5 + 0.5 * n;
                double hot = clamp(smooth(0.32, 0.86, g));
                double dark = clamp(smooth(0.52, 0.16, g));
                return new double[]{Math.max(hot, dark), hot > dark ? 0.9 : 0.16, hot > dark ? 0.8 : 0.1,
                        1.0, 0.7};
            }
            default -> {
                double n = 0.55 * wave(u, v, t, 2.3, 4.0, -1, 0) + 0.3 * wave(u, v, t, 5.1, 9.0, -2, 1.7)
                        + 0.15 * wave(u, v, t, 9.7, 17.0, -3, 4.1);
                double g = clamp(0.5 + 0.5 * n);
                double lick = Math.pow(g, 2.4);
                return new double[]{0.25 + 0.75 * lick, 1.0, 0.62 + 0.3 * lick, 0.25 + 0.55 * lick, 0.72};
            }
        }
    }

    /** Зигзаг разряда: y пути в точке u, ломаная по 8 узлам, узлы меняются вместе с кадром. */
    private static double arc(double u, int frame, int arcIndex, double mean) {
        int nodes = 8;
        double f = u * nodes;
        int i = (int) Math.floor(f);
        double k = f - i;
        double y0 = jitter(frame, arcIndex, i, mean), y1 = jitter(frame, arcIndex, i + 1, mean);
        return y0 + (y1 - y0) * k;
    }

    private static double jitter(int frame, int arcIndex, int node, double mean) {
        double a = hash(frame * 12.9 + arcIndex * 3.7, node * 2.11 + arcIndex);
        return clamp(mean + (a - 0.5) * 0.55, 0.02, 0.98);
    }

    private static double[] cell(double u, double v, double n) {
        return new double[]{Math.floor(u * n), Math.floor(v * n)};
    }

    /** Детерминированный шум 0..1 по паре чисел — одинаковый на всех прогонах и во всех кадрах. */
    private static double hash(double a, double b) {
        double x = Math.sin(a * 127.1 + b * 311.7) * 43758.5453;
        return x - Math.floor(x);
    }

    private static double wave(double u, double v, double t, double ku, double kv, double kt, double ph) {
        return Math.sin(TAU * (ku * u + kv * v + kt * t) + ph);
    }

    private static double smooth(double a, double b, double x) {
        double k = clamp((x - a) / (b - a));
        return k * k * (3 - 2 * k);
    }

    private static double clamp(double x) {
        return x < 0 ? 0 : Math.min(1, x);
    }

    private static double clamp(double x, double lo, double hi) {
        return x < lo ? lo : Math.min(hi, x);
    }

    private static int rgba(double r, double g, double b, int alpha) {
        return (alpha << 24) | (quant(r) << 16) | (quant(g) << 8) | quant(b);
    }

    private static int quant(double x) {
        return (int) Math.floor(clamp(x) * 255 + 0.5);
    }
}

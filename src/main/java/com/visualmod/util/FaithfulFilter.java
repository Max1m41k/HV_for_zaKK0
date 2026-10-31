package com.visualmod.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Пиксельная математика «чётких» текстур: апскейл по правилу Scale2x, край-сохраняющее
 * сглаживание, нерезкая маска, тон и квантизация палитры. Альфа не меняется нигде —
 * полупрозрачные кромки стекла, листвы и оверлеев при сглаживании превращаются в грязь, а
 * прозрачность критична для рендера блоков.
 *
 * Порядок и дефолты выбраны по замеру против референса (Faithful 32x, 1.21.4, 24 пары текстур).
 * Оказалось, что референс — не «богаче и резче», а наоборот: оттенков у него меньше, чем у ванили
 * (13 против 43 уникальных цветов на текстуру), плоских областей больше, а градиент на границах
 * ниже ванильного. Старый набор 55/20/25 без апскейла делал ровно обратное: уникальные цвета
 * 43 → 272, энтропия яркости 3.05 → 5.40 бит, доля трёх главных цветов 0.58 → 0.24 — отсюда и
 * «мыло в ореолах» на скринах. Поэтому по умолчанию: апскейл + палитра 16 цветов + лёгкая
 * насыщенность, а маска и контраст выключены (они уводят от референса в любую сторону).
 *
 * Класс намеренно без единого Minecraft-импорта: тот же код проверяется офлайн-пробой
 * на настоящих ванильных png — C:\hvchk\fc\probe\Probe.java считает те же метрики.
 */
public final class FaithfulFilter {

    private FaithfulFilter() {}

    /** Параметры перерисовки. Ноль у каждого ползунка = оставить текстуру как есть. */
    public static final class Spec {
        public final int smooth;
        public final int sharp;
        public final int contrast;
        public final int saturation;
        /** Сколько оттенков оставить; 0 = не трогать палитру. */
        public final int colors;
        public final boolean upscale;

        public Spec(int smooth, int sharp, int contrast, int saturation, int colors, boolean upscale) {
            this.smooth = clampPct(smooth);
            this.sharp = clampPct(sharp);
            this.contrast = clampPct(contrast);
            this.saturation = clampPct(saturation);
            this.colors = colors < 0 ? 0 : (colors > 64 ? 64 : colors);
            this.upscale = upscale;
        }

        public boolean identity() {
            return smooth <= 0 && sharp <= 0 && contrast <= 0 && saturation <= 0 && colors <= 0 && !upscale;
        }

        /** Тот же набор без апскейла — для больших текстур, где удвоение разбивает атлас. */
        public Spec withoutUpscale() {
            return new Spec(smooth, sharp, contrast, saturation, colors, false);
        }

        /** Подпись для applied.txt и для «пересобирать ли вообще». */
        public String key() {
            return "s" + smooth + "d" + sharp + "c" + contrast + "h" + saturation
                    + "q" + colors + (upscale ? "2x" : "1x");
        }

        private static int clampPct(int v) {
            return v < 0 ? 0 : (v > 100 ? 100 : v);
        }
    }

    /**
     * Применяет всё выбранное подряд и возвращает новый буфер.
     *
     * @param dim входные w/h; на выходе — размеры результата (отличаются при апскейле)
     */
    public static int[] apply(int[] px, int[] dim, Spec s) {
        int w = dim[0], h = dim[1];
        int[] cur = px;
        // сначала расширение: маска и тон работают уже по финальной сетке, иначе 3x3 размытие
        // съедало бы только что добавленные пиксели
        if (s.upscale) {
            cur = scale2x(cur, w, h);
            w *= 2;
            h *= 2;
            dim[0] = w;
            dim[1] = h;
        }
        if (s.smooth > 0) cur = soften(cur, w, h, s.smooth);
        if (s.sharp > 0) cur = unsharp(cur, w, h, s.sharp);
        if (s.contrast > 0 || s.saturation > 0) cur = tone(cur, w, h, s.contrast, s.saturation);
        // палитра — последний шаг: до неё маска и тон успевают наплодить промежуточных оттенков,
        // и именно срез до N цветов возвращает картинке «перерисованную» плоскость
        if (s.colors > 0) cur = quantize(cur, s.colors);
        return cur;
    }

    /**
     * Scale2x: каждый пиксель становится квадратом 2x2, а углы этого квадрата достаются из
     * соседей — если два соседних по углу цвета совпадают, цвет залезает в угол. Ровно это
     * и убирает «лесенку» на диагоналях, оставляя пиксельную сетку на месте.
     */
    private static int[] scale2x(int[] px, int w, int h) {
        int[] s = new int[px.length];
        for (int i = 0; i < px.length; i++) s[i] = (px[i] >>> 24) == 0 ? 0 : px[i];
        int[] o = new int[px.length * 4];
        int row = w * 2;
        for (int y = 0; y < h; y++) {
            int up = (y > 0 ? y - 1 : y) * w;
            int cur = y * w;
            int dn = (y < h - 1 ? y + 1 : y) * w;
            int out = y * 2 * row;
            for (int x = 0; x < w; x++) {
                int left = x > 0 ? x - 1 : x;
                int right = x < w - 1 ? x + 1 : x;
                int a = s[up + x], b = s[cur + right], c = s[cur + left], d = s[dn + x], p = s[cur + x];
                int o0 = a == c ? a : p;
                int o1 = a == b ? a : p;
                int o2 = c == d ? c : p;
                int o3 = b == d ? b : p;
                int e = out + x * 2;
                o[e] = o0;
                o[e + 1] = o1;
                o[e + row] = o2;
                o[e + row + 1] = o3;
            }
        }
        return o;
    }

    /**
     * Край-сохраняющее сглаживание: усредняем только тех соседей, чей цвет близок к центральному.
     * Поэтому мягкие формы внутри камня и дерева перетекают, как у референса, а граница блока с
     * другим цветом остаётся границей — обычный blur дал бы вместо этого кашу.
     */
    private static int[] soften(int[] px, int w, int h, int amount) {
        double k = amount / 100.0 * 0.75;
        int range = 26 + amount / 100 * 46;
        int[] out = new int[px.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                int c = px[i];
                if ((c >>> 24) == 0) {
                    out[i] = c;
                    continue;
                }
                int cr = (c >> 16) & 255, cg = (c >> 8) & 255, cb = c & 255;
                long sr = cr * 2L, sg = cg * 2L, sb = cb * 2L;
                int count = 2;
                for (int dy = -1; dy <= 1; dy++) {
                    int yy = y + dy;
                    if (yy < 0 || yy >= h) continue;
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dy == 0 && dx == 0) continue;
                        int xx = x + dx;
                        if (xx < 0 || xx >= w) continue;
                        int n = px[yy * w + xx];
                        if ((n >>> 24) == 0) continue;
                        int nr = (n >> 16) & 255, ng = (n >> 8) & 255, nb = n & 255;
                        if (Math.abs(nr - cr) + Math.abs(ng - cg) + Math.abs(nb - cb) > range) continue;
                        sr += nr;
                        sg += ng;
                        sb += nb;
                        count++;
                    }
                }
                int ar = (int) (sr / count), ag = (int) (sg / count), ab = (int) (sb / count);
                out[i] = (c & 0xFF000000)
                        | (clamp8((int) (cr + (ar - cr) * k)) << 16)
                        | (clamp8((int) (cg + (ag - cg) * k)) << 8)
                        | clamp8((int) (cb + (ab - cb) * k));
            }
        }
        return out;
    }

    /**
     * Нерезкая маска по 3x3. Прозрачного соседа заменяем центром: усреднять по чёрному
     * «прозрачному нулю» значит обвести каждую фигуру тёмным ореолом.
     */
    private static int[] unsharp(int[] px, int w, int h, int amount) {
        double k = amount / 100.0 * 0.9;
        int[] out = new int[px.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                int c = px[i];
                if ((c >>> 24) == 0) {
                    out[i] = c;
                    continue;
                }
                int cr = (c >> 16) & 255, cg = (c >> 8) & 255, cb = c & 255;
                long sr = 0, sg = 0, sb = 0;
                int count = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    int yy = y + dy;
                    if (yy < 0 || yy >= h) continue;
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx;
                        if (xx < 0 || xx >= w) continue;
                        count++;
                        int n = px[yy * w + xx];
                        if ((n >>> 24) == 0) {
                            sr += cr;
                            sg += cg;
                            sb += cb;
                            continue;
                        }
                        sr += (n >> 16) & 255;
                        sg += (n >> 8) & 255;
                        sb += n & 255;
                    }
                }
                out[i] = (c & 0xFF000000)
                        | (clamp8((int) (cr + k * (cr - (double) sr / count))) << 16)
                        | (clamp8((int) (cg + k * (cg - (double) sg / count))) << 8)
                        | clamp8((int) (cb + k * (cb - (double) sb / count)));
            }
        }
        return out;
    }

    /** Контраст вокруг середины шкалы, затем насыщенность вокруг яркости того же пикселя. */
    private static int[] tone(int[] px, int w, int h, int contrast, int saturation) {
        double cf = 1.0 + contrast / 100.0 * 1.2;
        double sf = 1.0 + saturation / 100.0 * 0.9;
        int[] out = new int[px.length];
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            if ((c >>> 24) == 0) {
                out[i] = c;
                continue;
            }
            int r = clamp8((int) (((c >> 16) & 255) * cf - 128.0 * (cf - 1.0) + 0.5));
            int g = clamp8((int) (((c >> 8) & 255) * cf - 128.0 * (cf - 1.0) + 0.5));
            int b = clamp8((int) ((c & 255) * cf - 128.0 * (cf - 1.0) + 0.5));
            int l = (int) (0.299 * r + 0.587 * g + 0.114 * b);
            r = clamp8((int) (l + (r - l) * sf + 0.5));
            g = clamp8((int) (l + (g - l) * sf + 0.5));
            b = clamp8((int) (l + (b - l) * sf + 0.5));
            out[i] = (c & 0xFF000000) | (r << 16) | (g << 8) | b;
        }
        return out;
    }

    /**
     * Медианный срез: палитра ровно из {@code colors} оттенков, каждый пиксель переводится в
     * ближайший. Это и есть главный шаг — референс именно квантует ваниль, а не обогащает её.
     *
     * Считаем по уникальным оттенкам (их у текстуры десятки, даже у большой — тысячи), поэтому
     * сложность не зависит от числа пикселей. Оттенки перед разбиением сортируем, иначе порядок
     * обхода HashMap зависел бы от вставки и одна и та же текстура получала бы разную палитру
     * от сборки к сборке.
     */
    private static int[] quantize(int[] px, int colors) {
        HashMap<Integer, int[]> uniq = new HashMap<>();
        for (int c : px) {
            if ((c >>> 24) == 0) continue;
            int[] rec = uniq.get(c & 0xFFFFFF);
            if (rec == null) uniq.put(c & 0xFFFFFF, new int[]{1});
            else rec[0]++;
        }
        int n = uniq.size();
        if (n == 0 || n <= colors) return px;
        int[] rgb = new int[n];
        int[] weight = new int[n];
        int i = 0;
        for (Integer key : uniq.keySet()) rgb[i++] = key;
        Arrays.sort(rgb);
        for (i = 0; i < n; i++) weight[i] = uniq.get(rgb[i])[0];

        List<int[]> boxes = new ArrayList<>();
        int[] all = new int[n];
        for (i = 0; i < n; i++) all[i] = i;
        boxes.add(all);
        while (boxes.size() < colors) {
            int best = -1, bestChannel = -1, bestRange = 0;
            for (int b = 0; b < boxes.size(); b++) {
                if (boxes.get(b).length < 2) continue;
                for (int ch = 0; ch < 3; ch++) {
                    int range = channelRange(boxes.get(b), rgb, ch);
                    if (range > bestRange) {
                        bestRange = range;
                        best = b;
                        bestChannel = ch;
                    }
                }
            }
            if (best < 0) break;
            int[][] halves = splitBox(boxes.remove(best), rgb, weight, bestChannel);
            boxes.add(halves[0]);
            boxes.add(halves[1]);
        }
        int[] palette = new int[boxes.size()];
        for (i = 0; i < boxes.size(); i++) palette[i] = average(boxes.get(i), rgb, weight);

        // ближайший оттенок ищем один раз на уникальный цвет, а не на пиксель
        int[] nearest = new int[n];
        for (i = 0; i < n; i++) {
            int best = palette[0], bd = Integer.MAX_VALUE;
            for (int p : palette) {
                int d = dist2(rgb[i], p);
                if (d < bd) {
                    bd = d;
                    best = p;
                }
            }
            nearest[i] = best;
        }
        HashMap<Integer, Integer> lut = new HashMap<>();
        for (i = 0; i < n; i++) lut.put(rgb[i], nearest[i]);

        int[] out = new int[px.length];
        for (i = 0; i < px.length; i++) {
            int c = px[i];
            if ((c >>> 24) == 0) {
                out[i] = c;
                continue;
            }
            Integer q = lut.get(c & 0xFFFFFF);
            out[i] = c & 0xFF000000 | (q == null ? c : q);
        }
        return out;
    }

    /** Разброс канала внутри набора оттенков — по нему выбираем, какой ящик делить следующим. */
    private static int channelRange(int[] members, int[] rgb, int ch) {
        int min = 255, max = 0;
        for (int m : members) {
            int v = (rgb[m] >> (16 - ch * 8)) & 255;
            if (v < min) min = v;
            if (v > max) max = v;
        }
        return max - min;
    }

    /**
     * Делит ящик пополам по весу выбранного канала. Границу ставим там, где значение канала
     * уже сменилось: два одинаковых оттенка не могут оказаться в разных половинах, иначе
     * «медианный» срез превращается в случайный.
     */
    private static int[][] splitBox(int[] members, int[] rgb, int[] weight, int ch) {
        long[] keyed = new long[members.length];
        for (int i = 0; i < members.length; i++) {
            int v = (rgb[members[i]] >> (16 - ch * 8)) & 255;
            keyed[i] = ((long) v << 32) | (members[i] & 0xFFFFFFFFL);
        }
        Arrays.sort(keyed);
        long total = 0;
        for (int m : members) total += weight[m];
        int at = 1;
        long acc = 0;
        for (int i = 0; i + 1 < keyed.length; i++) {
            acc += weight[(int) keyed[i]];
            if (acc * 2 >= total && (keyed[i] >>> 32) != (keyed[i + 1] >>> 32)) {
                at = i + 1;
                break;
            }
            at = i + 1;
        }
        return new int[][]{indexes(keyed, 0, at), indexes(keyed, at, keyed.length)};
    }

    private static int[] indexes(long[] keyed, int from, int to) {
        int[] out = new int[to - from];
        for (int i = 0; i < out.length; i++) out[i] = (int) keyed[from + i];
        return out;
    }

    /** Средневзвешенный оттенок ящика — то, чем заменим все его цвета. */
    private static int average(int[] members, int[] rgb, int[] weight) {
        long sr = 0, sg = 0, sb = 0, sw = 0;
        for (int m : members) {
            long w = weight[m];
            sr += ((rgb[m] >> 16) & 255) * w;
            sg += ((rgb[m] >> 8) & 255) * w;
            sb += (rgb[m] & 255) * w;
            sw += w;
        }
        if (sw == 0) return rgb[members[0]];
        return (int) (sr / sw) << 16 | (int) (sg / sw) << 8 | (int) (sb / sw);
    }

    private static int dist2(int a, int b) {
        int dr = ((a >> 16) & 255) - ((b >> 16) & 255);
        int dg = ((a >> 8) & 255) - ((b >> 8) & 255);
        int db = (a & 255) - (b & 255);
        return dr * dr + dg * dg + db * db;
    }

    private static int clamp8(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}

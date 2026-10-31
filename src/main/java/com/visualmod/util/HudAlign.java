package com.visualmod.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Привязка HUD-элементов друг к другу в редакторе (Ctrl + клик по элементу в чате).
 *
 * Соседи — прямоугольники остальных элементов редактора. Ищем совпадение левой/средней/правой
 * граней (и верхней/средней/нижней) с точностью TOL пикселей; если нашли — сдвигаем элемент
 * ровно по линии и запоминаем её, чтобы mixin нарисовал направляющую. Сетка — отдельный
 * режим: она срабатывает только там, где привязка к соседу не нашлась.
 */
public final class HudAlign {

    public static final int TOL = 3;
    public static final int[] STEPS = {2, 4, 8, 16};

    public static boolean snap = true;
    public static boolean grid;
    private static int stepIndex = 1;

    /** Вертикальные и горизонтальные линии, пойманные на последнем вызове resolve. */
    public static final List<int[]> guidesV = new ArrayList<>();
    public static final List<int[]> guidesH = new ArrayList<>();

    private HudAlign() {}

    public static int step() {
        return STEPS[stepIndex];
    }

    public static void nextStep() {
        stepIndex = (stepIndex + 1) % STEPS.length;
    }

    /**
     * @param others прямоугольники остальных элементов {x, y, w, h}
     * @return скорректированные {x, y}
     */
    public static int[] resolve(int x, int y, int w, int h, List<int[]> others, int sw, int sh) {
        guidesV.clear();
        guidesH.clear();
        x = axisX(x, y, w, h, others, sw, sh);
        y = axisY(x, y, w, h, others, sw, sh);
        if (grid) {
            int s = step();
            boolean snappedV = !guidesV.isEmpty(), snappedH = !guidesH.isEmpty();
            if (!snappedV) x = Math.round(x / (float) s) * s;
            if (!snappedH) y = Math.round(y / (float) s) * s;
        }
        return new int[]{x, y};
    }

    private static int axisX(int x, int y, int w, int h, List<int[]> others, int sw, int sh) {
        if (!snap) return x;
        int[] mine = {x, x + w / 2, x + w};
        int bestDelta = TOL + 1, bestLine = 0, bestMine = 0;
        for (int[] b : others) {
            int[] lines = {b[0], b[0] + b[2] / 2, b[0] + b[2]};
            for (int line : lines) {
                for (int m : mine) {
                    int d = Math.abs(m - line);
                    if (d < bestDelta) {
                        bestDelta = d;
                        bestLine = line;
                        bestMine = m;
                    }
                }
            }
        }
        // Центр экрана — самая частая привязка (аура хотбара, TargetHUD по центру).
        int d = Math.abs(x + w / 2 - sw / 2);
        if (d < bestDelta) {
            bestDelta = d;
            bestLine = sw / 2;
            bestMine = x + w / 2;
        }
        if (bestDelta > TOL) return x;
        guidesV.add(new int[]{bestLine, 0, bestLine, sh});
        return x + (bestLine - bestMine);
    }

    private static int axisY(int x, int y, int w, int h, List<int[]> others, int sw, int sh) {
        if (!snap) return y;
        int[] mine = {y, y + h / 2, y + h};
        int bestDelta = TOL + 1, bestLine = 0, bestMine = 0;
        for (int[] b : others) {
            int[] lines = {b[1], b[1] + b[3] / 2, b[1] + b[3]};
            for (int line : lines) {
                for (int m : mine) {
                    int d = Math.abs(m - line);
                    if (d < bestDelta) {
                        bestDelta = d;
                        bestLine = line;
                        bestMine = m;
                    }
                }
            }
        }
        int d = Math.abs(y + h / 2 - sh / 2);
        if (d < bestDelta) {
            bestDelta = d;
            bestLine = sh / 2;
            bestMine = y + h / 2;
        }
        if (bestDelta > TOL) return y;
        guidesH.add(new int[]{0, bestLine, sw, bestLine});
        return y + (bestLine - bestMine);
    }

    /** Пунктирная сетка под всем экраном — рисуется только пока открыт редактор. */
    public static void drawGrid(net.minecraft.client.gui.DrawContext ctx, int sw, int sh, int color) {
        if (!grid) return;
        int s = step();
        for (int x = s; x < sw; x += s) ctx.fill(x, 0, x + 1, sh, color);
        for (int y = s; y < sh; y += s) ctx.fill(0, y, sw, y + 1, color);
    }
}

package com.visualmod.util;

import com.visualmod.module.modules.hud.GuiStyle;
import net.minecraft.client.gui.DrawContext;

/**
 * «Мыльная» плёнка по референсу: молочно-серая основа и мягкие вертикальные полосы.
 * Полосы идут от y+r до y+h-r — ровно там, где боковая граница скруглённой панели ещё
 * прямая, поэтому плёнка не вылезает за углы и scissor не нужен.
 */
public final class Soap {
    private static final int BAND = 4;

    private Soap() {}

    /** Стиль SOAP выбран в GuiStyle — мыло идёт на окна GUI. */
    public static boolean onGui() {
        return GuiStyle.style == GuiStyle.Style.SOAP;
    }

    /** То же плюс разрешение на HUD-панели (настраивается в GuiStyle). */
    public static boolean onHud() {
        return onGui() && GuiStyle.soapOnHud;
    }

    public static void overlay(DrawContext ctx, float x, float y, float w, float h, float r) {
        overlay(ctx, x, y, w, h, r, 1f);
    }

    public static void overlay(DrawContext ctx, float x, float y, float w, float h, float r, float fade) {
        double milky = GuiStyle.soapMilky * Math.max(0.0, Math.min(1.0, (double) fade));
        if (milky <= 0.02 || w <= 6f || h <= 6f) return;

        r = Math.min(r, Math.min(w / 2f, h / 2f));
        int tint = GuiStyle.soapTint;
        float strength = (float) milky;

        HudStyle.fillRounded(ctx, x, y, w, h, r, alpha((int) (28 + 74 * strength), tint));

        float top = y + (float) Math.ceil(r);
        float bottom = y + h - (float) Math.ceil(r);
        if (bottom - top < 3f) return;

        float streaks = (float) (GuiStyle.soapStreaks / 100.0);
        if (streaks <= 0.02f) return;

        int x1 = (int) Math.floor(x);
        int y1 = (int) Math.floor(top);
        int y2 = (int) Math.ceil(bottom);
        int seed = (int) (x * 0.7f) + (int) (y * 1.3f);

        // Соседние полосы с одинаковой плотностью сливаем в один вызов fill:
        // на широкой панели это десятки полос, а отрисовок остаётся несколько.
        int cols = Math.max(1, (int) Math.ceil(w / BAND));
        int runAlpha = 0;
        int runStart = 0;
        for (int i = 0; i <= cols; i++) {
            int a = i < cols ? bandAlpha(seed + i * BAND, strength, streaks) : -1;
            if (a != runAlpha) {
                if (runAlpha > 0) ctx.fill(x1 + runStart * BAND, y1, x1 + i * BAND, y2, alpha(runAlpha, tint));
                runAlpha = a;
                runStart = i;
            }
        }
    }

    private static int bandAlpha(int offset, float strength, float streaks) {
        int a = (int) (strength * (10 + 96 * streak(offset)) * streaks);
        return a <= 3 ? 0 : Math.min(180, a);
    }

    /** Мягкий, почти без резких границ узор: сумма двух синусов с медленным дрейфом. */
    private static float streak(int i) {
        float a = (float) (0.5 + 0.5 * Math.sin(i * 0.117 + Math.sin(i * 0.031) * 1.9));
        float b = (float) (0.5 + 0.5 * Math.sin(i * 0.041 + 2.1));
        return Math.min(1f, 0.25f + 0.85f * (a * 0.62f + b * 0.38f));
    }

    private static int alpha(int a, int rgb) {
        return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0x00FFFFFF);
    }
}

package com.visualmod.util;

import com.visualmod.module.modules.hud.HudRadius;
import net.minecraft.client.gui.DrawContext;

/**
 * Встроенная фича Tab+: скругляет фон TAB-списка. Перехвачены штатные DrawContext.fill(...) внутри
 * PlayerListHud.render, поэтому ники, пинг и колонки скорборда остаются ванильными.
 */
public final class TabPlus {
    private static final String RADIUS_KEY = "TabPlus";
    /** Ванильный фон таба ~16px на строку: при такой высоте радиус выходит 4px. */
    private static final float BASE_R = 4f;
    /** Тоньше этого — разделитель или подложка строки: округление съедает пиксели. */
    private static final int MIN_THICK = 8;

    private TabPlus() {}

    public static void fill(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        int m = Math.min(x2 - x1, y2 - y1);
        if (ctx == null || m < MIN_THICK) {
            if (ctx != null) ctx.fill(x1, y1, x2, y2, color);
            return;
        }
        float r = HudRadius.has(RADIUS_KEY) ? HudRadius.get(RADIUS_KEY) : BASE_R * m / 16f;
        HudStyle.fillRounded(ctx, x1, y1, x2 - x1, y2 - y1, r, color);
    }
}

package com.visualmod.util;

import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Встроенный маркер: три точки у тех функций, у которых есть настройки (цвет, режимы — не бинд).
 * Модулем не является — выключить или настроить его нельзя, он часть интерфейса.
 */
public final class ConfigMark {
    private ConfigMark() {}

    public static boolean configurable(Module m) {
        return m instanceof Configurable c && !c.getSettings().isEmpty();
    }

    /** Рисуется правым краем у rightX, по центру строки на centerY. */
    public static void draw(DrawContext ctx, int rightX, int centerY, int color) {
        int y = centerY - 1;
        for (int i = 0; i < 3; i++) {
            int x = rightX - 8 + i * 3;
            ctx.fill(x, y, x + 2, y + 2, color);
        }
    }

    /** Текст строки с учётом места под маркер. */
    public static String trim(TextRenderer tr, String s, int rowWidth) {
        if (s == null) return "";
        int max = rowWidth - 10;
        if (max <= 0) return "";
        String r = tr.trimToWidth(s, max);
        return r == null ? "" : r;
    }
}

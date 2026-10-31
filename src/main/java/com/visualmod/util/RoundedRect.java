package com.visualmod.util;

import net.minecraft.client.gui.DrawContext;

public final class RoundedRect {
    private RoundedRect() {}

    public static void fill(DrawContext c, int x1, int y1, int x2, int y2, int r, int color) {
        fill(c, x1, y1, x2, y2, r, color, true, true);
    }

    public static void fill(DrawContext c, int x1, int y1, int x2, int y2, int r, int color, boolean top, boolean bottom) {
        int w = x2 - x1, h = y2 - y1;
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) { c.fill(x1, y1, x2, y2, color); return; }

        c.fill(x1, y1 + (top ? r : 0), x2, y2 - (bottom ? r : 0), color);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            if (top)    c.fill(x1 + inset, y1 + i,         x2 - inset, y1 + i + 1, color);
            if (bottom) c.fill(x1 + inset, y2 - i - 1,     x2 - inset, y2 - i,     color);
        }
    }

    public static void outline(DrawContext c, int x1, int y1, int x2, int y2, int r, int color, int fillColor) {
        fill(c, x1, y1, x2, y2, r, color);
        fill(c, x1 + 1, y1 + 1, x2 - 1, y2 - 1, Math.max(0, r - 1), fillColor);
    }
}

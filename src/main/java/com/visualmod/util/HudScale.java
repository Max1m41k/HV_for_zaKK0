package com.visualmod.util;

import net.minecraft.client.gui.DrawContext;

/** Масштаб одного HUD-элемента: трансформация вокруг его якоря (x, y). */
public final class HudScale {
    private HudScale() {}

    public static boolean on(float s) {
        return !Float.isNaN(s) && Math.abs(s - 1f) > 0.001f;
    }

    public static void begin(DrawContext ctx, float s, int x, int y) {
        if (!on(s)) return;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x, y);
        ctx.getMatrices().scale(s, s);
        ctx.getMatrices().translate(-x, -y);
    }

    public static void end(DrawContext ctx, float s) {
        if (!on(s)) return;
        ctx.getMatrices().popMatrix();
    }

    public static int scaled(int px, float s) {
        return Math.max(1, Math.round(px * s));
    }
}

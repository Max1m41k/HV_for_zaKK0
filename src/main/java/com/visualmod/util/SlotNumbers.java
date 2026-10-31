package com.visualmod.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Встроенная функция (без ModuleManager и GUI): номер слота в каждом слоте хотбара.
 * Координаты совпадают с ванильными из InGameHud.renderHotbar: предмет рисуется в
 * (centerX - 90 + slot*20, screenHeight - 19), ширина слота 20px.
 */
public final class SlotNumbers {

    private SlotNumbers() {}

    public static void drawVanilla(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.textRenderer == null || mc.options.hudHidden) return;
        int centerX = ctx.getScaledWindowWidth() / 2;
        int top = ctx.getScaledWindowHeight() - 19;
        for (int i = 0; i < 9; i++) {
            draw(ctx, mc, centerX - 90 + i * 20, top, i);
        }
    }

    /** Тот же номер поверх нашего хотбара: x — левый край первого предмета, y — его верх. */
    public static void drawOverHotbarHud(DrawContext ctx, int x, int y, int slotSize) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.textRenderer == null) return;
        for (int i = 0; i < 9; i++) {
            draw(ctx, mc, x + i * slotSize, y, i);
        }
    }

    private static void draw(DrawContext ctx, MinecraftClient mc, int itemX, int itemY, int slot) {
        ctx.drawTextWithShadow(mc.textRenderer, String.valueOf(slot + 1), itemX, itemY, 0xFFE6E6EE);
    }
}

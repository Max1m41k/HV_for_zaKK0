package com.visualmod.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ClickableWidget;

public final class ButtonStyle {
    private ButtonStyle() {}

    public static final int ORANGE = 0xFFFF7A00;
    public static final int ORANGE_LIGHT = 0xFFFFA630;

    public static boolean shouldStyle(Screen screen) {
        return screen instanceof MultiplayerScreen;
    }

    public static void render(ClickableWidget w, DrawContext ctx) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int x = w.getX(), y = w.getY(), bw = w.getWidth(), bh = w.getHeight();
        boolean active = w.active;
        boolean hot = active && (w.isHovered() || w.isFocused());

        int r = Math.max(2, Math.min(8, bh / 2));
        int border = !active ? 0xFF4A4A4A : hot ? ORANGE_LIGHT : ORANGE;
        int bg = !active ? 0xE0101010 : hot ? 0xF02A1606 : 0xF0101010;

        HudStyle.fillRounded(ctx, x, y, bw, bh, r, border);
        HudStyle.fillRounded(ctx, x + 1, y + 1, bw - 2, bh - 2, Math.max(1, r - 1), bg);

        String text = w.getMessage().getString();
        int max = bw - 10;
        if (tr.getWidth(text) > max) text = tr.trimToWidth(text, max - tr.getWidth("...")) + "...";
        int color = !active ? 0xFF777777 : hot ? 0xFFFFC27A : 0xFFFFFFFF;
        ctx.drawCenteredTextWithShadow(tr, text, x + bw / 2, y + (bh - 8) / 2, color);
    }
}

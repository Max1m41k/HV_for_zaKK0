package com.visualmod.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;

/**
 * Встроенная функция: пинг в TAB-листе цифрами вместо ванильных полосок.
 * Число выравнивается по правому краю slots полосок (10 px), поэтому колонка не разъезжается;
 * подложка нужна, чтобы цифры читались даже если имя игрока длинное и заехало под них.
 */
public final class TabPing {
    private TabPing() {}

    private static final int ICON_W = 10;

    public static boolean render(DrawContext ctx, int iconX, int iconY, PlayerListEntry entry) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.textRenderer == null || entry == null) return false;
            TextRenderer font = mc.textRenderer;

            int ping = entry.getLatency();
            String text = ping < 0 ? "?" : String.valueOf(ping);
            int color = ping < 0 ? 0xFFAAAAAA : colorOf(ping);
            int left = iconX + ICON_W - font.getWidth(text);

            // 1.21.11: у GUI нет оси глубины, порядок наложения задаёт очередь submission,
            // а мы вызываемся после строк списка — з-транслейт (150) из 1.21.4 больше не нужен.
            HudStyle.fillRounded(ctx, left - 2, iconY - 1, font.getWidth(text) + 4, 10, 2f, 0x8C101418);
            ctx.drawTextWithShadow(font, text, left, iconY, color);
            return true;
        } catch (Throwable t) {
            Diag.once("TabPing.render", t);
            return false;
        }
    }

    private static int colorOf(int ping) {
        if (ping < 60) return 0xFF6BE07A;
        if (ping < 120) return 0xFFE3E05A;
        if (ping < 200) return 0xFFE8A14A;
        if (ping < 400) return 0xFFE0604A;
        return 0xFFD03A3A;
    }
}

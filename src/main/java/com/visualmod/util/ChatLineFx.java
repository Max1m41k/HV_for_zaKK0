package com.visualmod.util;

import com.visualmod.module.modules.hud.ChatAnimations;
import com.visualmod.module.modules.hud.ChatRules;
import com.visualmod.module.modules.util.ChatPlus;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.Alignment;
import net.minecraft.client.font.DrawnTextConsumer;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.util.math.ColorHelper;

import java.util.List;

/**
 * Модульные эффекты одной строки чата. В 1.21.11 ChatHud рисует строки через два бэкенда
 * (ChatHud$Hud и ChatHud$Interactable), и оба зовут DrawnTextConsumer.text — сюда приходит
 * этот вызов уже оттуда.
 */
public final class ChatLineFx {
    private ChatLineFx() {}

    private static List<ChatHudLine.Visible> lines = List.of();
    private static TextRenderer textRenderer;
    private static int scanFrom;
    private static int currentTick;
    private static float chatScale = 1f;
    private static boolean buttons;

    /** Координаты x/y здесь — в масштабе чата: ChatHud уже применил scale+translate к матрицам DrawContext. */
    public static void beginFrame(List<ChatHudLine.Visible> visible, int scrolledLines,
                                  int currentTick, double scale, TextRenderer tr) {
        lines = visible;
        textRenderer = tr;
        scanFrom = Math.max(0, scrolledLines);
        ChatLineFx.currentTick = currentTick;
        chatScale = (float) scale;
        buttons = ChatPlus.copyButtons && ChatPlus.gutterChat() > 0f;
    }

    public static void draw(DrawContext ctx, DrawnTextConsumer consumer, Alignment align,
                            int x, int y, DrawnTextConsumer.Transformation pose, OrderedText text) {
        TextRenderer tr = textRenderer != null ? textRenderer : MinecraftClient.getInstance().textRenderer;
        if (tr == null || text == null || pose == null) {
            if (text != null) consumer.text(align, x, y, pose, text);
            return;
        }

        int index = indexOf(text);
        String raw = string(text);

        int bar = ChatRules.barColor(raw);
        if (bar != 0) HudStyle.fillRounded(ctx, x - 2, y - 1, tr.getWidth(text) + 6, 11, 4, bar);

        boolean animate = ChatAnimations.isActive();
        float offsetX = animate ? ChatAnimations.getSlideOffset(ageTicks(index)) : 0f;
        int px = x + (int) offsetX;

        if (animate && ChatAnimations.roundedBackground) {
            HudStyle.fillRounded(ctx, px - 2, y - 1, tr.getWidth(text) + 6, 11, 4, 0xAA0A0A0F);
        }

        int color = ChatRules.recolor(raw, 0xFFFFFF);
        String skinned = animate ? ChatAnimations.applyCustomFont(raw) : raw;
        OrderedText out = text;
        if (!skinned.equals(raw) || color != 0xFFFFFF) {
            // Ванильный цвет строки = белый с альфой из opacity, поэтому перекраску собираем вместе с альфой.
            int alpha = ColorHelper.channelFromFloat(Math.min(1f, Math.max(0f, pose.opacity()))) << 24;
            out = OrderedText.styledForwardsVisitedString(skinned, Style.EMPTY.withColor(alpha | (color & 0xFFFFFF)));
        }

        consumer.text(align, px, y, pose, out);

        collectButton(index, y, raw);
        HWRulesAuto.renderHighlight(ctx, tr, raw, px, y, chatScale);
    }

    private static void collectButton(int index, int lineY, String fallback) {
        if (!buttons) return;
        try {
            List<ChatHudLine.Visible> list = lines;
            // Индекс строки не определился — берём хотя бы её собственный текст, кнопка всё равно будет.
            if (index < 0) {
                ChatPlus.addPending(lineY, ChatLog.canonical(fallback));
                return;
            }
            if (index + 1 < list.size() && !list.get(index + 1).endOfEntry()) return;
            StringBuilder sb = new StringBuilder();
            for (int i = index; i >= 0; i--) {
                ChatHudLine.Visible line = list.get(i);
                if (sb.length() > 0) sb.append('\n');
                sb.append(string(line.content()));
                if (line.endOfEntry()) break;
            }
            ChatPlus.addPending(lineY, ChatLog.canonical(sb.length() > 0 ? sb.toString() : fallback));
        } catch (Throwable t) {
            Diag.once("ChatPlus.line", t);
        }
    }

    private static int ageTicks(int index) {
        if (index < 0 || index >= lines.size()) return 100000;
        return Math.max(0, currentTick - lines.get(index).addedTime());
    }

    /** Строки ChatHud передаются в бэкенд теми же объектами OrderedText, поэтому поиск по идентичности работает. */
    private static int indexOf(OrderedText text) {
        for (int i = scanFrom; i < lines.size(); i++) {
            if (lines.get(i).content() == text) {
                scanFrom = i + 1;
                return i;
            }
        }
        return -1;
    }

    private static String string(OrderedText text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        text.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        return sb.toString();
    }
}

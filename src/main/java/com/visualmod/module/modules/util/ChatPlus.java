package com.visualmod.module.modules.util;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.util.HudStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChatPlus extends Module {

    public static int scrollback = 1000;
    public static boolean copyButtons = true;
    public static float gutter = 12f;

    private static final long CHECK_MS = 1600L;
    private static final int MAX_CHECK_TEXTS = 256;

    private static final List<Hit> HITS = new ArrayList<>();
    private static final List<Pending> PENDING = new ArrayList<>();
    private static final Map<String, Long> COPIED_AT = new java.util.HashMap<>();

    public ChatPlus() {
        super("ChatPlus", "Infinite chat history and copy button next to every message", Category.UTIL);
        makePermanent();
    }

    public static boolean active() {
        if (VisualsMod.moduleManager == null) return false;
        ChatPlus m = VisualsMod.moduleManager.getModule(ChatPlus.class);
        return m != null && m.isEnabled();
    }

    public static int historyLimit() {
        return Math.max(100, Math.min(20000, scrollback));
    }

    public static float gutterChat() {
        if (!active() || !copyButtons) return 0f;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || !(mc.currentScreen instanceof ChatScreen)) return 0f;
        return Math.max(8f, Math.min(24f, gutter));
    }

    public static void beginFrame() {
        HITS.clear();
        PENDING.clear();
    }

    /** Строка чата запоминается во время render, а рисуется потом — в абсолютных координатах экрана. */
    public static void addPending(float chatLineY, String text) {
        if (text == null || text.isEmpty()) return;
        if (PENDING.size() < 200) PENDING.add(new Pending(chatLineY, text));
    }

    /**
     * Кнопки рисуются после всего чата и вне его матрицы: так их не может перекрыть фон соседней
     * строки, и они не зависят от того, применился ли сдвиг чата вправо.
     */
    public static void flush(DrawContext ctx, TextRenderer tr, double chatScale) {
        try {
            float g = gutterChat();
            if (g <= 0f || PENDING.isEmpty() || tr == null) return;
            float s = (float) Math.max(0.5, chatScale);
            int size = Math.max(7, Math.round(8f * s));
            int right = Math.round(s * (g - 2f));
            int left = right - size;
            if (left < 0) {
                left = 0;
                right = size;
            }
            for (Pending p : PENDING) {
                int top = Math.round(p.chatLineY * s);
                boolean copied = recentlyCopied(p.text);
                HudStyle.fillRounded(ctx, left, top - 1, size, size, 2f, copied ? 0xC0173A22 : 0xC012161F);
                if (copied) {
                    drawCheck(ctx, left, top - 1, size, 0xFF69F0A8);
                } else {
                    int plus = Math.round(tr.getWidth("+") * s);
                    ctx.drawTextWithShadow(tr, "+", left + (size - plus) / 2, top, 0xFFD6DCEA);
                }
                HITS.add(new Hit(left - 1, top - 2, right + 1, top + size, p.text));
            }
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("ChatPlus.flush", t);
        }
    }

    private static void drawCheck(DrawContext ctx, int bx, int by, int size, int color) {
        int x0 = bx + Math.round(size * 0.18f), y0 = by + Math.round(size * 0.55f);
        int xm = bx + Math.round(size * 0.42f), ym = by + Math.round(size * 0.78f);
        int x1 = bx + Math.round(size * 0.84f), y1 = by + Math.round(size * 0.20f);
        line(ctx, x0, y0, xm, ym, color);
        line(ctx, xm, ym, x1, y1, color);
    }

    private static void line(DrawContext ctx, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (steps == 0) {
            ctx.fill(x0, y0, x0 + 1, y0 + 1, color);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / steps;
            int y = y0 + (y1 - y0) * i / steps;
            ctx.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static boolean recentlyCopied(String text) {
        Long at = COPIED_AT.get(text);
        return at != null && System.currentTimeMillis() - at < CHECK_MS;
    }

    public static boolean handleClick(double mouseX, double mouseY) {
        if (!active() || !copyButtons) return false;
        Hit hit = null;
        for (Hit h : HITS) {
            if (mouseX >= h.x0 && mouseX <= h.x1 && mouseY >= h.y0 && mouseY <= h.y1) {
                hit = h;
                break;
            }
        }
        if (hit == null) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.keyboard == null) return false;
        try {
            mc.keyboard.setClipboard(hit.text);
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("ChatPlus.copy", t);
            return false;
        }
        long now = System.currentTimeMillis();
        COPIED_AT.put(hit.text, now);
        if (COPIED_AT.size() > MAX_CHECK_TEXTS) {
            long cutoff = now - CHECK_MS;
            COPIED_AT.values().removeIf(at -> at < cutoff);
        }
        return true;
    }

    private static final class Hit {
        final float x0, y0, x1, y1;
        final String text;

        Hit(float x0, float y0, float x1, float y1, String text) {
            this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1;
            this.text = text;
        }
    }

    private static final class Pending {
        final float chatLineY;
        final String text;

        Pending(float chatLineY, String text) {
            this.chatLineY = chatLineY;
            this.text = text;
        }
    }
}

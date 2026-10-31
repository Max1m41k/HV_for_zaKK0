package com.visualmod.gui;

import com.visualmod.module.settings.ColorSetting;
import com.visualmod.util.HudStyle;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

public final class ColorPicker {

    public enum Kind { GRID, WHEEL }

    private static final int HEADER = 22;
    private static final int PAD = 10;
    private static final int PREVIEW_H = 22;
    private static final int HINT_H = 12;
    private static final int GRID_COLS = 8;
    private static final int GRID_ROWS = 8;
    private static final int GRID_GAP = 4;
    private static final int CELL = 24;

    private static final float[][] GRID_SV = {
            {1.00f, 1.00f}, {1.00f, 0.82f}, {1.00f, 0.62f}, {1.00f, 0.42f},
            {0.72f, 1.00f}, {0.45f, 1.00f}, {0.22f, 0.92f}, {0.00f, 0.78f},
    };

    private final ColorSetting setting;
    private final Kind kind;
    private final int keepAlpha;

    private int panelX, panelY, panelW, panelH;
    private int discX, discY, discR;
    private int barX, barY, barW, barH;

    private float hue, sat, val;
    private boolean dragging;

    public ColorPicker(ColorSetting setting, Kind kind) {
        this.setting = setting;
        this.kind = kind;
        int c = setting.getColor();
        int alpha = c & 0xFF000000;
        // Часть модулей хранит цвет как RGB без альфы: если сохранить её как есть, пикер
        // запишет 0x00RRGGBB и элемент станет полностью невидимым.
        this.keepAlpha = alpha == 0 ? 0xFF000000 : alpha;
        float[] hsv = rgbToHsv(c);
        this.hue = hsv[0];
        this.sat = hsv[1];
        this.val = hsv[2];
    }

    public boolean isWheel() { return kind == Kind.WHEEL; }

    public void render(DrawContext ctx, int mx, int my, TextRenderer tr, int screenW, int screenH) {
        if (kind == Kind.WHEEL) {
            discR = Math.min(132, Math.max(72, screenH * 2 / 9));
            panelW = discR * 2 + PAD * 2 + 34;
            panelH = HEADER + discR * 2 + PREVIEW_H + HINT_H + PAD + 10;
        } else {
            panelW = GRID_COLS * CELL + (GRID_COLS - 1) * GRID_GAP + PAD * 2;
            panelH = HEADER + GRID_ROWS * CELL + (GRID_ROWS - 1) * GRID_GAP + PREVIEW_H + HINT_H + PAD + 6;
        }
        panelX = Math.max(4, Math.min(screenW - panelW - 4, (screenW - panelW) / 2));
        panelY = Math.max(4, Math.min(screenH - panelH - 4, (screenH - panelH) / 2));

        HudStyle.fillRounded(ctx, panelX, panelY, panelW, panelH, 10, 0xF4101018);
        HudStyle.fillRounded(ctx, panelX + PAD, panelY + 4, 3, 12, 1.5f, 0xFFFF6600);
        ctx.drawTextWithShadow(tr, setting.getName(), panelX + PAD + 8, panelY + 5, 0xFFFFFFFF);

        int cur = current();
        if (kind == Kind.WHEEL) {
            discX = panelX + PAD + discR;
            discY = panelY + HEADER + discR;
            drawDisc(ctx);
            barW = Math.max(14, discR / 5);
            barX = panelX + panelW - PAD - barW;
            barY = panelY + HEADER;
            barH = discR * 2;
            drawValueBar(ctx);

            double a = hue * Math.PI * 2.0;
            int mk = 8;
            int px = discX + (int) (Math.cos(a) * sat * discR) - mk / 2;
            int py = discY + (int) (Math.sin(a) * sat * discR) - mk / 2;
            HudStyle.fillRounded(ctx, px - 1, py - 1, mk + 2, mk + 2, (mk + 2) / 2f, 0xF0FFFFFF);
            HudStyle.fillRounded(ctx, px, py, mk, mk, mk / 2f, cur);
        } else {
            drawGrid(ctx, mx, my, cur);
        }

        int py = panelY + panelH - HINT_H - PREVIEW_H - PAD;
        HudStyle.fillRounded(ctx, panelX + PAD, py, panelW - PAD * 2, PREVIEW_H, 6, 0xDD1A1A22);
        HudStyle.fillRounded(ctx, panelX + PAD + 3, py + 3, PREVIEW_H - 6, PREVIEW_H - 6, 5, cur);
        ctx.drawTextWithShadow(tr, String.format("#%06X", cur & 0xFFFFFF),
                panelX + PAD + PREVIEW_H + 3, py + 7, 0xFFCCBBAA);
        ctx.drawTextWithShadow(tr, "ЛКМ — выбрать · ESC — закрыть",
                panelX + PAD, panelY + panelH - HINT_H - 3, 0xFF6A6A7E);
    }

    private void drawGrid(DrawContext ctx, int mx, int my, int cur) {
        int x0 = panelX + PAD, y0 = panelY + HEADER;
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int x = x0 + col * (CELL + GRID_GAP);
                int y = y0 + row * (CELL + GRID_GAP);
                int c = hsvToRgb(col / (float) GRID_COLS, GRID_SV[row][0], GRID_SV[row][1], 0xFF);
                HudStyle.fillRounded(ctx, x, y, CELL, CELL, 4, c);
                if (my >= y && my < y + CELL && mx >= x && mx < x + CELL) {
                    HudStyle.fillRounded(ctx, x - 1, y - 1, CELL + 2, CELL + 2, 5, 0x40FFFFFF);
                    HudStyle.fillRounded(ctx, x, y, CELL, CELL, 4, c);
                }
                if ((c & 0xFFFFFF) == (cur & 0xFFFFFF)) ring(ctx, x, y, 0xFFFFFFFF);
            }
        }
    }

    private static void ring(DrawContext ctx, int x, int y, int color) {
        ctx.fill(x - 2, y - 2, x + CELL + 2, y - 1, color);
        ctx.fill(x - 2, y + CELL + 1, x + CELL + 2, y + CELL + 3, color);
        ctx.fill(x - 2, y - 1, x - 1, y + CELL + 1, color);
        ctx.fill(x + CELL + 1, y - 1, x + CELL + 3, y + CELL + 1, color);
    }

    private void drawDisc(DrawContext ctx) {
        int step = Math.max(2, discR / 24);
        for (int dy = -discR; dy < discR; dy++) {
            float fy = (dy + 0.5f) / discR;
            float inner = 1f - fy * fy;
            if (inner <= 0f) continue;
            int span = (int) (Math.sqrt(inner) * discR);
            for (int dx = -span; dx < span; dx += step) {
                float fx = (dx + step * 0.5f) / discR;
                float r = (float) Math.hypot(fx, fy);
                if (r > 1f) continue;
                double ang = Math.atan2(fy, fx);
                float h = (float) (ang / (Math.PI * 2.0));
                if (h < 0f) h += 1f;
                ctx.fill(discX + dx, discY + dy, discX + dx + step, discY + dy + 1,
                        hsvToRgb(h, Math.min(1f, r), val, 0xFF));
            }
        }
    }

    private void drawValueBar(DrawContext ctx) {
        for (int y = 0; y < barH; y++) {
            ctx.fill(barX, barY + y, barX + barW, barY + y + 1,
                    hsvToRgb(hue, sat, 1f - y / (float) barH, 0xFF));
        }
        int ky = barY + (int) ((1f - val) * barH);
        ctx.fill(barX - 2, ky - 1, barX + barW + 2, ky + 2, 0xFFFFFFFF);
        ctx.fill(barX - 2, ky, barX + barW + 2, ky + 1, current());
    }

    private int current() {
        return keepAlpha | hsvToRgb(hue, sat, val, 0xFF);
    }

    private boolean inside(double mx, double my) {
        return mx >= panelX && mx <= panelX + panelW && my >= panelY && my <= panelY + panelH;
    }

    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return true;
        if (!inside(mx, my)) return false;
        dragging = true;
        apply(mx, my);
        return true;
    }

    public boolean mouseDragged(double mx, double my) {
        if (dragging) apply(mx, my);
        return true;
    }

    public void mouseReleased() {
        dragging = false;
    }

    public boolean keyPressed(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_ESCAPE;
    }

    private void apply(double mx, double my) {
        if (kind == Kind.WHEEL) {
            if (mx >= barX - 3 && mx <= barX + barW + 3 && my >= barY - 3 && my <= barY + barH + 3) {
                val = 1f - clamp01((float) ((my - barY) / (float) Math.max(1, barH)));
                setting.setColor(current());
                return;
            }
            double dx = mx - discX, dy = my - discY;
            double dist = Math.hypot(dx, dy);
            if (dist > discR) return;
            double r = Math.min(1.0, dist / discR);
            double ang = Math.atan2(dy, dx);
            if (ang < 0) ang += Math.PI * 2.0;
            hue = (float) (ang / (Math.PI * 2.0));
            sat = (float) r;
            setting.setColor(current());
            return;
        }
        if (mx < panelX + PAD || my < panelY + HEADER) return;
        int col = (int) ((mx - panelX - PAD) / (CELL + GRID_GAP));
        int row = (int) ((my - panelY - HEADER) / (CELL + GRID_GAP));
        if (col < 0 || col >= GRID_COLS || row < 0 || row >= GRID_ROWS) return;
        double inX = mx - panelX - PAD - col * (CELL + GRID_GAP);
        double inY = my - panelY - HEADER - row * (CELL + GRID_GAP);
        if (inX > CELL || inY > CELL) return;
        hue = col / (float) GRID_COLS;
        sat = GRID_SV[row][0];
        val = GRID_SV[row][1];
        setting.setColor(current());
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    public static int hsvToRgb(float h, float s, float v, int alpha) {
        h = (h - (float) Math.floor(h)) * 6f;
        int i = (int) h;
        float f = h - i;
        float p = v * (1f - s);
        float q = v * (1f - f * s);
        float t = v * (1f - (1f - f) * s);
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return (alpha & 0xFF) << 24
                | ((int) (clamp01(r) * 255f) << 16)
                | ((int) (clamp01(g) * 255f) << 8)
                | (int) (clamp01(b) * 255f);
    }

    private static float[] rgbToHsv(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d == 0f) h = 0f;
        else if (max == r) h = ((g - b) / d + (g < b ? 6f : 0f)) / 6f;
        else if (max == g) h = ((b - r) / d + 2f) / 6f;
        else h = ((r - g) / d + 4f) / 6f;
        return new float[]{h, max == 0f ? 0f : d / max, max};
    }
}

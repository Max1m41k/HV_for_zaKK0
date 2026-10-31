package com.visualmod.util;

import com.visualmod.VisualsMod;
import com.visualmod.gui.ClickGUI;
import com.visualmod.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Выпадающий список включённых модулей — часть «ArrayList» в InterfaceHUD.
 *
 * <p>Сам чип рисует InterfaceHUD (общий поток пилюль), а сюда он только отдаёт прямоугольник чипа
 * через {@link #anchor}: панель вытягивается влево от правого края чипа. Клик по чипу закрепляет
 * список открытым, клик по строке — открывает ClickGUI на этом модуле.
 */
public final class ModuleList {

    private record Hit(int x, int y, int w, int h, Module mod) {}

    private static final int MAX_VISIBLE_ROWS = 12;
    private static final int ROW_H = 12;
    private static final int PAD = 5;

    private static float anim = 0f;
    private static boolean open = false;
    private static long lastHoverAt = 0L;
    private static int scroll = 0;
    private static int chipX, chipY, chipW, chipH;
    private static int panelX, panelY, panelW, panelH;
    /** Масштаб чипа: панель рисуется в той же трансформации, поэтому курсор надо переводить в локальные координаты. */
    private static float scale = 1f;
    private static final List<Hit> hits = new ArrayList<>();

    private ModuleList() {}

    /** Прямоугольник чипа на этом кадре в локальных координатах HUD + его масштаб. */
    public static void anchor(int x, int y, int w, int h, float s) {
        chipX = x; chipY = y; chipW = w; chipH = h;
        scale = s <= 0.001f ? 1f : s;
    }

    public static int enabledCount() {
        return enabledModules().size();
    }

    /** Панель, если она открыта. Вызывать после того, как InterfaceHUD нарисовал все чипы. */
    public static void render(DrawContext ctx, MinecraftClient mc) {
        List<Module> enabled = enabledModules();
        boolean hoverChip = isHover(mc, chipX, chipY, chipW, chipH);
        boolean hoverPanel = open && anim > 0.4f && isHover(mc, panelX, panelY, panelW, panelH);
        if (hoverChip || hoverPanel) lastHoverAt = System.currentTimeMillis();
        boolean target = open || System.currentTimeMillis() - lastHoverAt < 220L;
        anim += ((target ? 1f : 0f) - anim) * 0.22f;

        if (anim <= 0.02f || enabled.isEmpty()) {
            hits.clear();
            return;
        }
        renderPanel(ctx, mc, enabled);
    }

    private static void renderPanel(DrawContext ctx, MinecraftClient mc, List<Module> enabled) {
        hits.clear();
        int widest = mc.textRenderer.getWidth("§e0");
        for (Module m : enabled) {
            String icon = HudIcons.forModule(m.getName());
            widest = Math.max(widest, mc.textRenderer.getWidth(m.getDisplayName())
                    + (icon == null ? 0 : HudIcons.STEP));
        }
        panelW = Math.max(chipW, widest + PAD * 2 + 10);
        int visible = Math.min(MAX_VISIBLE_ROWS, enabled.size());
        panelH = PAD * 2 + visible * ROW_H + 1;
        panelX = chipX + chipW - panelW;
        panelY = chipY + chipH + 3;

        int contentH = enabled.size() * ROW_H;
        int maxScroll = Math.max(0, contentH - visible * ROW_H);
        if (scroll > maxScroll) scroll = maxScroll;
        if (scroll < 0) scroll = 0;

        float ease = 1f - (1f - anim) * (1f - anim);
        int visH = Math.max(2, (int) (panelH * ease));
        ctx.enableScissor(panelX, panelY, panelX + panelW, panelY + visH);

        HudStyle.fillRounded(ctx, panelX, panelY, panelW, panelH, 6, 0xF2120F1A);
        HudStyle.fillRounded(ctx, panelX, panelY, panelW, 1, 0.5f, HudStyle.ACCENT);

        int rowY = panelY + PAD - scroll;
        for (Module m : enabled) {
            if (rowY + ROW_H < panelY || rowY > panelY + visH) { rowY += ROW_H; continue; }
            boolean hov = isHover(mc, panelX + 2, rowY, panelW - 4, ROW_H);
            if (hov) HudStyle.fillRounded(ctx, panelX + 2, rowY, panelW - 4, ROW_H, 4, 0x3DFFFFFF);
            String icon = HudIcons.forModule(m.getName());
            if (icon != null) HudIcons.draw(ctx, icon, panelX + PAD + 1, rowY + 3, 0xFFB9B2C6);
            ctx.drawTextWithShadow(mc.textRenderer, m.getDisplayName(),
                    panelX + PAD + 2 + (icon == null ? 0 : HudIcons.STEP), rowY + 2, 0xFFEDEDED);
            hits.add(new Hit(panelX + 2, rowY, panelW - 4, ROW_H, m));
            rowY += ROW_H;
        }

        ctx.disableScissor();

        if (enabled.size() > MAX_VISIBLE_ROWS) {
            float t = (float) scroll / Math.max(1, enabled.size() * ROW_H - visible * ROW_H);
            int trackH = Math.max(6, (int) (panelH * (visible / (float) enabled.size())));
            int knobY = panelY + PAD + (int) (t * (panelH - PAD * 2 - trackH));
            HudStyle.fillRounded(ctx, panelX + panelW - 3, knobY, 2, trackH, 1, 0x66FFFFFF);
        }
    }

    private static List<Module> enabledModules() {
        List<Module> out = new ArrayList<>();
        if (VisualsMod.moduleManager == null) return out;
        for (Module m : VisualsMod.moduleManager.getModules()) {
            if (m.isEnabled() && !m.isHidden()) out.add(m);
        }
        out.sort(Comparator.comparingInt((Module m) -> -m.getDisplayName().length()));
        return out;
    }

    /** Клик по чипу переключает «пришпиленное» состояние — список остаётся открытым, пока не кликнешь снова. */
    public static boolean clickChip(double mx, double my) {
        if (chipW <= 0) return false;
        double x = localX(mx), y = localY(my);
        if (x < chipX || x > chipX + chipW || y < chipY || y > chipY + chipH) return false;
        open = !open;
        lastHoverAt = System.currentTimeMillis();
        return true;
    }

    /** Возвращает true, если клик попал по строке — список закрывается, открывается ClickGUI с подсветкой. */
    public static boolean clickRow(MinecraftClient mc, double mx, double my) {
        if (!open && anim < 0.4f) return false;
        double x = localX(mx), y = localY(my);
        for (Hit h : hits) {
            if (x >= h.x && x <= h.x + h.w && y >= h.y && y <= h.y + h.h) {
                ClickGUI.pendingFocus = h.mod;
                mc.setScreen(new ClickGUI());
                open = false;
                anim = 0f;
                scroll = 0;
                return true;
            }
        }
        return false;
    }

    public static boolean scroll(MinecraftClient mc, double mx, double my, double amount) {
        if (!open && anim < 0.4f) return false;
        double x = localX(mx), y = localY(my);
        if (x < panelX || x > panelX + panelW || y < panelY || y > panelY + panelH) return false;
        scroll -= (int) Math.signum(amount) * ROW_H;
        if (scroll < 0) scroll = 0;
        return true;
    }

    public static void reset() {
        open = false;
        anim = 0f;
        scroll = 0;
        hits.clear();
    }

    private static boolean isHover(MinecraftClient mc, int x, int y, int w, int h) {
        if (mc == null || mc.mouse.isCursorLocked()) return false;
        double sx = mc.getWindow().getScaleFactor();
        return hits(localX(mc.mouse.getX() / sx), localY(mc.mouse.getY() / sx), x, y, w, h);
    }

    private static boolean hits(double px, double py, int x, int y, int w, int h) {
        return px >= x && px <= x + w && py >= y && py <= y + h;
    }

    /** Экранный курсор → локальные координаты панели: трансформация масштаба идёт вокруг угла чипа. */
    private static double localX(double mx) {
        return chipX + (mx - chipX) / scale;
    }

    private static double localY(double my) {
        return chipY + (my - chipY) / scale;
    }
}

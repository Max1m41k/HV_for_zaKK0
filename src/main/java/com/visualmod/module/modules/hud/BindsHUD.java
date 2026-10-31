package com.visualmod.module.modules.hud;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.util.BindRegistry;
import com.visualmod.util.HudIcons;
import com.visualmod.util.HudStyle;
import com.visualmod.util.KeyNames;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BindsHUD extends Module {

    private record Hit(int x, int y, int w, int h, String module) {}

    private static boolean open = false;
    private static float anim = 0f;
    private static float pop = 0f;
    private static long popUntil = 0L;
    private static String awaitingBind = null;

    private static int chipX, chipY, chipW = 118, chipH = 22;
    private static int panelX, panelY, panelW = 420, panelH = 40;
    private static final List<Hit> bindHits = new ArrayList<>();

    public BindsHUD() {
        super("BindsHUD", "Список биндов: раскрыть и назначить клавишу", Category.HUD);
    }

    public void render(DrawContext ctx, int x, int y) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;

        float target = open ? 1f : 0f;
        anim += (target - anim) * 0.16f;
        pop += (System.currentTimeMillis() < popUntil ? (1f - pop) * 0.28f : (0f - pop) * 0.18f);

        float pulse = 0.55f + 0.45f * (float) Math.sin(System.currentTimeMillis() / 420.0);
        chipX = x;
        chipY = y;

        renderChip(ctx, mc, pulse);
        if (anim > 0.01f) renderPanel(ctx, mc, pulse, anim);
    }

    private void renderChip(DrawContext ctx, MinecraftClient mc, float pulse) {
        List<String[]> preview = boundModules(8);
        int rowH = 14;
        int h = 22 + preview.size() * rowH;
        int w = 128;
        for (String[] r : preview) {
            w = Math.max(w, 24 + mc.textRenderer.getWidth(r[0]) + mc.textRenderer.getWidth(r[1]));
        }
        chipW = w;
        chipH = h;

        float popScale = 1f + pop * 0.08f;
        ctx.getMatrices().pushMatrix();
        float cx = chipX + chipW / 2f, cy = chipY + chipH / 2f;
        ctx.getMatrices().translate(cx, cy);
        ctx.getMatrices().scale(popScale, popScale);
        ctx.getMatrices().translate(-cx, -cy);

        HudStyle.glassPanel(ctx, chipX, chipY, chipW, chipH, 8, pulse);
        ctx.drawTextWithShadow(mc.textRenderer, "§6Binds", chipX + 8, chipY + 6, 0xFFFFAA55);
        String arrow = open ? "▲" : "▼";
        ctx.drawTextWithShadow(mc.textRenderer, arrow,
                chipX + chipW - mc.textRenderer.getWidth(arrow) - 8, chipY + 6, 0xFFFF8800);

        int y = chipY + 20;
        if (preview.isEmpty()) {
            ctx.drawTextWithShadow(mc.textRenderer, "§8нет биндов", chipX + 8, y, 0xFF888888);
        } else {
            for (String[] r : preview) {
                HudIcons.drawForModule(ctx, r.length > 2 ? r[2] : "", chipX + 9, y + 2, 0xFFB9B2C6);
                ctx.drawTextWithShadow(mc.textRenderer, r[0], chipX + 8 + HudIcons.STEP, y, 0xFFDDDDDD);
                ctx.drawTextWithShadow(mc.textRenderer, "§e" + r[1],
                        chipX + chipW - mc.textRenderer.getWidth(r[1]) - 8, y, 0xFFFFCC66);
                y += rowH;
            }
        }
        ctx.getMatrices().popMatrix();
    }

    private void renderPanel(DrawContext ctx, MinecraftClient mc, float pulse, float a) {
        Map<Module.Category, List<Module>> grouped = groupedBinds();
        bindHits.clear();

        int colW = 128, gap = 8, headerH = 16, rowH = 15, pad = 8;
        int cols = Math.max(1, grouped.size());
        int maxRows = 1;
        for (List<Module> list : grouped.values()) maxRows = Math.max(maxRows, list.size());
        if (grouped.isEmpty()) {
            cols = 1;
            maxRows = 1;
        }

        panelW = pad * 2 + cols * colW + (cols - 1) * gap;
        panelH = pad * 2 + headerH + maxRows * rowH + 10;
        panelX = chipX;
        panelY = chipY + chipH + 6;

        float ease = 1f - (1f - a) * (1f - a);
        int visH = Math.max(8, (int) (panelH * ease));
        ctx.enableScissor(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + visH + 2);

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(panelX + panelW / 2f, panelY);
        ctx.getMatrices().scale(0.92f + 0.08f * ease, 0.92f + 0.08f * ease);
        ctx.getMatrices().translate(-(panelX + panelW / 2f), -panelY);

        HudStyle.glassPanel(ctx, panelX, panelY, panelW, panelH, 10, pulse);

        if (grouped.isEmpty()) {
            ctx.drawTextWithShadow(mc.textRenderer, "Нет назначенных биндов",
                    panelX + 12, panelY + 12, 0xFFAAAAAA);
        } else {
            int col = 0;
            for (Map.Entry<Module.Category, List<Module>> e : grouped.entrySet()) {
                int cx = panelX + pad + col * (colW + gap);
                int cy = panelY + pad;
                ctx.drawTextWithShadow(mc.textRenderer, "§6" + e.getKey().getDisplayName(),
                        cx, cy, 0xFFFF8800);
                cy += headerH;
                for (Module m : e.getValue()) {
                    String key = BindRegistry.getKeyFor(m.getName());
                    String label = key == null ? "—" : KeyNames.displayLabel(key);
                    boolean await = m.getName().equals(awaitingBind);
                    boolean hov = isHover(mc, cx - 2, cy - 1, colW, rowH);
                    if (await) {
                        HudStyle.fillRounded(ctx, cx - 2, cy - 1, colW, rowH, 4, HudStyle.pack(0.35f, 0.18f, 0.55f, 0.75f));
                        label = "НАЖМИТЕ...";
                    } else if (hov) {
                        HudStyle.fillRounded(ctx, cx - 2, cy - 1, colW, rowH, 4, HudStyle.pack(1f, 0.45f, 0.08f, 0.22f));
                    }
                    int lw = mc.textRenderer.getWidth(label);
                    HudIcons.drawForModule(ctx, m.getName(), cx + 3, cy + 3, 0xFFB9B2C6);
                    ctx.drawTextWithShadow(mc.textRenderer,
                            mc.textRenderer.trimToWidth(m.getDisplayName(), colW - lw - 14 - HudIcons.STEP),
                            cx + 2 + HudIcons.STEP, cy + 2, 0xFFE8E8E8);
                    ctx.drawTextWithShadow(mc.textRenderer, label, cx + colW - lw - 4, cy + 2,
                            await ? 0xFFE0B0FF : 0xFFFFCC66);
                    bindHits.add(new Hit(cx - 2, cy - 1, colW, rowH, m.getName()));
                    cy += rowH;
                }
                col++;
            }
        }

        ctx.getMatrices().popMatrix();
        ctx.disableScissor();
    }

    private static List<String[]> boundModules(int limit) {
        List<String[]> out = new ArrayList<>();
        if (VisualsMod.moduleManager == null) return out;
        for (Module m : VisualsMod.moduleManager.getModules()) {
            if (m.isHidden()) continue;
            String key = BindRegistry.getKeyFor(m.getName());
            if (key == null) continue;
            out.add(new String[]{m.getDisplayName(), KeyNames.displayLabel(key), m.getName()});
            if (out.size() >= limit) break;
        }
        return out;
    }

    private static Map<Module.Category, List<Module>> groupedBinds() {
        Map<Module.Category, List<Module>> map = new LinkedHashMap<>();
        if (VisualsMod.moduleManager == null) return map;
        for (Module.Category c : Module.Category.values()) map.put(c, new ArrayList<>());
        for (Module m : VisualsMod.moduleManager.getModules()) {
            if (m.isHidden()) continue;
            if (BindRegistry.getKeyFor(m.getName()) == null) continue;
            map.get(m.getCategory()).add(m);
        }
        map.entrySet().removeIf(e -> e.getValue().isEmpty());
        return map;
    }

    public static boolean clickChip(double mx, double my) {
        if (mx < chipX || mx > chipX + chipW || my < chipY || my > chipY + chipH) return false;
        open = !open;
        popUntil = System.currentTimeMillis() + 180;
        return true;
    }

    public static boolean clickRow(double mx, double my) {
        if (!open || anim <= 0.45f) return false;
        for (Hit hit : bindHits) {
            if (mx >= hit.x && mx <= hit.x + hit.w && my >= hit.y && my <= hit.y + hit.h) {
                awaitingBind = hit.module;
                return true;
            }
        }
        if (mx >= panelX && mx <= panelX + panelW && my >= panelY && my <= panelY + panelH * anim) {
            awaitingBind = null;
            return true;
        }
        return false;
    }

    public static boolean onKeyForBind(int keyCode, int scancode) {
        if (awaitingBind == null) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            awaitingBind = null;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            BindRegistry.clearBind(awaitingBind);
            awaitingBind = null;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_INSERT) return true;
        String key = KeyNames.fromKeyCode(keyCode, scancode);
        if (key == null || KeyNames.RESERVED_INSERT.equals(key)) return true;
        BindRegistry.setBind(awaitingBind, key);
        awaitingBind = null;
        return true;
    }

    public static int[] bounds() {
        boolean isOpen = open && anim > 0.45f;
        int bottom = Math.max(chipY + chipH, isOpen ? panelY + panelH : 0);
        int w = Math.max(chipW, isOpen ? panelW : 0);
        return new int[]{chipX, chipY, Math.max(w, 40), Math.max(12, bottom - chipY)};
    }

    private static boolean isHover(MinecraftClient mc, int x, int y, int w, int h) {
        if (mc.mouse.isCursorLocked()) return false;
        double mx = mc.mouse.getX() / mc.getWindow().getScaleFactor();
        double my = mc.mouse.getY() / mc.getWindow().getScaleFactor();
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}

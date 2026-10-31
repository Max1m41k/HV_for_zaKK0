package com.visualmod.module.modules.hud;

import com.visualmod.module.Module;
import com.visualmod.util.HudIcons;
import com.visualmod.util.HudStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

/**
 * Сетка вещей из инвентаря (строки 2..4) как отдельная панель: шапка с иконкой и делением,
 * дальше — скруглённые слоты.
 */
public class InventoryPreview extends Module {

    private static final int COLS = 9, ROWS = 3, SLOT = 18, GAP = 2, PAD = 5, HEAD = 15;
    private static final int SLOT_BG = 0xFF33353B;

    private final MinecraftClient mc = MinecraftClient.getInstance();

    public InventoryPreview() {
        super("InventoryPreview", "Preview inventory on screen", Category.HUD);
    }

    public void render(DrawContext ctx, int x, int y) {
        if (!isEnabled() || mc.player == null || mc.textRenderer == null) return;
        int cell = SLOT + GAP;
        int pw = PAD * 2 + COLS * cell - GAP;
        int ph = HEAD + ROWS * cell - GAP + PAD;

        HudStyle.fillRounded(ctx, x, y, pw, ph, 7f, 0xF0141518);

        HudIcons.draw(ctx, "inventory", x + PAD, y + 3, 0xFFEDEDED);
        ctx.fill(x + PAD + HudIcons.SIZE + 4, y + 3, x + PAD + HudIcons.SIZE + 5, y + 3 + HudIcons.SIZE, 0xFF4A4C52);
        ctx.drawTextWithShadow(mc.textRenderer, getDisplayName(),
                x + PAD + HudIcons.SIZE + 9, y + 3, HudStyle.TEXT);

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int sx = x + PAD + col * cell, sy = y + HEAD + row * cell;
                HudStyle.fillRounded(ctx, sx, sy, SLOT, SLOT, 4f, SLOT_BG);
                ItemStack stack = mc.player.getInventory().getStack(9 + row * COLS + col);
                if (stack.isEmpty()) continue;
                ctx.drawItem(stack, sx + 1, sy + 1);
                ctx.drawStackOverlay(mc.textRenderer, stack, sx + 1, sy + 1);
            }
        }
    }

    public void render(DrawContext ctx) {
        render(ctx, 10, 350);
    }
}

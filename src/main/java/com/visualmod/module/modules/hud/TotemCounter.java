package com.visualmod.module.modules.hud;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;

public class TotemCounter extends Module {

    public TotemCounter() {
        super("TotemCounter", "Shows totem count and 3D render above experience bar", Category.HUD);
    }

    public static boolean isActive() {
        if (VisualsMod.moduleManager == null) return false;
        TotemCounter m = VisualsMod.moduleManager.getModule(TotemCounter.class);
        return m != null && m.isEnabled();
    }

    public static void renderTotemCounter(DrawContext ctx, MinecraftClient mc) {
        if (!isActive() || mc.player == null) return;

        int totemCount = 0;
        if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
            totemCount += mc.player.getOffHandStack().getCount();
        }
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                totemCount += stack.getCount();
            }
        }

        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();

        int x = screenWidth / 2 - 8;
        int y = screenHeight - 55;

        ItemStack totemStack = new ItemStack(Items.TOTEM_OF_UNDYING);
        ctx.drawItem(totemStack, x, y);

        int textColor = (totemCount <= 1) ? 0xFFFF5555 : 0xFFFFFFFF;
        String countStr = String.valueOf(totemCount);

        TextRenderer tr = mc.textRenderer;
        int textX = x + 8 - (tr.getWidth(countStr) / 2);
        int textY = y - 10;

        ctx.drawTextWithShadow(tr, countStr, textX, textY, textColor);
    }
}

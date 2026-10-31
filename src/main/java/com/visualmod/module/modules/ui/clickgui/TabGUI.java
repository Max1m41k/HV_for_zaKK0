package com.visualmod.module.modules.ui.clickgui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class TabGUI {

    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final String[] categories = {
            "Combat", "Movement", "Render", "Player", "Misc"
    };

    private int selected = 0;

    public void render(DrawContext context) {

        if (mc.player == null) return;

        int x = 5;
        int y = 40;

        for (int i = 0; i < categories.length; i++) {

            boolean active = i == selected;

            int color = active ? 0x55FFFF : 0xAAAAAA;

            context.drawTextWithShadow(
                    mc.textRenderer,
                    categories[i],
                    x,
                    y + i * 12,
                    color
            );
        }
    }

    public void moveDown() {
        selected = (selected + 1) % categories.length;
    }

    public void moveUp() {
        selected = (selected - 1 + categories.length) % categories.length;
    }
}

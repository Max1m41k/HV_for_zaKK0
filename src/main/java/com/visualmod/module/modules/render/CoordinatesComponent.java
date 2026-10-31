package com.visualmod.module.modules.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

public class CoordinatesComponent extends DraggableHUDComponent {
    public CoordinatesComponent() {
        super("coordinates", 10, 60, 120, 15);
    }

    @Override
    public void render(DrawContext context, TextRenderer textRenderer) {
        if (!visible) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            String coords = String.format("XYZ: %.1f / %.1f / %.1f",
                    client.player.getX(), client.player.getY(), client.player.getZ());
            context.drawText(textRenderer, coords, x, y, 0xFFFFFF, true);
        }
    }
}

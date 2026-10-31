package com.visualmod.module.modules.render;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

public class WatermarkComponent extends DraggableHUDComponent {
    public WatermarkComponent() {
        super("watermark", 10, 100, 100, 15);
    }

    @Override
    public void render(DrawContext context, TextRenderer textRenderer) {
        if (!visible) return;
        context.drawText(textRenderer, "PulseVisual Lite", x, y, 0xAAAAAA, false);
    }
}

package com.visualmod.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class BlurGUI {

    private final MinecraftClient mc = MinecraftClient.getInstance();

    public void renderBlur(DrawContext context) {

        if (mc.world == null) return;

        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();

        context.fill(0, 0, w, h, 0xAA000000);
    }
}

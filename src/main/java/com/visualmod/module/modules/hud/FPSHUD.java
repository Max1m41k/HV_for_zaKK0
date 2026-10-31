package com.visualmod.module.modules.hud;

import com.visualmod.module.Module;
import com.visualmod.util.HudStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class FPSHUD extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public FPSHUD() { super("FPS", "Show FPS on screen", Category.HUD); }

    public void render(DrawContext ctx, int x, int y) {
        if (!isEnabled() || mc.player == null) return;
        int fps = mc.getCurrentFps();
        int c = fps > 60 ? 0x55FF55 : fps > 30 ? 0xFFAA00 : 0xFF5555;
        HudStyle.drawIconInfoPanel(ctx, x, y, "fps", "FPS", String.valueOf(fps), c);
    }

    public void render(DrawContext ctx) { render(ctx, 5, 5); }
}

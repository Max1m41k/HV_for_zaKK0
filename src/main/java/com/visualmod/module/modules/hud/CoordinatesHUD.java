package com.visualmod.module.modules.hud;

import com.visualmod.module.Module;
import com.visualmod.util.HudStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class CoordinatesHUD extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public CoordinatesHUD() { super("Coordinates", "XYZ coordinates on screen", Category.HUD); }

    public void render(DrawContext ctx, int x, int y) {
        if (!isEnabled() || mc.player == null) return;
        var p = mc.player.getBlockPos();
        HudStyle.drawIconInfoPanel(ctx, x, y, "coords", "XYZ", p.getX() + ", " + p.getY() + ", " + p.getZ(), 0xFFFFFF);
    }

    public void render(DrawContext ctx) { render(ctx, 5, 5); }
}

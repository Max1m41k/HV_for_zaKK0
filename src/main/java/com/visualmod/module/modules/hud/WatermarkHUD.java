package com.visualmod.module.modules.hud;

import com.visualmod.module.Module;
import com.visualmod.util.HudIcons;
import com.visualmod.util.HudStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class WatermarkHUD extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public WatermarkHUD() {
        super("Watermark", "Client watermark on screen", Category.HUD);
    }

    public void render(DrawContext ctx, int x, int y) {
        if (!isEnabled() || mc.player == null) return;
        String text = "TriadVisual ";
        int w = mc.textRenderer.getWidth(text) + 14 + HudIcons.STEP, h = 18;
        GUIAnimations.Transform t = GUIAnimations.globalTransform();
        HudStyle.applyTransform(ctx, x, y, w, h, t);
        HudStyle.drawPanel(ctx, x, y, w, h, t.alpha());
        HudIcons.draw(ctx, "watermark", x + 6, y + (h - HudIcons.SIZE) / 2f, HudStyle.ACCENT);
        ctx.drawTextWithShadow(mc.textRenderer, text, x + 6 + HudIcons.STEP, y + 5, HudStyle.TEXT);
        HudStyle.popTransform(ctx);
    }

    public void render(DrawContext ctx) { render(ctx, 5, 5); }
}

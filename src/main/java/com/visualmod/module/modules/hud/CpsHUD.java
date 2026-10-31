package com.visualmod.module.modules.hud;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.util.HudStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class CpsHUD extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final java.util.Deque<Long> clicks = new java.util.ArrayDeque<>();

    public CpsHUD() {
        super("CPS HUD", "Show CPS on screen", Category.HUD);
    }

    public void registerClick() {
        long now = System.currentTimeMillis();
        clicks.addLast(now);
        prune(now);
    }

    public int getCps() {
        prune(System.currentTimeMillis());
        return clicks.size();
    }

    private void prune(long now) {
        while (!clicks.isEmpty() && clicks.peekFirst() < now - 1000) clicks.pollFirst();
    }

    @Override
    public void onDisable() {
        clicks.clear();
    }

    public void render(DrawContext ctx, int x, int y) {
        if (!isEnabled() || mc.player == null) return;
        int w = 64, h = 18;
        GUIAnimations.Transform t = GUIAnimations.globalTransform();
        HudStyle.applyTransform(ctx, x, y, w, h, t);
        HudStyle.drawPanel(ctx, x, y, w, h, t.alpha());
        HudStyle.drawIconLabel(ctx, x + 6, y + 5, "click", "CPS", String.valueOf(getCps()));
        HudStyle.popTransform(ctx);
    }

    public void render(DrawContext ctx) { render(ctx, 5, 5); }
}

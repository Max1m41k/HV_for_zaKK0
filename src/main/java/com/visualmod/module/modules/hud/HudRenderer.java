package com.visualmod.module.modules.hud;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.modules.hud.Cooldown;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

public class HudRenderer {
    public static void init() {
        HudPosition.load();
        HudRenderCallback.EVENT.register(HudRenderer::render);
    }

    private static void render(DrawContext ctx, RenderTickCounter tc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || VisualsMod.moduleManager == null) return;
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();

        for (Module m : VisualsMod.moduleManager.getModules()) {
            if (!m.isEnabled()) continue;

            int[] p = HudPosition.glide(m.getName(), sw, sh);
            int x = p[0], y = p[1];
            // widgetScale() = 1.0 без включённой Scene — подскок виджетов на доле добавляет она.
            float s = HudPosition.getScale(m.getName())
                    * com.visualmod.module.modules.util.Scene.widgetScale();
            com.visualmod.util.HudScale.begin(ctx, s, x, y);
            HudRadius.begin(m.getName());
            int accent = com.visualmod.util.Rgb.begin(m.getName());
            renderModule(ctx, m, x, y);
            com.visualmod.util.Rgb.end(accent);
            HudRadius.end();
            com.visualmod.util.HudScale.end(ctx, s);
        }

        TotemCounter.renderTotemCounter(ctx, mc);
    }

    private static void renderModule(DrawContext ctx, Module m, int x, int y) {
            if (m instanceof WatermarkHUD h) h.render(ctx, x, y);
            else if (m instanceof FPSHUD h) h.render(ctx, x, y);
            else if (m instanceof CpsHUD h) h.render(ctx, x, y);
            else if (m instanceof CoordinatesHUD h) h.render(ctx, x, y);
            else if (m instanceof ArmorStatus h) h.render(ctx, x, y);
            else if (m instanceof PotionStatus h) h.render(ctx, x, y);
            else if (m instanceof Keystrokes h) h.render(ctx, x, y);
            else if (m instanceof TargetHUD h) h.render(ctx, x, y);
            else if (m instanceof MiniMap h) h.render(ctx, x, y);
            else if (m instanceof InventoryPreview h) h.render(ctx, x, y);
            else if (m instanceof MusicOverlay h) h.render(ctx, x, y);
            else if (m instanceof HotbarHUD h) h.render(ctx, x, y);
            else if (m instanceof ChatAnimations h) h.render(ctx, x, y);
            else if (m instanceof TabGUI h) h.render(ctx, x, y);
            else if (m instanceof Cooldown h) h.render(ctx, x, y);
            else if (m instanceof BindsHUD h) h.render(ctx, x, y);
            else if (m instanceof DurabilityWarn h) h.render(ctx, x, y);
            else if (m instanceof SupplyWarn h) h.render(ctx, x, y);
    }
}

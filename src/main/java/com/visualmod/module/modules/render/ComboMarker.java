package com.visualmod.module.modules.render;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

public class ComboMarker extends Module implements Configurable {

    public static double timeout = 3.0;
    public static int color = 0xFFFFB02E;

    private static int combo = 0;
    private static long lastHit = 0;
    private static long popAt = 0;

    static { HudRenderCallback.EVENT.register((ctx, tc) -> draw(ctx)); }

    public ComboMarker() { super("ComboMarker", "Hit combo counter under the crosshair", Category.RENDER); }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new SliderSetting("Reset After", 1, 6, 0.5, () -> timeout, v -> timeout = v, "s"));
        l.add(new ColorSetting("Color", () -> color, c -> color = c));
        return l;
    }

    public static void onAttack(Entity target) {
        if (!(target instanceof LivingEntity) || VisualsMod.moduleManager == null) return;
        ComboMarker m = VisualsMod.moduleManager.getModule(ComboMarker.class);
        if (m == null || !m.isEnabled()) return;
        long now = System.currentTimeMillis();
        combo = (now - lastHit > timeout * 1000) ? 1 : combo + 1;
        lastHit = now; popAt = now;
    }

    private static void draw(DrawContext ctx) {
        try {
            if (VisualsMod.moduleManager == null) return;
            ComboMarker m = VisualsMod.moduleManager.getModule(ComboMarker.class);
            MinecraftClient mc = MinecraftClient.getInstance();
            if (m == null || !m.isEnabled() || mc.player == null || mc.options.hudHidden) return;
            long now = System.currentTimeMillis();
            if (mc.player.hurtTime > 0) combo = 0;
            if (combo <= 0 || now - lastHit > timeout * 1000) { combo = 0; return; }

            double age = (now - popAt) / 180.0;
            float scale = (float) (1.0 + 0.55 * Math.max(0, 1 - age) * (1 + Math.sin(age * 9) * 0.2));
            float fade = (float) Math.min(1.0, (timeout * 1000 - (now - lastHit)) / 600.0);
            int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
            String text = "x" + combo + " COMBO";
            int a = (int) (255 * Math.max(0.1f, fade));
            int rgb = (a << 24) | (color & 0xFFFFFF);
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(w / 2f, h / 2f + 24);
            ctx.getMatrices().scale(scale, scale);
            ctx.drawTextWithShadow(mc.textRenderer, text, -mc.textRenderer.getWidth(text) / 2, 0, rgb);
            ctx.getMatrices().popMatrix();
        } catch (Throwable t) { com.visualmod.util.Diag.once("ComboMarker", t); }
    }
}

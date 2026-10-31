package com.visualmod.module.modules.render;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.util.HudStyle;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

public class CompassBar extends Module implements Configurable {

    public static double yOffset = 6;
    public static double barWidth = 240;

    private static final String[] NAMES = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    static { HudRenderCallback.EVENT.register((ctx, tc) -> draw(ctx)); }

    public CompassBar() { super("CompassBar", "Compass strip at the top of the screen", Category.RENDER); }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new SliderSetting("Y Offset", 0, 60, 2, () -> yOffset, v -> yOffset = v));
        l.add(new SliderSetting("Width", 120, 400, 20, () -> barWidth, v -> barWidth = v));
        return l;
    }

    private static void draw(DrawContext ctx) {
        try {
            if (VisualsMod.moduleManager == null) return;
            CompassBar m = VisualsMod.moduleManager.getModule(CompassBar.class);
            MinecraftClient mc = MinecraftClient.getInstance();
            if (m == null || !m.isEnabled() || mc.player == null || mc.options.hudHidden) return;

            int w = (int) barWidth, x = (ctx.getScaledWindowWidth() - w) / 2, y = (int) yOffset, h = 20, cx = x + w / 2;
            HudStyle.drawPanel(ctx, x, y, w, h);
            double heading = MathHelper.wrapDegrees(mc.player.getYaw() + 180f);
            double pxPerDeg = w / 140.0;

            for (int d = 0; d < 360; d += 15) {
                double rel = MathHelper.wrapDegrees(d - heading);
                int px = cx + (int) Math.round(rel * pxPerDeg);
                if (px < x + 6 || px > x + w - 6) continue;
                boolean major = d % 45 == 0;
                int th = major ? 7 : 3;
                ctx.fill(px, y + h - 3 - th, px + 1, y + h - 3, major ? 0xFFFFFFFF : 0x88FFFFFF);
                if (major) {
                    String label = NAMES[d / 45];
                    int color = d == 0 ? 0xFFFF5555 : 0xFFFFFFFF;
                    ctx.drawTextWithShadow(mc.textRenderer, label, px - mc.textRenderer.getWidth(label) / 2, y + 3, color);
                }
            }
            ctx.fill(cx - 1, y + 1, cx + 1, y + 4, 0xFFFF7B00);
            String deg = String.valueOf((int) Math.round((heading + 360) % 360));
            ctx.drawTextWithShadow(mc.textRenderer, deg + "°", cx - mc.textRenderer.getWidth(deg + "°") / 2, y + h + 2, 0xFFB5B5C6);
        } catch (Throwable t) { com.visualmod.util.Diag.once("CompassBar", t); }
    }
}

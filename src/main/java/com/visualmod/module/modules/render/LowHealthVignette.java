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

import java.util.ArrayList;
import java.util.List;

public class LowHealthVignette extends Module implements Configurable {

    public static double threshold = 6;
    public static int color = 0xFFE01E37;
    public static double pulse = 1.0;

    static { HudRenderCallback.EVENT.register((ctx, tc) -> draw(ctx)); }

    public LowHealthVignette() { super("LowHealthVignette", "Pulsing screen edges when health is low", Category.RENDER); }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new SliderSetting("Hearts", 2, 10, 1, () -> threshold, v -> threshold = v));
        l.add(new ColorSetting("Color", () -> color, c -> color = c));
        l.add(new SliderSetting("Pulse", 0.3, 3.0, 0.1, () -> pulse, v -> pulse = v, "x"));
        return l;
    }

    private static void draw(DrawContext ctx) {
        try {
            if (VisualsMod.moduleManager == null) return;
            LowHealthVignette m = VisualsMod.moduleManager.getModule(LowHealthVignette.class);
            MinecraftClient mc = MinecraftClient.getInstance();
            if (m == null || !m.isEnabled() || mc.player == null || mc.options.hudHidden || mc.player.isCreative() || mc.player.isSpectator() || !mc.player.isAlive()) return;
            double limit = threshold * 2.0, hp = mc.player.getHealth();
            if (hp >= limit) return;
            double k = 1.0 - hp / limit;
            double t = System.currentTimeMillis() / 1000.0;
            double wave = 0.65 + 0.35 * Math.sin(t * pulse * (3.0 + 5.0 * k));
            int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
            int edge = (int) (Math.min(w, h) * (0.10 + 0.14 * k));
            int a = (int) (255 * Math.min(1.0, 0.25 + 0.65 * k) * wave);
            int rgb = color & 0xFFFFFF;
            int solid = (Math.max(0, Math.min(255, a)) << 24) | rgb;
            ctx.fillGradient(0, 0, w, edge, solid, rgb);
            ctx.fillGradient(0, h - edge, w, h, rgb, solid);
            int n = 20, step = Math.max(1, edge / n);
            for (int i = 0; i < n; i++) {
                double f = 1.0 - i / (double) n;
                int ai = (int) (a * f * f);
                int c = (Math.max(0, Math.min(255, ai)) << 24) | rgb;
                ctx.fill(i * step, 0, (i + 1) * step, h, c);
                ctx.fill(w - (i + 1) * step, 0, w - i * step, h, c);
            }
        } catch (Throwable t) { com.visualmod.util.Diag.once("LowHealthVignette", t); }
    }
}

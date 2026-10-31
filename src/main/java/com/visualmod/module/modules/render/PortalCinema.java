package com.visualmod.module.modules.render;

import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * PortalCinema: при смене измерения экран на мгновение уходит в цвет портала — затемнение,
 * ленты по краям и бегущая линия разлома. Обычный мягкий вход в мир рисует WorldFade,
 * этот слой работает только на смене key измерения.
 */
public class PortalCinema extends Module implements Configurable {

    public static double duration = 0.8;
    public static double strength = 0.85;
    public static boolean bands = true;

    private static RegistryKey<World> lastDim;
    private static long startedAt;

    static {
        HudRenderCallback.EVENT.register((ctx, tickDelta) -> render(ctx));
    }

    public PortalCinema() {
        super("PortalCinema", "Dimension change gets a short cinematic fade", Category.RENDER);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new SliderSetting("Duration", 0.3, 2.0, 0.05, () -> duration, v -> duration = v, "s"));
        l.add(new SliderSetting("Strength", 0.2, 1.0, 0.05, () -> strength, v -> strength = v, ""));
        l.add(new ModeSetting("Edge Bands", new String[]{"OFF", "ON"}, () -> bands ? 1 : 0, i -> bands = (i == 1)));
        return l;
    }

    @Override
    public void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        lastDim = mc.world == null ? null : mc.world.getRegistryKey();
        startedAt = 0;
    }

    @Override
    public void onDisable() {
        startedAt = 0;
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) {
            lastDim = null;
            return;
        }
        RegistryKey<World> key = mc.world.getRegistryKey();
        if (lastDim == null) {
            lastDim = key;
            return;
        }
        if (!lastDim.equals(key)) {
            lastDim = key;
            startedAt = System.currentTimeMillis();
        }
    }

    private static void render(DrawContext ctx) {
        if (startedAt == 0) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.getWindow() == null) return;
        long age = System.currentTimeMillis() - startedAt;
        long dur = (long) (Math.max(0.3, duration) * 1000.0);
        if (age < 0 || age >= dur) return;

        float u = age / (float) dur;
        float a = (u < 0.30f ? u / 0.30f : 1f - (u - 0.30f) / 0.70f);
        a = Math.max(0f, Math.min(1f, a)) * (float) Math.max(0.2, Math.min(1.0, strength));

        int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
        int tint = tintFor(mc.world.getRegistryKey());
        int accent = accentFor(mc.world.getRegistryKey());

        // Затемнение с цветом измерения: сначала он вытесняет картинку, потом отпускает.
        int body = argb((int) (a * 150f), tint);
        ctx.fill(0, 0, w, h, body);

        if (bands) {
            int band = Math.max(12, h / 5);
            int steps = 22;
            for (int i = 0; i < steps; i++) {
                float k = 1f - i / (float) steps;
                int alpha = (int) (a * 210f * k * k);
                int y0 = i * band / steps;
                int y1 = y0 + band / steps + 1;
                ctx.fill(0, y0, w, y1, argb(alpha, accent));
                ctx.fill(0, h - y1, w, h - y0, argb(alpha, accent));
            }
            int side = Math.max(6, w / 14);
            for (int i = 0; i < steps; i++) {
                float k = 1f - i / (float) steps;
                int alpha = (int) (a * 130f * k * k);
                int x0 = i * side / steps;
                int x1 = x0 + side / steps + 1;
                ctx.fill(x0, 0, x1, h, argb(alpha, accent));
                ctx.fill(w - x1, 0, w - x0, h, argb(alpha, accent));
            }
        }

        // Линия разлома проходит сверху вниз и гаснет: это читается как «портал схлопнулся».
        float sweep = 1f - (float) Math.pow(1f - u, 2.0);
        int ly = (int) (sweep * h);
        int lh = Math.max(1, h / 90);
        ctx.fill(0, ly, w, ly + lh, argb((int) (a * 220f), 0xFFFFFF));
        ctx.fill(0, ly - lh * 3, w, ly - lh, argb((int) (a * 70f), accent));
    }

    private static int tintFor(RegistryKey<World> key) {
        String p = key.getValue().getPath();
        if (p.contains("nether")) return 0x3A0B06;
        if (p.contains("end")) return 0x140A26;
        return 0x07131F;
    }

    private static int accentFor(RegistryKey<World> key) {
        String p = key.getValue().getPath();
        if (p.contains("nether")) return 0xFF5A2A;
        if (p.contains("end")) return 0xB07CFF;
        return 0x7FE0FF;
    }

    private static int argb(int alpha, int rgb) {
        int al = Math.max(0, Math.min(255, alpha));
        return (al << 24) | (rgb & 0xFFFFFF);
    }
}

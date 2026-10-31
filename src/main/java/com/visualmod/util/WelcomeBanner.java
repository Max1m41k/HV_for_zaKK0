package com.visualmod.util;

import net.minecraft.client.gl.RenderPipelines;
import com.visualmod.lang.Lang;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

/**
 * Встроенная встреча сервера: первые 5 секунд после входа на HolyWorld там, где боссбар, висит
 * оранжевая мыльная плашка с логой HW и приветствием. Плашка появляется сверху с лёгким заездом
 * и так же мягко уходит; внизу тает полоска остатка времени.
 */
public final class WelcomeBanner {
    private static final Identifier LOGO = Identifier.of("visualmod", "textures/gui/hw_logo.png");
    private static final int LOGO_PX = 32;

    private static final long SHOW_MS = 5_000L;
    private static final long IN_MS = 340L;
    private static final long OUT_MS = 620L;

    private static final int ORANGE = 0xFFFF8A1E;
    private static final int SOAP = 0xFFB45A;
    private static final int TEXT = 0xFFFFF3E4;

    private static long endsAt;
    private static String greeted;

    private WelcomeBanner() {}

    public static void tick(MinecraftClient mc) {
        try {
            if (mc == null || mc.player == null || mc.world == null || mc.getNetworkHandler() == null) {
                greeted = null;
                return;
            }
            String addr = EventGPS.serverAddress();
            if (!isHoly(addr)) {
                greeted = null;
                return;
            }
            if (addr.equals(greeted)) return;
            greeted = addr;
            endsAt = System.currentTimeMillis() + SHOW_MS;
        } catch (Throwable t) {
            Diag.once("WelcomeBanner.tick", t);
        }
    }

    private static boolean isHoly(String addr) {
        if (addr == null) return false;
        String host = addr.toLowerCase(java.util.Locale.ROOT).replaceFirst(":[0-9]+$", "");
        return host.endsWith("holyworld.ru") || host.endsWith("holyworld.me");
    }

    public static void render(DrawContext ctx, int scaledWidth, int top) {
        long left = endsAt - System.currentTimeMillis();
        if (left <= 0) return;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.textRenderer == null) return;

            float a = alpha(left);
            String title = Lang.isRu() ? "Добро пожаловать на HolyWorld" : "Welcome to HolyWorld";
            int logo = 24, pad = 5, gap = 8;
            int tw = mc.textRenderer.getWidth(title);
            int w = pad + logo + gap + tw + pad;
            int h = logo + pad * 2;
            int x = (scaledWidth - w) / 2;
            int y = top - Math.round((1f - easeIn(left)) * 9f);
            float r = 8f;

            HudStyle.fillRounded(ctx, x + 1f, y + 2f, w, h, r, alpha(0x000000, 0.30f * a));
            HudStyle.drawPanelRounded(ctx, x, y, w, h, a, ORANGE, HudStyle.FillStyle.GLASS);
            HudStyle.fillRounded(ctx, x + 1f, y + 1f, w - 2f, h - 2f, r - 1f, alpha(SOAP, 0.30f * a));
            HudStyle.fillRounded(ctx, x + 2f, y + 3f, w - 4f, (h - 6f) * 0.42f, 4f, alpha(0xFFFFFF, 0.10f * a));
            if (Soap.onHud()) Soap.overlay(ctx, x, y, w, h, r, a);

            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO, x + pad, y + pad, 0f, 0f,
                    logo, logo, LOGO_PX, LOGO_PX, alpha(0xFFFFFF, a));
            ctx.drawTextWithShadow(mc.textRenderer, title, x + pad + logo + gap, y + (h - 9) / 2,
                    alpha(TEXT, a));

            float rest = Math.max(0f, Math.min(1f, left / (float) SHOW_MS));
            HudStyle.fillRounded(ctx, x + 1f, y + h - 2f, (w - 2f) * rest, 1.6f, 0.8f, alpha(ORANGE, 0.85f * a));
        } catch (Throwable t) {
            Diag.once("WelcomeBanner.render", t);
        }
    }

    /** Доля появления: 0 в момент старта, 1 через IN_MS. */
    private static float easeIn(long left) {
        float t = Math.min(1f, (SHOW_MS - left) / (float) IN_MS);
        return 1f - (1f - t) * (1f - t);
    }

    private static float alpha(long left) {
        float in = Math.min(1f, (SHOW_MS - left) / (float) IN_MS);
        float out = Math.min(1f, left / (float) OUT_MS);
        return Math.min(in, out);
    }

    private static int alpha(int rgb, float a) {
        int k = Math.round(Math.max(0f, Math.min(1f, a)) * 255f);
        return (k << 24) | (rgb & 0xFFFFFF);
    }
}

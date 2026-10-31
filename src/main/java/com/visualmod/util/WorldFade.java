package com.visualmod.util;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.world.World;

/**
 * Мягкий вход в мир: после того как экран загрузки ушёл, картинка проявляется из черноты
 * примерно за секунду. Работает и в одиночной игре, и на сервере — ловим смену ClientWorld,
 * а не конкретный экран, поэтому переживает любые загрузчики.
 */
public final class WorldFade {
    private static final long DURATION_MS = 1000L;
    private static final long START_DELAY_MS = 250L;

    private static World lastWorld;
    private static long worldChangedAt;
    private static long startAt;
    private static boolean armed;

    private WorldFade() {}

    public static void init() {
        HudRenderCallback.EVENT.register((ctx, tc) -> render(ctx));
    }

    public static void tick(MinecraftClient mc) {
        if (mc == null || mc.world == null || mc.player == null) {
            lastWorld = null;
            armed = false;
            return;
        }
        long now = System.currentTimeMillis();
        if (mc.world != lastWorld) {
            lastWorld = mc.world;
            worldChangedAt = now;
            armed = true;
        }
        if (!armed || mc.currentScreen != null) return;
        if (now - worldChangedAt < START_DELAY_MS) return;
        armed = false;
        startAt = now;
    }

    private static void render(DrawContext ctx) {
        if (startAt == 0) return;
        long elapsed = System.currentTimeMillis() - startAt;
        if (elapsed < 0 || elapsed >= DURATION_MS) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) return;
        float u = elapsed / (float) DURATION_MS;
        float a = (1f - u) * (1f - u);
        ctx.fill(0, 0, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(),
                ((int) (a * 235f)) << 24);
    }
}

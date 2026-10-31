package com.visualmod.module.modules.hud;

import com.visualmod.util.HudStyle;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

public class NotificationManager {

    private static final int MAX = 5;
    private static final float ANIM_IN = 0.22f;
    private static final float HOLD = 2.1f;
    private static final float ANIM_OUT = 0.28f;
    private static final float TOTAL = ANIM_IN + HOLD + ANIM_OUT;

    private static final Deque<Notification> queue = new ArrayDeque<>();
    private static boolean registered = false;

    public static void init() {
        if (registered) return;
        registered = true;
        HudRenderCallback.EVENT.register(NotificationManager::render);
    }

    public static void show(String moduleName, boolean enabled) {
        if (queue.size() >= MAX) queue.pollFirst();
        queue.addLast(new Notification(moduleName, enabled, System.currentTimeMillis()));
    }

    private static void render(DrawContext ctx, RenderTickCounter tc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden) return;

        int screenW = ctx.getScaledWindowWidth();
        int screenH = ctx.getScaledWindowHeight();
        long now = System.currentTimeMillis();

        int notifH = 30, gap = 7;
        int baseY = Math.max(40, screenH / 2 - 50);

        Iterator<Notification> it = queue.iterator();
        int idx = 0;
        while (it.hasNext()) {
            Notification n = it.next();
            float age = (now - n.createdAt) / 1000f;
            if (age > TOTAL) { it.remove(); continue; }

            float slideT;
            if (age < ANIM_IN) slideT = age / ANIM_IN;
            else if (age < ANIM_IN + HOLD) slideT = 1f;
            else slideT = 1f - (age - ANIM_IN - HOLD) / ANIM_OUT;
            slideT = Math.max(0f, Math.min(1f, slideT));
            float eased = 1f - (1f - slideT) * (1f - slideT);

            String icon = n.enabled ? "✓" : "✗";
            String status = n.enabled ? "включён" : "выключен";
            String text = "модуль " + n.moduleName + " " + status + " " + icon;
            int notifW = Math.max(176, mc.textRenderer.getWidth(text) + 22);
            int baseX = screenW - notifW - 10;
            int drawX = (int) (baseX + (notifW + 14) * (1f - eased));
            int drawY = baseY + idx * (notifH + gap);

            float pulse = 0.55f + 0.45f * (float) Math.sin(now / 280.0);
            HudStyle.glassPanel(ctx, drawX, drawY, notifW, notifH, 10, n.enabled ? pulse : 0.25f);

            int accent = n.enabled
                    ? HudStyle.pack(0.25f, 0.95f, 0.40f, eased)
                    : HudStyle.pack(0.95f, 0.22f, 0.22f, eased);
            HudStyle.fillRounded(ctx, drawX + 5, drawY + 7, 3, notifH - 14, 1.5f, accent);

            ctx.drawTextWithShadow(mc.textRenderer, text, drawX + 14, drawY + 11,
                    HudStyle.pack(0.95f, 0.95f, 0.97f, eased));
            idx++;
        }
    }

    private record Notification(String moduleName, boolean enabled, long createdAt) {}
}

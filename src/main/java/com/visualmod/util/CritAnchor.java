package com.visualmod.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.EntityHitResult;

/**
 * Метка «БЕЙ» под прицелом, пока курсор наведён на другого игрока.
 * Чистая косметика: никаких таймингов, окон крита и подсказок, когда нажимать кнопку.
 */
public final class CritAnchor {

    // ===================== настройки (пишет их модуль CustomHitbox) =====================

    public static boolean enabled = true;
    public static int markerColor = 0xFFFF2A2A;

    private CritAnchor() {
    }

    /** Прицел стоит на живом игроке — не на себе, не на голом невидимом. */
    private static boolean hovering(MinecraftClient mc) {
        if (!enabled) return false;
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || p.isDead()) return false;
        if (!(mc.crosshairTarget instanceof EntityHitResult hit)) return false;
        if (!(hit.getEntity() instanceof PlayerEntity target) || target == p || target.isDead()) return false;
        return !invisibleAndNaked(target);
    }

    /**
     * Цель с невидимостью и без брони не подсвечивается: броня и так видна сквозь
     * невидимость, а голый невидимый игрок — это уже честный X-Ray-бонус.
     */
    private static final net.minecraft.entity.EquipmentSlot[] ARMOR_SLOTS = {
            net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.entity.EquipmentSlot.CHEST,
            net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.entity.EquipmentSlot.FEET,
    };

    private static boolean invisibleAndNaked(PlayerEntity target) {
        try {
            if (!target.hasStatusEffect(StatusEffects.INVISIBILITY)) return false;
            for (net.minecraft.entity.EquipmentSlot slot : ARMOR_SLOTS) {
                if (!target.getEquippedStack(slot).isEmpty()) return false;
            }
            return true;
        } catch (Throwable t) {
            Diag.once("CritAnchor.invisible", t);
            return true;
        }
    }

    public static void renderHud(DrawContext ctx) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (ctx == null || mc.player == null || mc.textRenderer == null) return;
            if (!hovering(mc)) return;

            TextRenderer font = mc.textRenderer;
            int cx = ctx.getScaledWindowWidth() / 2;
            int cy = ctx.getScaledWindowHeight() / 2;
            int y = cy + 14;
            Text label = Text.literal("БЕЙ").formatted(Formatting.BOLD);
            int w = font.getWidth(label) + 8;
            HudStyle.fillRounded(ctx, cx - w / 2f, y - 3, w, 13, 3f, withAlpha(markerColor, 0.35f));
            ctx.drawCenteredTextWithShadow(font, label, cx, y, markerColor);
        } catch (Throwable t) {
            Diag.once("CritAnchor.hud", t);
        }
    }

    private static int withAlpha(int rgb, float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        return (a << 24) | (rgb & 0x00FFFFFF);
    }
}

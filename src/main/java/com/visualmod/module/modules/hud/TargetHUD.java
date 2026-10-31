package com.visualmod.module.modules.hud;

import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.util.HudStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;

import java.util.Arrays;
import java.util.List;

public class TargetHUD extends Module implements Configurable {
    private static LivingEntity target;
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public enum TargetShape {
        CIRCLE,
        CORNERS,
        HEXAGON,
        CHEVRONS,
        CROSSHAIR,
        CHAINS,
        SOULS
    }

    public enum DisplayMode { PANEL, HP_INDICATOR }

    private static final int GOLD = 0xFFFFD700;
    private static final java.util.Map<java.util.UUID, Long> HITS = new java.util.concurrent.ConcurrentHashMap<>();

    private int accentColor = 0xFFFF8800;
    private int secondColor = 0xFF22DD88;
    private float markerScale = 1.0f;
    private float markerSpeed = 1.0f;
    private float markerThickness = 1.6f;
    private HudStyle.FillStyle fillStyle = HudStyle.FillStyle.GLASS;
    private TargetShape targetShape = TargetShape.CIRCLE;
    private DisplayMode displayMode = DisplayMode.PANEL;

    private final List<Setting> settings = List.of(
            new ColorSetting("Marker Color", () -> accentColor, c -> accentColor = c),
            new ColorSetting("Second Color", () -> secondColor, c -> secondColor = c),
            new ModeSetting(
                    "HUD Style",
                    Arrays.stream(HudStyle.FillStyle.values()).map(Enum::name).toArray(String[]::new),
                    () -> fillStyle.ordinal(),
                    i -> fillStyle = HudStyle.FillStyle.values()[i]
            ),
            new ModeSetting(
                    "Target Marker",
                    Arrays.stream(TargetShape.values()).map(Enum::name).toArray(String[]::new),
                    () -> targetShape.ordinal(),
                    i -> targetShape = TargetShape.values()[i]
            ),
            new SliderSetting("Marker Scale", 0.5f, 1.8f, 0.05f, () -> markerScale, v -> markerScale = (float) v, "x"),
            new SliderSetting("Marker Speed", 0.2f, 2.5f, 0.1f, () -> markerSpeed, v -> markerSpeed = (float) v, "x"),
            new SliderSetting("Marker Thickness", 0.5f, 4f, 0.1f, () -> markerThickness, v -> markerThickness = (float) v, "x"),
            new ModeSetting(
                    "Display Mode",
                    Arrays.stream(DisplayMode.values()).map(Enum::name).toArray(String[]::new),
                    () -> displayMode.ordinal(),
                    i -> displayMode = DisplayMode.values()[i]
            )
    );

    public TargetHUD() { super("TargetHUD", "Target info HUD with world markers", Category.HUD); }

    public static void setTarget(LivingEntity e) { target = e; }
    public static LivingEntity getTarget() { return target; }

    /** Помечает, что мы ударили цель: марки краснеют, а шевроны начинают крутиться. */
    public static void onHit(LivingEntity e) {
        if (e != null) HITS.put(e.getUuid(), System.currentTimeMillis());
    }

    /** 1.0 сразу после удара, 0 через 380 мс. */
    public static float hitFlash(LivingEntity e) {
        if (e == null) return 0f;
        Long t = HITS.get(e.getUuid());
        if (t == null) return 0f;
        float f = 1f - (System.currentTimeMillis() - t) / 380f;
        if (f <= 0f) { HITS.remove(e.getUuid()); return 0f; }
        return f;
    }

    public float getMarkerThickness() { return markerThickness; }
    public int getAccentColor() { return accentColor; }
    public int getSecondColor() { return secondColor; }
    public float getMarkerScale() { return markerScale; }
    public float getMarkerSpeed() { return markerSpeed; }
    public TargetShape getTargetShape() { return targetShape; }

    @Override
    public List<Setting> getSettings() { return settings; }

    private static int hpColor(float pct) {
        return pct > 0.5f ? 0xFF00CC44 : pct > 0.25f ? 0xFFFFAA00 : 0xFFEE3333;
    }

    public void render(DrawContext ctx, int x, int y) {
        if (!isEnabled() || mc.player == null || target == null || target.isDead()) return;

        float hp = target.getHealth();
        float max = target.getMaxHealth();
        float pct = Math.max(0f, Math.min(1f, hp / max));
        float absorption = target.getAbsorptionAmount();
        float absorptionPct = Math.min(1f, absorption / max);
        boolean hasGoldenShield = absorption > 0f;

        if (displayMode == DisplayMode.HP_INDICATOR) {
            renderHpIndicator(ctx, hp, pct, hasGoldenShield);
            return;
        }

        int w = 168, h = 68;
        HudStyle.drawPanelRounded(ctx, x, y, w, h, 1f, accentColor, fillStyle, 10f);

        int px = x + 7, py = y + 7, ps = 26;
        HudStyle.fillRounded(ctx, px - 1, py - 1, ps + 2, ps + 2, 6, 0x66000000 | (accentColor & 0xFFFFFF));
        SkinTextures skin = skinOf(target);
        if (skin != null) {
            PlayerSkinDrawer.draw(ctx, skin, px, py, ps);
        } else {
            HudStyle.fillRounded(ctx, px, py, ps, ps, 5, 0xFF2A2A36);
            ctx.drawCenteredTextWithShadow(mc.textRenderer, "?", px + ps / 2, py + 9, accentColor);
        }

        int tx = px + ps + 8;
        String name = target.getName().getString();
        if (name.length() > 16) name = name.substring(0, 16) + "…";
        ctx.drawTextWithShadow(mc.textRenderer, name, tx, y + 8, HudStyle.TEXT);

        // Расстояние до цели не показываем — это информация «из чит-меню», в косметическом моде её быть не должно.
        String sub = String.format("%.1f ❤", hp);
        ctx.drawTextWithShadow(mc.textRenderer, sub, tx, y + 19, HudStyle.TEXT_DIM);

        int bx = x + 7, bw = w - 14, bh = 5;
        int by = y + 38;
        HudStyle.fillRounded(ctx, bx, by, bw, bh, 2.5f, 0xFF1B1B22);
        if (hasGoldenShield) {
            int gw = (int) (bw * absorptionPct);
            if (gw > 2) HudStyle.fillRounded(ctx, bx, by, gw, bh, 2.5f, GOLD);
            by += bh + 2;
        }
        HudStyle.fillRounded(ctx, bx, by, bw, bh, 2.5f, 0xFF1B1B22);
        int fw = (int) (bw * pct);
        if (fw > 2) HudStyle.fillRounded(ctx, bx, by, fw, bh, 2.5f, hpColor(pct));

        String mx = String.format("%.0f", max);
        ctx.drawTextWithShadow(mc.textRenderer, mx, x + w - 8 - mc.textRenderer.getWidth(mx), y + 8, HudStyle.TEXT_DIM);

        renderArmorDurability(ctx, x, y, w, h);
        com.visualmod.module.modules.combat.TotemBurst.drawChip(ctx, x, y, w, target.getUuid());
    }

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private void renderArmorDurability(DrawContext ctx, int x, int y, int w, int h) {
        int rowY = y + h - 13;
        int barW = 19, barH = 5, gap = 2;
        int sumMax = 0;
        long sumLeft = 0;
        int drawn = 0;

        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = target.getEquippedStack(slot);
            int bx = x + 7 + drawn * (barW + gap);
            HudStyle.fillRounded(ctx, bx, rowY, barW, barH, 2.5f, 0xFF1B1B22);
            drawn++;
            if (stack.isEmpty()) continue;
            int maxD = stack.getMaxDamage();
            if (maxD <= 0) {
                HudStyle.fillRounded(ctx, bx, rowY, barW, barH, 2.5f, 0xFF8A8AA0);
                continue;
            }
            float dur = Math.max(0f, Math.min(1f, (maxD - stack.getDamage()) / (float) maxD));
            sumMax += maxD;
            sumLeft += (long) (maxD * dur);
            int fw = Math.max(2, (int) (barW * dur));
            HudStyle.fillRounded(ctx, bx, rowY, fw, barH, 2.5f, durColor(dur));
        }

        if (sumMax <= 0) return;
        float total = (float) (sumLeft * 100.0 / sumMax);
        String label = String.format("%.0f%%", total);
        ctx.drawTextWithShadow(mc.textRenderer, label,
                x + w - 8 - mc.textRenderer.getWidth(label), rowY, durColor(total / 100f));
    }

    private static int durColor(float pct) {
        return pct > 0.66f ? 0xFF4FB477 : pct > 0.33f ? 0xFFFFAA00 : 0xFFEE3333;
    }

    private void renderHpIndicator(DrawContext ctx, float hp, float pct, boolean hasGoldenShield) {
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int cx = sw / 2, cy = sh / 2 + 14;

        int color = hasGoldenShield ? GOLD : hpColor(pct);
        String text = String.format("%.1f ♥", hp);

        int tw = mc.textRenderer.getWidth(text);
        HudStyle.fillRounded(ctx, cx - tw / 2 - 6, cy - 4, tw + 12, 16, 6, 0xAA000000);
        ctx.drawCenteredTextWithShadow(mc.textRenderer, text, cx, cy, color);
    }

    private static SkinTextures skinOf(LivingEntity e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(e instanceof AbstractClientPlayerEntity p) || mc.getSkinProvider() == null) return null;
        try {
            return p.getSkin();
        } catch (Throwable t) {
            return null;
        }
    }

    public void render(DrawContext ctx) { render(ctx, 10, 200); }
}

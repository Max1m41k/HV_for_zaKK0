package com.visualmod.module.modules.hud;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.util.HudStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.GameMode;

import java.util.Arrays;
import java.util.List;

public class HotbarHUD extends Module implements Configurable {

    public enum BarStyle { VANILLA, PANEL, RECTANGLES }

    public static boolean replaceVanilla = true;
    public static boolean centered = true;
    public static boolean showOffhand = true;
    public static BarStyle barStyle = BarStyle.PANEL;

    private static final int HP_C = 0xFFE8404A;
    private static final int ABS_C = 0xFFFFD24A;
    private static final int ARM_C = 0xFFC3D2E2;
    private static final int FOOD_C = 0xFFE9A33C;
    private static final int TRACK = 0x4D000000;

    /** Диаметр слота-кружка и шаг между ними. */
    private static final int SLOT = 22, GAP = 2;

    private static final float[] ANIM = {-1f, -1f, -1f};
    private static final float[] PREV = {-1f, -1f, -1f};
    private static final float[] FLASH = {0f, 0f, 0f};

    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final List<Setting> settings = List.of(
            new ModeSetting("Replace Vanilla", new String[]{"OFF", "ON"}, () -> replaceVanilla ? 1 : 0, i -> replaceVanilla = (i == 1)),
            new ModeSetting("Centered", new String[]{"OFF", "ON"}, () -> centered ? 1 : 0, i -> centered = (i == 1)),
            new ModeSetting("Offhand", new String[]{"OFF", "ON"}, () -> showOffhand ? 1 : 0, i -> showOffhand = (i == 1)),
            new ModeSetting("Bars",
                    Arrays.stream(BarStyle.values()).map(Enum::name).toArray(String[]::new),
                    () -> barStyle.ordinal(),
                    i -> barStyle = BarStyle.values()[i])
    );

    public HotbarHUD() { super("HotbarHUD", "Custom hotbar that replaces the vanilla one", Category.HUD); }

    @Override public List<Setting> getSettings() { return settings; }

    private static boolean spectator(MinecraftClient mc) {
        return mc.interactionManager != null && mc.interactionManager.getCurrentGameMode() == GameMode.SPECTATOR;
    }

    public static boolean replacing() {
        if (VisualsMod.moduleManager == null) return false;
        HotbarHUD m = VisualsMod.moduleManager.getModule(HotbarHUD.class);
        MinecraftClient mc = MinecraftClient.getInstance();
        return m != null && m.isEnabled() && replaceVanilla && mc.player != null && !spectator(mc);
    }

    /** True while our own health/armor/hunger bars should replace the vanilla ones. */
    public static boolean customBars() {
        if (VisualsMod.moduleManager == null) return false;
        HotbarHUD m = VisualsMod.moduleManager.getModule(HotbarHUD.class);
        MinecraftClient mc = MinecraftClient.getInstance();
        return m != null && m.isEnabled() && barStyle != BarStyle.VANILLA && mc.player != null && !spectator(mc);
    }

    public void render(DrawContext ctx, int x, int y) {
        if (!isEnabled() || mc.player == null || spectator(mc)) return;
        final int slots = 9;
        int w = slots * SLOT + (slots - 1) * GAP + 10, h = SLOT + 10;
        if (centered) { x = (ctx.getScaledWindowWidth() - w) / 2; y = ctx.getScaledWindowHeight() - h - 2; }

        drawStatusBars(ctx, x, y, w);

        HudStyle.drawPanel(ctx, x, y, w, h, 1f, HudStyle.pill(h));
        int selected = mc.player.getInventory().getSelectedSlot();
        for (int i = 0; i < slots; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            int sx = x + 5 + i * (SLOT + GAP), sy = y + 5;
            drawSlot(ctx, sx, sy, i == selected);
            if (!stack.isEmpty()) {
                ctx.drawItem(stack, sx + (SLOT - 16) / 2, sy + (SLOT - 16) / 2);
                ctx.drawStackOverlay(mc.textRenderer, stack, sx + (SLOT - 16) / 2, sy + (SLOT - 16) / 2);
            }
        }
        com.visualmod.util.SlotNumbers.drawOverHotbarHud(ctx, x + 5 + (SLOT - 16) / 2, y + 5 + (SLOT - 16) / 2, SLOT + GAP);
        if (showOffhand) {
            ItemStack off = mc.player.getOffHandStack();
            if (!off.isEmpty()) {
                int ow = SLOT + 10, ox = x - ow - GAP;
                HudStyle.drawPanel(ctx, ox, y, ow, h, 1f, HudStyle.pill(h));
                drawSlot(ctx, ox + 5, y + 5, false);
                ctx.drawItem(off, ox + 5 + (SLOT - 16) / 2, y + 5 + (SLOT - 16) / 2);
                ctx.drawStackOverlay(mc.textRenderer, off, ox + 5 + (SLOT - 16) / 2, y + 5 + (SLOT - 16) / 2);
            }
        }
    }

    /** Слот-кружок: у выбранного — кольцо акцента, у остальных тёмная тарелка. */
    private static void drawSlot(DrawContext ctx, int x, int y, boolean selected) {
        float r = HudStyle.pill(SLOT);
        if (selected) HudStyle.fillRounded(ctx, x - 1, y - 1, SLOT + 2, SLOT + 2, r + 1, HudStyle.ACCENT);
        HudStyle.fillRounded(ctx, x, y, SLOT, SLOT, r, selected ? 0xDD20242E : 0x9912151C);
    }

    private void drawStatusBars(DrawContext ctx, int x, int y, int w) {
        if (barStyle == BarStyle.VANILLA) return;
        PlayerEntity p = mc.player;
        tick(0, Math.max(0f, p.getHealth()) / 2f);
        tick(1, Math.max(0f, p.getArmor()) / 2f);
        tick(2, Math.max(0, p.getHungerManager().getFoodLevel()) / 2f);

        if (barStyle == BarStyle.PANEL) drawPanelBars(ctx, x, y, w, p);
        else drawRectBars(ctx, x, y, w, p);
    }

    /** Bars sit in half-heart units (0..10); value drops animate and flash white when damage lands. */
    private static void tick(int i, float target) {
        if (ANIM[i] < 0f) { ANIM[i] = target; PREV[i] = target; return; }
        if (target < PREV[i] - 0.001f) FLASH[i] = 1f;
        PREV[i] = target;
        ANIM[i] += (target - ANIM[i]) * 0.25f;
        FLASH[i] *= 0.9f;
    }

    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }

    private static int flashColor(int base, int slot) {
        float f = FLASH[slot];
        return f > 0.05f ? HudStyle.lerpColor(base, 0xFFFFFFFF, Math.min(0.7f, f)) : base;
    }

    /** Одна строка сердец, над ней броня, справа — полоска голода. */
    private void drawPanelBars(DrawContext ctx, int x, int y, int w, PlayerEntity p) {
        int pw = Math.max(158, w), ph = 27;
        int px = x, py = y - ph - 4;
        HudStyle.drawPanelRounded(ctx, px, py, pw, ph, 1f, HudStyle.ACCENT, HudStyle.FillStyle.GLASS, 8f);

        int armorRow = py + 6, barRow = py + 15;
        int left = px + 14, right = px + pw - 76;

        ctx.fill(px + 6, armorRow + 1, px + 9, armorRow + 4, flashColor(ARM_C, 1));
        drawUnits(ctx, left, armorRow, ANIM[1], flashColor(ARM_C, 1));

        ctx.fill(px + 6, barRow + 1, px + 9, barRow + 4, flashColor(HP_C, 0));
        drawUnits(ctx, left, barRow, ANIM[0], flashColor(HP_C, 0));
        drawUnits(ctx, right, barRow, ANIM[2], flashColor(FOOD_C, 2));

        float abs = p.getAbsorptionAmount();
        if (abs > 0.05f) {
            HudStyle.fillRounded(ctx, left, barRow - 4f, 70f * clamp01(abs / 20f), 2f, 1f, ABS_C);
        }
    }

    /** Десять делений по halves: целые деления заполнены, переходное — частично. */
    private static void drawUnits(DrawContext ctx, float x0, float y, float value, int color) {
        for (int i = 0; i < 10; i++) {
            float fx = x0 + i * 7f;
            HudStyle.fillRounded(ctx, fx, y, 6, 4, 2f, TRACK);
            float f = clamp01(value - i);
            if (f > 0.03f) HudStyle.fillRounded(ctx, fx, y, Math.max(2.2f, 6f * f), 4, 2f, color);
        }
    }

    private void drawRectBars(DrawContext ctx, int x, int y, int w, PlayerEntity p) {
        final int barW = 64, barH = 5;
        float maxHalf = Math.max(1f, p.getMaxHealth() / 2f);

        // Броня сверху, здоровье снизу.
        bar(ctx, x, y - 17, barW, barH, clamp01(ANIM[1] / 10f), 1);
        bar(ctx, x, y - 10, barW, barH, clamp01(ANIM[0] / maxHalf), 0);
        float abs = p.getAbsorptionAmount();
        if (abs > 0.05f) {
            float start = x + barW * clamp01(ANIM[0] / maxHalf) + 1f;
            HudStyle.fillRounded(ctx, start, y - 10, Math.min(barW * 0.6f, barW * abs / 20f), barH, barH / 2f, ABS_C);
        }
        bar(ctx, x + w - barW, y - 14, barW, barH, clamp01(ANIM[2] / 10f), 2);
    }

    private void bar(DrawContext ctx, float x, float y, float w, float h, float frac, int slot) {
        HudStyle.fillRounded(ctx, x, y, w, h, h / 2f, TRACK);
        float fw = w * clamp01(frac);
        if (fw < 1.4f) return;
        int[] cols = {HP_C, ARM_C, FOOD_C};
        HudStyle.fillRounded(ctx, x, y, fw, h, h / 2f, flashColor(cols[slot], slot));
        float tip = Math.min(3f, fw);
        HudStyle.fillRounded(ctx, x + fw - tip, y + 1f, tip, h - 2f, 1.5f, 0x80FFFFFF);
    }

    public void render(DrawContext ctx) { render(ctx, 10, 10); }
}

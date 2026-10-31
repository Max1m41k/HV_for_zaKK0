package com.visualmod.module.modules.hud;

import com.visualmod.VisualsMod;
import com.visualmod.lang.Lang;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.module.settings.ToggleSetting;
import com.visualmod.util.Diag;
import com.visualmod.util.HudStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Предупреждение о прочности: как только инструмент в руке или броня на игроке держится на
 * последнем дыхании, рядом появляется мягко пульсирующая пилюля с остатком. Порог настраивается.
 */
public class DurabilityWarn extends Module implements Configurable {

    public static double threshold = 25;
    public static boolean heldTool = true;
    public static boolean armor = true;
    public static int style = 0;
    public static boolean breakSound = true;

    /** Броня «кончается» в очках прочности, а не в процентах: 120 → «!», 70 → «!!», 30 → «!!!». */
    private static final int BANG_1 = 120, BANG_2 = 70, BANG_3 = 30;
    /** Остаток брони, при котором играем разбитое стекло. */
    public static int glassUnder = 25;

    private final List<Setting> settings = List.of(
            new SliderSetting("Threshold", 5, 60, 1, () -> threshold, v -> threshold = v, " %"),
            new ModeSetting("Style", new String[]{"PULSE", "ICON"}, () -> style, i -> style = i),
            new ToggleSetting("Held Item", () -> heldTool, v -> heldTool = v),
            new ToggleSetting("Armor", () -> armor, v -> armor = v),
            new ToggleSetting("Glass On Break", () -> breakSound, v -> breakSound = v),
            new SliderSetting("Glass Under", 1, 120, 1, () -> glassUnder, v -> glassUnder = (int) v)
    );

    public DurabilityWarn() {
        super("DurabilityWarn", "Pulsing warning when your tool or armor is about to break", Category.HUD);
    }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }

    public static boolean active() {
        if (VisualsMod.moduleManager == null) return false;
        DurabilityWarn m = VisualsMod.moduleManager.getModule(DurabilityWarn.class);
        return m != null && m.isEnabled();
    }

    /** Доля прочности left/max, если предмет ещё держится, иначе null. */
    private static Double worn(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isDamageable()) return null;
        int max = stack.getMaxDamage();
        if (max <= 0) return null;
        return (double) (max - stack.getDamage()) / max;
    }

    /** Наихудшая прочность из руки и брони; -1, если считать нечего. Чип InterfaceHUD. */
    public static double worst(MinecraftClient mc) {
        if (mc.player == null) return -1;
        double worst = -1;
        ItemStack[] stacks = { mc.player.getMainHandStack(), mc.player.getOffHandStack(),
                com.visualmod.util.McCompat.armorStack(mc.player, 0), com.visualmod.util.McCompat.armorStack(mc.player, 1),
                com.visualmod.util.McCompat.armorStack(mc.player, 2), com.visualmod.util.McCompat.armorStack(mc.player, 3) };
        for (ItemStack stack : stacks) {
            Double f = worn(stack);
            if (f != null && (worst < 0 || f < worst)) worst = f;
        }
        return worst;
    }

    private static final class Warn {
        final ItemStack stack;
        final double fraction;
        final int broken;
        final int left;

        Warn(ItemStack stack, double fraction) {
            this.stack = stack;
            this.fraction = fraction;
            this.broken = (int) Math.round(stack.getMaxDamage() * (1 - fraction));
            this.left = stack.getMaxDamage() - this.broken;
        }

        /** Сколько знаков тревоги рисовать: 0 — предмет ещё крепкий. */
        String bangs() {
            if (left <= BANG_3) return "!!!";
            if (left <= BANG_2) return "!!";
            if (left <= BANG_1) return "!";
            return "";
        }
    }

    private static List<Warn> collect(MinecraftClient mc) {
        double lim = Math.max(0.01, Math.min(0.95, threshold / 100.0));
        List<Warn> out = new ArrayList<>(5);
        if (heldTool) {
            add(out, mc.player.getMainHandStack(), lim);
            add(out, mc.player.getOffHandStack(), lim);
        }
        if (armor) {
            for (int i = 0; i < 4; i++) add(out, com.visualmod.util.McCompat.armorStack(mc.player, i), lim);
        }
        return out;
    }

    private static void add(List<Warn> out, ItemStack stack, double lim) {
        Double f = worn(stack);
        if (f == null || f > lim) return;
        out.add(new Warn(stack, f));
    }

    public void render(DrawContext ctx, int x, int y) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!active() || mc.player == null) return;
            checkGlass(mc);

            boolean icons = style == 1;
            int pad = 4, rowH = icons ? 20 : 12;
            List<Warn> warns = collect(mc);
            if (warns.isEmpty()) {
                // Пустая панель невидима, но рамка в редакторе должна оставаться той же ширины,
                // иначе модуль приходится ловить мышью вслепую.
                HudRadius.note(x, y, icons ? 24 : 88, pad * 2 + rowH);
                return;
            }

            int w = icons ? 6 + warns.size() * 18 : 88;
            int h = pad * 2 + warns.size() * rowH;
            float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 360.0);
            HudStyle.drawPanelRounded(ctx, x, y, w, h, 1f, HudStyle.ACCENT, HudStyle.FillStyle.GLASS);

            for (int i = 0; i < warns.size(); i++) {
                Warn warn = warns.get(i);
                String bang = warn.bangs();
                int ix = x + pad, iy = y + pad + i * rowH;
                ctx.drawItem(warn.stack, ix, iy);
                if (icons) {
                    if (bang.isEmpty()) {
                        int dot = 0xFF000000 | HudStyle.lerpColor(0xF04848, 0xFFAA33, pulse);
                        HudStyle.fillRounded(ctx, ix + 12, iy + 12, 5, 5, 2.5f, dot);
                    } else {
                        ctx.drawTextWithShadow(mc.textRenderer, bang, ix + 4, iy + 12, 0xFFFF5A5A);
                    }
                    continue;
                }
                int pct = (int) Math.ceil(warn.fraction * 100);
                String label = pct + "% · " + warn.left + (bang.isEmpty() ? "" : " " + bang);
                int color = HudStyle.lerpColor(0xF04848, 0xFFAA33, (float) (pulse * 0.7));
                ctx.drawTextWithShadow(mc.textRenderer, label, ix + 18, iy + 2, 0xFF000000 | color);
            }

            if (!icons) {
                String title = Lang.isRu() ? "На грани" : "Near breaking";
                ctx.drawTextWithShadow(mc.textRenderer, title, x + pad, y - 10, HudStyle.TEXT_DIM);
            }
        } catch (Throwable t) {
            Diag.once("DurabilityWarn.render", t);
        }
    }

    /** Остаток прочности в каждом слоте брони с прошлого кадра; -1 — слот пуст. */
    private static final int[] glassSeen = new int[4];

    /** Броня уходит ниже порога — один раз на слот играем разбитое стекло. */
    private static void checkGlass(MinecraftClient mc) {
        if (!breakSound || mc.getSoundManager() == null) {
            Arrays.fill(glassSeen, -1);
            return;
        }
        for (int i = 0; i < 4; i++) {
            ItemStack stack = com.visualmod.util.McCompat.armorStack(mc.player, i);
            int left = (stack.isEmpty() || !stack.isDamageable()) ? -1
                    : stack.getMaxDamage() - stack.getDamage();
            if (left >= 0 && glassSeen[i] > glassUnder && left <= glassUnder) {
                mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.BLOCK_GLASS_BREAK, 1f, 0.85f));
            }
            glassSeen[i] = left;
        }
    }
}

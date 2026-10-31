package com.visualmod.module.modules.hud;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.util.Diag;
import com.visualmod.util.HudStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;

import java.util.ArrayList;
import java.util.List;

/**
 * Тихий счётчик запасов: тотемы и золотые яблоки всегда видны маленькой пилюлей,
 * а когда их меньше порога — пилюля начинает мягко пульсировать красным.
 */
public class SupplyWarn extends Module implements Configurable {

    public static int totemAlert = 2;
    public static int gappleAlert = 3;
    public static int showMode = 0;
    public static int listMode = 1;

    /** Тотем и золото — индексы 0 и 1, на них опирается чип InterfaceHUD. */
    private static final Item[] CORE = { Items.TOTEM_OF_UNDYING, Items.GOLDEN_APPLE };
    /** Всё остальное: показываем только то, что реально есть в инвентаре или на исходе. */
    private static final Item[] EXTRA = {
            Items.ENCHANTED_GOLDEN_APPLE, Items.ENDER_PEARL, Items.NETHER_STAR, Items.SNOWBALL,
            Items.PRISMARINE_SHARD, Items.POPPED_CHORUS_FRUIT, Items.FIRE_CHARGE };
    public static int otherAlert = 2;

    private final List<Setting> settings = List.of(
            new SliderSetting("Totem Alert", 1, 8, 1, () -> totemAlert, v -> totemAlert = (int) v),
            new SliderSetting("Gapple Alert", 1, 16, 1, () -> gappleAlert, v -> gappleAlert = (int) v),
            new SliderSetting("Other Alert", 1, 16, 1, () -> otherAlert, v -> otherAlert = (int) v),
            new ModeSetting("List", new String[]{"CORE", "FULL"}, () -> listMode, i -> listMode = i),
            new ModeSetting("Show", new String[]{"ALWAYS", "WHEN LOW"}, () -> showMode, i -> showMode = i)
    );

    public SupplyWarn() {
        super("SupplyWarn", "Discreet low-stock warning for totems, apples and battle supplies", Category.HUD);
    }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }

    public static boolean active() {
        if (VisualsMod.moduleManager == null) return false;
        SupplyWarn m = VisualsMod.moduleManager.getModule(SupplyWarn.class);
        return m != null && m.isEnabled();
    }

    /** Старая пара «main + offHand»: в 1.21.11 вторая живёт в экипировке, а не в инвентаре. */
    private static List<ItemStack> counted(MinecraftClient mc) {
        List<ItemStack> out = new ArrayList<>(mc.player.getInventory().getMainStacks());
        out.add(mc.player.getEquippedStack(EquipmentSlot.OFFHAND));
        return out;
    }

    private static int count(Iterable<ItemStack> list, Item item) {
        int n = 0;
        for (ItemStack stack : list) {
            if (stack.isOf(item)) n += stack.getCount();
        }
        return n;
    }

    /** Зелье лечения II считается отдельно: предмет тот же, отличается только компонент. */
    private static int countStrongHealing(Iterable<ItemStack> list) {
        int n = 0;
        for (ItemStack stack : list) {
            if (!stack.isOf(Items.POTION)) continue;
            if (stack.getOrDefault(DataComponentTypes.POTION_CONTENTS, PotionContentsComponent.DEFAULT)
                    .matches(Potions.STRONG_HEALING)) n += stack.getCount();
        }
        return n;
    }

    /** Тотемы и золотые яблоки во всех слотах — циферблат для чипа InterfaceHUD. */
    public static int[] stock(MinecraftClient mc) {
        List<ItemStack> all = counted(mc);
        return new int[]{
                count(all, Items.TOTEM_OF_UNDYING),
                count(all, Items.GOLDEN_APPLE)
        };
    }

    /** Одна ячейка: чем отличаются тотемы от остального — только порогом тревоги. */
    private record Supply(ItemStack icon, int count, int alert) {
        boolean low() { return count <= alert; }
    }

    private static List<Supply> supplies(MinecraftClient mc) {
        List<ItemStack> all = counted(mc);
        List<Supply> out = new ArrayList<>();
        out.add(new Supply(new ItemStack(CORE[0]), count(all, CORE[0]), totemAlert));
        out.add(new Supply(new ItemStack(CORE[1]), count(all, CORE[1]), gappleAlert));
        if (listMode == 0) return out;
        for (Item item : EXTRA) out.add(new Supply(new ItemStack(item), count(all, item), otherAlert));
        out.add(new Supply(new ItemStack(Items.POTION), countStrongHealing(all), otherAlert));
        return out;
    }

    public void render(DrawContext ctx, int x, int y) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!active() || mc.player == null) return;

            List<Supply> all = supplies(mc);
            List<Supply> shown = new ArrayList<>();
            for (Supply s : all) {
                if (showMode == 0 || s.low() || s.count() > 0) shown.add(s);
            }
            if (shown.isEmpty()) {
                if (showMode == 1) return;
                shown = all;
            }

            int cell = 30, rowH = 24, pad = 4, cols = Math.min(5, shown.size());
            int rows = (shown.size() + cols - 1) / cols;
            int w = pad * 2 + cell * cols, h = pad * 2 + rowH * rows;
            HudStyle.drawPanelRounded(ctx, x, y, w, h, 1f, HudStyle.ACCENT, HudStyle.FillStyle.GLASS);

            for (int i = 0; i < shown.size(); i++) {
                Supply s = shown.get(i);
                cell(ctx, mc, x + pad + (i % cols) * cell, y + pad + (i / cols) * rowH, s);
            }
        } catch (Throwable t) {
            Diag.once("SupplyWarn.render", t);
        }
    }

    private static void cell(DrawContext ctx, MinecraftClient mc, int x, int y, Supply s) {
        ctx.drawItem(s.icon(), x, y - 2);
        String label = s.count() <= 0 ? "0" : String.valueOf(s.count());
        ctx.drawTextWithShadow(mc.textRenderer, label, x + 17, y + 2, s.low() ? 0xFFFF6A6A : HudStyle.TEXT);
        if (!s.low()) return;

        // Пульс только подсветкой ячейки: иконка и цифра остаются читаемыми.
        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 300.0);
        int rgb = HudStyle.lerpColor(0xF04848, 0xFFB08A, pulse * 0.8f);
        HudStyle.fillRounded(ctx, x - 2, y - 4, 26, 20, 5f, ((0x30 + (int) (0x40 * pulse)) << 24) | rgb);
    }
}

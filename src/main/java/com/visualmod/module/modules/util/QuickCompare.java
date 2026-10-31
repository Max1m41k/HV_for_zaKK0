package com.visualmod.module.modules.util;

import com.visualmod.VisualsMod;
import com.visualmod.lang.Lang;
import com.visualmod.module.Module;
import com.visualmod.util.Diag;
import com.visualmod.util.HudStyle;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Rarity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Кликнул два предмета в любом инвентаре — поверх экрана всплывает окно сравнения:
 * имя, редкость, прочность и зачарования с подсветкой различий (зелёный — преимущество, красный — отставание).
 * Третий клик начинает пару заново, выключение модуля сбрасывает.
 */
public class QuickCompare extends Module {

    private static ItemStack first;
    private static ItemStack second;

    static {
        HudRenderCallback.EVENT.register((ctx, tickDelta) -> {
            // Пока открыт инвентарь, окно рисует сам экран: HUD-проход иначе нарисовал бы его дважды.
            if (MinecraftClient.getInstance().currentScreen instanceof HandledScreen) return;
            draw(ctx);
        });
    }

    public QuickCompare() {
        super("QuickCompare", "Hold Ctrl and click two items in any inventory for a side-by-side comparison", Category.UTIL);
    }

    /** Ctrl+ЛКМ по слоту: первый клик берёт левый предмет, второй — правый, третий начинает заново. */
    public static boolean consumeClick(Screen screen, ItemStack stack, int button, boolean ctrlDown) {
        if (!ctrlDown || button != 0 || !active()) return false;
        if (!(screen instanceof HandledScreen)) return false;
        if (stack == null || stack.isEmpty()) return false;
        capture(stack);
        return true;
    }

    public static boolean active() {
        if (VisualsMod.moduleManager == null) return false;
        QuickCompare m = VisualsMod.moduleManager.getModule(QuickCompare.class);
        return m != null && m.isEnabled();
    }

    public static void capture(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        if (first == null) first = stack;
        else if (second == null) second = stack;
        else {
            first = stack;
            second = null;
        }
    }

    @Override
    public void onDisable() {
        first = null;
        second = null;
    }

    // ---- рендер ----

    private static Map<RegistryEntry<Enchantment>, Integer> enchants(ItemStack stack) {
        Map<RegistryEntry<Enchantment>, Integer> map = new HashMap<>();
        ItemEnchantmentsComponent c = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (c != null) {
            for (Object2IntMap.Entry<RegistryEntry<Enchantment>> e : c.getEnchantmentEntries()) {
                map.put(e.getKey(), e.getIntValue());
            }
        }
        return map;
    }

    private static final class Line {
        final String text;
        final int color;

        Line(String text, int color) {
            this.text = text;
            this.color = color;
        }
    }

    private static String rarityRu(Rarity r) {
        boolean ru = Lang.isRu();
        if (r == Rarity.EPIC) return ru ? "Эпический" : "Epic";
        if (r == Rarity.RARE) return ru ? "Редкий" : "Rare";
        if (r == Rarity.UNCOMMON) return ru ? "Необычный" : "Uncommon";
        return ru ? "Обычный" : "Common";
    }

    private static List<Line> column(ItemStack self, ItemStack other) {
        List<Line> lines = new ArrayList<>();
        Rarity rarity = self.getRarity();
        Integer rc = rarity.getFormatting().getColorValue();
        lines.add(new Line(rarityRu(rarity), rc == null ? 0xFFDDDDDD : 0xFF000000 | rc));
        if (self.isDamageable()) {
            int left = self.getMaxDamage() - self.getDamage();
            lines.add(new Line((Lang.isRu() ? "Прочность " : "Durability ") + left + "/" + self.getMaxDamage(), 0xFFB8C0D0));
            lines.add(new Line("\u00A7bar", 0));
        }
        Map<RegistryEntry<Enchantment>, Integer> mine = enchants(self);
        Map<RegistryEntry<Enchantment>, Integer> theirs = other == null ? Map.of() : enchants(other);
        for (Map.Entry<RegistryEntry<Enchantment>, Integer> e : mine.entrySet()) {
            int lvl = e.getValue();
            Integer otherLvl = theirs.get(e.getKey());
            int color;
            if (otherLvl == null) color = 0xFF69F0A8;          // у второго такого нет — зелёный
            else if (otherLvl > lvl) color = 0xFFF06A6A;       // у второго выше — красный
            else if (otherLvl < lvl) color = 0xFF69F0A8;
            else color = 0xFFE8ECF5;
            lines.add(new Line(Enchantment.getName(e.getKey(), lvl).getString(), color));
        }
        if (other != null) {
            for (Map.Entry<RegistryEntry<Enchantment>, Integer> e : theirs.entrySet()) {
                if (!mine.containsKey(e.getKey())) {
                    lines.add(new Line(Enchantment.getName(e.getKey(), e.getValue()).getString() + " —", 0x80F06A6A));
                }
            }
        }
        return lines;
    }

    public static void draw(DrawContext ctx) {
        try {
            if (!active() || first == null) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            TextRenderer tr = mc.textRenderer;

            List<Line> la = column(first, second);
            List<Line> lb = second == null ? null : column(second, first);
            String waiting = Lang.isRu() ? "Кликни второй предмет…" : "Click a second item…";

            int pad = 10, gap = 18, iconZone = 26;
            int colW = Math.max(tr.getWidth(first.getName().getString()) + iconZone, 96);
            for (Line l : la) colW = Math.max(colW, tr.getWidth(l.text) + 6);
            if (lb != null) {
                colW = Math.max(colW, tr.getWidth(second.getName().getString()) + iconZone);
                for (Line l : lb) colW = Math.max(colW, tr.getWidth(l.text) + 6);
            } else {
                colW = Math.max(colW, tr.getWidth(waiting) + 6);
            }
            colW = Math.min(colW, 180);

            int rows = Math.max(la.size(), lb == null ? 1 : lb.size());
            int headH = 22;
            int w = pad * 2 + colW + (lb == null ? 0 : colW + gap);
            int h = pad * 2 + headH + rows * 10 + 8;
            int px = (mc.getWindow().getScaledWidth() - w) / 2;
            int py = Math.max(4, (mc.getWindow().getScaledHeight() - h) / 3);

            HudStyle.drawPanelRounded(ctx, px, py, w, h, 1f, HudStyle.ACCENT, HudStyle.FillStyle.GLASS);

            drawColumn(ctx, tr, first, la, px + pad, py + pad, colW);
            if (second != null && lb != null) {
                ctx.fill(px + pad + colW + gap / 2 - 1, py + pad, px + pad + colW + gap / 2, py + h - pad, 0x30FFFFFF);
                drawColumn(ctx, tr, second, lb, px + pad + colW + gap, py + pad, colW);
            } else {
                ctx.drawTextWithShadow(tr, waiting, px + pad, py + pad + headH + 2, HudStyle.TEXT_DIM);
            }
        } catch (Throwable t) {
            Diag.once("QuickCompare.draw", t);
        }
    }

    private static void drawColumn(DrawContext ctx, TextRenderer tr, ItemStack stack, List<Line> lines, int x, int y, int colW) {
        ctx.drawItem(stack, x, y);
        ctx.drawStackOverlay(tr, stack, x + 1, y + 1);
        String name = tr.trimToWidth(stack.getName().getString(), colW - 24);
        ctx.drawTextWithShadow(tr, name, x + 22, y + 4, 0xFFFFFFFF);

        int ly = y + 22;
        for (Line l : lines) {
            if (l.text.equals("\u00A7bar")) {
                int left = stack.getMaxDamage() - stack.getDamage();
                float frac = stack.getMaxDamage() <= 0 ? 0f : (float) left / stack.getMaxDamage();
                int bw = colW - 8;
                HudStyle.fillRounded(ctx, x + 2, ly + 1, bw, 4, 2f, 0x60000000);
                HudStyle.fillRounded(ctx, x + 2, ly + 1, Math.max(2f, bw * frac), 4, 2f,
                        0xFF000000 | HudStyle.lerpColor(0xF04848, 0x69E07A, frac));
                ly += 10;
                continue;
            }
            ctx.drawTextWithShadow(tr, tr.trimToWidth(l.text, colW - 4), x + 2, ly, l.color);
            ly += 10;
        }
    }
}

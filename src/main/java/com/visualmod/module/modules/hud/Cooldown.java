package com.visualmod.module.modules.hud;

import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.util.HudIcons;
import com.visualmod.util.HudStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Cooldown extends Module implements Configurable {

    public static boolean showIcons = true;
    public static boolean hideEmpty = true;
    public static boolean showBar = true;
    /** Иконки на экране: вместо таблицы — кружки кулдауна вокруг иконки предмета. */
    public static boolean iconsLayout = false;
    public static int color = 0xFF7B5CF0;
    public static float maxRows = 6f;

    private static final class Entry {
        final String group;
        final long endMs;
        final long totalMs;
        Entry(String group, long endMs, long totalMs) {
            this.group = group; this.endMs = endMs; this.totalMs = totalMs;
        }
    }

    private static final Map<String, Entry> ACTIVE = new LinkedHashMap<>();

    public Cooldown() {
        super("Cooldown", "Item cooldowns assigned by the server", Category.HUD);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> list = new ArrayList<>();
        list.add(new ColorSetting("Color", () -> color, c -> color = c));
        list.add(new ModeSetting("Layout", new String[]{"Table", "Icons"}, () -> iconsLayout ? 1 : 0, i -> iconsLayout = i == 1));
        list.add(new ModeSetting("Icons", new String[]{"OFF", "ON"}, () -> showIcons ? 1 : 0, i -> showIcons = i == 1));
        list.add(new ModeSetting("Bar", new String[]{"OFF", "ON"}, () -> showBar ? 1 : 0, i -> showBar = i == 1));
        list.add(new ModeSetting("Hide When Empty", new String[]{"OFF", "ON"}, () -> hideEmpty ? 1 : 0, i -> hideEmpty = i == 1));
        list.add(new SliderSetting("Max Rows", 2f, 10f, 1f, () -> maxRows, v -> maxRows = (float) v));
        return list;
    }

    public static void onCooldownSet(String group, int ticks) {
        if (group == null || group.isEmpty() || ticks <= 0) return;
        long total = ticks * 50L;
        long now = System.currentTimeMillis();
        Entry prev = ACTIVE.get(group);
        if (prev != null && Math.abs(prev.endMs - (now + total)) < 60L) return;
        ACTIVE.put(group, new Entry(group, now + total, total));
    }

    public static void onCooldownRemoved(String group) {
        if (group != null) ACTIVE.remove(group);
    }

    private static void prune(long now) {
        Iterator<Map.Entry<String, Entry>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().endMs <= now) it.remove();
        }
    }

    public void render(DrawContext ctx, int x, int y) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.textRenderer == null) return;
        long now = System.currentTimeMillis();
        prune(now);
        if (ACTIVE.isEmpty() && hideEmpty) return;

        List<Entry> rows = new ArrayList<>(ACTIVE.values());
        rows.sort(Comparator.comparingLong(e -> e.endMs));
        int limit = Math.max(1, (int) maxRows);
        if (rows.size() > limit) rows = rows.subList(0, limit);

        if (iconsLayout) {
            renderIcons(ctx, mc, x, y, rows, now);
            return;
        }

        int rowH = showIcons ? 20 : 14;
        int w = 132;
        int h = 16 + rows.size() * (rowH + (showBar ? 5 : 0)) + 4;

        HudStyle.fillRounded(ctx, x, y, w, h, 7, 0xB212121A);
        HudIcons.draw(ctx, "clock", x + 8, y + 6, color | 0xFF000000);
        ctx.drawTextWithShadow(mc.textRenderer, "Cooldowns", x + 7 + HudIcons.STEP, y + 5, color | 0xFF000000);

        int cy = y + 16;
        for (Entry e : rows) {
            long leftMs = Math.max(0, e.endMs - now);
            float frac = e.totalMs <= 0 ? 0f : Math.min(1f, leftMs / (float) e.totalMs);
            Item item = itemOf(e.group);
            String label = item == null ? e.group : item.getName().getString();

            int tx = x + 7;
            if (showIcons) {
                if (item != null) {
                    ctx.drawItem(new ItemStack(item), tx, cy);
                } else {
                    HudStyle.fillRounded(ctx, tx, cy + 2, 14, 14, 4, 0xFF2A2A36);
                }
                tx += 18;
            }

            int textW = w - (tx - x) - 26;
            ctx.drawTextWithShadow(mc.textRenderer, mc.textRenderer.trimToWidth(label, Math.max(10, textW)),
                    tx, cy + (showIcons ? 5 : 1), 0xFFE6E6EE);

            String time = leftMs >= 1000 ? (leftMs / 1000L) + "s" : Math.max(1, leftMs / 50L) + "t";
            ctx.drawTextWithShadow(mc.textRenderer, time, x + w - 6 - mc.textRenderer.getWidth(time),
                    cy + (showIcons ? 5 : 1), color | 0xFF000000);

            if (showBar) {
                int by = cy + rowH - 1;
                HudStyle.fillRounded(ctx, x + 7, by, w - 14, 3, 1.5f, 0xFF26262F);
                if (frac > 0.01f) {
                    HudStyle.fillRounded(ctx, x + 7, by, (w - 14) * frac, 3, 1.5f, color | 0xFF000000);
                }
                cy += 5;
            }
            cy += rowH;
        }
    }

    /**
     * Режим Icons: кружок кулдауна вокруг иконки предмета (как откат заклинаний),
     * строкой ниже — сколько осталось. Отсчёт идёт от «12 часов» по часовой стрелке.
     */
    private void renderIcons(DrawContext ctx, MinecraftClient mc, int x, int y, List<Entry> rows, long now) {
        int step = 26, r = 12, cy = y + r;
        for (int i = 0; i < rows.size(); i++) {
            Entry e = rows.get(i);
            int cx = x + r + i * step;
            long leftMs = Math.max(0, e.endMs - now);
            float frac = e.totalMs <= 0 ? 0f : Math.min(1f, leftMs / (float) e.totalMs);
            Item item = itemOf(e.group);

            HudStyle.fillRounded(ctx, cx - r, cy - r, r * 2, r * 2, r, 0xB212121A);
            HudStyle.ring(ctx, cx, cy, r - 1f, r + 1f, 0f, 360f, 0x33FFFFFF);
            if (frac > 0.005f) {
                HudStyle.ring(ctx, cx, cy, r - 1f, r + 1f, 90f, -360f * frac, color | 0xFF000000);
            }

            if (item != null) {
                ctx.drawItem(new ItemStack(item), cx - 8, cy - 8);
            } else {
                String shortName = e.group.length() > 3 ? e.group.substring(e.group.lastIndexOf(':') + 1,
                        Math.min(e.group.length(), e.group.lastIndexOf(':') + 4)) : e.group;
                ctx.drawTextWithShadow(mc.textRenderer, shortName, cx - mc.textRenderer.getWidth(shortName) / 2,
                        cy - 4, 0xFFE6E6EE);
            }

            String time = leftMs >= 1000 ? (leftMs / 1000L) + "s" : Math.max(1, leftMs / 50L) + "t";
            ctx.drawTextWithShadow(mc.textRenderer, time, cx - mc.textRenderer.getWidth(time) / 2, cy + r + 1,
                    color | 0xFF000000);
        }
        HudRadius.note(x, y, Math.max(24, rows.size() * step + 2 * r - step + 4), 2 * r + 12);
    }

    private static Item itemOf(String group) {
        Identifier id = Identifier.tryParse(group);
        if (id == null) return null;
        Item item = Registries.ITEM.get(id);
        return item == null || item == net.minecraft.item.Items.AIR ? null : item;
    }

    @Override
    public void onDisable() {
        ACTIVE.clear();
    }
}

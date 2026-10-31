package com.visualmod.util;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Внутренняя система (не модуль): раскрашивает зачарования в тултипе по их функции и ставит перед
 * каждым иконку. Нестандартные (не minecraft:) зачарования получают значок HW и оранжево-синий градиент.
 */
public final class EnchantGlow {
    private EnchantGlow() {}

    private static final String ICON_BLOOD = "☠";
    private static final String ICON_FIRE = "✦";
    private static final String ICON_WATER = "◆";
    private static final String ICON_SHIELD = "❖";
    private static final String ICON_STAR = "★";
    private static final String ICON_BLADE = "⚔";

    public static Consumer<Text> wrap(Consumer<Text> out,
                                      Iterable<Object2IntMap.Entry<RegistryEntry<Enchantment>>> entries) {
        return line -> {
            try {
                String plain = line.getString();
                for (var e : entries) {
                    if (!Enchantment.getName(e.getKey(), e.getIntValue()).getString().equals(plain)) continue;
                    out.accept(styled(e.getKey(), plain));
                    return;
                }
            } catch (Throwable t) {
                Diag.once("EnchantGlow", t);
            }
            out.accept(line);
        };
    }

    private static Text styled(RegistryEntry<Enchantment> entry, String label) {
        String id = entry.getIdAsString() == null ? "" : entry.getIdAsString().toLowerCase(Locale.ROOT);
        boolean vanilla = id.startsWith("minecraft:");

        MutableText out = Text.empty();
        if (!vanilla) {
            out.append(Text.literal("HW ").formatted(Formatting.GOLD, Formatting.BOLD));
            // Оранжево-синий градиент по словам — нестандартный обход должен читаться как наш.
            int i = 0;
            for (String word : label.split("(?<= )")) {
                out.append(Text.literal(word).formatted((i++ & 1) == 0 ? Formatting.GOLD : Formatting.DARK_AQUA));
            }
            return out;
        }

        Palette p = palette(id, label.toLowerCase(Locale.ROOT));
        out.append(Text.literal(p.icon + " ").formatted(p.color));
        out.append(p.bold ? Text.literal(label).formatted(p.color, Formatting.BOLD)
                          : Text.literal(label).formatted(p.color));
        return out;
    }

    private static Palette palette(String id, String lower) {
        if (hit(id, lower, "sharpness", "smite", "bane", "impaling", "density", "artifact_of_motion"))
            return new Palette(ICON_BLOOD, Formatting.DARK_RED, true);
        if (hit(id, lower, "fire_protection", "flame", "fire_aspect", "burning", "magma"))
            return new Palette(ICON_FIRE, Formatting.GOLD, true);
        if (hit(id, lower, "aqua_affinity", "water", "depth", "respiration", "riptide", "channeling", "frost"))
            return new Palette(ICON_WATER, Formatting.DARK_BLUE, true);
        if (hit(id, lower, "protection", "thorns", "feather", "falling", "projectile", "blast"))
            return new Palette(ICON_SHIELD, Formatting.GREEN, false);
        if (hit(id, lower, "unbreaking", "mending"))
            return new Palette(ICON_STAR, Formatting.AQUA, false);
        if (hit(id, lower, "power", "punch", "infinity", "luck", "lure", "efficiency", "fortune", "silk", "looting", "sweeping"))
            return new Palette(ICON_BLADE, Formatting.LIGHT_PURPLE, false);
        return new Palette(ICON_STAR, Formatting.YELLOW, false);
    }

    private static boolean hit(String id, String lower, String... needles) {
        for (String n : needles) if (id.contains(n) || lower.contains(n)) return true;
        return false;
    }

    private record Palette(String icon, Formatting color, boolean bold) {}
}

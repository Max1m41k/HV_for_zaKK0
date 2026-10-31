package com.visualmod.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

/**
 * Встроенная подсветка своего ника в TAB-листе: ряд получает мягкую зелёную плашку и заметный
 * зелёный скоб слева, сам ник красится в тот же цвет. Модуля нет — фича работает всегда, как
 * пинг цифрами.
 */
public final class TabSelf {
    public static final int GREEN = 0x5BE38A;

    private static final int PLATE = 0x3A2ECC71;
    private static final int EDGE = 0xB05BE38A;
    private static final int NAME_W = 9;

    private TabSelf() {}

    /** Ряд, чью иконку сейчас рисуют: getSkinTextures вызывается прямо перед draw. */
    private static PlayerListEntry pending;

    public static void mark(PlayerListEntry entry) {
        pending = entry;
    }

    public static boolean isSelf(PlayerListEntry entry) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || entry == null) return false;
        GameProfile profile = entry.getProfile();
        if (profile == null) return false;
        if (profile.id() != null && profile.id().equals(mc.player.getUuid())) return true;
        String nick = profile.name();
        if (nick == null) return false;
        String session = mc.getSession() == null ? null : mc.getSession().getUsername();
        return nick.equalsIgnoreCase(mc.player.getName().getString())
                || (session != null && nick.equalsIgnoreCase(session));
    }

    /**
     * Плашку рисуем до скина и ника — тогда текст ложится поверх и остаётся читаемым.
     * Координаты те, что ваниль передаёт в PlayerSkinDrawer.draw: x левого края скина, y его верх.
     */
    public static void plate(DrawContext ctx, int skinX, int skinY) {
        try {
            PlayerListEntry entry = pending;
            pending = null;
            if (ctx == null || entry == null || !isSelf(entry)) return;
            int w = NAME_W + nameWidth(entry) + 6;
            int h = 12;
            int y = skinY - 2;
            HudStyle.fillRounded(ctx, skinX - 3f, y, w, h, 3f, PLATE);
            HudStyle.fillRounded(ctx, skinX - 3f, y, 2f, h, 1f, EDGE);
        } catch (Throwable t) {
            Diag.once("TabSelf.plate", t);
        }
    }

    /** Ник своего ряда в зелёный; плашка без перекраски читалась бы плохо. */
    public static Text green(Text name) {
        if (name == null) return null;
        try {
            TextColor color = TextColor.fromRgb(GREEN);
            MutableText out = Text.empty().setStyle(name.getStyle().withColor(color).withBold(true));
            if (name.getSiblings().isEmpty()) {
                out.append(name.copy());
                return out;
            }
            for (Text sibling : name.getSiblings()) {
                out.append(sibling.copy().setStyle(sibling.getStyle().withColor(color)));
            }
            return out;
        } catch (Throwable t) {
            Diag.once("TabSelf.green", t);
            return name;
        }
    }

    private static int nameWidth(PlayerListEntry entry) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.textRenderer == null) return 40;
        Text name = entry.getDisplayName() != null
                ? entry.getDisplayName()
                : Text.literal(entry.getProfile().name());
        return mc.textRenderer.getWidth(name);
    }
}

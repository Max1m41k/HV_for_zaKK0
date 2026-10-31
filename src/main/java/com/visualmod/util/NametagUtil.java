package com.visualmod.util;

import com.visualmod.module.modules.cosmetics.NametagStyle;
import com.visualmod.module.modules.player.Friends;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public final class NametagUtil {
    private NametagUtil() {}

    public static Text decorate(Text name, String playerName, int place) {
        try {
            NametagStyle m = NametagStyle.module();
            if (m == null || name == null || !m.shows(place) || !matches(playerName)) return name;
            Text mark = m.mark();
            if (mark == null) return name;
            if (m.after()) return Text.empty().setStyle(name.getStyle()).append(name.copy()).append(spacedMark(mark, true));
            return Text.empty().setStyle(name.getStyle()).append(spacedMark(mark, false)).append(name.copy());
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("NametagUtil.decorate", t);
            return name;
        }
    }

    public static Text decorateLine(Text message, int place) {
        try {
            NametagStyle m = NametagStyle.module();
            if (m == null || message == null || !m.shows(place)) return message;
            String line = message.getString();
            String lead = line.stripLeading();
            if (lead.isEmpty()) return message;
            Hit hit = startedByName(lead);
            if (hit == null || !matches(hit.nick())) return message;
            Text mark = m.mark();
            if (mark == null) return message;
            int start = line.length() - lead.length();
            int end = start + hit.end();
            if (m.after()) return insertAt(message, end, spacedMark(mark, true));
            return Text.empty().setStyle(message.getStyle()).append(spacedMark(mark, false)).append(message.copy());
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("NametagUtil.line", t);
            return message;
        }
    }

    private static boolean matches(String playerName) {
        if (playerName == null) return false;
        return switch (NametagStyle.target) {
            case 0 -> playerName.equalsIgnoreCase(NameProtectUtil.realName());
            case 1 -> Friends.isFriendName(playerName);
            default -> true;
        };
    }

    private record Hit(String nick, int end) {}

    private static Hit startedByName(String lead) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return null;
        Hit best = null;
        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            if (entry.getProfile() == null) continue;
            String n = entry.getProfile().name();
            if (n == null || n.isEmpty()) continue;
            for (String form : forms(n)) {
                if (!lead.startsWith(form)) continue;
                int end = form.length();
                if (best == null || end > best.end()) best = new Hit(n, end);
            }
        }
        return best;
    }

    private static String[] forms(String n) {
        return new String[] { n, "<" + n + ">", "[" + n + "]", "* " + n, "\u2022 " + n };
    }

    private static Text spacedMark(Text mark, boolean after) {
        return after ? Text.literal(" ").append(mark.copy()) : mark.copy().append(" ");
    }

    private static Text insertAt(Text message, int offset, Text mark) {
        MutableText out = Text.empty().setStyle(message.getStyle());
        int consumed = 0;
        boolean done = false;
        for (Text leaf : leaves(message)) {
            String s = leaf.getString();
            int len = s.length();
            if (!done && offset >= consumed && offset <= consumed + len) {
                int cut = offset - consumed;
                if (cut == 0) {
                    out.append(mark);
                    out.append(leaf.copy());
                } else if (cut == len) {
                    out.append(leaf.copy());
                    out.append(mark);
                } else {
                    out.append(slice(leaf, 0, cut));
                    out.append(mark);
                    out.append(slice(leaf, cut, len));
                }
                done = true;
            } else {
                out.append(leaf.copy());
            }
            consumed += len;
        }
        if (!done) out.append(mark);
        return out;
    }

    private static Text slice(Text leaf, int from, int to) {
        return Text.literal(leaf.getString().substring(from, to)).setStyle(leaf.getStyle());
    }

    private static java.util.List<Text> leaves(Text t) {
        java.util.List<Text> out = new java.util.ArrayList<>();
        collect(t, out);
        return out;
    }

    private static void collect(Text t, java.util.List<Text> out) {
        if (t.getSiblings().isEmpty()) { out.add(t); return; }
        for (Text c : t.getSiblings()) collect(c, out);
    }
}

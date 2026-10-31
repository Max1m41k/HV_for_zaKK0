package com.visualmod.module.modules.hud;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class ChatRules extends Module implements Configurable {

    public static boolean timestamp = true;
    public static boolean mention = true;
    public static int mentionColor = 0xFFFFD24D;
    public static boolean mentionBar = true;
    public static boolean antiSpam = true;
    public static double window = 6.0;

    private static final Deque<Entry> RECENT = new ArrayDeque<>();
    private static final class Entry {
        final String text;
        final long at;
        final int repeats;
        Entry(String text, long at, int repeats) { this.text = text; this.at = at; this.repeats = repeats; }
    }

    private static String cachedNick = "";
    private static long nickStamp = 0;

    public ChatRules() {
        super("ChatRules", "Timestamps, mention highlight and local anti-spam in chat", Category.HUD);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Timestamp", new String[]{"OFF", "ON"}, () -> timestamp ? 1 : 0, i -> timestamp = i == 1));
        l.add(new ModeSetting("Mention", new String[]{"OFF", "ON"}, () -> mention ? 1 : 0, i -> mention = i == 1));
        l.add(new ColorSetting("Mention Color", () -> mentionColor, c -> mentionColor = c));
        l.add(new ModeSetting("Mention Bar", new String[]{"OFF", "ON"}, () -> mentionBar ? 1 : 0, i -> mentionBar = i == 1));
        l.add(new ModeSetting("Anti-spam", new String[]{"OFF", "ON"}, () -> antiSpam ? 1 : 0, i -> antiSpam = i == 1));
        l.add(new SliderSetting("Window", 1, 30, 1, () -> window, v -> window = v, "s"));
        return l;
    }

    public static boolean active() {
        if (VisualsMod.moduleManager == null) return false;
        ChatRules m = VisualsMod.moduleManager.getModule(ChatRules.class);
        return m != null && m.isEnabled();
    }

    public static Text stamp(Text message) {
        if (!active() || !timestamp) return message;
        // ChatTools делает метку с выбором формата (HH:MM / HH:MM:SS) — две метки в одной строке не нужны.
        if (com.visualmod.module.modules.util.ChatTools.willStampTime()) return message;
        String t = new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date());
        Text label = Text.empty().append("[" + t + "] ").formatted(net.minecraft.util.Formatting.DARK_GRAY);
        net.minecraft.text.MutableText out = Text.empty();
        out.append(label);
        out.append(message);
        return out;
    }

    public static String key(String raw) {
        if (raw == null) return "";
        return raw.replaceFirst("^\\[\\d{2}:\\d{2}] ", "");
    }

    public static boolean isSpam(String raw) {
        if (!active() || !antiSpam || raw == null || raw.isBlank()) return false;
        // Группировка ChatTools показывает повторы как «×N» — молча вырезать сообщение тогда уже не надо.
        if (com.visualmod.module.modules.util.ChatTools.groupsDuplicates()) return false;
        raw = key(raw);
        long now = System.currentTimeMillis();
        long win = (long) (window * 1000);
        while (!RECENT.isEmpty() && now - RECENT.peekFirst().at > win) RECENT.pollFirst();

        Entry last = RECENT.peekLast();
        if (last != null && now - last.at <= win && last.text.equals(raw)) {
            RECENT.pollLast();
            RECENT.addLast(new Entry(last.text, now, last.repeats + 1));
            return true;
        }
        RECENT.addLast(new Entry(raw, now, 0));
        if (RECENT.size() > 64) RECENT.pollFirst();
        return false;
    }

    public static boolean mentions(String raw) {
        if (!active() || !mention || raw == null) return false;
        String nick = nick();
        return nick.length() > 1 && raw.toLowerCase().contains(nick.toLowerCase());
    }

    public static int recolor(String raw, int original) {
        return mentions(raw) ? (mentionColor & 0xFFFFFF) : original;
    }

    public static int barColor(String raw) {
        if (!mentions(raw) || !mentionBar) return 0;
        int a = 0x55;
        return (a << 24) | (mentionColor & 0xFFFFFF);
    }

    private static String nick() {
        long now = System.currentTimeMillis();
        if (now - nickStamp > 5000) {
            MinecraftClient mc = MinecraftClient.getInstance();
            cachedNick = mc != null && mc.player != null ? mc.player.getName().getString() : "";
            nickStamp = now;
        }
        return cachedNick;
    }
}

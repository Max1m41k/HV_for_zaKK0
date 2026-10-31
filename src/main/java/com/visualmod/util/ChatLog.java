package com.visualmod.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.visualmod.lang.Lang;
import com.visualmod.module.modules.util.ChatTools;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Журнал чата для ChatTools: история, закрепление, группировка повторов, метки времени
 * и кликабельные координаты. Всё живёт на клиентском потоке, поэтому без синхронизации.
 */
public final class ChatLog {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int MAX_HISTORY = 400;
    private static final long GROUP_WINDOW_MS = 15_000L;

    private static final Pattern COORDS = Pattern.compile(
            "(-?\\d{1,5}(?:\\.\\d+)?)\\s+(-?\\d{1,3}(?:\\.\\d+)?)\\s+(-?\\d{1,5}(?:\\.\\d+)?)");
    private static final Pattern TIME_PREFIX = Pattern.compile("^(?:§8\\d{1,2}:\\d{2}(?::\\d{2})?§r |\\[\\d{1,2}:\\d{2}] )");
    private static final Pattern COUNT_SUFFIX = Pattern.compile(" §8×\\d+$");
    private static final Pattern SENDER_ANGLE = Pattern.compile("^<([^>]{1,32})>");
    private static final Pattern SENDER_PREFIXED = Pattern.compile("^(?:\\[[^\\]]{1,24}]\\s*)?([^<>:§]{1,24}):\\s");

    private static final List<Entry> HISTORY = new ArrayList<>();
    private static final Set<String> PINS = new LinkedHashSet<>();

    private static String lastBase = "";
    private static int lastCount = 0;
    private static long lastAt = 0L;
    private static boolean pinsLoaded = false;

    private ChatLog() {}

    public static final class Entry {
        public final String text;
        public final long timeMs;
        public final String sender;
        public int count;

        Entry(String text, long timeMs, String sender, int count) {
            this.text = text;
            this.timeMs = timeMs;
            this.sender = sender;
            this.count = count;
        }

        public boolean pinned() { return PINS.contains(text); }
    }

    /** Текст без наших украшений: по нему сравниваются повторы и хранится история. */
    public static String canonical(String raw) {
        if (raw == null) return "";
        String s = TIME_PREFIX.matcher(raw).replaceFirst("");
        s = COUNT_SUFFIX.matcher(s).replaceFirst("");
        return s.trim();
    }

    /**
     * Единственная точка, куда чат попадает перед отрисовкой: считает повтор, кладёт строку
     * в историю и возвращает Text с меткой времени, счётчиком и кликабельными координатами.
     */
    public static Text decorate(Text message) {
        try {
            String raw = message.getString();
            String base = canonical(raw);
            if (base.isEmpty()) return message;

            int count = 1;
            if (ChatTools.grouping) count = repeat(base);
            record(base, count);

            boolean time = ChatTools.timestamps > 0;
            boolean coords = ChatTools.coordCopy && COORDS.matcher(base).find();
            if (!time && !coords && count < 2) return message;

            MutableText out = Text.empty();
            if (time) out.append(Text.literal("§8" + clock(System.currentTimeMillis(), ChatTools.timestamps) + "§r "));
            out.append(coords ? withCoords(message) : message);
            if (count > 1) out.append(Text.literal(" §8×" + count));
            return out;
        } catch (Throwable t) {
            Diag.once("ChatLog.decorate", t);
            return message;
        }
    }

    private static int repeat(String base) {
        long now = System.currentTimeMillis();
        if (base.equals(lastBase) && now - lastAt < GROUP_WINDOW_MS) {
            lastCount++;
        } else {
            lastBase = base;
            lastCount = 1;
        }
        lastAt = now;
        return lastCount;
    }

    private static void record(String base, int count) {
        if (count > 1 && !HISTORY.isEmpty() && HISTORY.get(0).text.equals(base)) {
            HISTORY.get(0).count = count;
            return;
        }
        HISTORY.add(0, new Entry(base, System.currentTimeMillis(), senderOf(base), count));
        while (HISTORY.size() > MAX_HISTORY) HISTORY.remove(HISTORY.size() - 1);
    }

    public static String senderOf(String text) {
        Matcher m = SENDER_ANGLE.matcher(text);
        if (m.find()) return m.group(1);
        m = SENDER_PREFIXED.matcher(text);
        if (m.find()) {
            String name = m.group(1).trim();
            if (!name.isEmpty() && !name.contains(" ")) return name;
        }
        return null;
    }

    public static String clock(long timeMs, int mode) {
        SimpleDateFormat f = new SimpleDateFormat(mode == 2 ? "HH:mm:ss" : "HH:mm", Locale.ROOT);
        return f.format(new Date(timeMs));
    }

    /** Координаты из строки: первое совпадение «x y z» или null. */
    public static String firstCoords(String text) {
        if (text == null) return null;
        Matcher m = COORDS.matcher(text);
        return m.find() ? m.group().trim() : null;
    }

    private static Text withCoords(Text src) {
        MutableText out = Text.empty();
        src.visit((style, part) -> {
            appendCoords(out, style, part);
            return Optional.<Void>empty();
        }, Style.EMPTY);
        return out;
    }

    private static void appendCoords(MutableText out, Style style, String part) {
        if (part == null || part.isEmpty()) return;
        Matcher m = COORDS.matcher(part);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) out.append(Text.literal(part.substring(last, m.start())).setStyle(style));
            String value = (m.group(1) + " " + m.group(2) + " " + m.group(3));
            String hint = Lang.isRu() ? "§eКлик: §7скопировать координаты" : "§eClick: §7copy coordinates";
            out.append(Text.literal(m.group()).setStyle(style
                    .withUnderline(true)
                    .withColor(style.getColor() == null ? 0xFFB9E4 : style.getColor().getRgb())
                    .withClickEvent(new ClickEvent.CopyToClipboard(value))
                    .withHoverEvent(new HoverEvent.ShowText(Text.of(hint)))));
            last = m.end();
        }
        if (last < part.length()) out.append(Text.literal(part.substring(last)).setStyle(style));
    }

    // --- закрепление ---

    public static boolean togglePin(String text) {
        loadPins();
        String base = canonical(text);
        boolean nowPinned;
        if (PINS.remove(base)) nowPinned = false;
        else { PINS.add(base); nowPinned = true; }
        savePins();
        return nowPinned;
    }

    public static boolean isPinned(String text) {
        loadPins();
        return PINS.contains(canonical(text));
    }

    public static List<String> pins() {
        loadPins();
        return new ArrayList<>(PINS);
    }

    // --- поиск ---

    /** filter: 0 — всё, 1 — только игроки, 2 — только системные, 3 — закреплённые. */
    public static List<Entry> query(String needle, int filter) {
        loadPins();
        String q = needle == null ? "" : needle.trim().toLowerCase(Locale.ROOT);
        String bySender = null;
        if (q.startsWith("@")) {
            int sp = q.indexOf(' ');
            bySender = (sp < 0 ? q.substring(1) : q.substring(1, sp));
            q = sp < 0 ? "" : q.substring(sp + 1).trim();
        }
        List<Entry> out = new ArrayList<>();
        for (Entry e : HISTORY) {
            if (filter == 1 && e.sender == null) continue;
            if (filter == 2 && e.sender != null) continue;
            if (filter == 3 && !PINS.contains(e.text)) continue;
            if (bySender != null && (e.sender == null || !e.sender.toLowerCase(Locale.ROOT).contains(bySender))) continue;
            if (!q.isEmpty() && !e.text.toLowerCase(Locale.ROOT).contains(q)) continue;
            out.add(e);
        }
        return out;
    }

    public static int size() { return HISTORY.size(); }

    // --- файл закреплённых ---

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("visualmod_chat_pins.json"); }

    private static void loadPins() {
        if (pinsLoaded) return;
        pinsLoaded = true;
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            JsonObject o = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), JsonObject.class);
            if (o == null || !o.has("pins")) return;
            PINS.clear();
            for (var el : (JsonArray) o.get("pins")) PINS.add(el.getAsString());
        } catch (Throwable t) {
            Diag.once("ChatLog.loadPins", t);
        }
    }

    private static void savePins() {
        try {
            JsonArray arr = new JsonArray();
            for (String p : PINS) arr.add(p);
            JsonObject o = new JsonObject();
            o.add("pins", arr);
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(o), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            Diag.once("ChatLog.savePins", t);
        }
    }
}

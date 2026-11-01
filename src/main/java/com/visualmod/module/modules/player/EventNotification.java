package com.visualmod.module.modules.player;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.visualmod.VisualsMod;
import com.visualmod.events.HolyEvents;
import com.visualmod.module.Module;
import com.visualmod.module.modules.hud.HudPosition;
import com.visualmod.module.modules.hud.HudRadius;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.screen.EventTableScreen;
import com.visualmod.util.HudStyle;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EventNotification extends Module implements Configurable {

    public static int display = 0;
    public static boolean popups = true, sound = true, chat = true, status = true, demo = false;
    public static int anarchy = 0;
    public static int minRarity = 0;

    private static final String NAME = "EventNotification";
    private static final int BACK = 0xE60C0F16;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<String, String> KNOWN = new LinkedHashMap<>();
    private static final Set<String> DISABLED = new HashSet<>();
    private static boolean typesLoaded = false;

    private static final class Popup { final String text; final long until; Popup(String t, long u) { text = t; until = u; } }
    private static final List<Popup> POPUPS = new ArrayList<>();
    private static Set<String> known = null;
    private static volatile List<HolyEvents.Ev> shown = List.of();

    private static volatile int bx, by, bw, bh;
    private static volatile boolean boundsValid = false;

    static { HudRenderCallback.EVENT.register(EventNotification::draw); }

    public EventNotification() { super("EventNotification", "HolyWorld events from the official API", Category.PLAYER); }

    @Override
    public List<Setting> getSettings() {
        loadTypes();
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Display", new String[]{"COMPACT", "LIST"}, () -> display, i -> display = i));
        l.add(new ModeSetting("Popups", new String[]{"OFF", "ON"}, () -> popups ? 1 : 0, i -> popups = (i == 1)));
        l.add(new ModeSetting("Anarchy", new String[]{"ALL", "SOLO", "DUO", "TRIO", "CLAN", "LITE 1.20"}, () -> anarchy, i -> anarchy = i));
        l.add(new ModeSetting("Min Rarity", new String[]{"ALL", "RARE+", "EPIC+", "LEGENDARY"}, () -> minRarity, i -> minRarity = i));
        l.add(new ModeSetting("Sound", new String[]{"OFF", "ON"}, () -> sound ? 1 : 0, i -> sound = (i == 1)));
        l.add(new ModeSetting("Chat Message", new String[]{"OFF", "ON"}, () -> chat ? 1 : 0, i -> chat = (i == 1)));
        l.add(new ModeSetting("Status Line", new String[]{"OFF", "ON"}, () -> status ? 1 : 0, i -> status = (i == 1)));
        l.add(new ModeSetting("Demo (for placing HUD)", new String[]{"OFF", "ON"}, () -> demo ? 1 : 0, i -> demo = (i == 1)));
        synchronized (KNOWN) {
            for (Map.Entry<String, String> e : KNOWN.entrySet()) {
                final String id = e.getKey();
                l.add(new ModeSetting(e.getValue(), new String[]{"OFF", "ON"}, () -> DISABLED.contains(id) ? 0 : 1, i -> setType(id, i == 1)));
            }
        }
        return l;
    }

    @Override public void onEnable() { loadTypes(); known = null; HolyEvents.start(EventNotification::onUpdate); }
    @Override public void onDisable() { HolyEvents.stop(); synchronized (POPUPS) { POPUPS.clear(); } known = null; shown = List.of(); boundsValid = false; }

    private static Path typesFile() { return FabricLoader.getInstance().getConfigDir().resolve("visualmod_events.json"); }

    private static synchronized void loadTypes() {
        if (typesLoaded) return;
        typesLoaded = true;
        synchronized (KNOWN) {
            KNOWN.put("CUBE", "Кубик"); KNOWN.put("MINE", "Смертельная шахта");
            try {
                Path f = typesFile();
                if (Files.exists(f)) {
                    JsonObject o = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), JsonObject.class);
                    if (o.has("known")) for (var e : o.getAsJsonObject("known").entrySet()) KNOWN.put(e.getKey(), e.getValue().getAsString());
                    if (o.has("disabled")) for (var e : (JsonArray) o.get("disabled")) DISABLED.add(e.getAsString());
                }
            } catch (Exception ex) { System.err.println("[VisualsMod] EventNotification: не удалось прочитать типы ивентов: " + ex); }
        }
    }

    private static void saveTypes() {
        try {
            JsonObject o = new JsonObject(), k = new JsonObject(); JsonArray d = new JsonArray();
            synchronized (KNOWN) { KNOWN.forEach(k::addProperty); }
            DISABLED.forEach(d::add);
            o.add("known", k); o.add("disabled", d);
            Files.createDirectories(typesFile().getParent());
            Files.writeString(typesFile(), GSON.toJson(o), StandardCharsets.UTF_8);
        } catch (Exception ignored) { }
    }

    private static void setType(String id, boolean on) { if (on) DISABLED.remove(id); else DISABLED.add(id); saveTypes(); }

    public static int rank(String rare) {
        String s = rare == null ? "" : rare.toLowerCase(Locale.ROOT);
        if (s.contains("мифич")) return 4; if (s.contains("легенд")) return 3; if (s.contains("эпич")) return 2; if (s.contains("редк")) return 1;
        return 0;
    }
    private static String rareColor(int rank) { return switch (rank) { case 4 -> "§c"; case 3 -> "§6"; case 2 -> "§d"; case 1 -> "§9"; default -> "§7"; }; }

    public static String rareTag(HolyEvents.Ev e) { int r = rank(e.rare()); return r == 0 ? "" : rareColor(r) + "[" + e.rare() + "]"; }

    public static String typeLabel(HolyEvents.Ev e) { return e.displayName(); }

    public static String prettyServer(String name) {
        if (name == null || name.isBlank()) return "?";
        String n = HolyEvents.clean(name);
        // Сырые id из API, если /servers не ответил или сервер новый.
        Matcher m = Pattern.compile("(?iu)^LITE_NEW_ANARCHY_?(\\d+)$").matcher(n);
        if (m.find()) return "Лайт (1.20) #" + m.group(1);
        m = Pattern.compile("(?iu)^LITE_ANARCHY_?(\\d+)$").matcher(n);
        if (m.find()) return "Лайт #" + m.group(1);
        m = Pattern.compile("(?iu)^ANARCHY_?(\\d+)$").matcher(n);
        if (m.find()) return "Анархия #" + m.group(1);
        // Человеческие имена API: «СолоЛайт #1», «ДуоЛайт #17», «Лайт (1.20) #2».
        m = Pattern.compile("(?iu)лайт\\s*\\(\\s*1[.,]20\\s*\\)\\s*#?\\s*(\\d+)").matcher(n);
        if (m.find()) return "Лайт (1.20) #" + m.group(1);
        m = Pattern.compile("(?iu)(соло|дуо|трио|клан)\\s*лайт\\s*#?\\s*(\\d+)").matcher(n);
        if (m.find()) return cap(m.group(1)) + "Лайт #" + m.group(2);
        m = Pattern.compile("(?iu)(?:лайт|lite|light)\\s*#?\\s*(\\d+)").matcher(n);
        if (m.find()) return "Лайт #" + m.group(1);
        m = Pattern.compile("(?iu)(?:анархия|anarchy)\\s*#?\\s*(\\d+)").matcher(n);
        if (m.find()) return "Анархия #" + m.group(1);
        return n;
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    /** Босса нельзя терять из-за фильтра редкости: у него rare обычно «default». */
    public static boolean isBoss(HolyEvents.Ev e) {
        if (e == null) return false;
        String id = e.id() == null ? "" : e.id().toUpperCase(Locale.ROOT);
        if (id.contains("BOSS")) return true;
        String n = e.displayName() == null ? "" : e.displayName().toLowerCase(Locale.ROOT);
        return n.contains("босс") || n.contains("boss");
    }

    public static int serverNumber(String name) { Matcher m = Pattern.compile("(\\d+)").matcher(name == null ? "" : name); return m.find() ? Integer.parseInt(m.group(1)) : 0; }

    public static String ageText(HolyEvents.Ev e) {
        long s = Math.max(0, (System.currentTimeMillis() - e.firstSeen()) / 1000), h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        String t = h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%d:%02d", m, sec);
        return "идёт " + (e.observed() ? "" : "≥") + t;
    }

    /**
     * Тип сервера: 0 — другой, 1 соло, 2 дуо, 3 трио, 4 клан, 5 лайт 1.20.
     * Считаем по serverId, а имя — запасной вариант: /servers иногда не отвечает,
     * и тогда serverName приходит сырым «LITE_ANARCHY_17».
     */
    static int serverKind(HolyEvents.Ev e) {
        String id = e.serverId() == null ? "" : e.serverId().toUpperCase(Locale.ROOT);
        if (id.startsWith("LITE_NEW_ANARCHY")) return 5;
        String n = e.serverName() == null ? "" : e.serverName().toLowerCase(Locale.ROOT);
        if (n.contains("1.20")) return 5;
        if (n.startsWith("соло")) return 1;
        if (n.startsWith("дуо")) return 2;
        if (n.startsWith("трио")) return 3;
        if (n.startsWith("клан")) return 4;
        if (id.startsWith("LITE_ANARCHY")) {
            int num = serverNumber(id);
            return num <= 9 ? 1 : num <= 19 ? 2 : num <= 29 ? 3 : 4;
        }
        return 0;
    }

    static boolean anarchyOk(HolyEvents.Ev e) {
        return anarchy == 0 || serverKind(e) == anarchy;
    }

    static boolean passes(HolyEvents.Ev e) {
        return !DISABLED.contains(e.id())
                && (isBoss(e) || rank(e.rare()) >= minRarity)
                && anarchyOk(e);
    }

    public static boolean isOver(double mx, double my) {
        if (!boundsValid || VisualsMod.moduleManager == null) return false;
        EventNotification m = VisualsMod.moduleManager.getModule(EventNotification.class);
        return m != null && m.isEnabled() && mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
    }
    public static List<HolyEvents.Ev> filteredEvents() { return shown; }
    public static void openTable() { MinecraftClient mc = MinecraftClient.getInstance(); mc.setScreen(new EventTableScreen(mc.currentScreen)); }

    private static void onUpdate(List<HolyEvents.Ev> list) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean newType = false;
        synchronized (KNOWN) { for (HolyEvents.Ev e : list) if (!KNOWN.containsKey(e.id())) { KNOWN.put(e.id(), e.displayName()); newType = true; } }
        if (newType) saveTypes();

        List<HolyEvents.Ev> filtered = new ArrayList<>();
        for (HolyEvents.Ev e : list) if (passes(e)) filtered.add(e);
        shown = filtered;
        try {
            com.visualmod.util.EventGPS.onEventsUpdated(filtered);
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("EventGPS", t);
        }

        Set<String> now = new HashSet<>();
        for (HolyEvents.Ev e : list) now.add(e.key());
        if (known != null) {
            for (HolyEvents.Ev e : filtered) {
                if (known.contains(e.key())) continue;
                String r = rareTag(e);
                String text = "Новый ивент: " + e.displayName() + (r.isEmpty() ? "" : " " + r + "§e") + " — " + prettyServer(e.serverName());
                mc.execute(() -> notifyNew(mc, text));
            }
        }
        known = now;
    }

    private static void notifyNew(MinecraftClient mc, String text) {
        if (popups) synchronized (POPUPS) { POPUPS.add(new Popup(text, System.currentTimeMillis() + 9000)); }
        if (mc.player != null && chat) mc.player.sendMessage(Text.literal("§d[Ивент] §f" + text), false);
        if (sound && mc.getSoundManager() != null) mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f));
    }

    private static void draw(DrawContext ctx, RenderTickCounter tc) {
        try {
            if (VisualsMod.moduleManager == null) return;
            EventNotification m = VisualsMod.moduleManager.getModule(EventNotification.class);
            MinecraftClient mc = MinecraftClient.getInstance();
            if (m == null || !m.isEnabled() || mc.player == null || mc.options.hudHidden) return;

            HudPosition.getAll().putIfAbsent(NAME, new int[]{10, 110});
            int[] g = HudPosition.glide(NAME, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
            int x = g[0], y = g[1];
            long now = System.currentTimeMillis();

            // Список строится всегда: COMPACT — короткая строка без редкости и не больше шести,
            // LIST — все ивенты с тегом редкости. Раньше при display == 0 строк не было вовсе.
            List<String> rows = new ArrayList<>();
            int limit = display == 0 ? 6 : Integer.MAX_VALUE;
            for (HolyEvents.Ev e : shown) {
                if (rows.size() >= limit) break;
                StringBuilder sb = new StringBuilder();
                sb.append(isBoss(e) ? "§cБОСС §8· §d" : "§d").append(e.displayName());
                sb.append(" §7· §f").append(prettyServer(e.serverName()));
                if (display == 1 && rank(e.rare()) > 0) sb.append(' ').append(rareTag(e));
                sb.append(" §8· §a").append(ageText(e));
                rows.add(sb.toString());
            }
            if (shown.size() > rows.size()) rows.add("§8… и ещё " + (shown.size() - rows.size()));
            List<String> pops = new ArrayList<>();
            synchronized (POPUPS) { POPUPS.removeIf(p -> p.until < now); for (Popup p : POPUPS) pops.add("§e" + p.text); }
            if (demo && shown.isEmpty()) { rows.add("§dКубик §7· §fДуоЛайт #17 §6[Легендарный] §8· §aидёт 3:21"); rows.add("§dСмертельная шахта §7· §fТриоЛайт #20 §8· §aидёт 0:45"); }

            boundsValid = false;
            int iy = y;
            int bwMax = 0;
            int lastPad = 0;
            if (status) {
                long ok = HolyEvents.lastOk(); String err = HolyEvents.lastError();
                int n = shown.size();
                int bosses = 0;
                for (HolyEvents.Ev e : shown) if (isBoss(e)) bosses++;
                String time = ok == 0 ? "" : new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(ok));
                boolean inChat = mc.currentScreen instanceof ChatScreen;
                String line; int bar;
                if (err != null) { line = "§cAPI недоступен: " + (err.length() > 34 ? err.substring(0, 34) : err); bar = 0xFFE05555; }
                else if (ok == 0) { line = "§7подключение к API…"; bar = 0xFF8A8A9E; }
                else if (n > 0) { line = "§a● Ивентов: " + n + (bosses > 0 ? " §8· §cбоссов: " + bosses : "") + " §7· обновлено " + time + (inChat ? " §8· §aклик — таблица" : ""); bar = 0xFF3BE06A; }
                else { line = "§7нет активных ивентов §8· §7обновлено " + time; bar = 0xFF5A5A6E; }
                int sw = Math.max(150, mc.textRenderer.getWidth(line) + 20);
                panel(ctx, x, iy, sw, 18);
                ctx.fill(x + 3, iy + 3, x + 5, iy + 15, bar);
                ctx.drawTextWithShadow(mc.textRenderer, line, x + 10, iy + 5, 0xFFFFFFFF);
                bwMax = sw;
                iy += 22;
            }

            int w = 120;
            for (String s : rows) w = Math.max(w, mc.textRenderer.getWidth(s) + 18);
            for (String s : pops) w = Math.max(w, mc.textRenderer.getWidth(s) + 18);
            if (!rows.isEmpty()) {
                int rowH = display == 0 ? 10 : 12;
                int h = 18 + rows.size() * rowH;
                panel(ctx, x, iy, w, h);
                HudStyle.drawTitle(ctx, x + 9, iy + 5, "Ивенты · " + rows.size());
                ctx.fill(x + 9, iy + 16, x + w - 9, iy + 17, 0x30FFFFFF);
                for (int i = 0; i < rows.size(); i++) ctx.drawTextWithShadow(mc.textRenderer, rows.get(i), x + 9, iy + 18 + i * rowH, 0xFFFFFFFF);
                bwMax = Math.max(bwMax, w);
                iy += h + 4;
                lastPad = 4;
            }
            for (String s : pops) {
                panel(ctx, x, iy, w, 18);
                ctx.drawTextWithShadow(mc.textRenderer, s, x + 9, iy + 5, 0xFFFFFFFF);
                bwMax = Math.max(bwMax, w);
                iy += 22;
                lastPad = 4;
            }
            if (bwMax > 0) {
                bx = x; by = y; bw = bwMax; bh = iy - lastPad - y; boundsValid = true;
            }
        } catch (Throwable t) { com.visualmod.util.Diag.once("EventNotification", t); }
    }

    private static void panel(DrawContext ctx, int x, int y, int w, int h) {
        float r = HudRadius.active();
        HudStyle.fillRounded(ctx, x, y, w, h, r, BACK);
        HudStyle.drawPanel(ctx, x, y, w, h, 1f, r);
    }
}

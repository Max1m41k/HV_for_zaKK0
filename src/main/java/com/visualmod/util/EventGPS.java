package com.visualmod.util;

import com.visualmod.util.McCompat;
import com.visualmod.events.HolyEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Внутренняя система: EventNotification видит ивент на нашей анархии — EventGPS отправляет /event,
 * вытаскивает координаты из ответа чата и ставит метку (см. WaypointManager).
 */
public final class EventGPS {
    private EventGPS() {}

    private static final class Pending {
        final String key, name; final boolean boss; final long until;
        Pending(String key, String name, boolean boss, long until) { this.key = key; this.name = name; this.boss = boss; this.until = until; }
    }

    private static final Pattern LABELED_X = Pattern.compile("(?i)\\bx\\D{0,4}(-?\\d{1,7})");
    private static final Pattern LABELED_Y = Pattern.compile("(?i)\\by\\D{0,4}(-?\\d{1,5})");
    private static final Pattern LABELED_Z = Pattern.compile("(?i)\\bz\\D{0,4}(-?\\d{1,7})");
    private static final Pattern TRIPLE = Pattern.compile("(-?\\d{1,7})[\\s,;]+(-?\\d{1,5})[\\s,;]+(-?\\d{1,7})");

    private static final List<Pending> QUEUE = new ArrayList<>();
    private static final Set<String> MARKED = new HashSet<>();
    private static Pending waiting;

    /** Анти-спам: не больше 2-х /event за окно 4 минуты (риск кика за флуд в PvP). */
    private static final int MAX_SENDS_PER_WINDOW = 2;
    private static final long WINDOW_MS = 240_000L;
    private static long windowStart = 0;
    private static int sendsInWindow = 0;

    public static void onEventsUpdated(List<HolyEvents.Ev> live) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        Set<String> liveKeys = new HashSet<>();
        for (HolyEvents.Ev e : live) liveKeys.add(e.key());
        WaypointManager.retainEvents(liveKeys);
        MARKED.removeIf(k -> !liveKeys.contains(k));

        for (HolyEvents.Ev e : live) {
            String key = e.key();
            if (MARKED.contains(key) || WaypointManager.hasEvent(key)) continue;
            if (!sameServer(e)) continue;
            if (queuedOrWaiting(key)) continue;
            QUEUE.add(new Pending(key, e.displayName(), isBoss(e.displayName()),
                    System.currentTimeMillis() + 8000));
            System.out.println("[VisualsMod] EventGPS: ивент «" + e.displayName() + "» на нашей анархии (" + e.serverName() + ") — запрашиваю /event");
        }
        pump(mc);
    }

    public static void tick(MinecraftClient mc) {
        WaypointManager.tick(mc);
        if (mc.player == null) { QUEUE.clear(); waiting = null; return; }
        if (waiting != null && System.currentTimeMillis() > waiting.until) {
            System.out.println("[VisualsMod] EventGPS: «/event» не ответил координатами для «" + waiting.name + "»");
            capture("--- таймаут: /event не вернул координат для «" + waiting.name + "» ---");
            waiting = null;
        }
        pump(mc);
    }

    private static void pump(MinecraftClient mc) {
        if (waiting != null || QUEUE.isEmpty() || mc.player == null || mc.world == null) return;
        long now = System.currentTimeMillis();
        // Окно 4 минуты: максимум 2 отправки /event, дальше ждём переноса окна — не флудим сервер.
        if (windowStart == 0 || now - windowStart >= WINDOW_MS) { windowStart = now; sendsInWindow = 0; }
        if (sendsInWindow >= MAX_SENDS_PER_WINDOW) return;
        if (onSpawn(mc)) { QUEUE.clear(); return; }

        // Сначала обслуживаем боссов — они быстро появляются и исчезают.
        int idx = 0;
        for (int i = 0; i < QUEUE.size(); i++) if (QUEUE.get(i).boss) { idx = i; break; }
        Pending p = QUEUE.remove(idx);
        waiting = p;
        sendsInWindow++;
        try {
            mc.player.networkHandler.sendChatCommand("event");
        } catch (Exception e) {
            Diag.once("EventGPS.query", e);
            waiting = null;
        }
    }

    public static void onChatLine(String plain) {
        Pending p = waiting;
        if (p == null) return;
        String s = plain == null ? "" : plain.trim();
        if (s.isEmpty() || s.startsWith("/")) return;
        capture(s);

        int[] c = parseCoords(s);
        if (c == null) return;

        // Сервер в ответе /event называет АКТИВНЫЙ ивент. Ставим метку на него, а не на тот,
        // что стоит в очереди, иначе координаты одного вешаются на имя другого (корабль -> «Кубик»).
        HolyEvents.Ev ev = matchLive(s);
        if (ev == null) return;

        boolean boss = isBoss(ev.displayName());
        // Одна метка-ивент за раз: при переходе на другую анархию старая метка стирается.
        WaypointManager.clearEvents();
        WaypointManager.addEvent(ev.key(), ev.displayName(), c[0], c[1], c[2], !boss);
        MARKED.add(ev.key());
        waiting = null;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.literal(
                "§d[TriadVisual] §aGPS: §e" + ev.displayName() + " §7→ " + c[0] + " " + c[1] + " " + c[2]
                        + (boss ? " §8(босс: только подпись, без луча)" : "")), false);
    }

    /** Живой ивент из EventNotification, чьё имя встречается в строке ответа /event. */
    private static HolyEvents.Ev matchLive(String s) {
        String low = norm(s);
        HolyEvents.Ev best = null;
        for (HolyEvents.Ev e : HolyEvents.get()) {
            String dn = norm(e.displayName());
            if (dn.isEmpty() || !low.contains(dn)) continue;
            if (best == null || dn.length() > norm(best.displayName()).length()) best = e;
        }
        return best;
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private static int[] parseCoords(String s) {
        Integer x = first(LABELED_X, s), y = first(LABELED_Y, s), z = first(LABELED_Z, s);
        if (x != null && y != null && z != null && Math.abs(y) <= 512) return new int[]{x, y, z};

        Matcher t = TRIPLE.matcher(s);
        while (t.find()) {
            int a = parseIntSafe(t.group(1));
            int b = parseIntSafe(t.group(2));
            int c = parseIntSafe(t.group(3));
            if (Math.abs(b) <= 512) return new int[]{a, b, c};
        }
        return null;
    }

    private static Integer first(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? parseIntSafe(m.group(1)) : null;
    }

    /** Пока ждём ответ на /event, пишем каждую строку чата в лог — по нему подстраиваем regex. */
    private static void capture(String line) {
        try {
            java.nio.file.Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Path f = dir.resolve("visualmod_eventgps_log.txt");
            String entry = java.time.LocalTime.now() + "  " + line + System.lineSeparator();
            if (java.nio.file.Files.exists(f) && java.nio.file.Files.size(f) > 64 * 1024) java.nio.file.Files.delete(f);
            java.nio.file.Files.write(f, entry.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) { }
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    private static boolean queuedOrWaiting(String key) {
        if (waiting != null && waiting.key.equals(key)) return true;
        for (Pending p : QUEUE) if (p.key.equals(key)) return true;
        return false;
    }

    private static boolean isBoss(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return n.contains("босс") || n.contains("boss");
    }

    /** На спавне координаты ивента бессмысленны — не спамим сервер командой. */
    private static boolean onSpawn(MinecraftClient mc) {
        try {
            BlockPos spawn = mc.world.getSpawnPoint().getPos();
            Vec3d pos = McCompat.pos(mc.player);
            return Math.hypot(pos.x - spawn.getX(), pos.z - spawn.getZ()) < 45;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean sameServer(HolyEvents.Ev e) {
        String addr = serverAddress();
        if (addr == null) return false;
        String host = addr.toLowerCase(Locale.ROOT).replaceFirst(":[0-9]+$", "");
        // На прокси mc.holyworld.ru нельзя однозначно определить бэкенд по хосту.
        // Разрешаем всем ивентам HW: сервер сам скажет в /event, если ивент не наш.
        if (isHolyHost(host)) return true;
        String sid = e.serverId() == null ? "" : e.serverId().toLowerCase(Locale.ROOT);
        if (sid.isEmpty()) return false;
        return host.equals(sid) || host.startsWith(sid + ".") || host.contains("." + sid) || sid.contains(host);
    }

    private static boolean isHolyHost(String host) {
        return host.endsWith("holyworld.ru") || host.endsWith("holyworld.me");
    }

    /** Подключение идёт к HolyWorld? Нужен LiteApi: на чужих серверах контроль функций не применяется. */
    public static boolean onHolyWorld() {
        String addr = serverAddress();
        if (addr != null && isHolyHost(addr.toLowerCase(Locale.ROOT).replaceFirst(":[0-9]+$", ""))) return true;
        // Вход по IP или зеркалу: адрес на HW не похож, но бренд сервера называет себя сам.
        return isHolyBrand(serverBrand());
    }

    /** Бренд из пакета `minecraft:brand`: «HolyWorld (Proxy 12-1)», «fabric», «... <- NanoLimbo». */
    public static String serverBrand() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            return mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getBrand();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Прокси HolyWorld отдают бренд «HolyWоrld», где «о» — кириллическая U+043E, и на других
     * прокси она латинская. Поэтому сравниваем по нормализованному виду, а не по исходной строке.
     */
    private static boolean isHolyBrand(String brand) {
        if (brand == null) return false;
        String s = brand.toLowerCase(Locale.ROOT).replaceAll("§.", "");
        StringBuilder norm = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            int hom = HOMO_CYR.indexOf(s.charAt(i));
            norm.append(hom >= 0 ? HOMO_LAT.charAt(hom) : s.charAt(i));
        }
        return norm.indexOf("holyworld") >= 0;
    }

    private static final String HOMO_CYR = "аеорсхукмнтвіј";
    private static final String HOMO_LAT = "aeopcxykmhtbij";
    private static String digits(String s) {
        if (s == null) return "";
        Matcher m = Pattern.compile("(\\d+)").matcher(s);
        String best = "";
        while (m.find()) if (m.group(1).length() > best.length()) best = m.group(1);
        return best;
    }

    public static String serverAddress() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.getNetworkHandler() == null || mc.getNetworkHandler().getServerInfo() == null) return null;
            String a = mc.getNetworkHandler().getServerInfo().address;
            return a == null || a.isBlank() ? null : a;
        } catch (Exception e) {
            return null;
        }
    }
}

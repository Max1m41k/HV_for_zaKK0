package com.visualmod.events;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class HolyEvents {
    private HolyEvents() {}

    public record Ev(String id, String displayName, String rare, String serverId, String serverName, String instanceId, long firstSeen, boolean observed) {
        public String key() { return instanceId != null && !instanceId.isEmpty() ? instanceId : id + "@" + serverId; }
    }

    private static final String BASE = "https://api.holyworld.me/v1/";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private static final Map<String, String> SERVERS = new ConcurrentHashMap<>();
    private static final Map<String, Long> FIRST_SEEN = new ConcurrentHashMap<>();
    private static volatile List<Ev> current = List.of();
    private static volatile String lastRaw = "";
    private static volatile long serversAt = 0, lastOkAt = 0;
    private static volatile String lastError = null;
    private static boolean firstPollDone = false;
    private static long firstPollAt = Long.MAX_VALUE;
    private static ScheduledExecutorService ex;

    public static List<Ev> get() { return current; }
    public static long lastOk() { return lastOkAt; }
    public static String lastError() { return lastError; }

    public static synchronized void start(Consumer<List<Ev>> onUpdate) {
        if (ex != null) return;
        firstPollDone = false; firstPollAt = Long.MAX_VALUE;
        ex = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "holy-events"); t.setDaemon(true); return t; });
        ex.scheduleWithFixedDelay(() -> {
            try { poll(onUpdate); lastError = null; lastOkAt = System.currentTimeMillis(); }
            catch (Exception e) { lastError = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : ""); }
        }, 0, 15, TimeUnit.SECONDS);
    }

    public static synchronized void stop() {
        if (ex != null) { ex.shutdownNow(); ex = null; }
        current = List.of();
    }

    private static String http(String path) throws Exception {
        HttpRequest rq = HttpRequest.newBuilder(URI.create(BASE + path)).timeout(Duration.ofSeconds(8)).GET().build();
        HttpResponse<String> rs = HTTP.send(rq, HttpResponse.BodyHandlers.ofString());
        if (rs.statusCode() != 200) throw new IllegalStateException("HTTP " + rs.statusCode());
        return rs.body();
    }

    public static String clean(String s) { return s == null ? "" : s.replaceAll("(?i)[§&][0-9a-fk-or]", "").trim(); }

    private static String str(JsonObject o, String k, String def) {
        return o != null && o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : def;
    }

    private static void poll(Consumer<List<Ev>> cb) throws Exception {
        long now = System.currentTimeMillis();
        if (SERVERS.isEmpty() || now - serversAt > 600_000) {
            JsonObject o = JsonParser.parseString(http("servers")).getAsJsonObject();
            for (var e : o.entrySet()) if (e.getValue().isJsonPrimitive()) SERVERS.put(e.getKey(), clean(e.getValue().getAsString()));
            serversAt = now;
        }
        String raw = http("events");
        String t = raw.trim();
        if (!t.equals(lastRaw) && !t.equals("{}") && !t.equals("[]") && !t.isEmpty()) {
            try {
                Files.createDirectories(FabricLoader.getInstance().getConfigDir());
                Files.writeString(FabricLoader.getInstance().getConfigDir().resolve("visualmod_events_raw.json"), raw, StandardCharsets.UTF_8);
            } catch (Exception ignored) { }
        }
        lastRaw = t;

        List<Ev> list = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
        for (var en : root.entrySet()) {
            String sid = en.getKey();
            if (!en.getValue().isJsonArray()) continue;
            for (JsonElement el : (JsonArray) en.getValue()) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                JsonObject md = o.has("metadata") && o.get("metadata").isJsonObject() ? o.getAsJsonObject("metadata") : null;
                String id = str(o, "id", "?");
                String inst = str(o, "instanceId", id + "@" + sid);
                long first = FIRST_SEEN.computeIfAbsent(inst, k -> now);
                list.add(new Ev(id, clean(str(md, "displayName", id)), clean(str(md, "rare", "default")), sid,
                        SERVERS.getOrDefault(sid, clean(sid)), inst, first, firstPollDone && first >= firstPollAt));
                keys.add(inst);
            }
        }
        FIRST_SEEN.keySet().retainAll(keys);
        if (!firstPollDone) firstPollAt = System.currentTimeMillis();
        firstPollDone = true;
        current = list;
        cb.accept(list);
    }

}

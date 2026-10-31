package com.visualmod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class HolyBadge {
    private static final String BASE = com.visualmod.util.Endpoints.AUTH;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private static final Set<String> USERS = ConcurrentHashMap.newKeySet();
    private static final int ORANGE = 0xFF7B00;
    private static String hwid;
    private static boolean started = false;

    public static void start() {
        if (started) return;
        started = true;
        ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "holy-badge"); t.setDaemon(true); return t; });
        ex.scheduleWithFixedDelay(() -> { try { fetchList(); } catch (Exception ignored) {} }, 20, 60, TimeUnit.SECONDS);
        ex.scheduleWithFixedDelay(() -> { try { ping(); } catch (Exception ignored) {} }, 30, 600, TimeUnit.SECONDS);
    }

    private static String hwid() { if (hwid == null) hwid = HWIDManager.getHWID(); return hwid; }

    private static void ping() throws Exception {
        String key = LockScreen.savedKey();
        if (key == null) return;
        JsonObject chk = new JsonObject();
        chk.addProperty("key", key); chk.addProperty("hwid", hwid());
        JsonObject r = post("/api/check", chk);
        if (r != null && r.has("valid") && !r.get("valid").getAsBoolean()) { LockScreen.dropLicense(); return; }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getSession() == null) return;
        JsonObject p = new JsonObject();
        p.addProperty("key", key); p.addProperty("hwid", hwid()); p.addProperty("nick", mc.getSession().getUsername());
        post("/api/presence", p);
    }

    private static void fetchList() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + "/api/presence")).timeout(Duration.ofSeconds(8)).GET().build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        JsonObject o = JsonParser.parseString(resp.body()).getAsJsonObject();
        if (!o.has("users")) return;
        JsonArray arr = o.getAsJsonArray("users");
        USERS.clear();
        for (JsonElement e : arr) USERS.add(e.getAsString().toLowerCase(Locale.ROOT));
    }

    private static JsonObject post(String path, JsonObject body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + path)).timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }

    public static boolean hasMod(String nick) {
        if (nick == null) return false;
        String low = nick.toLowerCase(Locale.ROOT);
        if (USERS.contains(low)) return true;
        // Активированный через AltManager альт показывает треугольник сразу,
        // не дожидаясь пока /api/presence обновит серверный список.
        if (!com.visualmod.screen.AccountManager.isLicensed(nick)) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc != null && mc.getSession() != null
                && low.equals(mc.getSession().getUsername().toLowerCase(Locale.ROOT));
    }

    /** Немедленно отправляет presence-пинг — используется после переключения на licensed-альт. */
    public static void pingNow() {
        new Thread(() -> { try { ping(); } catch (Exception ignored) {} }, "holy-badge-ping").start();
    }

    public static Text withBadge(Text original) { return withBadge(original, false); }

    public static Text withBadge(Text original, boolean friend) {
        MutableText tri = Text.literal("\u25B2 ").setStyle(Style.EMPTY.withColor(friend ? 0x3BE06A : ORANGE));
        return tri.append(original);
    }

    private HolyBadge() {}
}

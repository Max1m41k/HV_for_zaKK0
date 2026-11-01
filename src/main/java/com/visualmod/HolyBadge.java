package com.visualmod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

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
    private static String hwid;
    private static boolean started = false;

    public static void start() {
        if (started) return;
        started = true;
        // HWID считается через PowerShell: греем в фоне, чтобы значок не дёргал его в кадре.
        new Thread(() -> { try { hwid(); } catch (Throwable ignored) {} }, "holy-badge-hwid").start();
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
        if (!isOwnNick(low)) return false;
        // Свой ник: знак привязан к HWID, а не к аккаунту — ключ на этой машине есть,
        // значит он есть под любым ником. Ветка AccountManager — для альтов из AltManager.
        return LockScreen.hwidLicensed() || com.visualmod.screen.AccountManager.isLicensed(nick);
    }

    private static boolean isOwnNick(String low) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return false;
        if (mc.player != null && low.equals(mc.player.getName().getString().toLowerCase(Locale.ROOT))) return true;
        return mc.getSession() != null && low.equals(mc.getSession().getUsername().toLowerCase(Locale.ROOT));
    }

    /** Немедленно отправляет presence-пинг — используется после переключения на licensed-альт. */
    public static void pingNow() {
        new Thread(() -> { try { ping(); } catch (Exception ignored) {} }, "holy-badge-ping").start();
    }

    public static final Identifier BADGE_FONT_ID = Identifier.of("visualmod", "hw_badge");
    private static final StyleSpriteSource.Font BADGE_FONT = new StyleSpriteSource.Font(BADGE_FONT_ID);
    private static final String BADGE_GLYPH = "\uE000";

    /** Текст со шрифтом значка — такой узел перекрашивать нельзя, иначе куб станет одноцветным. */
    public static boolean isBadgeStyle(Style style) {
        return style != null && style.getFont() instanceof StyleSpriteSource.Font f && BADGE_FONT_ID.equals(f.id());
    }

    public static Text withBadge(Text original) { return withBadge(original, false); }

    public static Text withBadge(Text original, boolean friend) {
        MutableText glyph = Text.literal(BADGE_GLYPH)
                .setStyle(Style.EMPTY.withFont(BADGE_FONT).withColor(friend ? 0x3BE06A : 0xFFFFFF));
        // Пробел — дефолтным шрифтом: в hw_badge есть только сам глиф.
        return Text.empty().append(glyph).append(Text.literal(" ")).append(original);
    }

    private HolyBadge() {}
}

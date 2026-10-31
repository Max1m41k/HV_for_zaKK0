package com.visualmod.module.modules.player;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.visualmod.HWIDManager;
import com.visualmod.LockScreen;
import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.screen.FriendsScreen;
import com.visualmod.util.Endpoints;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class Friends extends Module implements Configurable {

    public static boolean noHit = true;
    public static int tag = 0;

    private static final Set<String> FRIENDS = ConcurrentHashMap.newKeySet();
    public static volatile List<String> friendList = new ArrayList<>(), incoming = new ArrayList<>(), outgoing = new ArrayList<>();
    public static volatile String lastMessage = "";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private static ScheduledExecutorService ex;
    private static long lastNotice = 0;

    public Friends() { super("Friends", "Mutual friends: no hit, [Teammate] tag, green triangle", Category.PLAYER); }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Open Manager", new String[]{"OFF", "ON"}, () -> 0, i -> {
            if (i == 1) { MinecraftClient mc = MinecraftClient.getInstance(); mc.execute(() -> mc.setScreen(new FriendsScreen(mc.currentScreen))); }
        }));
        l.add(new ModeSetting("No Hit Friends", new String[]{"OFF", "ON"}, () -> noHit ? 1 : 0, i -> noHit = (i == 1)));
        l.add(new ModeSetting("Tag", new String[]{"TEAMMATE", "FRIEND", "NONE"}, () -> tag, i -> tag = i));
        return l;
    }

    @Override
    public synchronized void onEnable() {
        if (ex != null) return;
        ex = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "holy-friends"); t.setDaemon(true); return t; });
        ex.scheduleWithFixedDelay(() -> { try { call("list", null, null); } catch (Exception ignored) { } }, 2, 60, TimeUnit.SECONDS);
    }

    @Override
    public synchronized void onDisable() { if (ex != null) { ex.shutdownNow(); ex = null; } FRIENDS.clear(); }

    private static boolean active() {
        if (VisualsMod.moduleManager == null) return false;
        Friends m = VisualsMod.moduleManager.getModule(Friends.class);
        return m != null && m.isEnabled();
    }

    public static boolean isFriendName(String nick) { return nick != null && active() && FRIENDS.contains(nick.toLowerCase(Locale.ROOT)); }

    public static boolean isFriend(Entity e) { return e instanceof PlayerEntity p && isFriendName(p.getGameProfile().name()); }

    public static boolean blocksAttack(Entity target) {
        if (!noHit || !isFriend(target)) return false;
        long now = System.currentTimeMillis();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (now - lastNotice > 1500 && mc.inGameHud != null) {
            lastNotice = now;
            mc.inGameHud.setOverlayMessage(Text.literal("§aНе бью друга: " + ((PlayerEntity) target).getGameProfile().name()), false);
        }
        return true;
    }

    public static String tagText() { return tag == 0 ? "Teammate" : tag == 1 ? "Friend" : null; }

    public static boolean available() { return LockScreen.savedKey() != null; }

    public static void call(String action, String nick, Consumer<String> done) throws Exception {
        String key = LockScreen.savedKey();
        if (key == null) { lastMessage = "Друзья доступны только с лицензионным ключом."; if (done != null) post(done, lastMessage); return; }
        JsonObject b = new JsonObject();
        b.addProperty("key", key); b.addProperty("hwid", HWIDManager.getHWID()); b.addProperty("action", action);
        if (nick != null) { b.addProperty("nick", nick); b.addProperty("query", nick); }
        HttpRequest rq = HttpRequest.newBuilder(URI.create(Endpoints.AUTH + "/api/friends/mod")).timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json; charset=UTF-8").POST(HttpRequest.BodyPublishers.ofString(b.toString())).build();
        HttpResponse<String> rs = HTTP.send(rq, HttpResponse.BodyHandlers.ofString());
        JsonObject o = JsonParser.parseString(rs.body()).getAsJsonObject();
        if (!o.has("ok") || !o.get("ok").getAsBoolean()) { lastMessage = o.has("message") ? o.get("message").getAsString() : "Ошибка"; if (done != null) post(done, lastMessage); return; }
        friendList = toList(o.get("friends")); incoming = toList(o.get("incoming")); outgoing = toList(o.get("outgoing"));
        FRIENDS.clear();
        for (String f : friendList) FRIENDS.add(f.toLowerCase(Locale.ROOT));
        lastMessage = o.has("message") ? o.get("message").getAsString() : "";
        if (done != null) post(done, lastMessage);
    }

    public static void act(String action, String nick, Consumer<String> done) {
        Thread t = new Thread(() -> { try { call(action, nick, done); } catch (Exception e) { post(done, "Нет связи с сервером"); } }, "holy-friends-act");
        t.setDaemon(true); t.start();
    }

    private static void post(Consumer<String> done, String msg) { if (done != null) MinecraftClient.getInstance().execute(() -> done.accept(msg)); }

    private static List<String> toList(JsonElement e) {
        if (e == null || !e.isJsonArray()) return new ArrayList<>();
        List<String> out = new ArrayList<>();
        for (JsonElement x : (JsonArray) e) out.add(x.getAsString());
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }
}

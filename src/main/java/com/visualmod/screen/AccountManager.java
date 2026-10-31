package com.visualmod.screen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AccountManager {
    public static List<String> accounts = new ArrayList<>();

    /** Ники, на которые активирован лицензионный ключ, привязанный к этому HWID. */
    private static final Set<String> licensedNicks = new LinkedHashSet<>();

    /** ник, на котором мы играли последовательно через AltManager — нужно вернуть сессию к нему при запуске. */
    private static String lastNick = null;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Файл резолвим лениво: MinecraftClient.getInstance() на static-init может быть null при загрузке класса. */
    private static File saveFile() {
        return new File(MinecraftClient.getInstance().runDirectory, "holy_accounts.json");
    }

    public static void loadAccounts() {
        File f = saveFile();
        if (!f.exists()) return;
        try {
            // GSON.fromJson(reader, Object.class) возвращает LinkedTreeMap, а не JsonObject —
            // поэтому парсим через JsonParser и работаем с JsonElement напрямую.
            JsonElement parsed = JsonParser.parseString(Files.readString(f.toPath(), StandardCharsets.UTF_8));
            if (parsed.isJsonArray()) {
                // старый формат: просто список ников
                List<String> loaded = GSON.fromJson(parsed, new TypeToken<List<String>>(){}.getType());
                if (loaded != null) accounts = loaded;
                return;
            }
            if (!parsed.isJsonObject()) return;
            JsonObject o = parsed.getAsJsonObject();
            if (o.has("accounts")) {
                List<String> loaded = GSON.fromJson(o.get("accounts"), new TypeToken<List<String>>(){}.getType());
                if (loaded != null) accounts = loaded;
            }
            licensedNicks.clear();
            if (o.has("licensed")) {
                List<String> lic = GSON.fromJson(o.get("licensed"), new TypeToken<List<String>>(){}.getType());
                if (lic != null) licensedNicks.addAll(lic);
            }
            if (o.has("lastNick") && !o.get("lastNick").isJsonNull()) lastNick = o.get("lastNick").getAsString();
        } catch (Exception e) {
            com.visualmod.util.Diag.once("AccountManager.load", e);
        }
    }

    public static void saveAccounts() {
        File f = saveFile();
        try (Writer w = Files.newBufferedWriter(f.toPath(), StandardCharsets.UTF_8)) {
            JsonObject o = new JsonObject();
            JsonArray arr = new JsonArray();
            for (String s : accounts) arr.add(s);
            o.add("accounts", arr);
            JsonArray lic = new JsonArray();
            for (String s : licensedNicks) lic.add(s);
            o.add("licensed", lic);
            if (lastNick != null) o.addProperty("lastNick", lastNick);
            GSON.toJson(o, w);
        } catch (IOException e) {
            com.visualmod.util.Diag.once("AccountManager.save", e);
        }
    }

    public static boolean isLicensed(String nick) {
        return nick != null && licensedNicks.contains(nick.toLowerCase());
    }

    public static void markLicensed(String nick) {
        if (nick == null || nick.isEmpty()) return;
        licensedNicks.add(nick.toLowerCase());
        saveAccounts();
    }

    public static void markUnlicensed(String nick) {
        if (nick == null) return;
        if (licensedNicks.remove(nick.toLowerCase())) saveAccounts();
    }

    public static String lastNick() { return lastNick; }

    public static void setLastNick(String nick) {
        lastNick = nick;
        saveAccounts();
    }
}

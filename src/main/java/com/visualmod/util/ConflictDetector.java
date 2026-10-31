package com.visualmod.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConflictDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger("VisualsMod");

    public static void checkAndProtect() {
        try {
            List<String> detected = findConflicts();
            if (!detected.isEmpty()) showNativeDialogAndExit(detected);
        } catch (Throwable t) {
            LOGGER.warn("[VisualsMod] Проверка конфликтов пропущена: {}", t.toString());
        }
    }

    static List<String> findConflicts() {
        Set<String> extra = new HashSet<>();
        Set<String> allowed = new HashSet<>();
        loadUserConfig(extra, allowed);

        Path self = ownLocation();
        List<String> result = new ArrayList<>();

        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            String id = mod.getMetadata().getId();
            if (isOwn(mod, self)) continue;
            if (ConflictRules.isConflict(id, mod.getMetadata().getName(), extra, allowed)) {
                result.add(mod.getMetadata().getName() + " (id: " + id + ")");
            }
        }
        return result;
    }

    private static Path ownLocation() {
        try {
            return Path.of(ConflictDetector.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isOwn(ModContainer mod, Path self) {
        if (ConflictRules.OWN_IDS.contains(ConflictRules.normalize(mod.getMetadata().getId()))) return true;
        if (self == null) return false;
        try {
            for (Path p : mod.getOrigin().getPaths()) {
                if (p.toAbsolutePath().normalize().equals(self)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static void loadUserConfig(Set<String> block, Set<String> allow) {
        try {
            Path f = FabricLoader.getInstance().getConfigDir().resolve("visualmod_conflicts.json");
            if (!Files.exists(f)) return;
            JsonObject o = new Gson().fromJson(Files.readString(f, StandardCharsets.UTF_8), JsonObject.class);
            read(o, "block", block);
            read(o, "allow", allow);
        } catch (Exception e) {
            LOGGER.warn("[VisualsMod] Не удалось прочитать visualmod_conflicts.json: {}", e.toString());
        }
    }

    private static void read(JsonObject o, String key, Set<String> into) {
        if (o == null || !o.has(key) || !o.get(key).isJsonArray()) return;
        JsonArray a = o.getAsJsonArray(key);
        for (JsonElement e : a) into.add(ConflictRules.normalize(e.getAsString()));
    }

    private static void showNativeDialogAndExit(List<String> detected) {
        StringBuilder sb = new StringBuilder();
        sb.append("Обнаружен сторонний визуальный клиент!\n\n");
        sb.append("Удалите из папки mods:\n");
        for (String mod : detected) sb.append(" - ").append(mod).append("\n");
        sb.append("\nЕсли это ошибка, добавьте id мода в \"allow\" файла config/visualmod_conflicts.json");

        LOGGER.error("[VisualsMod] ОБНАРУЖЕН КОНФЛИКТ: {}", detected);

        TinyFileDialogs.tinyfd_messageBox("Защита VisualsMod", sb.toString().replace('"', '\''), "ok", "error", true);

        System.exit(1);
    }
}

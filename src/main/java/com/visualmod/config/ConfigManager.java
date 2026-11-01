package com.visualmod.config;

import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.KeySetting;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.module.settings.ToggleSetting;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.visualmod.module.Module;
import com.visualmod.module.ModuleManager;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_DIR = new File(MinecraftClient.getInstance().runDirectory, "visualmod_configs");
    private static final File LAST_CONFIG_FILE = new File(CONFIG_DIR, "last_config.txt");

    public static String currentConfigName = "default";

    public static final String GENERATOR = "holy-visuals";
    public static final int CONFIG_VERSION = 1;

    public static String lastError = null;

    public static int lastBetaSkipped = 0;

    public static Map<String, String> keybinds = new HashMap<>();
    public static Map<String, String> macros = new HashMap<>();

    public static ModuleManager moduleManager;

    public static void init(ModuleManager manager) {
        moduleManager = manager;
        if (!CONFIG_DIR.exists()) {
            CONFIG_DIR.mkdirs();
        }
        loadLastConfigName();
        loadConfig(currentConfigName);
    }

    public static boolean saveConfig(String name) {
        if (moduleManager == null) return false;
        name = normalizeName(name);
        currentConfigName = name;
        File configFile = new File(CONFIG_DIR, name + ".json");

        JsonObject configJson = new JsonObject();

        configJson.addProperty("generator", GENERATOR);
        configJson.addProperty("configVersion", CONFIG_VERSION);

        JsonObject modulesJson = new JsonObject();
        for (Module m : moduleManager.getModules()) {
            // Пишем намерение, а не факт: сокрытие по блок-листу выключает модуль, но настройку
            // пользователя оно отменять не должно, иначе автосейв затирает конфиг «всем выключено».
            modulesJson.addProperty(m.getName().toLowerCase(), m.getDesired());
        }
        configJson.add("modules", modulesJson);

        JsonObject settingsJson = new JsonObject();
        for (Module m : moduleManager.getModules()) {
            if (!(m instanceof Configurable c)) continue;
            JsonObject o = new JsonObject();
            for (Setting st : c.getSettings()) {
                if (st instanceof SliderSetting sl) o.addProperty(st.getName(), sl.getValue());
                else if (st instanceof ModeSetting ms) o.addProperty(st.getName(), ms.getIndex());
                else if (st instanceof ToggleSetting tg) o.addProperty(st.getName(), tg.isEnabled());
                else if (st instanceof ColorSetting cs) o.addProperty(st.getName(), cs.getColor());
                else if (st instanceof KeySetting ks) o.add(st.getName(), ks.getKey() == null
                        ? com.google.gson.JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(ks.getKey()));
            }
            if (o.size() > 0) settingsJson.add(m.getName().toLowerCase(), o);
        }
        configJson.add("settings", settingsJson);

        JsonObject bindsJson = new JsonObject();
        keybinds.forEach(bindsJson::addProperty);
        configJson.add("binds", bindsJson);

        JsonObject macrosJson = new JsonObject();
        macros.forEach(macrosJson::addProperty);
        configJson.add("macros", macrosJson);

        JsonObject cosmeticsJson = new JsonObject();
        cosmeticsJson.addProperty("cape", com.visualmod.module.modules.cosmetics.CosmeticsManager.equippedCape);
        cosmeticsJson.addProperty("hat", com.visualmod.module.modules.cosmetics.CosmeticsManager.equippedHat);
        cosmeticsJson.addProperty("armor", com.visualmod.module.modules.cosmetics.CosmeticsManager.equippedArmor);
        cosmeticsJson.addProperty("face", com.visualmod.module.modules.cosmetics.CosmeticsManager.equippedFace);
        cosmeticsJson.addProperty("gloves", com.visualmod.module.modules.cosmetics.CosmeticsManager.equippedGloves);
        cosmeticsJson.addProperty("horns", com.visualmod.module.modules.cosmetics.CosmeticsManager.equippedHorns);
        cosmeticsJson.addProperty("mask", com.visualmod.module.modules.cosmetics.CosmeticsManager.equippedMask);
        cosmeticsJson.addProperty("tail", com.visualmod.module.modules.cosmetics.CosmeticsManager.equippedTail);
        cosmeticsJson.addProperty("legs", com.visualmod.module.modules.cosmetics.CosmeticsManager.equippedLegs);
        cosmeticsJson.addProperty("back", com.visualmod.module.modules.cosmetics.CosmeticsManager.equippedBack);
        cosmeticsJson.addProperty("pet", com.visualmod.module.modules.cosmetics.CosmeticsManager.equippedPet);
        cosmeticsJson.addProperty("petAI", com.visualmod.module.modules.cosmetics.CosmeticsManager.petAI);
        cosmeticsJson.addProperty("nimbus", com.visualmod.module.modules.cosmetics.CosmeticsManager.equippedNimbus);
        cosmeticsJson.addProperty("nimbusColor", com.visualmod.module.modules.cosmetics.CosmeticsManager.nimbusColor);
        configJson.add("cosmetics", cosmeticsJson);

        JsonObject posJson = new JsonObject();
        for (Map.Entry<String, int[]> e : com.visualmod.module.modules.hud.HudPosition.getAll().entrySet()) {
            int[] p = e.getValue();
            if (p == null || p.length < 2) continue;
            JsonArray xy = new JsonArray();
            xy.add(p[0]);
            xy.add(p[1]);
            if (p.length >= 3) xy.add(p[2]);
            posJson.add(e.getKey(), xy);
        }
        configJson.add("positions", posJson);

        try {
            File tmp = new File(CONFIG_DIR, name + ".json.tmp");
            try (FileWriter writer = new FileWriter(tmp)) {
                GSON.toJson(configJson, writer);
            }
            // Копия снимается со СТАРОГО файла, поэтому до замены.
            com.visualmod.util.ConfigGuard.rotateBeforeSave(name, configFile);
            java.nio.file.Files.move(tmp.toPath(), configFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            saveLastConfigName(name);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void loadConfig(String name) {
        loadConfig(name, true);
    }

    private static String whyNotOurConfig(JsonObject json) {
        if (!json.has("modules") || !json.get("modules").isJsonObject())
            return "похоже, это не конфиг holy-visuals: нет раздела modules";
        if (json.has("generator") && !json.get("generator").isJsonNull()
                && !GENERATOR.equals(json.get("generator").getAsString()))
            return "это конфиг от другого мода (generator: " + json.get("generator").getAsString() + ")";
        if (json.getAsJsonObject("modules").entrySet().isEmpty())
            return "в конфиге нет ни одного модуля — пустой файл";
        return null;
    }

    public static boolean loadConfig(String name, boolean createIfMissing) {
        name = normalizeName(name);
        lastError = null;
        File configFile = new File(CONFIG_DIR, name + ".json");
        if (!configFile.exists()) {
            lastError = "нет файла visualmod_configs/" + name + ".json";
            if (createIfMissing) saveConfig(name);
            return false;
        }

        try (FileReader reader = new FileReader(configFile)) {
            JsonObject configJson;
            try {
                configJson = GSON.fromJson(reader, JsonObject.class);
            } catch (RuntimeException brokenJson) {
                com.visualmod.util.Diag.once("ConfigManager$parse", brokenJson);
                lastError = "файл повреждён: " + brokenJson.getClass().getSimpleName();
                if (tryHeal(name, configFile, createIfMissing)) return true;
                return false;
            }
            if (configJson == null) {
                lastError = "файл пустой или битый JSON";
                if (tryHeal(name, configFile, createIfMissing)) return true;
                return false;
            }

            String why = whyNotOurConfig(configJson);
            if (why != null) { lastError = why; return false; }

            currentConfigName = name;
            keybinds.clear();
            macros.clear();

            lastBetaSkipped = 0;
            // Один пакет на всю загрузку: пак-модули перезагружают ресурсы, по перезагрузке на шаг.
            com.visualmod.util.RuntimePacks.beginBatch();
            try {
                if (configJson.has("settings") && configJson.get("settings").isJsonObject() && moduleManager != null) {
                    applySettings(configJson.getAsJsonObject("settings"));
                }

                if (configJson.has("modules") && moduleManager != null) {
                    JsonObject modulesJson = configJson.getAsJsonObject("modules");
                    for (Module m : moduleManager.getModules()) {
                        String key = m.getName().toLowerCase();
                        if (!modulesJson.has(key)) continue;
                        boolean shouldEnable = modulesJson.get(key).getAsBoolean();
                        // setEnabled принимает намерение и применяет его настолько, насколько модуль
                        // разблокирован: скрытый блок-листом он останется выключенным, но намерение
                        // сохранит и вернётся сам, когда сокрытие снимут.
                        m.setEnabled(shouldEnable);
                        if (shouldEnable && m.isHidden() && !m.isEnabled()) lastBetaSkipped++;
                    }
                }
            } finally {
                com.visualmod.util.RuntimePacks.endBatch();
            }

            if (configJson.has("binds")) {
                JsonObject bindsJson = configJson.getAsJsonObject("binds");
                bindsJson.asMap().forEach((k, v) -> {
                    String migrated = com.visualmod.util.KeyNames.migrateLegacy(k);
                    if (migrated != null) keybinds.put(migrated, v.getAsString());
                });
            }

            if (configJson.has("macros")) {
                JsonObject macrosJson = configJson.getAsJsonObject("macros");
                macrosJson.asMap().forEach((k, v) -> {
                    String migrated = com.visualmod.util.KeyNames.migrateLegacy(k);
                    if (migrated != null) macros.put(migrated, v.getAsString());
                });
            }

            if (configJson.has("cosmetics") && configJson.get("cosmetics").isJsonObject()) {
                JsonObject cosmeticsJson = configJson.getAsJsonObject("cosmetics");
                com.visualmod.module.modules.cosmetics.CosmeticsManager.equippedCape =
                        cosmeticsJson.has("cape") ? cosmeticsJson.get("cape").getAsInt() : -1;
                com.visualmod.module.modules.cosmetics.CosmeticsManager.equippedHat =
                        cosmeticsJson.has("hat") ? cosmeticsJson.get("hat").getAsInt() : -1;
                com.visualmod.module.modules.cosmetics.CosmeticsManager.equippedArmor =
                        cosmeticsJson.has("armor") ? cosmeticsJson.get("armor").getAsInt() : -1;
                com.visualmod.module.modules.cosmetics.CosmeticsManager.equippedFace =
                        cosmeticsJson.has("face") ? cosmeticsJson.get("face").getAsInt() : -1;
                com.visualmod.module.modules.cosmetics.CosmeticsManager.equippedGloves =
                        cosmeticsJson.has("gloves") ? cosmeticsJson.get("gloves").getAsInt() : -1;
                com.visualmod.module.modules.cosmetics.CosmeticsManager.equippedHorns =
                        cosmeticsJson.has("horns") ? cosmeticsJson.get("horns").getAsInt() : -1;
                com.visualmod.module.modules.cosmetics.CosmeticsManager.equippedMask =
                        cosmeticsJson.has("mask") ? cosmeticsJson.get("mask").getAsInt() : -1;
                com.visualmod.module.modules.cosmetics.CosmeticsManager.equippedTail =
                        cosmeticsJson.has("tail") ? cosmeticsJson.get("tail").getAsInt() : -1;
                com.visualmod.module.modules.cosmetics.CosmeticsManager.equippedLegs =
                        cosmeticsJson.has("legs") ? cosmeticsJson.get("legs").getAsInt() : -1;
                com.visualmod.module.modules.cosmetics.CosmeticsManager.equippedBack =
                        cosmeticsJson.has("back") ? cosmeticsJson.get("back").getAsInt() : -1;
                com.visualmod.module.modules.cosmetics.CosmeticsManager.equippedPet =
                        cosmeticsJson.has("pet") ? cosmeticsJson.get("pet").getAsInt() : -1;
                if (cosmeticsJson.has("petAI")) com.visualmod.module.modules.cosmetics.CosmeticsManager
                        .petAI = cosmeticsJson.get("petAI").getAsBoolean();
                com.visualmod.module.modules.cosmetics.CosmeticsManager.equippedNimbus =
                        cosmeticsJson.has("nimbus") ? cosmeticsJson.get("nimbus").getAsInt() : -1;
                if (cosmeticsJson.has("nimbusColor")) com.visualmod.module.modules.cosmetics.CosmeticsManager
                        .nimbusColor = cosmeticsJson.get("nimbusColor").getAsInt();
            }

            if (configJson.has("positions") && configJson.get("positions").isJsonObject()) {
                applyPositions(configJson.getAsJsonObject("positions"));
            }

            saveLastConfigName(name);
            return true;
        } catch (IOException | RuntimeException e) {
            com.visualmod.util.Diag.once("ConfigManager$load", e);
            if (lastError == null) lastError = "файл повреждён: " + e.getClass().getSimpleName();
            return tryHeal(name, configFile, createIfMissing);
        }
    }

    private static boolean healing = false;

    /**
     * Основной файл читается не полностью — откладываем его и пробуем последнюю пригодную копию.
     * Возвращает true, если с долеченного файла конфиг всё-таки поднялся.
     */
    private static boolean tryHeal(String name, File configFile, boolean createIfMissing) {
        if (healing) return false;
        healing = true;
        try {
            File healedFrom = com.visualmod.util.ConfigGuard.heal(name, configFile);
            if (healedFrom == null) return false;
            lastError = null;
            boolean ok = loadConfig(name, createIfMissing);
            if (ok) com.visualmod.util.ConfigGuard.announce(healedFrom);
            return ok;
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("ConfigGuard", t);
            return false;
        } finally {
            healing = false;
        }
    }

    public static File configDir() {
        return CONFIG_DIR;
    }

    private static void applyPositions(JsonObject all) {
        Map<String, int[]> loaded = new HashMap<>();
        all.asMap().forEach((k, v) -> {
            if (!(v instanceof JsonArray a) || a.size() < 2) return;
            try {
                int[] p = a.size() >= 3
                        ? new int[]{a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()}
                        : new int[]{a.get(0).getAsInt(), a.get(1).getAsInt()};
                loaded.put(k, p);
            } catch (RuntimeException ignored) {}
        });
        com.visualmod.module.modules.hud.HudPosition.applyAll(loaded);
        com.visualmod.module.modules.hud.HudPosition.save();
    }

    private static String normalizeName(String name) {
        if (name == null) return "default";
        String n = name.trim().toLowerCase().replaceAll("[\\\\/:*?\"<>|\\s]", "_");
        return n.isEmpty() || n.equals("_") ? "default" : n;
    }

    private static void applySettings(JsonObject all) {
        for (int pass = 0; pass < 2; pass++) {
            for (Module m : moduleManager.getModules()) {
                if (!(m instanceof Configurable c)) continue;
                String key = m.getName().toLowerCase();
                if (!all.has(key) || !all.get(key).isJsonObject()) continue;
                JsonObject o = all.getAsJsonObject(key);
                for (Setting st : c.getSettings()) {
                    if (!o.has(st.getName())) continue;
                    try {
                        if (pass == 0 && st instanceof ModeSetting ms) ms.setIndex(o.get(st.getName()).getAsInt());
                        else if (pass == 1 && st instanceof SliderSetting sl) sl.setValue(o.get(st.getName()).getAsDouble());
                        else if (pass == 1 && st instanceof ToggleSetting tg) tg.setEnabled(o.get(st.getName()).getAsBoolean());
                        else if (pass == 1 && st instanceof ColorSetting cs) cs.setColor(o.get(st.getName()).getAsInt());
                        else if (pass == 1 && st instanceof KeySetting ks) {
                            com.google.gson.JsonElement e = o.get(st.getName());
                            ks.setKey(e == null || e.isJsonNull() ? null : e.getAsString());
                        }
                    } catch (RuntimeException ignored) {}
                }
            }
        }
    }

    /** Имена конфигов из visualmod_configs — для колонки Configs в ClickGUI. */
    public static java.util.List<String> listConfigs() {
        java.util.List<String> names = new java.util.ArrayList<>();
        File[] files = CONFIG_DIR.listFiles((dir, n) -> n.toLowerCase().endsWith(".json"));
        if (files != null) {
            for (File f : files) names.add(f.getName().substring(0, f.getName().length() - 5));
            names.sort(java.util.Comparator.naturalOrder());
        }
        if (!names.contains("default")) names.add(0, "default");
        return names;
    }

    public static void autoSave() {
        saveConfig(currentConfigName);
    }

    private static void loadLastConfigName() {
        if (LAST_CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(LAST_CONFIG_FILE)) {
                StringBuilder sb = new StringBuilder();
                int ch;
                while ((ch = reader.read()) != -1) {
                    sb.append((char) ch);
                }
                currentConfigName = sb.toString().trim();
            } catch (IOException e) {
                currentConfigName = "default";
            }
        }
    }

    private static void saveLastConfigName(String name) {
        try (FileWriter writer = new FileWriter(LAST_CONFIG_FILE)) {
            writer.write(name);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

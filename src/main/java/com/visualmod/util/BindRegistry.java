package com.visualmod.util;

import com.visualmod.config.ConfigManager;

public final class BindRegistry {
    private BindRegistry() {}

    public static String getKeyFor(String moduleName) {
        for (var e : ConfigManager.keybinds.entrySet()) {
            if (e.getValue().equalsIgnoreCase(moduleName)) return e.getKey();
        }
        return null;
    }

    public static void setBind(String moduleName, String translationKey) {
        ConfigManager.keybinds.values().removeIf(v -> v.equalsIgnoreCase(moduleName));
        ConfigManager.keybinds.remove(translationKey);
        ConfigManager.keybinds.put(translationKey, moduleName);
        ConfigManager.autoSave();
    }

    public static void clearBind(String moduleName) {
        ConfigManager.keybinds.values().removeIf(v -> v.equalsIgnoreCase(moduleName));
        ConfigManager.autoSave();
    }
}

package com.visualmod.util;

import com.visualmod.config.ConfigManager;
import com.visualmod.module.Module;

public final class BindRegistry {
    private BindRegistry() {}

    public static String getKeyFor(String moduleName) {
        for (var e : ConfigManager.keybinds.entrySet()) {
            if (e.getValue().equalsIgnoreCase(moduleName)) return e.getKey();
        }
        return null;
    }

    /**
     * false — бинд не назначен: модуля нет или он скрыт (серверным контролем или BETA-ключом).
     * Скрытую функцию нельзя привязать к клавише ни из GUI, ни из `.bind`, ни из чипов BindsHUD,
     * поэтому проверка стоит здесь, а не в трёх вызывающих местах.
     */
    public static boolean setBind(String moduleName, String translationKey) {
        Module m = ConfigManager.moduleManager == null
                ? null : ConfigManager.moduleManager.getModuleByName(moduleName);
        if (m == null || m.isHidden()) return false;
        ConfigManager.keybinds.values().removeIf(v -> v.equalsIgnoreCase(moduleName));
        ConfigManager.keybinds.remove(translationKey);
        ConfigManager.keybinds.put(translationKey, moduleName);
        ConfigManager.autoSave();
        return true;
    }

    public static void clearBind(String moduleName) {
        ConfigManager.keybinds.values().removeIf(v -> v.equalsIgnoreCase(moduleName));
        ConfigManager.autoSave();
    }
}

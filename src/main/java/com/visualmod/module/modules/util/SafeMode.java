package com.visualmod.module.modules.util;

import com.visualmod.VisualsMod;
import com.visualmod.config.ConfigManager;
import com.visualmod.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class SafeMode extends Module {
    private static boolean locked = false;

    public SafeMode() {
        super("SafeMode", "Disables everything until Insert is pressed", Category.UTIL);
    }

    public static boolean isLocked() { return locked; }

    @Override
    public void onEnable() {
        if (VisualsMod.moduleManager != null) {
            for (Module m : VisualsMod.moduleManager.getModules()) {
                if (m == this) continue;
                if (m.isEnabled()) {
                    m.setEnabled(false);
                    m.onDisable();
                }
            }
        }
        locked = true;
    }

    public static void tryUnlock() {
        if (!locked) return;
        locked = false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("§b[SafeMode] §aВизуал разблокирован, загружаю последний конфиг..."), false);
        }
        ConfigManager.loadConfig(ConfigManager.currentConfigName);
    }
}

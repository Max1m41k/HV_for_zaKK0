package com.visualmod.module.modules.util;

import com.visualmod.VisualsMod;
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
        // Блокировщик включаем до обхода: resync() выключит модули сам, а их намерение останется целым.
        locked = true;
        com.visualmod.util.RuntimePacks.beginBatch();
        try {
            if (VisualsMod.moduleManager != null) {
                for (Module m : VisualsMod.moduleManager.getModules()) {
                    if (m == this) continue;
                    m.resync();
                }
            }
        } finally {
            com.visualmod.util.RuntimePacks.endBatch();
        }
    }

    @Override
    public void onDisable() {
        // Снимать блокировку должен любой путь выключения, а не только Insert: иначе «модуль выключен,
        // а визуал всё ещё заложен» жило бы до перезапуска игры.
        tryUnlock();
    }

    public static void tryUnlock() {
        if (!locked) return;
        locked = false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("§b[SafeMode] §aВизуал разблокирован, возвращаю модули..."), false);
        }
        if (VisualsMod.moduleManager == null) return;
        SafeMode itself = VisualsMod.moduleManager.getModule(SafeMode.class);
        if (itself != null) itself.setEnabled(false);
        com.visualmod.util.RuntimePacks.beginBatch();
        try {
            for (Module m : VisualsMod.moduleManager.getModules()) {
                m.resync();
            }
        } finally {
            com.visualmod.util.RuntimePacks.endBatch();
        }
    }
}

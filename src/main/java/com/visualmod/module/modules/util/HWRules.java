package com.visualmod.module.modules.util;

import com.visualmod.module.Module;
import com.visualmod.screen.HwRulesScreen;
import net.minecraft.client.MinecraftClient;

/** Opens the HolyWorld rules table when toggled on. */
public class HWRules extends Module {
    public HWRules() {
        super("HWRules", "Opens a table with HolyWorld server rules", Category.UTIL);
    }

    @Override
    public void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || com.visualmod.config.ConfigManager.applying) return;
        mc.setScreen(new HwRulesScreen(mc.currentScreen));
    }
}

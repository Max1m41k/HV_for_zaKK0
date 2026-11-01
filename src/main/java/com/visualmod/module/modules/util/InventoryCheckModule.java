package com.visualmod.module.modules.util;

import com.visualmod.module.Module;
import com.visualmod.util.InventoryCheck;
import com.visualmod.util.PvpSafe;
import net.minecraft.client.MinecraftClient;

/**
 * Переключатель InventoryCheck: логика, запрос /invsee и панель над головой — в util.InventoryCheck.
 * В режиме PVP серверная команда недоступна, поэтому модуль гасит себя сам.
 */
public class InventoryCheckModule extends Module {

    public InventoryCheckModule() {
        super("InventoryCheck", "Shows the inventory of the player in your crosshair", Category.UTIL);
        InventoryCheck.init();
    }

    @Override
    public void onEnable() {
        InventoryCheck.setActive(true);
    }

    @Override
    public void onDisable() {
        InventoryCheck.setActive(false);
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        if (PvpSafe.inPvp()) {
            setEnabled(false);
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.player != null) {
                mc.player.sendMessage(net.minecraft.text.Text.of(
                        "§d[HolyVisuals] §cInventoryCheck выключен: §7в режиме PVP /invsee недоступен"), false);
            }
            return;
        }
        InventoryCheck.tick(MinecraftClient.getInstance());
    }
}

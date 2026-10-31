package com.visualmod.module.modules.movement;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;

public class ToggleSneak extends Module {

    private static boolean toggled = false;

    static {
        ClientTickEvents.START_CLIENT_TICK.register(ToggleSneak::onStartTick);
    }

    public ToggleSneak() {
        super("ToggleSneak", "Press sneak once to keep sneaking", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        toggled = false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null) { KeyBinding k = mc.options.sneakKey; while (k.wasPressed()) {  } }
    }

    @Override
    public void onDisable() {
        toggled = false;
        KeyBinding.updatePressedStates();
    }

    private static void onStartTick(MinecraftClient mc) {
        if (VisualsMod.moduleManager == null) return;
        ToggleSneak m = VisualsMod.moduleManager.getModule(ToggleSneak.class);
        if (m == null || !m.isEnabled()) return;
        if (mc.player == null || mc.options == null) { toggled = false; return; }

        KeyBinding k = mc.options.sneakKey;
        boolean flipped = false;
        while (k.wasPressed()) { toggled = !toggled; flipped = true; }
        if (flipped && mc.currentScreen == null && mc.inGameHud != null) {
            mc.inGameHud.setOverlayMessage(Text.literal("Sneak: " + (toggled ? "ON" : "OFF")), false);
        }
        // Shift на пассажире = ванильный выход из лодки/маунта: в транспорте залипший toggle
        // сбрасываем и состояние клавиши не навязываем.
        if (mc.player.getVehicle() != null) {
            if (toggled) {
                toggled = false;
                k.setPressed(false);
            }
        } else if (mc.currentScreen == null) {
            k.setPressed(toggled);
        }
    }
}

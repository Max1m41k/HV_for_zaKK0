package com.visualmod.module.modules.movement;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

import java.util.List;

public class AutoWalk extends Module implements Configurable {

    public static boolean stopOnHit = true;

    static {
        ClientTickEvents.START_CLIENT_TICK.register(AutoWalk::onStartTick);
    }

    private final List<Setting> settings = List.of(
            new ModeSetting("Stop On Hit", new String[]{"OFF", "ON"}, () -> stopOnHit ? 1 : 0, i -> stopOnHit = (i == 1))
    );

    public AutoWalk() {
        super("AutoWalk", "Walk forward automatically, S to stop", Category.MOVEMENT);
    }

    @Override public List<Setting> getSettings() { return settings; }

    @Override
    public void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null) { KeyBinding b = mc.options.backKey; while (b.wasPressed()) {  } }
    }

    @Override
    public void onDisable() {
        KeyBinding.updatePressedStates();
    }

    private static void stop(AutoWalk m) {
        m.setEnabled(false);
    }

    private static void onStartTick(MinecraftClient mc) {
        if (VisualsMod.moduleManager == null) return;
        AutoWalk m = VisualsMod.moduleManager.getModule(AutoWalk.class);
        if (m == null || !m.isEnabled()) return;
        if (mc.player == null || mc.options == null) return;
        if (mc.currentScreen != null) return;

        if (mc.options.backKey.wasPressed()) { stop(m); return; }
        if (stopOnHit && mc.player.hurtTime > 0) { stop(m); return; }
        mc.options.forwardKey.setPressed(true);
    }
}

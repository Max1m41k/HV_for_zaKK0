package com.visualmod.module.modules.player;

import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;

import java.util.ArrayList;
import java.util.List;

public class AutoRespawn extends Module implements Configurable {

    public static float delay = 0.5f;
    public static boolean instant = false;

    private long diedAtMs = 0L;
    private boolean sent = false;

    public AutoRespawn() {
        super("AutoRespawn", "Automatically respawn after death", Category.PLAYER);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> list = new ArrayList<>();
        list.add(new SliderSetting("Delay", 0.0f, 2.0f, 0.1f, () -> delay, v -> delay = (float) v, "s"));
        list.add(new ModeSetting("Instant", new String[]{"OFF", "ON"}, () -> instant ? 1 : 0, i -> instant = (i == 1)));
        return list;
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.getNetworkHandler() == null) {
            diedAtMs = 0L;
            sent = false;
            return;
        }

        if (!mc.player.isDead()) {
            if (sent || diedAtMs != 0L) {
                diedAtMs = 0L;
                sent = false;
            }
            if (mc.currentScreen instanceof DeathScreen) mc.setScreen(null);
            return;
        }

        if (diedAtMs == 0L) {
            diedAtMs = System.currentTimeMillis();
            sent = false;
        }
        if (sent) return;

        float wait = instant ? 0f : Math.max(0f, delay);
        if (System.currentTimeMillis() - diedAtMs < (long) (wait * 1000f)) return;

        sent = true;
        mc.player.requestRespawn();
    }
}

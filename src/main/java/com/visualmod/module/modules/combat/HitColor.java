package com.visualmod.module.modules.combat;

import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HitColor extends Module implements Configurable {

    public static boolean isFeatureEnabled = false;

    private static final Map<UUID, Long> hitPlayers = new HashMap<>();
    private static final long DURATION = 300;

    private int color = 0xFFFF3333;
    private double intensity = 0.55;

    private final List<Setting> settings = List.of(
            new ColorSetting("Fill Color", () -> color, c -> color = c),
            new SliderSetting("Intensity", 0.15, 1.0, 0.05, () -> intensity, v -> intensity = v)
    );

    public HitColor() {
        super("HitColor", "Colored fill on hit (covers armor too)", Category.COMBAT);
    }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }

    public int getColor() { return color; }
    public double getIntensity() { return intensity; }

    @Override
    public void onEnable() {
        isFeatureEnabled = true;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        isFeatureEnabled = false;
        hitPlayers.clear();
        super.onDisable();
    }

    public static void onHit(PlayerEntity player) {
        if (!isFeatureEnabled) return;
        hitPlayers.put(player.getUuid(), System.currentTimeMillis());
    }

    public static boolean shouldRender(PlayerEntity player) {
        if (!isFeatureEnabled) return false;
        Long time = hitPlayers.get(player.getUuid());
        if (time == null) return false;
        if (System.currentTimeMillis() - time > DURATION) {
            hitPlayers.remove(player.getUuid());
            return false;
        }
        return true;
    }

    public static float getFadeProgress(PlayerEntity player) {
        Long time = hitPlayers.get(player.getUuid());
        if (time == null) return 0f;
        float t = (System.currentTimeMillis() - time) / (float) DURATION;
        return Math.max(0f, 1f - t);
    }
}

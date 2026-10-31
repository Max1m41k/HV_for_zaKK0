package com.visualmod.module.modules.render;

import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;

import java.util.Arrays;
import java.util.List;

public class NoFog extends Module implements Configurable {

    public enum ColorMode {
        VANILLA,
        CUSTOM
    }

    public static ColorMode colorMode = ColorMode.VANILLA;
    private int color = 0xFF7A5CFF;
    private double distanceMultiplier = 4;

    private final List<Setting> settings = List.of(
            new ModeSetting(
                    "Fog Color",
                    Arrays.stream(ColorMode.values()).map(Enum::name).toArray(String[]::new),
                    () -> colorMode.ordinal(),
                    i -> colorMode = ColorMode.values()[i]
            ),
            new ColorSetting("Custom Color", () -> color, c -> color = c),
            new SliderSetting("Distance", 1, 10, 0.5, () -> distanceMultiplier, v -> distanceMultiplier = v, "x")
    );

    public NoFog() {
        super("NoFog", "Removes / customizes fog", Category.RENDER);
    }

    public double getDistanceMultiplier() { return distanceMultiplier; }
    public int getColor() { return color; }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }
}

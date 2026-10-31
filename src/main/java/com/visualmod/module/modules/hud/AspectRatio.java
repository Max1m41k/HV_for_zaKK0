package com.visualmod.module.modules.hud;

import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;

import java.util.ArrayList;
import java.util.List;

public class AspectRatio extends Module implements Configurable {

    public enum Ratio { R4_3, R16_9, R1_1, R16_10, CUSTOM }

    public static Ratio ratio = Ratio.R4_3;
    public static double customRatio = 1.333;

    public AspectRatio() {
        super("AspectRatio", "Changes the real 3D render aspect ratio (stretched resolution)", Category.HUD);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> list = new ArrayList<>();
        list.add(new ModeSetting(
                "Ratio",
                java.util.Arrays.stream(Ratio.values()).map(Enum::name).toArray(String[]::new),
                () -> ratio.ordinal(),
                i -> ratio = Ratio.values()[i]
        ));
        if (ratio == Ratio.CUSTOM) {
            list.add(new SliderSetting("Custom Ratio", 0.5, 3.0, 0.01, () -> customRatio, v -> customRatio = v));
        }
        return list;
    }

    public static double targetRatio() {
        return switch (ratio) {
            case R4_3 -> 4.0 / 3.0;
            case R16_9 -> 16.0 / 9.0;
            case R1_1 -> 1.0;
            case R16_10 -> 16.0 / 10.0;
            case CUSTOM -> customRatio;
        };
    }
}

package com.visualmod.module.modules.movement;

import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;

import java.util.ArrayList;
import java.util.List;

public class Strafe extends Module implements Configurable {

    public static float intensity = 1.0f;
    public static float speed = 1.0f;
    public static String style = "LEAN";

    private static final String[] STYLES = {"LEAN", "BOB", "SPIN"};

    public Strafe() {
        super("Strafe", "Strafe animation effect", Category.MOVEMENT);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> list = new ArrayList<>();
        list.add(new SliderSetting("Intensity", 0.2f, 2.0f, 0.1f, () -> intensity, v -> intensity = (float) v, "x"));
        list.add(new SliderSetting("Speed", 0.5f, 2.0f, 0.1f, () -> speed, v -> speed = (float) v, "x"));
        list.add(new ModeSetting("Style", STYLES, () -> styleIndex(), i -> style = STYLES[i]));
        return list;
    }

    private int styleIndex() {
        for (int i = 0; i < STYLES.length; i++) {
            if (STYLES[i].equals(style)) return i;
        }
        return 0;
    }
}

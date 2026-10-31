package com.visualmod.module.modules.combat;

import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;

import java.util.ArrayList;
import java.util.List;

public class CriticalEffect extends Module implements Configurable {

    public static int color = 0xFFFFAA00;
    public static float size = 1.5f;
    public static float duration = 0.3f;
    public static String style = "STARS";

    private static final String[] STYLES = {"STARS", "SPARKLE", "EXPLOSION"};

    public CriticalEffect() {
        super("CriticalEffect", "Visual effect on critical hits", Category.COMBAT);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> list = new ArrayList<>();
        list.add(new ColorSetting("Color", () -> color, c -> color = c));
        list.add(new SliderSetting("Size", 0.5f, 3.0f, 0.1f, () -> size, v -> size = (float) v, "x"));
        list.add(new SliderSetting("Duration", 0.1f, 0.5f, 0.05f, () -> duration, v -> duration = (float) v, "s"));
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

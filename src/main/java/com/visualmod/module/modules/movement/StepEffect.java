package com.visualmod.module.modules.movement;

import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;

import java.util.ArrayList;
import java.util.List;

public class StepEffect extends Module implements Configurable {

    public static int color = 0xFFAAAAAA;
    public static float size = 0.3f;
    public static float duration = 0.5f;
    public static String style = "CIRCLES";

    private static final String[] STYLES = {"CIRCLES", "RIPPLE", "DUST"};

    public StepEffect() {
        super("StepEffect", "Visual effect when stepping", Category.MOVEMENT);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> list = new ArrayList<>();
        list.add(new ColorSetting("Color", () -> color, c -> color = c));
        list.add(new SliderSetting("Size", 0.1f, 1.0f, 0.05f, () -> size, v -> size = (float) v, "m"));
        list.add(new SliderSetting("Duration", 0.2f, 1.0f, 0.1f, () -> duration, v -> duration = (float) v, "s"));
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

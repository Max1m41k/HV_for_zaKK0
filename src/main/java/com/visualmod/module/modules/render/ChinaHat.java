package com.visualmod.module.modules.render;

import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;

import java.util.ArrayList;
import java.util.List;

public class ChinaHat extends Module implements Configurable {

    public enum Style { CLASSIC, PULSE }

    public static Style style = Style.CLASSIC;
    private int color = 0xFF7B5CF0;

    public ChinaHat() {
        super("ChinaHat", "China hat cosmetic (visible only to you)", Category.COSMETICS);
    }

    public int getColor() { return color; }

    @Override
    public List<Setting> getSettings() {
        List<Setting> list = new ArrayList<>();
        list.add(new ColorSetting("Color", () -> color, c -> color = c));
        list.add(new ModeSetting(
                "Style",
                java.util.Arrays.stream(Style.values()).map(Enum::name).toArray(String[]::new),
                () -> style.ordinal(),
                i -> style = Style.values()[i]
        ));
        return list;
    }

    public static float getPulseAlpha(float base) {
        if (style != Style.PULSE) return base;
        float t = (System.currentTimeMillis() % 1500) / 1500f;
        float wave = (float) (0.5 + 0.5 * Math.sin(t * Math.PI * 2));
        return base * (0.5f + wave * 0.6f);
    }
}

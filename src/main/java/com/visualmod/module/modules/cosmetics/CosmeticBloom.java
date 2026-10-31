package com.visualmod.module.modules.cosmetics;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;

import java.util.ArrayList;
import java.util.List;

public class CosmeticBloom extends Module implements Configurable {

    public enum Style { TIGHT, WIDE, PULSE }

    public static Style style = Style.WIDE;
    public static double strength = 65;
    public static double spread = 1.0;

    public CosmeticBloom() {
        super("CosmeticBloom", "Halo around glowing cosmetic parts", Category.COSMETICS);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Style", new String[]{"TIGHT", "WIDE", "PULSE"},
                () -> style.ordinal(), i -> style = Style.values()[i]));
        l.add(new SliderSetting("Strength", 0, 100, 5, () -> strength, v -> strength = v, "%"));
        l.add(new SliderSetting("Spread", 0.4, 2.5, 0.1, () -> spread, v -> spread = v, "x"));
        return l;
    }

    public static float amount() {
        if (VisualsMod.moduleManager == null) return 0f;
        CosmeticBloom m = VisualsMod.moduleManager.getModule(CosmeticBloom.class);
        if (m == null || !m.isEnabled() || strength <= 0.005) return 0f;
        if (com.visualmod.module.modules.render.AutoReduce.glow()) return 0f;
        float base = (float) (strength / 100.0);
        if (style == Style.PULSE) {
            double t = System.currentTimeMillis() / 1000.0;
            base *= 0.55f + 0.45f * (float) (0.5 + 0.5 * Math.sin(t * 2.4));
        }
        return base;
    }

    public static float offsetPx() {
        float w = style == Style.TIGHT ? 1.1f : 2.1f;
        return w * (float) spread;
    }
}

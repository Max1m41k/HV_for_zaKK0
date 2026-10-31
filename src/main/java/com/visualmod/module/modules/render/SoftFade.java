package com.visualmod.module.modules.render;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;

import java.util.List;

/**
 * Плавное растворение чанков на границе прорисовки: подтягивает start ванильного (или NoFog) тумана
 * к его end, так что мир мягко тает в цвете горизонта вместо жёсткой обрезки.
 */
public class SoftFade extends Module implements Configurable {

    public static int mode = 0; // 0 = FOG, 1 = OFF
    public static double fadeStart = 0.7;

    private final List<Setting> settings = List.of(
            new ModeSetting("Mode", new String[]{"FOG", "OFF"}, () -> mode, i -> mode = i),
            new SliderSetting("Fade Start", 0.4, 0.95, 0.05, () -> fadeStart, v -> fadeStart = v)
    );

    public SoftFade() {
        super("SoftFade", "Chunks dissolve smoothly at the render-distance edge", Category.RENDER);
    }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }

    public static boolean active() {
        if (mode != 0) return false;
        if (VisualsMod.moduleManager == null) return false;
        SoftFade m = VisualsMod.moduleManager.getModule(SoftFade.class);
        return m != null && m.isEnabled();
    }
}

package com.visualmod.module.modules.player;

import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;

import java.util.List;

public class SwingAnimation extends Module implements Configurable {

    public static int mode = 0;
    public static double strength = 1.0;
    public static double angleDeg = 0;
    public static double tiltDeg = 0;

    private final List<Setting> settings = List.of(
            new ModeSetting("Mode", SwingMath.MODES, () -> mode, i -> mode = i),
            new SliderSetting("Strength", 0.2, 2.0, 0.1, () -> strength, v -> strength = v),
            new SliderSetting("Angle", -180, 180, 1, () -> angleDeg, v -> angleDeg = v, "°"),
            new SliderSetting("Tilt", -180, 180, 1, () -> tiltDeg, v -> tiltDeg = v, "°")
    );

    public SwingAnimation() {
        super("SwingAnimations", "First-person hit animations", Category.RENDER);
    }

    @Override
    public List<Setting> getSettings() { return settings; }

    public boolean replacesVanillaSwing() {
        return isEnabled() && mode != SwingMath.VANILLA;
    }
}

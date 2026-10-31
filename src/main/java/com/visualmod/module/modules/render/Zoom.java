package com.visualmod.module.modules.render;

import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;

import java.util.List;

public class Zoom extends Module implements Configurable {

    public static double factor = 4.0;

    private final List<Setting> settings = List.of(
            new SliderSetting("Zoom", 1.5, 10, 0.5, () -> factor, v -> factor = v, "x")
    );

    public Zoom() {
        super("Zoom", "Optifine-like zoom", Category.RENDER);
    }

    @Override
    public List<Setting> getSettings() { return settings; }
}

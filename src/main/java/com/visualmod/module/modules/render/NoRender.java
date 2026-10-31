package com.visualmod.module.modules.render;

import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;

import java.util.List;

public class NoRender extends Module implements Configurable {

    public static boolean noHurtCam = true;
    public static boolean noFovChanges = true;

    private final List<Setting> settings = List.of(
            new ModeSetting(
                    "No Hurtcam",
                    new String[]{"OFF", "ON"},
                    () -> noHurtCam ? 1 : 0,
                    i -> noHurtCam = (i == 1)
            ),
            new ModeSetting(
                    "No FOV Changes",
                    new String[]{"OFF", "ON"},
                    () -> noFovChanges ? 1 : 0,
                    i -> noFovChanges = (i == 1)
            )
    );

    public NoRender() {
        super("NoRender", "Disables camera shake and FOV changes", Category.RENDER);
    }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }
}

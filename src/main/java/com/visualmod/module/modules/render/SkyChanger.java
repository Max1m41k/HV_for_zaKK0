package com.visualmod.module.modules.render;

import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.Setting;
import com.visualmod.util.WorldRenderUtil;

import java.util.List;

public class SkyChanger extends Module implements Configurable {

    private int color = 0xFF7A5CFF;

    private final List<Setting> settings = List.of(
            new ColorSetting("Sky Color", () -> color, c -> color = c)
    );

    public SkyChanger() {
        super("SkyChanger", "Changes sky color", Category.RENDER);
    }

    public float getRed() { return WorldRenderUtil.colorR(color); }
    public float getGreen() { return WorldRenderUtil.colorG(color); }
    public float getBlue() { return WorldRenderUtil.colorB(color); }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }
}

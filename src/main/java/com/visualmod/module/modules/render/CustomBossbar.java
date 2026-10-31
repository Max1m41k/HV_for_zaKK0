package com.visualmod.module.modules.render;

import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.util.CustomBossbarState;

import java.util.List;

/**
 * CustomBossbar: цвет и вид полоски босса настраиваются, а если полоска принадлежит мобу,
 * по центру появляется его иконка. Вся логика и все значения живут в {@link CustomBossbarState}
 * (миксин имеет право обращаться только к утилите), здесь лишь настройки модуля.
 */
public class CustomBossbar extends Module implements Configurable {

    private final List<Setting> settings = List.of(
            new ModeSetting("Color Mode", new String[]{"Vanilla", "Custom"},
                    () -> CustomBossbarState.colorMode, i -> CustomBossbarState.colorMode = i),
            new ColorSetting("Bar Color", () -> CustomBossbarState.color, c -> CustomBossbarState.color = c),
            new ModeSetting("Style", CustomBossbarState.STYLE_OPTIONS,
                    () -> CustomBossbarState.style, i -> CustomBossbarState.style = i),
            new ModeSetting("Mob Icon", new String[]{"OFF", "ON"},
                    () -> CustomBossbarState.mobIcon ? 1 : 0, i -> CustomBossbarState.mobIcon = (i == 1)),
            new SliderSetting("Icon Size", 8, 24, 1,
                    () -> CustomBossbarState.iconSize, v -> CustomBossbarState.iconSize = v)
    );

    public CustomBossbar() {
        super("CustomBossbar", "Custom boss bar color, style and the owning mob icon in its centre",
                Category.RENDER);
    }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }
}

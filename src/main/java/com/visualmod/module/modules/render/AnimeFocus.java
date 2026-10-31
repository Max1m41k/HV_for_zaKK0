package com.visualmod.module.modules.render;

import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.module.settings.ToggleSetting;
import com.visualmod.util.FocusVeil;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;

import java.util.List;

/**
 * Гипер-туннелирование: пока модуль включён, небо становится белым, все блоки рядом —
 * чёрными с белой окантовкой, а фигурки игроков — сплошь белыми.
 */
public class AnimeFocus extends Module implements Configurable {

    static {
        // До сущностей: пелена и чёрные кубы должны лечь под модели игроков.
        WorldRenderEvents.BEFORE_ENTITIES.register(FocusVeil::render);
    }

    private final List<Setting> settings = List.of(
            new ColorSetting("Sky Color", () -> FocusVeil.skyColor, c -> FocusVeil.skyColor = c),
            new ColorSetting("Block Color", () -> FocusVeil.blockColor, c -> FocusVeil.blockColor = c),
            new ColorSetting("Outline Color", () -> FocusVeil.outlineColor, c -> FocusVeil.outlineColor = c),
            new SliderSetting("Range", 6, 32, 1, () -> FocusVeil.range, v -> FocusVeil.range = v, " blocks"),
            new ToggleSetting("Outlines", () -> FocusVeil.outlines, v -> FocusVeil.outlines = v),
            new ToggleSetting("White Players", () -> FocusVeil.whiteFigures, v -> FocusVeil.whiteFigures = v)
    );

    public AnimeFocus() {
        super("AnimeFocus", "White sky, black outlined blocks and white player figures", Category.RENDER);
    }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }
}

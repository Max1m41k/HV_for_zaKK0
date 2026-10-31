package com.visualmod.module.modules.player;

import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.ToggleSetting;
import com.visualmod.util.CritAnchor;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CustomHitbox extends Module implements Configurable {

    static {
        HudRenderCallback.EVENT.register((ctx, tc) -> CritAnchor.renderHud(ctx));
    }

    public enum Style { OUTLINE, FILLED }
    public enum FillStyle { SOLID, PULSE, GLASS }

    public static Style style = Style.OUTLINE;
    public static FillStyle fillStyle = FillStyle.PULSE;
    private int color = 0xFFFF0000;

    public CustomHitbox() {
        super("CustomHitbox", "Custom hitbox color, outline and fill", Category.RENDER);
    }

    public int getColor() { return color; }
    public void setColor(int color) { this.color = color; }

    @Override
    public List<Setting> getSettings() {
        List<Setting> list = new ArrayList<>();
        list.add(new ColorSetting("Color", () -> color, c -> color = c));
        list.add(new ModeSetting(
                "Style",
                Arrays.stream(Style.values()).map(Enum::name).toArray(String[]::new),
                () -> style.ordinal(),
                i -> style = Style.values()[i]
        ));
        if (style == Style.FILLED) {
            list.add(new ModeSetting(
                    "Fill Style",
                    Arrays.stream(FillStyle.values()).map(Enum::name).toArray(String[]::new),
                    () -> fillStyle.ordinal(),
                    i -> fillStyle = FillStyle.values()[i]
            ));
        }
        list.add(new ToggleSetting("Beat Marker", () -> CritAnchor.enabled, v -> CritAnchor.enabled = v));
        list.add(new ColorSetting("Marker Color", () -> CritAnchor.markerColor, c -> CritAnchor.markerColor = c));
        return list;
    }

    public static float getFillAlpha(float base) {
        return switch (fillStyle) {
            case SOLID -> base;
            case GLASS -> base * 0.35f;
            case PULSE -> {
                float t = (System.currentTimeMillis() % 1500) / 1500f;
                float wave = (float) (0.5 + 0.5 * Math.sin(t * Math.PI * 2));
                yield base * (0.5f + wave * 0.6f);
            }
        };
    }
}

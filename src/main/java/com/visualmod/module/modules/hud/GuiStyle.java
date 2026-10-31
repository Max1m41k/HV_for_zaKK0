package com.visualmod.module.modules.hud;

import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.module.settings.ToggleSetting;

import java.util.ArrayList;
import java.util.List;

public class GuiStyle extends Module implements Configurable {

    public enum Style {
        SOAP,
        GLASS,
        COLUMN,
        CUSTOM
    }

    public static Style style = Style.SOAP;
    public static int customColor = 0xFF14141C;
    public static double customFill = 0.85;
    public static double soapMilky = 0.55;
    public static double soapStreaks = 70;
    public static int soapTint = 0xFFE6E9F0;
    public static boolean soapOnHud = true;

    public GuiStyle() {
        super("GuiStyle", "ClickGUI window appearance", Category.HUD);
    }

    @Override
    public void onDisable() {
        style = Style.SOAP;
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> list = new ArrayList<>();
        list.add(new ModeSetting(
                "Style",
                java.util.Arrays.stream(Style.values()).map(Enum::name).toArray(String[]::new),
                () -> style.ordinal(),
                i -> style = Style.values()[i]
        ));
        if (style == Style.CUSTOM) {
            list.add(new ColorSetting("Color", () -> customColor, c -> customColor = c));
            list.add(new SliderSetting("Fill", 0.1, 1.0, 0.05, () -> customFill, v -> customFill = v));
        }
        if (style == Style.SOAP) {
            list.add(new ColorSetting("Soap Tint", () -> soapTint, c -> soapTint = c));
            list.add(new SliderSetting("Milky", 0.0, 1.0, 0.05, () -> soapMilky, v -> soapMilky = v));
            list.add(new SliderSetting("Streaks", 0.0, 1.0, 0.05, () -> soapStreaks / 100.0, v -> soapStreaks = v * 100.0));
            list.add(new ToggleSetting("On HUD panels", () -> soapOnHud, b -> soapOnHud = b));
        }
        return list;
    }
}

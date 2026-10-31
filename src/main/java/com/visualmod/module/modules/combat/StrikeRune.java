package com.visualmod.module.modules.combat;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.module.settings.ToggleSetting;

import java.util.ArrayList;
import java.util.List;

/**
 * Руна удара: у основания клинка два кольца, которые заполняются по мере перезарядки атаки,
 * и вспышка в момент готовности. Рисует HandEffects в проходе руки.
 */
public class StrikeRune extends Module implements Configurable {

    public static double radius = 0.11;
    public static int readyColor = 0xFF8CFFD0;
    public static int idleColor = 0xFFFF8C4A;
    public static boolean flash = true;

    public StrikeRune() {
        super("StrikeRune", "Rune on the blade fills with attack cooldown", Category.COMBAT);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new SliderSetting("Radius", 0.04, 0.30, 0.01, () -> radius, v -> radius = v, "b"));
        l.add(new ColorSetting("Ready", () -> readyColor, c -> readyColor = c));
        l.add(new ColorSetting("Charging", () -> idleColor, c -> idleColor = c));
        l.add(new ToggleSetting("Flash", () -> flash, v -> flash = v));
        return l;
    }

    public static boolean active() {
        if (VisualsMod.moduleManager == null) return false;
        StrikeRune m = VisualsMod.moduleManager.getModule(StrikeRune.class);
        return m != null && m.isEnabled();
    }
}

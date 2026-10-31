package com.visualmod.module.modules.combat;

import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;

import java.util.ArrayList;
import java.util.List;

public class HitSplash extends Module implements Configurable {

    public static int color = 0xFF7B5CF0;
    public static String style = "GIF";
    public static float width = 1.2f;
    public static float fade = 0.32f;
    public static float slash = 1.0f;

    public static boolean hitFlash = true;
    public static float size = 1.0f;
    public static float duration = 0.2f;

    public static boolean inHand = true;
    public static float anchorX = 0.5f;
    public static float anchorY = 0.62f;
    public static float anchorZ = 0.0f;

    public static float blade = 0.62f;

    private static final String[] STYLES = {"GIF", "RIBBON", "GLOW", "DASHED"};

    /** GIF — полоса из готовых кадров, остальные режимы — лента, собранная из точек взмаха. */
    public static boolean isGif() {
        return "GIF".equals(style);
    }

    public HitSplash() {
        super("HitSplash", "Sword swing ribbon and hit flash", Category.COMBAT);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> list = new ArrayList<>();
        list.add(new ColorSetting("Color", () -> color, c -> color = c));
        list.add(new ModeSetting("Style", STYLES, () -> styleIndex(), i -> style = STYLES[i]));
        list.add(new SliderSetting("Width", 0.2f, 3.0f, 0.1f, () -> width, v -> width = (float) v, "x"));
        list.add(new SliderSetting("Fade", 0.1f, 0.8f, 0.01f, () -> fade, v -> fade = (float) v, "s"));
        if (isGif()) {
            list.add(new SliderSetting("Slash", 0.4f, 2.0f, 0.05f, () -> slash, v -> slash = (float) v, "x"));
        }
        list.add(new SliderSetting("Blade", 0.2f, 1.0f, 0.05f, () -> blade, v -> blade = (float) v, "u"));
        list.add(new ModeSetting("Hit Flash", new String[]{"OFF", "ON"}, () -> hitFlash ? 1 : 0, i -> hitFlash = i == 1));
        if (hitFlash) {
            list.add(new SliderSetting("Flash Size", 0.5f, 3.0f, 0.1f, () -> size, v -> size = (float) v, "x"));
            list.add(new SliderSetting("Flash Time", 0.05f, 0.5f, 0.05f, () -> duration, v -> duration = (float) v, "s"));
        }
        list.add(new ModeSetting("In Hand", new String[]{"OFF", "ON"}, () -> inHand ? 1 : 0, i -> inHand = i == 1));
        if (inHand) {
            list.add(new SliderSetting("Guard X", 0.0f, 1.0f, 0.02f, () -> anchorX, v -> anchorX = (float) v));
            list.add(new SliderSetting("Guard Y", 0.0f, 1.2f, 0.02f, () -> anchorY, v -> anchorY = (float) v));
            list.add(new SliderSetting("Guard Z", -0.5f, 0.5f, 0.02f, () -> anchorZ, v -> anchorZ = (float) v));
        }
        return list;
    }

    private int styleIndex() {
        for (int i = 0; i < STYLES.length; i++) {
            if (STYLES[i].equals(style)) return i;
        }
        return 0;
    }
}

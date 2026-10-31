package com.visualmod.module.modules.combat;

import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.sword.BladeSkinLink;

import java.util.ArrayList;
import java.util.List;

/**
 * Аура меча: свет вокруг клинка от первого лица (HandEffects) и в мире от F5 (CombatEffects),
 * плюс слой Skin — та же аура, вживлённая в текстуру меча анимированным стрипом.
 * Пак клинка один на двоих с BladeElements, применяет его арбитр BladeSkinLink.
 */
public class SwordAura extends Module implements Configurable {

    public static int color = 0xFF7B5CF0;
    public static float radius = 0.5f;
    public static float intensity = 1.0f;
    public static String style = "GLOW";

    public static boolean inHand = true;
    public static float anchorX = 0.5f;
    public static float anchorY = 0.62f;
    public static float anchorZ = 0.0f;

    public static float blade = 0.55f;
    public static float speed = 0.9f;
    public static float turns = 6.0f;

    /** Аура в самой текстуре меча: тот же стрип, что у BladeElements, поэтому видна и в дропе. */
    public static boolean skin = true;

    private static final String[] STYLES = {"GLOW", "PARTICLES", "RING", "CLOTH"};

    static {
        BladeSkinLink.init();
    }

    public SwordAura() {
        super("SwordAura", "Glowing aura around held weapon", Category.COMBAT);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> list = new ArrayList<>();
        list.add(new ColorSetting("Color", () -> color, c -> color = c));
        list.add(new SliderSetting("Radius", 0.2f, 1.5f, 0.1f, () -> radius, v -> radius = (float) v, "m"));
        list.add(new SliderSetting("Intensity", 0.2f, 2.0f, 0.1f, () -> intensity, v -> intensity = (float) v, "x"));
        list.add(new ModeSetting("Style", STYLES, () -> styleIndex(), i -> style = STYLES[i]));
        list.add(new SliderSetting("Blade", 0.2f, 1.0f, 0.05f, () -> blade, v -> blade = (float) v, "u"));
        list.add(new SliderSetting("Speed", 0.2f, 2.5f, 0.1f, () -> speed, v -> speed = (float) v, "x"));
        list.add(new SliderSetting("Turns", 2.0f, 14.0f, 0.5f, () -> turns, v -> turns = (float) v));
        list.add(new ModeSetting("In Hand", new String[]{"OFF", "ON"}, () -> inHand ? 1 : 0, i -> inHand = i == 1));
        if (inHand) {
            list.add(new SliderSetting("Guard X", 0.0f, 1.0f, 0.02f, () -> anchorX, v -> anchorX = (float) v));
            list.add(new SliderSetting("Guard Y", 0.0f, 1.2f, 0.02f, () -> anchorY, v -> anchorY = (float) v));
            list.add(new SliderSetting("Guard Z", -0.5f, 0.5f, 0.02f, () -> anchorZ, v -> anchorZ = (float) v));
        }
        list.add(new ModeSetting("Skin", new String[]{"OFF", "ON"}, () -> skin ? 1 : 0, i -> skin = i == 1));
        return list;
    }

    private int styleIndex() {
        for (int i = 0; i < STYLES.length; i++) {
            if (STYLES[i].equals(style)) return i;
        }
        return 0;
    }
}

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
 * BladeElements: стихия вживлена в саму текстуру меча — огонь лижет кромку, иней собирается
 * кристаллами, разряды прыгают по лезвию, пустота закручивает ленту. Кадры рисует BladeSkin,
 * стрип и mcmeta пишет BladeSkinPack, поэтому узор виден и в руке, и в дропе, и в инвентаре.
 *
 * Квадов поверх модели больше нет: они читались хуже, чем настоящая анимированная текстура.
 * Пак у клинка один на двоих с SwordAura, применяет его общий арбитр BladeSkinLink.
 */
public class BladeElements extends Module implements Configurable {

    public static final String[] ELEMENTS = {"FIRE", "FROST", "SHOCK", "VOID"};

    public static String element = "FIRE";
    public static float speed = 1.0f;
    public static int color = 0xFFFF7A1A;
    public static float skinStrength = 1.0f;

    static {
        BladeSkinLink.init();
    }

    public BladeElements() {
        super("BladeElements", "Elemental pattern animated inside the sword texture", Category.COMBAT);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Element", ELEMENTS, () -> elementIndex(), i -> element = ELEMENTS[i]));
        l.add(new SliderSetting("Speed", 0.2f, 3.0f, 0.1f, () -> speed, v -> speed = (float) v, "x"));
        l.add(new ColorSetting("Color", () -> color, c -> color = c));
        l.add(new SliderSetting("Strength", 0.3f, 2.0f, 0.1f, () -> skinStrength, v -> skinStrength = (float) v, "x"));
        return l;
    }

    private int elementIndex() {
        for (int i = 0; i < ELEMENTS.length; i++) if (ELEMENTS[i].equals(element)) return i;
        return 0;
    }
}

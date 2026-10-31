package com.visualmod.module.modules.player;

import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;

import java.util.ArrayList;
import java.util.List;

public class RespawnAnimation extends Module implements Configurable {

    public static int color = 0xFF7B5CF0;
    public static float duration = 1.8f;
    public static String style = RespawnFx.ASSEMBLE_NAME;
    public static boolean cinema = true;

    private static final String[] STYLES = {
            RespawnFx.ASSEMBLE_NAME, RespawnFx.SKY_NAME, RespawnFx.PORTAL_NAME };
    private static final String[] SOUND_MODES = {"OFF", "ON"};
    private static final String[] CAMERA_MODES = {"OFF", "ON"};

    public RespawnAnimation() {
        super("RespawnAnimation", "Visual effect and Re:Zero sound on respawn; cinema camera shows the scene from the side", Category.PLAYER);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> list = new ArrayList<>();
        list.add(new ColorSetting("Color", () -> color, c -> color = c));
        list.add(new SliderSetting("Duration", 0.6f, 4.0f, 0.2f, () -> duration, v -> duration = (float) v, "s"));
        list.add(new ModeSetting("Style", STYLES, () -> index(STYLES, style), i -> style = STYLES[i]));
        list.add(new ModeSetting("Cinema Camera", CAMERA_MODES, () -> cinema ? 1 : 0, i -> cinema = i == 1));
        list.add(new ModeSetting("Respawn Sound", SOUND_MODES, () -> index(SOUND_MODES, com.visualmod.sound.RespawnCue.mode),
                i -> com.visualmod.sound.RespawnCue.mode = SOUND_MODES[clamp(i, SOUND_MODES.length)]));
        list.add(new SliderSetting("Sound Volume", 0.1f, 1.0f, 0.05f,
                () -> com.visualmod.sound.RespawnCue.volume, v -> com.visualmod.sound.RespawnCue.volume = (float) v));
        return list;
    }

    @Override
    public void onDisable() {
        RespawnFx.cancel();
    }

    private static int index(String[] values, String current) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) return i;
        }
        return 0;
    }

    private static int clamp(int i, int size) {
        return Math.max(0, Math.min(size - 1, i));
    }
}

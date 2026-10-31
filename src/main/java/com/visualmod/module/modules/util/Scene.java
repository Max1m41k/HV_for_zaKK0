package com.visualmod.module.modules.util;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.util.BeatPulse;

import java.util.ArrayList;
import java.util.List;

/**
 * Единый ритм-профиль интерфейса. Момент доли и его сила живут в BeatPulse (он один слушает
 * WASAPI-метр и ищет скачки), а этот модуль решает, насколько сильно доля видно в трёх местах:
 * «дыхание» всего HUD, аура вокруг хотбара и подскок отдельных виджетов.
 *
 * Модуль выключен или режим OFF — все три потребителя работают ровно так, как работали без него:
 * gain 1.0, аура только от громкости, виджеты без подскока.
 */
public class Scene extends Module implements Configurable {

    public enum Look { OFF, SOFT, STAGE, HEAVY }

    public static Look look = Look.STAGE;
    public static double pulse = 100;
    public static double aura = 100;
    public static double widgets = 60;

    private final List<Setting> settings = new ArrayList<>();

    public Scene() {
        super("Scene", "One beat profile shared by HUD breath, hotbar aura and widget pop", Category.UTIL);
        settings.add(new ModeSetting("Mode", new String[]{"OFF", "SOFT", "STAGE", "HEAVY"},
                () -> look.ordinal(), i -> look = Look.values()[i]));
        settings.add(new SliderSetting("Pulse", 0, 200, 5, () -> pulse, v -> pulse = v, "%"));
        settings.add(new SliderSetting("Aura", 0, 200, 5, () -> aura, v -> aura = v, "%"));
        settings.add(new SliderSetting("Widgets", 0, 150, 5, () -> widgets, v -> widgets = v, "%"));
    }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }

    /** Общий профиль действует, только когда модуль включён и режим выбран. */
    public static boolean active() {
        Scene m = module();
        return m != null && m.isEnabled() && look != Look.OFF;
    }

    /** Множитель амплитуды «дыхания» HUD; 1.0 — профиль не вмешивается. */
    public static float pulseGain() {
        if (!active()) return 1f;
        return clampGain((float) (modeGain() * pulse / 100.0));
    }

    /** Аура хотбара: плавная громкость остаётся базой, доля добавляет сверху общий «щелчок». */
    public static float aura(float lvl) {
        if (!active()) return lvl;
        float beat = clamp01(BeatPulse.pulse() * clampGain((float) (modeGain() * aura / 100.0)));
        return Math.max(lvl * 0.7f, Math.min(1f, lvl * 0.3f + beat));
    }

    /** Масштаб отдельного виджета на доле: 1.0 — подскока нет. */
    public static float widgetScale() {
        if (!active() || widgets <= 0) return 1f;
        float g = clampGain((float) (modeGain() * widgets / 100.0));
        return 1f + 0.02f * g * clamp01(BeatPulse.pulse());
    }

    private static float modeGain() {
        return switch (look) {
            case OFF -> 0f;
            case SOFT -> 0.6f;
            case STAGE -> 1f;
            case HEAVY -> 1.7f;
        };
    }

    private static float clampGain(float v) {
        return Math.max(0f, Math.min(3.4f, v));
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(1f, v);
    }

    private static Scene module() {
        return VisualsMod.moduleManager == null ? null : VisualsMod.moduleManager.getModule(Scene.class);
    }
}

package com.visualmod.module.modules.util;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.record.Recording;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * HighlightReel: во время записи помечает каждый подтверждённый килл, а в просмотре клипа
 * проигрывает только нарезку — кусок «до удара … после смерти» с плавным перелётом камеры
 * между фрагментами.
 */
public class HighlightReel extends Module implements Configurable {

    public static float preRoll = 2.5f;
    public static float tail = 2.0f;
    public static float blend = 0.35f;
    public static float merge = 2.5f;
    public static boolean autoReel = true;
    public static boolean loop = true;
    public static boolean banner = true;

    public record Cut(float a, float b, String label) {}

    public HighlightReel() {
        super("HighlightReel", "Marks kills while recording and plays them as a cut reel", Category.UTIL);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new SliderSetting("Pre-roll", 0f, 5f, 0.1f, () -> preRoll, v -> preRoll = (float) v, "s"));
        l.add(new SliderSetting("Tail", 0f, 5f, 0.1f, () -> tail, v -> tail = (float) v, "s"));
        l.add(new SliderSetting("Blend", 0f, 1.2f, 0.05f, () -> blend, v -> blend = (float) v, "s"));
        l.add(new SliderSetting("Merge", 0f, 8f, 0.1f, () -> merge, v -> merge = (float) v, "s"));
        l.add(new ModeSetting("Auto reel", new String[]{"OFF", "ON"}, () -> autoReel ? 1 : 0, i -> autoReel = i == 1));
        l.add(new ModeSetting("Loop", new String[]{"OFF", "ON"}, () -> loop ? 1 : 0, i -> loop = i == 1));
        l.add(new ModeSetting("Banner", new String[]{"OFF", "ON"}, () -> banner ? 1 : 0, i -> banner = i == 1));
        return l;
    }

    public static boolean isOn() {
        Module m = VisualsMod.moduleManager == null ? null : VisualsMod.moduleManager.getModule(HighlightReel.class);
        return m != null && m.isEnabled();
    }

    /** Куски нарезки: киллы, лежащие ближе Merge секунд друг к другу, склеиваются в один фрагмент. */
    public static List<Cut> cuts(Recording r) {
        List<Cut> out = new ArrayList<>();
        if (r == null || r.marks.isEmpty()) return out;

        List<Recording.Mark> marks = new ArrayList<>(r.marks);
        marks.sort(Comparator.comparingDouble((Recording.Mark m) -> m.t));
        float dur = r.duration();
        if (dur <= 0f) return out;

        for (Recording.Mark m : marks) {
            float a = Math.max(0f, Math.min(dur, m.t - Math.max(0f, preRoll)));
            float b = Math.max(0f, Math.min(dur, m.t + Math.max(0f, tail)));
            if (b - a < 0.2f) continue;
            String label = m.label == null ? "" : m.label;
            if (!out.isEmpty()) {
                Cut last = out.get(out.size() - 1);
                if (a - last.b() <= Math.max(0f, merge)) {
                    out.set(out.size() - 1, new Cut(last.a(), Math.max(last.b(), b),
                            last.label().isEmpty() ? label : last.label()));
                    continue;
                }
            }
            out.add(new Cut(a, b, label));
        }
        return out;
    }

    /** Кадр, на который стоит перелететь при переключении куска. */
    public static float blendSeconds() {
        return Math.max(0.05f, blend);
    }
}

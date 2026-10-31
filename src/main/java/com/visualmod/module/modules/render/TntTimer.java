package com.visualmod.module.modules.render;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.util.TntWatch;

import java.util.Locale;

public class TntTimer extends Module {

    /** Дальше этого числа цифры всё равно не прочитать. */
    public static final double MAX_DISTANCE = 64.0;

    public TntTimer() {
        super("TntTimer", "Shows fuse time above lit TNT", Category.RENDER);
    }

    public static boolean isActive() {
        if (VisualsMod.moduleManager == null) return false;
        TntTimer m = VisualsMod.moduleManager.getModule(TntTimer.class);
        return m != null && m.isEnabled();
    }

    /** Кастомный TNT без синхронизированного фитиля показывается заглушкой, пока не выучен. */
    public static String label(TntWatch.Bomb b) {
        if (!b.fuseKnown()) return "TNT";
        return String.format(Locale.ROOT, "%.2fs", b.seconds());
    }

    public static int colorFor(TntWatch.Bomb b) {
        if (!b.fuseKnown()) return 0xFFFFAA00;
        double s = b.seconds();
        if (s <= 1.0) return 0xFFFF5555;
        if (s <= 2.0) return 0xFFFFAA00;
        return 0xFF55FF55;
    }
}

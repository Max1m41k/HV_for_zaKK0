package com.visualmod.module.modules.combat;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.module.settings.ToggleSetting;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Раскалка клинка от серии критов: каждый крит поднимает жар, обычный удар сбивает его,
 * после паузы клинок остывает. Градиент и искры рисует HandEffects в проходе руки.
 */
public class CritHeat extends Module implements Configurable {

    public static double window = 2.0;
    public static double gain = 0.34;
    public static int coolColor = 0xFFFFC24A;
    public static int hotColor = 0xFF9FE8FF;
    public static boolean embers = true;

    private static final long COOL_MS = 700;
    private static float heat = 0f;
    private static int streak = 0;
    private static long lastCritMs = 0L;

    public CritHeat() {
        super("CritHeat", "Crit streak heats the blade: glow and embers", Category.COMBAT);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new SliderSetting("Window", 0.6, 4.0, 0.1, () -> window, v -> window = v, "s"));
        l.add(new SliderSetting("Gain", 0.1, 0.8, 0.05, () -> gain, v -> gain = v));
        l.add(new ColorSetting("Cool", () -> coolColor, c -> coolColor = c));
        l.add(new ColorSetting("Hot", () -> hotColor, c -> hotColor = c));
        l.add(new ToggleSetting("Embers", () -> embers, v -> embers = v));
        return l;
    }

    @Override
    public void onEnable() { reset(); }

    @Override
    public void onDisable() { reset(); }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.isDead()) reset();
    }

    private static void reset() {
        heat = 0f;
        streak = 0;
        lastCritMs = 0L;
    }

    public static boolean active() {
        if (VisualsMod.moduleManager == null) return false;
        CritHeat m = VisualsMod.moduleManager.getModule(CritHeat.class);
        return m != null && m.isEnabled();
    }

    public static void onHit(boolean crit) {
        if (!active()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        long now = System.currentTimeMillis();
        if (crit) {
            if (now - lastCritMs > (long) (Math.max(0.4, window) * 1000)) streak = 0;
            streak++;
            heat = Math.min(1f, heat + (float) gain);
            lastCritMs = now;
        } else {
            heat *= 0.55f;
            if (heat < 0.02f) reset();
        }
    }

    /** Текущий жар 0..1 с остыванием после окна серии. */
    public static float heat() {
        if (heat <= 0.02f) return 0f;
        long idle = System.currentTimeMillis() - lastCritMs;
        long hold = (long) (Math.max(0.4, window) * 1000);
        if (idle <= hold) return Math.min(1f, heat);
        float k = 1f - (idle - hold) / (float) COOL_MS;
        if (k <= 0f) { reset(); return 0f; }
        return Math.min(1f, heat * k);
    }

    public static int streak() { return streak; }
}

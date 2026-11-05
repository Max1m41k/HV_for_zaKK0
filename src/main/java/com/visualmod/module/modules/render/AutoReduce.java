package com.visualmod.module.modules.render;

import com.visualmod.VisualsMod;
import com.visualmod.lang.Lang;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.module.settings.ToggleSetting;
import com.visualmod.util.Diag;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * «Автоматически бережем FPS»: когда кадры падают ниже порога, мод на время вырезает часть частиц,
 * гало и экранные эффекты, а когда fps возвращается и держится несколько секунд — всё возвращает.
 * Настройки пользователя не трогаем: сокращение живёт только в этом модуле.
 */
public class AutoReduce extends Module implements Configurable {

    public static double fpsTarget = 45;
    public static double cutParticles = 55;
    public static int holdSeconds = 4;
    public static boolean cutGlow = true;
    public static boolean cutEffects = true;
    public static boolean announce = true;

    private static volatile boolean reduced;
    private static int lowTicks, highTicks, particleCursor;

    private final List<Setting> settings = new ArrayList<>();

    public AutoReduce() {
        super("AutoReduce", "Trims particles, glow and effects during fps drops, then restores them", Category.RENDER);
        settings.add(new SliderSetting("Below FPS", 10, 120, 5, () -> fpsTarget, v -> fpsTarget = v));
        settings.add(new SliderSetting("Cut Particles", 10, 90, 5, () -> cutParticles, v -> cutParticles = v, "%"));
        settings.add(new SliderSetting("Hold", 2, 15, 1, () -> holdSeconds, v -> holdSeconds = (int) v, "s"));
        settings.add(new ToggleSetting("Cut Glow", () -> cutGlow, v -> cutGlow = v));
        settings.add(new ToggleSetting("Cut Effects", () -> cutEffects, v -> cutEffects = v));
        settings.add(new ToggleSetting("Announce", () -> announce, v -> announce = v));
    }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }

    @Override
    public void onDisable() {
        reduced = false;
        lowTicks = highTicks = 0;
    }

    @Override
    public void onTick() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null) {
                reduced = false;
                lowTicks = highTicks = 0;
                return;
            }
            float low = (float) fpsTarget;
            int fps = mc.getCurrentFps();

            if (fps <= low) {
                highTicks = 0;
                if (!reduced && ++lowTicks >= 25) {
                    reduced = true;
                    say(mc, Lang.isRu() ? "§eTriadVisual §7— бережём FPS: частицы и свечение урезаны"
                            : "§eTriadVisual §7— saving fps: particles and glow trimmed");
                }
            } else if (fps >= low + 12) {
                lowTicks = 0;
                if (reduced && ++highTicks >= holdSeconds * 20) {
                    reduced = false;
                    say(mc, Lang.isRu() ? "§aTriadVisual §7— эффекты возвращены"
                            : "§aTriadVisual §7— effects restored");
                }
            } else {
                // Серая зона между порогом и порогом+12: держим текущее состояние, чтобы не мигать.
                lowTicks = highTicks = 0;
            }
        } catch (Throwable t) {
            Diag.once("AutoReduce.tick", t);
        }
    }

    private static void say(MinecraftClient mc, String text) {
        if (!announce) return;
        try {
            mc.player.sendMessage(Text.literal(text), true);
        } catch (Throwable t) {
            Diag.once("AutoReduce.say", t);
        }
    }

    public static boolean isActive() {
        if (VisualsMod.moduleManager == null) return false;
        AutoReduce m = VisualsMod.moduleManager.getModule(AutoReduce.class);
        return m != null && m.isEnabled();
    }

    public static boolean reduced() {
        return reduced && isActive();
    }

    public static boolean glow() {
        return reduced() && cutGlow;
    }

    public static boolean effects() {
        return reduced() && cutEffects;
    }

    /**
     * Пропускаем часть частиц шагом по счётчику: без генератора случайных чисел и без скачков —
     * поток режется плавно, а порядок появления сохраняется.
     */
    public static boolean shouldSkipParticle() {
        if (!reduced() || cutParticles <= 0) return false;
        int keep = Math.max(5, 100 - (int) cutParticles);
        particleCursor = (particleCursor + 1) % 100;
        return particleCursor >= keep;
    }
}

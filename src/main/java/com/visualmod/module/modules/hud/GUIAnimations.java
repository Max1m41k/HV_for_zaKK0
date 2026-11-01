package com.visualmod.module.modules.hud;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.sound.ModSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

import java.util.Arrays;
import java.util.List;

public class GUIAnimations extends Module implements Configurable {

    public enum Style { FADE, SLIDE, SCALE, POP, STEPS }

    public enum Sound {
        NONE, AKRON, AKROFF, BELL, BONK, BUBBLE, CELON, CELOFF,
        DISABLEOLD, ENABLEOLD, METALLIC, NURON, NUROFF
    }

    public static Style style = Style.FADE;
    public static Sound sound = Sound.NONE;
    public static double durationMs = 400;

    private long enabledAt = 0;

    private final List<Setting> settings = List.of(
            new ModeSetting(
                    "Style",
                    Arrays.stream(Style.values()).map(Enum::name).toArray(String[]::new),
                    () -> style.ordinal(),
                    i -> style = Style.values()[i]
            ),
            new SliderSetting(
                    "Speed", 100, 1000, 50,
                    () -> durationMs,
                    v -> durationMs = v,
                    "ms"
            ),
            new ModeSetting(
                    "Sound",
                    Arrays.stream(Sound.values()).map(Enum::name).toArray(String[]::new),
                    () -> sound.ordinal(),
                    i -> sound = Sound.values()[i]
            )
    );

    public GUIAnimations() {
        super("GUIAnimations", "Animated GUI transitions", Category.HUD);
    }

    @Override
    public void onEnable() {
        enabledAt = System.currentTimeMillis();
        playSound();
    }

    public void triggerOpen() {
        if (isEnabled()) {
            enabledAt = System.currentTimeMillis();
        }
    }

    /**
     * Звук открытия GUI по клавише. GUIAnimations выключен — играем мягкий дефолт,
     * включён с Sound=NONE — молчим: NONE это и есть явный выбор пользователя.
     */
    public static void playOpen() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getSoundManager() == null || VisualsMod.moduleManager == null) return;
        GUIAnimations ga = VisualsMod.moduleManager.getModule(GUIAnimations.class);
        if (ga != null && ga.isEnabled()) {
            if (sound != Sound.NONE) ga.playSound();
            return;
        }
        mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.35f, 1.5f));
    }

    private void playSound() {
        SoundEvent event = switch (sound) {
            case NONE -> null;
            case AKRON -> ModSounds.AKRON;
            case AKROFF -> ModSounds.AKROFF;
            case BELL -> ModSounds.BELL;
            case BONK -> ModSounds.BONK;
            case BUBBLE -> ModSounds.BUBBLE;
            case CELON -> ModSounds.CELON;
            case CELOFF -> ModSounds.CELOFF;
            case DISABLEOLD -> ModSounds.DISABLEOLD;
            case ENABLEOLD -> ModSounds.ENABLEOLD;
            case METALLIC -> ModSounds.METALLIC;
            case NURON -> ModSounds.NURON;
            case NUROFF -> ModSounds.NUROFF;
        };
        if (event == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getSoundManager() != null) {
            mc.getSoundManager().play(PositionedSoundInstance.ui(event, 1.0f));
        }
    }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }

    public float getProgress() {
        if (!isEnabled()) return 1f;
        float t = (System.currentTimeMillis() - enabledAt) / (float) durationMs;
        return Math.min(1f, Math.max(0f, t));
    }

    public float getEasedProgress() {
        float p = getProgress();
        return 1f - (1f - p) * (1f - p);
    }

    public float getFade() {
        return getEasedProgress();
    }

    public Transform getTransform() {
        float p = getEasedProgress();
        return switch (style) {
            case FADE -> new Transform(p, 1f, 0f);
            case SLIDE -> new Transform(p, 1f, (1f - p) * 24f);
            case SCALE -> new Transform(p, 0.86f + p * 0.14f, 0f);
            case POP -> new Transform(p, easeOutBack(p), 0f);
            // STEPS анимирует сами колонки (stepsColumnAlpha/stepsRowAlpha), окно остаётся на месте.
            case STEPS -> Transform.IDENTITY;
        };
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float x = t - 1f;
        return 1f + c3 * x * x * x + c1 * x * x;
    }

    public static float globalFade() {
        GUIAnimations mod = get();
        return mod != null && mod.isEnabled() ? mod.getFade() : 1f;
    }

    public static Transform globalTransform() {
        GUIAnimations mod = get();
        return mod != null && mod.isEnabled() ? mod.getTransform() : Transform.IDENTITY;
    }

    public static float staggerAlpha(int index) {
        GUIAnimations mod = get();
        if (mod == null || !mod.isEnabled()) return 1f;
        float p = mod.getEasedProgress();
        float start = Math.min(0.6f, index * 0.03f);
        return Math.max(0f, Math.min(1f, (p - start) / (1f - start)));
    }

    private static GUIAnimations get() {
        if (com.visualmod.VisualsMod.moduleManager == null) return null;
        return com.visualmod.VisualsMod.moduleManager.getModule(GUIAnimations.class);
    }

    /** STEPS включён: колонки появляются волной от центра, внутри колонки — снизу вверх. */
    public static boolean stepsActive() {
        GUIAnimations mod = get();
        return mod != null && mod.isEnabled() && style == Style.STEPS;
    }

    /** Доля появления колонки; ring — расстояние в колонках от центральной (0 = центр). */
    public static float stepsColumnAlpha(int ring) {
        GUIAnimations mod = get();
        if (mod == null || !mod.isEnabled()) return 1f;
        float p = mod.getEasedProgress();
        float start = Math.min(0.65f, Math.max(0, ring) * 0.13f);
        return clamp((p - start) / Math.max(0.25f, 1f - start));
    }

    /**
     * Появление строки внутри уже проявляющейся колонки: y измеряется сверху вниз, поэтому
     * нижние строки (f≈0) видны раньше верхних — затухание «наоборот».
     */
    public static float stepsRowAlpha(float reveal, int listY, int listH, int rowY, int rowH) {
        if (listH <= 0) return reveal;
        float f = clamp(1f - (rowY + rowH - listY) / (float) listH);
        return clamp((reveal - f * 0.5f) / (1f - f * 0.5f));
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    public record Transform(float alpha, float scale, float offsetY) {
        public static final Transform IDENTITY = new Transform(1f, 1f, 0f);
    }
}

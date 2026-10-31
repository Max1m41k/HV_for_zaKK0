package com.visualmod.module.modules.hud;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.module.modules.util.Scene;
import com.visualmod.util.BrowserLoudness;
import com.visualmod.util.BrowserMedia;
import com.visualmod.util.BeatPulse;
import com.visualmod.util.Diag;
import com.visualmod.util.HudStyle;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Аура музыки: переливающееся сияние расходится КОЛЬЦАМИ ВОКРУГ всего хотбара — сверху, снизу
 * и по бокам, а не только над слотами. Чем громче музыка, тем шире кольцо.
 *
 * Плавность: громкость сглаживается экспоненциальным фильтром, привязанным к реальному времени
 * между кадрами (nanoTime), поэтому скорость затухания не зависит от FPS; радиус дополнительно
 * пропускается через второй фильтр, а кольцо нарисовано тонкими слоями, чтобы градиент читался
 * как сплошной.
 *
 * Цвет не настраивается специально — он всё время плывёт по радуге, поэтому аура выглядит живой.
 * Громкость проходит через гамму 0.55: спокойный кусок трека уже даёт читаемое свечение, а припев
 * раскручивает кольцо до упора. Поверх кольца на каждой доле выстреливает тонкая вспышка.
 * Геометрия привязана к нашему хотбару (HotbarHUD: slotSize 20, x = (ширина - 188) / 2).
 *
 * Громкость берётся из BrowserLoudness (пик WASAPI default render endpoint). Meter один на
 * всё устройство и слышит тоже звуки игры, поэтому loudness() отдаёт не мгновенный пик, а
 * уровень, продержавшийся почти полторы секунды: удар меча, взрыв или шагающий звук кольцо не
 * раскачают — реагирует только идущая музыка (плюс условие «BrowserMedia сообщает: играет»).
 */
public class MusicDrag extends Module implements Configurable {

    public static double height = 44;
    public static double sensitivity = 2.2;
    public static double damping = 0.5;

    private static final int SLOTS = 9;
    private static final int SLOT = 20;
    private static final int LAYERS = 30;

    private static float value;
    private static float reach;
    private static long lastFrame;
    private static long lastMediaCheck;

    static { HudRenderCallback.EVENT.register((ctx, tc) -> draw(ctx)); }

    public MusicDrag() {
        super("MusicDrag", "Iridescent music aura rising out of the hotbar", Category.HUD);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new SliderSetting("Height", 12, 72, 1, () -> height, v -> height = v, "px"));
        l.add(new SliderSetting("Sensitivity", 0.5, 4.0, 0.1, () -> sensitivity, v -> sensitivity = v, "x"));
        l.add(new SliderSetting("Damping", 0.0, 0.9, 0.05, () -> damping, v -> damping = v));
        return l;
    }

    @Override
    public void onEnable() {
        reset();
        BrowserLoudness.start();
        ensureMedia();
    }

    @Override
    public void onDisable() {
        BrowserLoudness.stop();
        reset();
        stopMediaIfAlone();
    }

    private static void reset() {
        value = 0f;
        reach = 0f;
        lastFrame = 0L;
    }

    /** Источник музыки нужен только для привязки «играет / не играет» — не мешаем Плееру. */
    private static void ensureMedia() {
        try {
            if (BrowserMedia.track() != null) return;
            if (ownsOverlay()) return;
            BrowserMedia.start(BrowserMedia.Source.SMTC);
        } catch (Throwable t) {
            Diag.once("MusicDrag.media", t);
        }
    }

    private static void stopMediaIfAlone() {
        try {
            if (ownsOverlay()) return;
            BrowserMedia.stop();
        } catch (Throwable t) {
            Diag.once("MusicDrag.media.stop", t);
        }
    }

    /** True, когда Плеер включён на локальном плейлисте — его источник трогать нельзя. */
    private static boolean ownsOverlay() {
        MusicOverlay mo = VisualsMod.moduleManager == null
                ? null : VisualsMod.moduleManager.getModule(MusicOverlay.class);
        return mo != null && mo.isEnabled() && MusicOverlay.source == 0;
    }

    private static void draw(DrawContext ctx) {
        try {
            if (VisualsMod.moduleManager == null) return;
            MusicDrag m = VisualsMod.moduleManager.getModule(MusicDrag.class);
            MinecraftClient mc = MinecraftClient.getInstance();
            // в спектаторе хотбара нет — ауре не из чего выходить
            if (m == null || !m.isEnabled() || mc.player == null || mc.player.isSpectator()
                    || mc.options.hudHidden) return;

            long nowMs = System.currentTimeMillis();
            long nowNs = System.nanoTime();
            if (nowMs - lastMediaCheck > 2500L) {
                lastMediaCheck = nowMs;
                ensureMedia();
            }

            float target = clamp01(BrowserLoudness.loudness() * (float) sensitivity);
            float dt = lastFrame <= 0L ? 1f / 60f
                    : Math.min(0.25f, Math.max(1f / 240f, (nowNs - lastFrame) / 1_000_000_000f));
            lastFrame = nowNs;

            // Экспоненциальный фильтр с шагом, пересчитанным от РЕАЛЬНОГО времени между кадрами:
            // только так аура одинаково плавно и растёт, и затухает при любом FPS.
            double damp = Math.min(0.9, Math.max(0.0, damping));
            float rate = target > value
                    ? (float) (6.0 + 26.0 * (1.0 - damp))
                    : (float) (1.6 + 5.0 * (1.0 - damp));
            value += (target - value) * (1f - (float) Math.exp(-rate * dt));
            if (value < 0f) value = 0f;
            // Второй фильтр — на сам радиус: громкость может прыгнуть, кольцо обязано только расплываться.
            reach += (value - reach) * (1f - (float) Math.exp(-9.0 * dt));
            // Scene подмешивает общий доля из BeatPulse: без него функция возвращает lvl как есть.
            float lvl = Scene.aura(clamp01(reach * 1.15f));
            // Гамма 0.55: WASAPI-пик тихого припева — это 0.2..0.35, и без неё кольцо оставалось
            // «едва теплится». Степень делает так, чтобы спокойная музыка уже давала заметное
            // свечение, а громкая раскручивала его до упора.
            float shape = (float) Math.pow(lvl, 0.55);
            float beat = clamp01(BeatPulse.pulse());
            float total = (float) Math.max(6, height) * shape;
            if (total < 1.2f) return;

            int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
            int w = SLOTS * SLOT + 8, h = SLOT + 8;
            int x = (sw - w) / 2, y = sh - h - 2;
            float t = nowMs / 1000f;

            float band = total / LAYERS;
            float thick = band + 1.7f;
            // Вниз экрана тянуть нельзя — под хотбаром остаётся 2 пикселя, поэтому нижняя
            // половина кольца расходится медленнее остальных.
            final float bottom = 0.55f;
            for (int j = 0; j < LAYERS; j++) {
                float k = j / (float) (LAYERS - 1);            // 0 — у хотбара, 1 — внешняя кромка
                float g = 1.5f + j * band;
                float gb = 1.5f + j * band * bottom;
                float inset = j * total * 0.006f;              // кольцо мягко сужается наружу
                float wob = 1f + 0.07f * (float) Math.sin(t * 2.4 + j * 0.5);
                // Показатель 1.15 вместо 1.7: свет долетает до внешней кромки, а не гаснет в трёх пикселях.
                float a = (float) Math.pow(1f - k, 1.15f) * (0.42f + 0.58f * lvl + 0.25f * beat) * wob;
                int col = pack(hsv(t * 0.16f + j * 0.030f, 0.52f, 1f), a * 0.92f);

                float rx = x - inset, ry = y - inset;
                float rw = w + inset * 2f, rh = h + inset * 2f;
                // Рамка из четырёх полос: верх и низ перекрывают боковые полосы по всей ширине,
                // поэтому в углах кольца не остаётся дыр.
                HudStyle.fillRounded(ctx, rx - g - thick, ry - g - thick, rw + g * 2f + thick * 2f, thick,
                        thick * 0.5f, col);
                HudStyle.fillRounded(ctx, rx - g - thick, ry + rh + gb, rw + g * 2f + thick * 2f, thick,
                        thick * 0.5f, col);
                HudStyle.fillRounded(ctx, rx - g - thick, ry - g, thick, rh + g + gb, thick * 0.5f, col);
                HudStyle.fillRounded(ctx, rx + rw + g, ry - g, thick, rh + g + gb, thick * 0.5f, col);
            }

            // Вспышка на доле: тонкое кольцо выстреливает от кромки хотбара наружу. Оно привязано
            // к BeatPulse, поэтому видно именно момент удара, а не общее «тепло».
            if (beat > 0.05f) {
                float fr = 2.5f + (float) Math.max(10, height) * (0.35f + 0.65f * shape) * beat;
                float ft = 1.2f + 2.2f * (1f - beat);
                int flare = pack(hsv(t * 0.16f + 0.08f, 0.28f, 1f), 0.85f * beat);
                HudStyle.fillRounded(ctx, x - fr - ft, y - fr - ft, w + fr * 2f + ft * 2f, ft, ft * 0.5f, flare);
                HudStyle.fillRounded(ctx, x - fr - ft, y + h + fr, w + fr * 2f + ft * 2f, ft, ft * 0.5f, flare);
                HudStyle.fillRounded(ctx, x - fr - ft, y - fr, ft, h + fr * 2f, ft * 0.5f, flare);
                HudStyle.fillRounded(ctx, x + w + fr, y - fr, ft, h + fr * 2f, ft * 0.5f, flare);
            }

            // Яркая кромка вплотную к хотбару: то же кольцо, но минимального радиуса и плотнее.
            float coreT = Math.min(5.5f, 2.2f + 3.0f * shape) + 1.4f * beat;
            int core = pack(hsv(t * 0.16f, 0.22f, 1f), 0.66f + 0.34f * lvl);
            HudStyle.fillRounded(ctx, x - coreT - 1, y - coreT - 1, w + (coreT + 1) * 2f, coreT + 1.5f, coreT, core);
            HudStyle.fillRounded(ctx, x - coreT - 1, y - coreT - 1, coreT, h + coreT * 2f + 1f, coreT, core);
            HudStyle.fillRounded(ctx, x + w, y - coreT - 1, coreT, h + coreT * 2f + 1f, coreT, core);
            // Снизу влезает только то, что не упёрлось за край экрана.
            float below = Math.min(coreT, sh - (y + h) - 1f);
            if (below > 0.6f) {
                HudStyle.fillRounded(ctx, x - coreT - 1, y + h, w + (coreT + 1) * 2f, below, coreT, core);
            }
        } catch (Throwable t) {
            Diag.once("MusicDrag", t);
        }
    }

    private static int pack(int rgb, float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    /** Переливающийся цвет: hue плывёт по кругу, насыщенность и яркость фиксированы. */
    private static int hsv(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        int i = (int) (h * 6f);
        float f = h * 6f - i;
        float p = v * (1f - s);
        float q = v * (1f - f * s);
        float u = v * (1f - (1f - f) * s);
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = u; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = u; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = u; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return (Math.round(r * 255f) << 16) | (Math.round(g * 255f) << 8) | Math.round(b * 255f);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}

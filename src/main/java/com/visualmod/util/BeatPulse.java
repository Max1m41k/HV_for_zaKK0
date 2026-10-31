package com.visualmod.util;

import com.visualmod.VisualsMod;
import com.visualmod.module.modules.hud.MusicDrag;
import com.visualmod.module.modules.hud.MusicOverlay;
import net.minecraft.client.MinecraftClient;

/**
 * Встроенный пульс интерфейса: весь HUD «дышит» в такт музыке, которая играет в браузере.
 *
 * Моменты доли ищем по скачку громкости (WASAPI-метр из BrowserLoudness), а не по таймеру:
 * только так пульс не расходится с треком и не зависит от FPS. Когда музыка не играет,
 * scale остаётся ровно 1.0 — интерфейс не шевелится вообще.
 *
 * Мы — единственные часы ритма: pulse() отсюда берут Scene (аура хотбара и подскок виджетов),
 * чтобы весь интерфейс щёлкал на одну и ту же долю, а не из трёх независимых фильтров.
 */
public final class BeatPulse {

    private static final float MAX_BREATH = 0.014f;
    private static final long MIN_GAP_MS = 190L;
    private static final long IDLE_MS = 4000L;

    private static float env, avg, jumpAvg, last, strength, breath = 1f;
    private static long lastSample, lastBeat = -IDLE_MS, lastMeterCheck;
    private static final long[] gaps = new long[8];
    private static int gapCount;
    private static float bpm;
    private static boolean ownsMeter, inWorld;

    private BeatPulse() {}

    /** 0..1 — сила последнего доли. */
    public static float pulse() {
        return strength;
    }

    /** Масштаб всего HUD: 1.0 — покоится, до 1.014 на сильном доле. */
    public static float hudScale() {
        return breath;
    }

    /** Оценка темпа, чтобы «Плеер» мог показать BPM; 0 — ритм ещё не пойман. */
    public static float bpm() {
        return bpm;
    }

    public static void init() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                boolean now = client.world != null && client.player != null;
                if (now && !inWorld) startMeter();
                if (!now && inWorld) releaseMeter();
                inWorld = now;
            } catch (Throwable t) {
                Diag.once("BeatPulse.tick", t);
            }
        });
    }

    /** Вызывается из миксина HUD ровно один раз за кадр. */
    public static void update() {
        long now = System.currentTimeMillis();
        float dt = lastSample == 0L ? 1f / 60f
                : Math.min(0.1f, Math.max(0.001f, (now - lastSample) / 1000f));
        lastSample = now;

        MinecraftClient mc = MinecraftClient.getInstance();
        boolean hud = mc.world != null && mc.player != null && !mc.options.hudHidden;
        if (hud && now - lastMeterCheck > 3000L) {
            lastMeterCheck = now;
            if (!BrowserLoudness.isActive()) startMeter();
        }
        float v = hud ? BrowserLoudness.loudness() : 0f;

        float jump = v - last;
        last = v;
        env = Math.max(v, env - dt * 1.8f);
        avg += (env - avg) * Math.min(1f, dt * 0.55f);
        float pos = jump > 0f ? jump : 0f;
        jumpAvg += (pos - jumpAvg) * Math.min(1f, dt * 1.6f);

        if (pos > Math.max(0.035f, jumpAvg * 1.9f) && env > avg * 1.15f && env > 0.05f
                && now - lastBeat > MIN_GAP_MS) {
            onBeat(now);
        }
        if (now - lastBeat > IDLE_MS) {
            strength = 0f;
            env = 0f;
            avg = 0f;
            jumpAvg = 0f;
            bpm = 0f;
            gapCount = 0;
        }

        strength = Math.max(0f, strength - dt / release());
        // Амплитуду даёт Scene (общий ритм-профиль); без него множитель ровно 1.0.
        float maxBreath = MAX_BREATH * com.visualmod.module.modules.util.Scene.pulseGain();
        float target = 1f + maxBreath * strength * strength * (3f - 2f * strength);
        breath += (target - breath) * Math.min(1f, dt * 22f);
        if (target <= 1.0002f && breath < 1.0004f) breath = 1f;
    }

    private static void onBeat(long now) {
        long gap = now - lastBeat;
        if (gap > MIN_GAP_MS && gap < 2400L) {
            gaps[gapCount++ % gaps.length] = gap;
            bpm = bpmFromGaps();
        }
        lastBeat = now;
        strength = Math.min(1f, 0.45f + last * 3.5f);
    }

    /** Спад подобран от темпа: на быстрых треках пульс успевает погаснуть до следующего доли. */
    private static float release() {
        if (bpm <= 0f) return 0.26f;
        return Math.min(0.5f, Math.max(0.14f, 60f / bpm * 0.5f));
    }

    private static float bpmFromGaps() {
        int n = Math.min(gapCount, gaps.length);
        if (n < 3) return 0f;
        long[] tmp = new long[n];
        System.arraycopy(gaps, 0, tmp, 0, n);
        java.util.Arrays.sort(tmp);
        long median = tmp[n / 2];
        if (median < 240L || median > 1500L) return 0f;
        return 60000f / median;
    }

    /** Кольцо вокруг выбранного слота: маркер доли там, где глаз и так держит хотбар. */
    public static void drawHotbarBeat(net.minecraft.client.gui.DrawContext ctx) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden || strength < 0.08f) return;
            int centerX = ctx.getScaledWindowWidth() / 2;
            int top = ctx.getScaledWindowHeight() - 19;
            float x = centerX - 91f + mc.player.getInventory().getSelectedSlot() * 20f;
            float y = top - 2f;
            float s = strength;
            float t = 1.1f + 1.9f * s;
            int col = HudStyle.pack(1f, 0.60f + 0.32f * s, 0.14f, 0.28f + 0.55f * s);
            HudStyle.fillRounded(ctx, x - t, y - t, 24f + t * 2f, t, t * 0.5f, col);
            HudStyle.fillRounded(ctx, x - t, y + 24f, 24f + t * 2f, t, t * 0.5f, col);
            HudStyle.fillRounded(ctx, x - t, y, t, 24f, t * 0.5f, col);
            HudStyle.fillRounded(ctx, x + 24f, y, t, 24f, t * 0.5f, col);
        } catch (Throwable tr) {
            Diag.once("BeatPulse.hotbar", tr);
        }
    }

    private static synchronized void startMeter() {
        try {
            if (!BrowserMedia.isRunning()) {
                if (ownedByModules()) return;
                BrowserMedia.start(BrowserMedia.Source.SMTC);
                ownsMeter = true;
            }
            BrowserLoudness.start();
        } catch (Throwable t) {
            Diag.once("BeatPulse.meter", t);
        }
    }

    private static synchronized void releaseMeter() {
        try {
            if (ownsMeter && !ownedByModules()) BrowserMedia.stop();
            ownsMeter = false;
        } catch (Throwable t) {
            Diag.once("BeatPulse.meter.stop", t);
        }
    }

    /** «Плеер» на локальном плейлисте и «Перетаскивание» сами поднимают источник — не мешаем. */
    private static boolean ownedByModules() {
        if (VisualsMod.moduleManager == null) return false;
        MusicOverlay mo = VisualsMod.moduleManager.getModule(MusicOverlay.class);
        if (mo != null && mo.isEnabled() && MusicOverlay.source == 0) return true;
        MusicDrag md = VisualsMod.moduleManager.getModule(MusicDrag.class);
        return md != null && md.isEnabled();
    }
}

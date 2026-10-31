package com.visualmod.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Счётчик приёма пакетов. Раньше был отдельным модулем PacketMeter, теперь это только ядро
 * цифр — рисует чип «Packets» в InterfaceHUD.
 *
 * <p>Счётчик ведём сами на io-потоке netty, поэтому цифры свежие, а не из ванильного среднего
 * за 10 секунд. TCP скрывает настоящие потери, поэтому «потери» стоит читать как долю окон
 * приёма, когда поток пакетов проваливался ниже 60 % наблюдённого уровня; «зависла» — сколько
 * прошло с последнего входящего пакета; дрожание — разброс серверного пинга.
 */
public final class PacketStats {

    private static final int HIST = 30;
    private static final int PINGS = 12;
    private static final int WINDOW_TICKS = 10;

    public static double smoothing = 40;

    private static final AtomicLong INBOUND = new AtomicLong();
    private static volatile long lastInboundNs = 0L;

    private static final float[] pps = new float[HIST];
    private static final int[] pingBuf = new int[PINGS];
    private static int ppsCount, pingCount, tick;
    private static float peak, loss, jitter;
    private static long stallMs;
    private static boolean armed;

    private PacketStats() {}

    /** Каждый входящий пакет из mixin; вызывается на io-потоке netty, поэтому только атомарные поля. */
    public static void recordInbound() {
        lastInboundNs = System.nanoTime();
        INBOUND.incrementAndGet();
    }

    /** Пакетов в секунду за последнее окно. */
    public static float rate() {
        return pps[HIST - 1];
    }

    public static float lossPct() {
        return loss;
    }

    public static float jitterMs() {
        return jitter;
    }

    /** Сколько миллисекунд прошло с последнего входящего пакета. */
    public static long stallMs() {
        return stallMs;
    }

    public static boolean stalled() {
        return stallMs > 700L;
    }

    /** Пинг из списка игроков; -1, если сервер его ещё не отдал. */
    public static int ping(MinecraftClient mc) {
        try {
            ClientPlayNetworkHandler nh = mc == null ? null : mc.getNetworkHandler();
            if (nh == null || mc.player == null) return -1;
            PlayerListEntry e = nh.getPlayerListEntry(mc.player.getUuid());
            return e == null ? -1 : e.getLatency();
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Окно счётчика: ведём его из onTick у InterfaceHUD, чипу цифры нужны всегда. */
    public static void pump() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.getNetworkHandler() == null) return;

            if (!armed) {
                armed = true;
                INBOUND.set(0L);
                lastInboundNs = System.nanoTime();
                return;
            }
            if (++tick % WINDOW_TICKS != 0) return;

            stallMs = Math.max(0L, (System.nanoTime() - lastInboundNs) / 1_000_000L);
            sample(INBOUND.getAndSet(0L) * (1000f / (WINDOW_TICKS * 50f)));
            samplePing(mc);
        } catch (Throwable t) {
            Diag.once("PacketStats.pump", t);
        }
    }

    private static void sample(float instant) {
        System.arraycopy(pps, 1, pps, 0, HIST - 1);
        pps[HIST - 1] = instant;
        if (ppsCount < HIST) ppsCount++;

        float max = 0f;
        for (int i = HIST - ppsCount; i < HIST; i++) max = Math.max(max, pps[i]);
        float k = 1f - (float) Math.min(0.9, Math.max(0.0, smoothing / 100.0));
        peak = peak <= 0f ? max : peak + (max - peak) * k;
        if (max <= 1f) return;

        int bad = 0;
        for (int i = HIST - ppsCount; i < HIST; i++) {
            if (pps[i] < peak * 0.6f) bad++;
        }
        loss += (100f * bad / ppsCount - loss) * 0.25f;
    }

    private static void samplePing(MinecraftClient mc) {
        int p = ping(mc);
        if (p < 0 || (pingCount > 0 && pingBuf[PINGS - 1] == p)) return;
        System.arraycopy(pingBuf, 1, pingBuf, 0, PINGS - 1);
        pingBuf[PINGS - 1] = p;
        if (pingCount < PINGS) pingCount++;

        float sum = 0f;
        for (int i = PINGS - pingCount + 1; i < PINGS; i++) sum += Math.abs(pingBuf[i] - pingBuf[i - 1]);
        jitter = pingCount < 2 ? 0f : sum / (pingCount - 1);
    }
}

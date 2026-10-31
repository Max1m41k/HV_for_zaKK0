package com.visualmod.sound;

import com.visualmod.module.modules.player.RespawnFx;
import com.visualmod.util.Diag;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.world.ClientWorld;

public final class RespawnCue {

    public static String mode = "ON";
    public static float volume = 0.8f;

    private static boolean wasDead = false;
    private static ClientWorld lastWorld;
    private static ClientPlayerEntity lastPlayer;

    private RespawnCue() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(RespawnCue::tick);
    }

    private static void tick(MinecraftClient mc) {
        ClientWorld world = mc.world;
        ClientPlayerEntity player = mc.player;

        // Новый мир (заход на сервер, смена измерения) — не считаем это респавном.
        if (world != lastWorld) {
            lastWorld = world;
            lastPlayer = player;
            wasDead = false;
            return;
        }
        if (player == null) return;

        if (player.isDead() || player.getHealth() <= 0f || mc.currentScreen instanceof DeathScreen) {
            wasDead = true;
            lastPlayer = player;
            return;
        }

        // Смена экземпляра игрока в том же мире = респавн, в том числе мгновенный на серверах
        // с авто-респавном, где состояние «мёртв» не успевает ни в один тик.
        boolean respawned = wasDead || player != lastPlayer;
        lastPlayer = player;
        if (!respawned) return;

        wasDead = false;
        try {
            RespawnFx.trigger(mc);
        } catch (Throwable t) {
            Diag.once("RespawnFx", t);
        }
        play(mc);
    }

    private static void play(MinecraftClient mc) {
        if ("OFF".equals(mode)) return;
        // Раньше звук требовал включённого модуля анимации и молчал у всех,
        // у кого анимация выключена — за звук отвечает только его собственная настройка.
        mc.getSoundManager().play(PositionedSoundInstance.ui(
                ModSounds.REZERO_RETURN, 1.0f, Math.max(0.05f, Math.min(1f, volume))));
    }
}

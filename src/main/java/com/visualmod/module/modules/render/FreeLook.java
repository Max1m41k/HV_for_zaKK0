package com.visualmod.module.modules.render;

import com.visualmod.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;

public class FreeLook extends Module {

    public static float cameraYaw, cameraPitch;
    private static Perspective previous = null;
    private static boolean active = false;
    private static Object lastPlayer = null;

    public FreeLook() { super("FreeLook", "Look around without turning", Category.RENDER); }

    public static boolean isActive() { return active; }

    @Override public void onEnable() { activate(); }

    @Override public void onDisable() { deactivate(); }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) { if (active) deactivate(); return; }
        if (!active || lastPlayer != mc.player) activate();
    }

    private static void activate() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options == null) return;
        cameraYaw = mc.player.getYaw();
        cameraPitch = mc.player.getPitch();
        if (previous == null) previous = mc.options.getPerspective();
        if (mc.options.getPerspective() == Perspective.FIRST_PERSON) mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        lastPlayer = mc.player;
        active = true;
    }

    private static void deactivate() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (previous != null && mc.options != null) mc.options.setPerspective(previous);
        previous = null;
        active = false;
        lastPlayer = null;
    }
}

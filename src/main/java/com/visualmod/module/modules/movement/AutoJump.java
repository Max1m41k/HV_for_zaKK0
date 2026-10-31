package com.visualmod.module.modules.movement;

import com.visualmod.module.Module;
import net.minecraft.client.MinecraftClient;

public class AutoJump extends Module {

    private static Boolean previous = null;

    public AutoJump() {
        super("AutoJump", "Vanilla auto-jump onto 1-block ledges", Category.MOVEMENT);
    }

    @Override
    public void onEnable() { apply(); }

    @Override
    public void onTick() { if (previous == null) apply(); }

    private static void apply() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options == null) return;
        previous = mc.options.getAutoJump().getValue();
        mc.options.getAutoJump().setValue(true);
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null && previous != null) mc.options.getAutoJump().setValue(previous);
        previous = null;
    }
}

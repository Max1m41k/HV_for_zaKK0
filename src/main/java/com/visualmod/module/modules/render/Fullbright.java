package com.visualmod.module.modules.render;

import com.visualmod.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

public class Fullbright extends Module {

    private static final int OUR_DURATION = 400;

    private final MinecraftClient mc = MinecraftClient.getInstance();

    public Fullbright() {
        super("Fullbright", "Night Vision", Category.RENDER);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        StatusEffectInstance cur = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (cur != null && cur.getDuration() > OUR_DURATION) return;
        if (cur == null || cur.getDuration() < 300) {
            mc.player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.NIGHT_VISION, OUR_DURATION, 0, false, false, false));
        }
    }

    @Override
    public void onDisable() {
        if (mc.player == null) return;
        StatusEffectInstance cur = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (cur != null && cur.getDuration() <= OUR_DURATION) {
            mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        }
    }
}

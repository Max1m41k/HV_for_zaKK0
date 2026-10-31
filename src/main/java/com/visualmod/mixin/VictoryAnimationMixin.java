package com.visualmod.mixin;

import com.visualmod.module.modules.render.KillEffect;
import com.visualmod.module.modules.render.victory.VictoryAnimationEngine;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class VictoryAnimationMixin {

    @Inject(method = "onDeath", at = @At("HEAD"), require = 0)
    private void onEntityDeath(DamageSource source, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        LivingEntity self = (LivingEntity)(Object)this;
        if (self == (Object)mc.player) return;
        if (!(self instanceof net.minecraft.entity.player.PlayerEntity)) return;

        boolean ourKill = KillEffect.lastTarget == self
                && System.currentTimeMillis() - KillEffect.lastTargetTime < 8000;
        if (!ourKill) return;

        VictoryAnimationEngine.onKill(self);
        com.visualmod.module.modules.render.Killsound.onKill(self);
    }
}

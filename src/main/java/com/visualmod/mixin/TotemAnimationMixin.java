package com.visualmod.mixin;

import com.visualmod.module.modules.combat.TotemBurst;
import com.visualmod.module.modules.player.TotemAnimation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class TotemAnimationMixin {

    @Inject(method = "tryUseDeathProtector", at = @At("RETURN"), require = 0)
    private void visuals$onTotemUse(net.minecraft.entity.damage.DamageSource source,
                                    CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() == null || !cir.getReturnValue()) return;
        LivingEntity self = (LivingEntity) (Object) this;
        TotemBurst.onTotemPop(self);
        if (self instanceof PlayerEntity player) TotemAnimation.trigger(player);
    }

    @Inject(method = "handleStatus", at = @At("HEAD"))
    private void visuals$onTotemStatus(byte status, CallbackInfo ci) {
        if (status != 35) return;
        LivingEntity self = (LivingEntity) (Object) this;
        TotemBurst.onTotemPop(self);
        if (self instanceof PlayerEntity player) TotemAnimation.trigger(player);
    }
}

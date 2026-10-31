package com.visualmod.mixin;

import com.visualmod.module.modules.render.ItemPhysics;
import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemEntity.class)
public abstract class ItemPhysicsMixin {

    @Inject(method = "getRotation(FF)F", at = @At("RETURN"), cancellable = true, require = 0)
    private static void visuals$noSpin(float ageInTicks, float uniqueOffset, CallbackInfoReturnable<Float> cir) {
        if (ItemPhysics.stopSpin && ItemPhysics.active()) cir.setReturnValue(uniqueOffset);
    }
}

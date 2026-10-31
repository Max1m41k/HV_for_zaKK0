package com.visualmod.mixin;

import com.visualmod.VisualsMod;
import com.visualmod.module.modules.combat.AutoShift;
import com.visualmod.module.modules.hud.TargetHUD;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public class TargetHUDMixin {

    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void onAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (VisualsMod.moduleManager == null) return;

        if (target instanceof LivingEntity living) {
            TargetHUD hud = VisualsMod.moduleManager.getModule(TargetHUD.class);
            if (hud != null) TargetHUD.setTarget(living);
        }

        AutoShift autoShift = VisualsMod.moduleManager.getModule(AutoShift.class);
        if (autoShift != null && autoShift.isEnabled()) autoShift.onAttack(target);
    }
}

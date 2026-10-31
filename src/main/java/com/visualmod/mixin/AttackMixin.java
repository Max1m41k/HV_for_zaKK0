package com.visualmod.mixin;

import com.visualmod.module.modules.combat.CustomSound;
import com.visualmod.module.modules.combat.HitColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class AttackMixin {

    @Shadow public HitResult crosshairTarget;

    @Inject(
            method = "doAttack",
            at = @At("HEAD")
    )
    private void visuals$attack(CallbackInfoReturnable<Boolean> info) {
        com.visualmod.weapon.SwordInspect.cancel();
        com.visualmod.module.modules.cosmetics.CosmeticReaction.onSwing();
        if (this.crosshairTarget != null && this.crosshairTarget.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHitResult = (EntityHitResult) this.crosshairTarget;
            if (entityHitResult.getEntity() instanceof PlayerEntity targetPlayer) {
                HitColor.onHit(targetPlayer);

                CustomSound.playHitSound();
            }
        }
    }
}

package com.visualmod.mixin;

import com.visualmod.module.modules.player.FakePlayer;
import com.visualmod.module.modules.player.Friends;
import com.visualmod.module.modules.render.ComboMarker;
import com.visualmod.module.modules.render.KillEffect;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class KillEffectAttackMixin {

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true, require = 0)
    private void visuals$attack(PlayerEntity player, Entity target, CallbackInfo ci) {
        KillEffect.onAttack(target);
        if (FakePlayer.onAttack(target)) { ci.cancel(); return; }
        if (Friends.blocksAttack(target)) { ci.cancel(); return; }
        ComboMarker.onAttack(target);
        com.visualmod.util.KillTracker.onAttack(target);
    }

    @Inject(method = "interactEntity(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void visuals$interact(PlayerEntity player, Entity entity, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (FakePlayer.isFake(entity)) cir.setReturnValue(ActionResult.PASS);
    }

    @Inject(method = "interactEntityAtLocation(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/hit/EntityHitResult;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void visuals$interactAt(PlayerEntity player, Entity entity, net.minecraft.util.hit.EntityHitResult hit, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (FakePlayer.isFake(entity)) cir.setReturnValue(ActionResult.PASS);
    }
}

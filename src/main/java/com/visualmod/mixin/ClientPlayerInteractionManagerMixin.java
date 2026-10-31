package com.visualmod.mixin;

import com.visualmod.VisualsMod;
import com.visualmod.module.modules.combat.CombatEffects;
import com.visualmod.module.modules.combat.HitColor;
import com.visualmod.module.modules.render.DamageParticles;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {

    @Inject(
            method = "attackEntity",
            at = @At("TAIL")
    )
    private void hit(PlayerEntity player, Entity target, CallbackInfo ci) {
        boolean crit = com.visualmod.module.modules.combat.HitConfirm.isCritical(player);
        if (target instanceof PlayerEntity targetPlayer) {
            HitColor.onHit(targetPlayer);
            com.visualmod.module.modules.hud.TargetHUD.onHit(targetPlayer);
            com.visualmod.module.modules.hud.ExecuteMark.onHit(targetPlayer);
            com.visualmod.module.modules.hud.DuelBoard.onHit(targetPlayer);
            if (VisualsMod.moduleManager != null) {
                DamageParticles.onHit(VisualsMod.moduleManager.getModule(DamageParticles.class), targetPlayer);
            }
        }
        CombatEffects.onAttack(target);
        com.visualmod.module.modules.combat.HitConfirm.onHit(target, crit);
        com.visualmod.module.modules.combat.GroundSlash.onAttack(target);
        com.visualmod.module.modules.combat.ShockwaveOnHit.onAttack(target, crit);
        com.visualmod.module.modules.combat.CritHeat.onHit(crit);
        com.visualmod.module.modules.combat.MaceSlam.onAttack(player, target);
        com.visualmod.module.modules.combat.ReachGhost.onAttack(player, target);
        com.visualmod.module.modules.combat.SparksTrail.onAttack(player, target);
        com.visualmod.module.modules.combat.ChannelLink.onAttack(player, target);
        com.visualmod.module.modules.combat.AirCut.onAttack(player, target);
        com.visualmod.module.modules.combat.CritStar.onAttack(player, target, crit);
        com.visualmod.module.modules.combat.BladeMorph.onHit();
        com.visualmod.cosmetics.PetAI.onAttack(target);
    }

    /** Свой правый клик предметом: стан и трапки применяются именно так. */
    @Inject(method = "interactItem", at = @At("HEAD"), require = 0)
    private void visuals$useItem(net.minecraft.entity.player.PlayerEntity player, net.minecraft.util.Hand hand,
                                 CallbackInfoReturnable<net.minecraft.util.ActionResult> cir) {
        com.visualmod.module.modules.player.HolyItems.onLocalUse(hand);
    }

    /** То же, но по блоку — трапку ставят в землю, и это другой метод. */
    @Inject(method = "interactBlock", at = @At("HEAD"), require = 0)
    private void visuals$useOnBlock(net.minecraft.client.network.ClientPlayerEntity player, net.minecraft.util.Hand hand,
                                    net.minecraft.util.hit.BlockHitResult hit,
                                    CallbackInfoReturnable<net.minecraft.util.ActionResult> cir) {
        com.visualmod.module.modules.player.HolyItems.onLocalUse(hand);
    }
}

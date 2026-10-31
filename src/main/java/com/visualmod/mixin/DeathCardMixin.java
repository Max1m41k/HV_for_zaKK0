package com.visualmod.mixin;

import com.visualmod.module.modules.hud.DeathCard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public class DeathCardMixin {

    @Inject(method = "onDeath", at = @At("HEAD"), require = 0)
    private void visuals$onPlayerDeath(DamageSource source, CallbackInfo ci) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || (Object) this != mc.player) return;
            DeathCard.onDeath(source);
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("DeathCardMixin", t);
        }
    }
}

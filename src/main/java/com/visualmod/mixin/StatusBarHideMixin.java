package com.visualmod.mixin;

import com.visualmod.module.modules.hud.HotbarHUD;
import com.visualmod.module.modules.hud.PotionStatus;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class StatusBarHideMixin {

    @Inject(
            method = "renderHealthBar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/entity/player/PlayerEntity;IIIIFIIIZ)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void visuals$hideHealth(DrawContext ctx, PlayerEntity player, int a, int b, int c,
                                          int d, float health, int e, int f, int g, boolean blinking,
                                          CallbackInfo ci) {
        if (HotbarHUD.customBars()) ci.cancel();
    }

    @Inject(
            method = "renderArmor(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/entity/player/PlayerEntity;IIII)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void visuals$hideArmor(DrawContext ctx, PlayerEntity player, int a, int b, int c, int d,
                                          CallbackInfo ci) {
        if (HotbarHUD.customBars()) ci.cancel();
    }

    @Inject(
            method = "renderFood(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/entity/player/PlayerEntity;II)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void visuals$hideFood(DrawContext ctx, PlayerEntity player, int a, int b,
                                         CallbackInfo ci) {
        if (HotbarHUD.customBars()) ci.cancel();
    }

    /** Столбик иконок эффектов в правом нижнем углу: у PotionStatus свой, с своими картинками. */
    @Inject(
            method = "renderStatusEffectOverlay(Lnet/minecraft/client/gui/DrawContext;"
                    + "Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void visuals$hideEffects(DrawContext ctx, RenderTickCounter tick, CallbackInfo ci) {
        if (PotionStatus.hidesVanilla()) ci.cancel();
    }
}

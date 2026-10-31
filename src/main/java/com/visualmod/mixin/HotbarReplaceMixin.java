package com.visualmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

@Mixin(InGameHud.class)
public abstract class HotbarReplaceMixin {

    @Inject(method = "renderHotbar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void onRenderHotbar(DrawContext ctx, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (com.visualmod.VisualsMod.moduleManager != null &&
                com.visualmod.VisualsMod.moduleManager.getModule(com.visualmod.module.modules.hud.HotbarHUD.class).isEnabled()) {

            ci.cancel();
        }
    }

    /** SlotNumbers — встроенная функция, поэтому рисуем после ванильного хотбара всегда. */
    @Inject(method = "renderHotbar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("TAIL"))
    private void visuals$slotNumbers(DrawContext ctx, RenderTickCounter tickCounter, CallbackInfo ci) {
        com.visualmod.util.SlotNumbers.drawVanilla(ctx);
        com.visualmod.util.BeatPulse.drawHotbarBeat(ctx);
    }
}

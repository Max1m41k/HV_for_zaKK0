package com.visualmod.mixin;

import com.visualmod.util.GlassBlur;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * GlassBlur: в начале кадра HUD в буфере ещё только мир, поэтому снимок для
 * «мыльного стекла» panels нужно делать именно здесь, до всей остальной отрисовки.
 */
@Mixin(InGameHud.class)
public class GlassBlurMixin {

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("HEAD"))
    private void visuals$markHudFrame(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        GlassBlur.newFrame();
    }
}

package com.visualmod.mixin;

import com.visualmod.util.TntWarning;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class TntWarningHudMixin {

    /**
     * Пишем после всего HUD, а не после хотбара: собственный хотбар отменяет renderHotbar
     * целиком, и его TAIL не выполняется вовсе.
     */
    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("TAIL"), require = 0)
    private void visuals$tntWarning(DrawContext ctx, RenderTickCounter tickCounter, CallbackInfo ci) {
        TntWarning.draw(ctx);
    }
}

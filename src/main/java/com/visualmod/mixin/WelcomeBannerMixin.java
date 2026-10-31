package com.visualmod.mixin;

import com.visualmod.util.Diag;
import com.visualmod.util.WelcomeBanner;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Встроенное приветствие HolyWorld рисуется поверх HUD в зоне боссбара. Логика в утилите:
 * из пакета com.visualmod.mixin сюда вызывать ничего нельзя.
 */
@Mixin(InGameHud.class)
public class WelcomeBannerMixin {

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("TAIL"))
    private void visuals$welcome(DrawContext ctx, RenderTickCounter tickCounter, CallbackInfo ci) {
        try {
            WelcomeBanner.render(ctx, ctx.getScaledWindowWidth(), 12);
        } catch (Throwable t) {
            Diag.once("WelcomeBanner", t);
        }
    }
}

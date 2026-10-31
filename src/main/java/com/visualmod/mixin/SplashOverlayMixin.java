package com.visualmod.mixin;

import com.visualmod.screen.HolyIntroOverlay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {

    @Shadow private long reloadCompleteTime;
    @Shadow @Final private boolean reloading;
    @Shadow @Final private MinecraftClient client;

    @Inject(method = "render", at = @At("TAIL"))
    private void visuals$startIntro(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.reloading || this.reloadCompleteTime == -1L || !HolyIntroOverlay.shouldPlay()) return;
        if (this.client.getOverlay() != (Object) this) return;
        HolyIntroOverlay.markPlayed();
        this.client.setOverlay(new HolyIntroOverlay());
    }
}

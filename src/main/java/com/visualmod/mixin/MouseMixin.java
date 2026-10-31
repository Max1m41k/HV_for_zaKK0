package com.visualmod.mixin;

import com.visualmod.module.modules.render.victory.VictoryAnimationEngine;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {

    @Inject(
            method = "onMouseButton",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onMouseButtonInject(long window, net.minecraft.client.input.MouseInput input, int action, CallbackInfo ci) {
        if (input.button() == 0 && action == 1) {
            if (VictoryAnimationEngine.isPlaying()) {
                VictoryAnimationEngine.onScreenClick();
            }
        }
    }
}

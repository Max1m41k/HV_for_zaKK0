package com.visualmod.mixin;

import com.visualmod.module.modules.player.Optimizer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.InactivityFpsLimiter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InactivityFpsLimiter.class)
public class OptimizerFpsMixin {

    @Inject(method = "update", at = @At("RETURN"), cancellable = true, require = 0)
    private void opt$advancedFpsLimiter(CallbackInfoReturnable<Integer> cir) {
        if (!Optimizer.active()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        int currentFpsLimit = cir.getReturnValue();

        if (Optimizer.backgroundFps && !mc.isWindowFocused()) {
            cir.setReturnValue(Math.min(currentFpsLimit, (int) Optimizer.backgroundFpsLimit));
            return;
        }

        if (currentFpsLimit <= 0 || currentFpsLimit > 250) {
            cir.setReturnValue(250);
        }
    }
}

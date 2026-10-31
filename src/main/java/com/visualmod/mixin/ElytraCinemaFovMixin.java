package com.visualmod.mixin;

import com.visualmod.module.modules.render.ElytraCinema;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Прицел «наезжает» во время планирования: расширяем FOV, пока ElytraCinema активен. */
@Mixin(GameRenderer.class)
public class ElytraCinemaFovMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void visuals$cinemaFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        try {
            float s = ElytraCinema.strength();
            if (s <= 0.02f) return;
            cir.setReturnValue(cir.getReturnValue() * ElytraCinema.fovScale());
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("ElytraCinema.fov", t);
        }
    }
}

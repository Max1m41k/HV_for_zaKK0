package com.visualmod.mixin;

import com.visualmod.module.modules.combat.Finisher;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Наезд камеры на добивании: Finisher отдаёт множитель FOV, здесь он применяется. */
@Mixin(GameRenderer.class)
public class FinisherFovMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void visuals$finisherFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        try {
            float s = Finisher.fovScale();
            if (s >= 0.999f) return;
            cir.setReturnValue(cir.getReturnValue() * s);
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("Finisher.fov", t);
        }
    }
}

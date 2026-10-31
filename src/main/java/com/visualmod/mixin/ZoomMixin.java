package com.visualmod.mixin;

import com.visualmod.VisualsMod;
import com.visualmod.module.modules.render.Zoom;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class ZoomMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void visuals$zoom(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        if (VisualsMod.moduleManager == null) return;
        Zoom mod = VisualsMod.moduleManager.getModule(Zoom.class);
        if (mod == null || !mod.isEnabled()) return;
        cir.setReturnValue((float) (cir.getReturnValue() / Zoom.factor));
    }
}

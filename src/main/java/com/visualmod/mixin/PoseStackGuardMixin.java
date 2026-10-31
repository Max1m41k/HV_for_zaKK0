package com.visualmod.mixin;

import com.visualmod.util.Diag;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class PoseStackGuardMixin {

    @Inject(method = "checkEmpty", at = @At("HEAD"), cancellable = true)
    private void visuals$unwind(MatrixStack matrices, CallbackInfo ci) {
        // Iris держит свои пуши вокруг неба/облаков поверх этой проверки: вслепую
        // сбрасывать стек под шейдер-паком нельзя — собьём их трансформации.
        if (com.visualmod.util.IrisCompat.shaderActive()) return;
        if (matrices.isEmpty()) return;
        int leaked = 0;
        while (!matrices.isEmpty() && leaked < 64) {
            matrices.pop();
            leaked++;
        }
        Diag.once("pose-stack", new IllegalStateException("Незакрытых пушей в стеке мира: " + leaked));
        ci.cancel();
    }
}

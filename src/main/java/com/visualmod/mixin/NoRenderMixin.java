package com.visualmod.mixin;

import com.visualmod.VisualsMod;
import com.visualmod.module.modules.render.NoRender;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class NoRenderMixin {

    @Shadow private float fovMultiplier;
    @Shadow private float lastFovMultiplier;

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void visuals$noHurtCam(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (!active()) return;
        if (NoRender.noHurtCam) ci.cancel();
    }

    /**
     * FOV замораживается ПОСЛЕ ванильного расчёта, а не отменой HEAD: fovMultiplier в
     * GameRenderer не имеет инициализатора, поэтому отмена leaves его = 0 → перспективная
     * матрица вырождается и мир вообще не рендерится при заходе с включённым модулем.
     */
    @Inject(method = "updateFovMultiplier", at = @At("TAIL"))
    private void visuals$noFov(CallbackInfo ci) {
        if (!active() || !NoRender.noFovChanges) return;
        this.fovMultiplier = 1.0f;
        this.lastFovMultiplier = 1.0f;
    }

    private static boolean active() {
        if (VisualsMod.moduleManager == null) return false;
        NoRender mod = VisualsMod.moduleManager.getModule(NoRender.class);
        return mod != null && mod.isEnabled();
    }
}

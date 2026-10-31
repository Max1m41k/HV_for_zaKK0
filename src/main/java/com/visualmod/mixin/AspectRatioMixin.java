package com.visualmod.mixin;

import com.visualmod.VisualsMod;
import com.visualmod.module.modules.hud.AspectRatio;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GameRenderer.class)
public class AspectRatioMixin {

    @Redirect(
            method = "getBasicProjectionMatrix",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/Window;getFramebufferWidth()I")
    )
    private int visuals$fakeFramebufferWidth(Window window) {
        int realWidth = window.getFramebufferWidth();
        // Iris строит из этой же функции матрицу для своего G-буфера: если соврать
        // про ширину, под шейдер-паком разъезжается перспектива всего мира.
        if (com.visualmod.util.IrisCompat.shaderActive()) return realWidth;
        if (VisualsMod.moduleManager == null) return realWidth;
        AspectRatio mod = VisualsMod.moduleManager.getModule(AspectRatio.class);
        if (mod == null || !mod.isEnabled()) return realWidth;

        int realHeight = window.getFramebufferHeight();
        return (int) Math.round(realHeight * AspectRatio.targetRatio());
    }
}

package com.visualmod.mixin;

import com.visualmod.VisualsMod;
import com.visualmod.module.modules.hud.CpsHUD;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.Mouse.class)
public class CpsMixin {

    // 1.21.11: Mouse.onMouseButton(long, MouseInput, action) — кнопка внутри рекорда MouseInput.
    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void visuals$countClick(long window, net.minecraft.client.input.MouseInput input, int action, CallbackInfo ci) {
        if (action != GLFW.GLFW_PRESS) return;
        int button = input.button();
        if (button != GLFW.GLFW_MOUSE_BUTTON_1 && button != GLFW.GLFW_MOUSE_BUTTON_2) return;
        if (VisualsMod.moduleManager == null) return;
        CpsHUD counter = VisualsMod.moduleManager.getModule(CpsHUD.class);
        if (counter != null && counter.isEnabled()) counter.registerClick();
    }
}

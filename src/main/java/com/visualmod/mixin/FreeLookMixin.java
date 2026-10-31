package com.visualmod.mixin;

import com.visualmod.module.modules.render.FreeLook;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class FreeLookMixin {

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void visuals$freeLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if (!FreeLook.isActive()) return;
        if ((Object) this != MinecraftClient.getInstance().player) return;

        FreeLook.cameraYaw += (float) cursorDeltaX * 0.15f;
        FreeLook.cameraPitch += (float) cursorDeltaY * 0.15f;
        if (FreeLook.cameraPitch > 90f) FreeLook.cameraPitch = 90f;
        if (FreeLook.cameraPitch < -90f) FreeLook.cameraPitch = -90f;
        ci.cancel();
    }
}

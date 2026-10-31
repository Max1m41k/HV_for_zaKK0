package com.visualmod.mixin;

import com.visualmod.module.modules.render.FreeLook;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Camera.class)
public abstract class FreeLookCameraMixin {

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @org.spongepowered.asm.mixin.injection.ModifyArgs(
            method = "update",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V")
    )
    private void visuals$overrideRotation(org.spongepowered.asm.mixin.injection.invoke.arg.Args args) {
        if (!FreeLook.isActive()) return;
        args.set(0, FreeLook.cameraYaw);
        args.set(1, FreeLook.cameraPitch);
    }
}

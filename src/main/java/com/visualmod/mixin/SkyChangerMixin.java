package com.visualmod.mixin;

import com.visualmod.VisualsMod;
import com.visualmod.module.modules.render.CustomSky;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FogRenderer.class, priority = 999999)
public class SkyChangerMixin {

    @Inject(method = "getFogColor(Lnet/minecraft/client/render/Camera;FLnet/minecraft/client/world/ClientWorld;IF)Lorg/joml/Vector4f;", at = @At("RETURN"), cancellable = true, require = 0)
    private static void onGetFogColor(Camera camera, float tickDelta, ClientWorld world, int clampedViewDistance, float skyDarkness, CallbackInfoReturnable<Vector4f> cir) {
        if (com.visualmod.util.IrisCompat.shaderActive()) return;
        if (VisualsMod.moduleManager == null) return;
        CustomSky mod = VisualsMod.moduleManager.getModule(CustomSky.class);
        if (mod == null || !mod.isEnabled()) return;

        float[] c = CustomSky.fogRgb(world, tickDelta);
        if (c == null) return;

        Vector4f original = cir.getReturnValue();
        float a = (original != null) ? original.w : 1.0f;
        cir.setReturnValue(new Vector4f(c[0], c[1], c[2], a));
    }
}

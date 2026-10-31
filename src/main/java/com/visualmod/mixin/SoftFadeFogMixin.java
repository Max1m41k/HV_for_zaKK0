package com.visualmod.mixin;

import com.visualmod.module.modules.render.SoftFade;
import com.visualmod.util.Diag;
import com.visualmod.util.FocusVeil;
import com.visualmod.util.IrisCompat;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.render.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * SoftFade: применяется ПОСЛЕ NoFog (999999) и AnimeFocus (1000000), поэтому даже с
 * отключённым/отодвинутым туманом граница прорисовки всё равно мягко тает в цвете горизонта.
 * Под шейдерами и активным AnimeFocus не вмешивается.
 */
@Mixin(value = FogRenderer.class, priority = 1000001)
public class SoftFadeFogMixin {

    @ModifyVariable(method = "applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/fog/FogRenderer;applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"),
            ordinal = 0)
    private FogData visuals$softFade(FogData data) {
        try {
            if (!SoftFade.active()) return data;
            if (IrisCompat.shaderActive()) return data;
            if (FocusVeil.strength() > 0.02f) return data;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.gameRenderer == null) return data;
            Camera camera = mc.gameRenderer.getCamera();
            if (camera == null || camera.getSubmersionType() != CameraSubmersionType.NONE) return data;

            float end = data.renderDistanceEnd;
            if (!(end > 8f)) return data;
            float start = end * (float) Math.max(0.05, Math.min(0.95, SoftFade.fadeStart));
            if (start >= end - 1f) return data;
            data.renderDistanceStart = start;
        } catch (Throwable t) {
            Diag.once("SoftFade.fog", t);
        }
        return data;
    }
}

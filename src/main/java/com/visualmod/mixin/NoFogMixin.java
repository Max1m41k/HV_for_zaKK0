package com.visualmod.mixin;

import com.visualmod.VisualsMod;
import com.visualmod.module.modules.render.NoFog;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.render.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = FogRenderer.class, priority = 999999)
public class NoFogMixin {

    @ModifyVariable(method = "applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/fog/FogRenderer;applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"),
            ordinal = 0)
    private FogData onFogData(FogData data) {
        // Под шейдер-паком туманом командует пак: он перетирает юниформы своими значениями,
        // а попытка переписать даёт серые «стены» на горизонте.
        if (com.visualmod.util.IrisCompat.shaderActive()) return data;
        if (VisualsMod.moduleManager == null) return data;
        NoFog mod = VisualsMod.moduleManager.getModule(NoFog.class);
        if (mod == null || !mod.isEnabled()) return data;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.gameRenderer == null) return data;
        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null || camera.getSubmersionType() != CameraSubmersionType.NONE) return data;

        float mult = (float) mod.getDistanceMultiplier();
        // renderDistanceEnd == «дальность» прежнего applyFog: от неё и пляшем множитель.
        float viewDistance = data.renderDistanceEnd;
        data.renderDistanceStart = viewDistance * mult * 10.0f;
        data.renderDistanceEnd = viewDistance * (mult + 0.5f) * 10.0f;
        return data;
    }
}

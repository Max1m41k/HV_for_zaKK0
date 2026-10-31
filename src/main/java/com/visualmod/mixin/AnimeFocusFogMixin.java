package com.visualmod.mixin;

import com.visualmod.util.FocusVeil;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * AnimeFocus: за радиусом чёрных блоков мир должен растворяться в белом.
 * Priority выше, чем у шейдерных и неба — пока эффект активен, командует туманом этот миксин.
 */
@Mixin(value = FogRenderer.class, priority = 1000000)
public class AnimeFocusFogMixin {

    @Inject(method = "getFogColor(Lnet/minecraft/client/render/Camera;FLnet/minecraft/client/world/ClientWorld;IF)Lorg/joml/Vector4f;",
            at = @At("RETURN"), cancellable = true)
    private void visuals$whiteFogColor(Camera camera, float tickDelta, ClientWorld world,
                                       int clampedViewDistance, float skyDarkness,
                                       CallbackInfoReturnable<Vector4f> cir) {
        try {
            float k = FocusVeil.strength();
            if (k <= 0.02f) return;
            Vector4f orig = cir.getReturnValue();
            float[] c = FocusVeil.fogTint();
            cir.setReturnValue(new Vector4f(c[0], c[1], c[2], orig == null ? 1f : orig.w));
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("AnimeFocusFog.color", t);
        }
    }

    /**
     * 1.21.11 считает дальность тумана в FogData и пишет её в юниформ последним действием applyFog,
     * причём renderDistanceStart/End перетираются уже после модификаторов — поэтому правим ровно
     * перед этим вызовом. Форма тумана (CYLINDER) в новом рендере не задаётся: она зашита в шейдер.
     */
    @ModifyVariable(method = "applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/fog/FogRenderer;applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"),
            ordinal = 0)
    private FogData visuals$whiteFogRange(FogData data) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.gameRenderer == null) return data;
            Camera camera = mc.gameRenderer.getCamera();
            if (camera == null || camera.getSubmersionType() != CameraSubmersionType.NONE) return data;
            float k = FocusVeil.strength();
            if (k <= 0.02f) return data;
            float reach = (float) Math.max(4.0, FocusVeil.range);
            data.renderDistanceStart = reach * 0.45f;
            data.renderDistanceEnd = reach * 1.02f;
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("AnimeFocusFog.shape", t);
        }
        return data;
    }
}

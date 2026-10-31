package com.visualmod.mixin;

import com.visualmod.VisualsMod;
import com.visualmod.module.modules.render.CustomSky;
import com.visualmod.module.modules.render.NoFog;
import com.visualmod.module.modules.render.Shaders;
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

@Mixin(value = FogRenderer.class, priority = 999999)
public class ShadersAtmosphereMixin {

    @Inject(method = "getFogColor(Lnet/minecraft/client/render/Camera;FLnet/minecraft/client/world/ClientWorld;IF)Lorg/joml/Vector4f;",
            at = @At("RETURN"), cancellable = true)
    private void visuals$fogColor(Camera camera, float tickDelta, ClientWorld world,
                                  int clampedViewDistance, float skyDarkness,
                                  CallbackInfoReturnable<Vector4f> cir) {
        try {
            if (com.visualmod.util.IrisCompat.shaderActive()) return;
            Vector4f orig = cir.getReturnValue();
            if (orig == null || inFluid()) return;

            if (Shaders.moodOn() && !skyTakenByCustomSky()) {
                float[] c = Shaders.moodColor(world, tickDelta);
                if (c != null) {
                    float k = 0.55f * Shaders.moodStrength();
                    orig = new Vector4f(orig.x + (c[0] - orig.x) * k, orig.y + (c[1] - orig.y) * k,
                            orig.z + (c[2] - orig.z) * k, orig.w);
                }
            }

            float[] tint = Shaders.fogTintColor();
            if (tint != null) {
                float k = tint[3];
                cir.setReturnValue(new Vector4f(orig.x + (tint[0] - orig.x) * k,
                        orig.y + (tint[1] - orig.y) * k,
                        orig.z + (tint[2] - orig.z) * k, orig.w));
            }
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("ShadersAtmosphere.fogColor", t);
        }
    }

    @ModifyVariable(method = "applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/fog/FogRenderer;applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"),
            ordinal = 0)
    private FogData visuals$fogRange(FogData data) {
        try {
            if (com.visualmod.util.IrisCompat.shaderActive()) return data;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null || mc.gameRenderer == null) return data;
            Camera camera = mc.gameRenderer.getCamera();
            if (camera == null || camera.getSubmersionType() != CameraSubmersionType.NONE) return data;
            if (enabled(NoFog.class)) return data;
            float tickDelta = mc.getRenderTickCounter().getTickProgress(false);
            ClientWorld world = mc.world;
            // renderDistanceEnd в 1.21.11 и есть та «дальность», что раньше приходила параметром.
            float viewDistance = data.renderDistanceEnd;
            float start = data.renderDistanceStart;
            float end = viewDistance;
            boolean changed = false;

            if (Shaders.moodOn()) {
                float k = Shaders.fogCompress(world, tickDelta);
                if (k < 0.999f) { start *= k; end *= k; changed = true; }
            }
            float[] range = Shaders.fogRange(world, tickDelta, camera.getCameraPos().y);
            if (range != null) {
                start = range[0] * viewDistance;
                end = Math.max(start + 1f, range[1] * viewDistance);
                changed = true;
            }
            if (!changed) return data;
            data.renderDistanceStart = Math.max(0f, start);
            data.renderDistanceEnd = Math.max(1f, end);
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("ShadersAtmosphere.fogShape", t);
        }
        return data;
    }

    private static boolean inFluid() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc != null && mc.player != null && mc.player.isTouchingWater();
    }

    private static boolean skyTakenByCustomSky() {
        return CustomSky.tintSky && enabled(CustomSky.class);
    }

    private static <T extends com.visualmod.module.Module> boolean enabled(Class<T> cls) {
        if (VisualsMod.moduleManager == null) return false;
        T m = VisualsMod.moduleManager.getModule(cls);
        return m != null && m.isEnabled();
    }
}

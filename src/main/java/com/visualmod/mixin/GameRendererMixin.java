package com.visualmod.mixin;

import com.visualmod.VisualsMod;
import com.visualmod.module.modules.player.RespawnCamera;
import com.visualmod.module.modules.player.TotemAnimation;
import com.visualmod.module.modules.render.NoRender;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true, remap = true)
    private void onTiltViewWhenHurt(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (VisualsMod.moduleManager != null) {
            NoRender mod = VisualsMod.moduleManager.getModule(NoRender.class);
            if (mod != null && mod.isEnabled() && NoRender.noHurtCam) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "showFloatingItem", at = @At("HEAD"), cancellable = true)
    private void visuals$cancelVanillaTotem(ItemStack floatingItem, CallbackInfo ci) {
        if (floatingItem != null && floatingItem.isOf(Items.TOTEM_OF_UNDYING)
                && TotemAnimation.shouldCancelVanillaTotem()) {
            ci.cancel();
        }
    }

    /** Сцена возрождения снимается облётом: руки первого лица в кадре не нужны. */
    // 1.21.11: renderHand(float, boolean, Matrix4f) — камеры в сигнатуре больше нет.
    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true, require = 0)
    private void visuals$noHandsInRespawnScene(float tickDelta, boolean advance, Matrix4f matrix, CallbackInfo ci) {
        if (RespawnCamera.active) ci.cancel();
    }

    /** Курсор: прячем системную стрелку ровно тогда, когда наш курсор реально рисуется. */
    @Inject(method = "render", at = @At("TAIL"))
    private void visuals$syncCursorMode(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        com.visualmod.module.modules.render.CustomCursor.syncCursorMode();
    }
}

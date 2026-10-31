package com.visualmod.mixin;

import com.visualmod.cosmetics.CosmeticRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Верхний слой косметики: врезка сразу после отрисовки модели, внутри её же push/pop — пока стек
 * содержит поворот тела и приседание, которые ваниль уже посчитала. В TAIL этого не получить:
 * pop() там отработал до конца метода.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class CosmeticsAttachMixin {

    @Shadow
    public abstract EntityModel<?> getModel();

    // 1.21.11: модель уходит в очередь команд (submitModel), провайдера потребителей в сигнатуре нет.
    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;"
                    + "Lnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;"
                    + "Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModel("
                            + "Lnet/minecraft/client/model/Model;Ljava/lang/Object;"
                            + "Lnet/minecraft/client/util/math/MatrixStack;"
                            + "Lnet/minecraft/client/render/RenderLayer;III"
                            + "Lnet/minecraft/client/texture/Sprite;I"
                            + "Lnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void visuals$cosmetics(LivingEntityRenderState state, MatrixStack matrixStack,
                                   OrderedRenderCommandQueue queue, CameraRenderState cameraRenderState,
                                   CallbackInfo ci) {
        if (!(state instanceof PlayerEntityRenderState ps)) return;
        if (!(getModel() instanceof PlayerEntityModel model)) return;
        try {
            CosmeticRenderer.renderAttached(ps, model, matrixStack, queue);
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("CosmeticsAttachMixin", t);
        }
    }
}

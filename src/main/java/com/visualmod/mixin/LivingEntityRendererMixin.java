package com.visualmod.mixin;

import net.minecraft.client.render.RenderLayers;
import com.visualmod.module.modules.combat.HitColor;
import com.visualmod.util.FocusVeil;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    // 1.21.11: updateRenderState перегружен мостовым методом, поэтому нужен точный дескриптор.
    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;"
                    + "Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL")
    )
    private void visuals$hitState(
            LivingEntity entity,
            LivingEntityRenderState state,
            float tickDelta,
            CallbackInfo ci
    ) {
        if (entity instanceof PlayerEntity player) {
            if (HitColor.shouldRender(player)) {
                state.hurt = true;
            }
        }
    }

    /** AnimeFocus: фигурка игрока рисуется сплошным белым квадратом вместо скина. */
    @Inject(
            method = "getRenderLayer",
            at = @At("RETURN"),
            cancellable = true
    )
    private void visuals$whiteFigure(
            LivingEntityRenderState state,
            boolean visible,
            boolean translucent,
            boolean glowing,
            CallbackInfoReturnable<RenderLayer> cir
    ) {
        boolean white = state instanceof PlayerEntityRenderState
                && cir.getReturnValue() != null
                && FocusVeil.whiteFigureRequested();
        FocusVeil.markFigure(white);
        if (white) {
            cir.setReturnValue(RenderLayers.entityCutoutNoCull(FocusVeil.FIGURE_TEXTURE));
        }
    }

    /** Белая фигурка не должна сереть в темноте — свет ставим на максимум. */
    // 1.21.11: модель уходит в очередь команды, свет — 5-й аргумент submitModel.
    @ModifyArg(
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
                            + "Lnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V"
            ),
            index = 4,
            require = 0
    )
    private int visuals$fullbright(int light) {
        return FocusVeil.figureWhite() ? 0xF000F0 : light;
    }
}

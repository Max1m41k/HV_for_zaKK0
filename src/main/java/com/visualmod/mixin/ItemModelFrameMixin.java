package com.visualmod.mixin;

import com.visualmod.hand.HandEffects;
import com.visualmod.util.Diag;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Catch собственного кадра модели предмета. Внутри LayerRenderState.render стек уже несёт
 * display-трансформацию и центровку -0.5, поэтому координаты квадов пригодны как есть — только
 * там оверлей можно притянуть к модели, а не к руке.
 */
@Mixin(ItemRenderState.LayerRenderState.class)
public class ItemModelFrameMixin {

    @Shadow
    @Final
    private List<BakedQuad> quads;

    @Inject(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;III)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitItem("
                            + "Lnet/minecraft/client/util/math/MatrixStack;"
                            + "Lnet/minecraft/item/ItemDisplayContext;"
                            + "III[ILjava/util/List;"
                            + "Lnet/minecraft/client/render/RenderLayer;"
                            + "Lnet/minecraft/client/render/item/ItemRenderState$Glint;)V",
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void visuals$modelFrame(MatrixStack matrices, OrderedRenderCommandQueue queue,
                                    int light, int overlay, int outlineColor, CallbackInfo ci) {
        try {
            HandEffects.captureModelFrame(matrices.peek().getPositionMatrix(), quads);
        } catch (Throwable t) {
            Diag.once("ItemModelFrameMixin", t);
        }
    }
}

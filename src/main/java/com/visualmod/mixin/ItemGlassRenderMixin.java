package com.visualmod.mixin;

import com.visualmod.module.modules.render.GlassItem;
import com.visualmod.util.Diag;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Предмет в руке: прокси на VertexConsumerProvider, чтобы вершины стали стеклянными.
 * Точка — статический renderItem, который очередь вызывает уже на исполнении команды, поэтому
 * сюда попадают и руки, и мировые предметы; режим отсекает всё, кроме первой руки.
 */
@Mixin(ItemRenderer.class)
public class ItemGlassRenderMixin {

    @ModifyVariable(
            method = "renderItem(Lnet/minecraft/item/ItemDisplayContext;"
                    + "Lnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/client/render/VertexConsumerProvider;"
                    + "II[ILjava/util/List;"
                    + "Lnet/minecraft/client/render/RenderLayer;"
                    + "Lnet/minecraft/client/render/item/ItemRenderState$Glint;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static VertexConsumerProvider visuals$glass(VertexConsumerProvider providers,
                                                        ItemDisplayContext mode,
                                                        MatrixStack matrices) {
        try {
            if (!GlassItem.itemsAsBlank() || !GlassItem.isFirstPersonHand(mode)) return providers;
            return GlassItem.wrap(providers);
        } catch (Throwable t) {
            Diag.once("ItemGlassRenderMixin", t);
            return providers;
        }
    }
}

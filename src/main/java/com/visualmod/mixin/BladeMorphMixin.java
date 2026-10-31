package com.visualmod.mixin;

import com.visualmod.module.modules.combat.BladeMorph;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HeldItemRenderer.class, priority = 800)
public class BladeMorphMixin {

    @Inject(
            method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
            at = @At("HEAD"),
            require = 0
    )
    private void visuals$applyMorph(LivingEntity entity, ItemStack stack, ItemDisplayContext mode,
                                    MatrixStack matrices,
                                    OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        if (mode != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                && mode != ItemDisplayContext.FIRST_PERSON_LEFT_HAND) return;
        BladeMorph.apply(matrices, mode.isLeftHand() ? -1 : 1);
    }
}

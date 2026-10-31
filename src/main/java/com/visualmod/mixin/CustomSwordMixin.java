package com.visualmod.mixin;

import com.visualmod.module.modules.cosmetics.CustomShield;
import com.visualmod.module.modules.cosmetics.CustomTotem;
import com.visualmod.module.modules.render.CustomCrossbow;
import com.visualmod.module.modules.render.CustomSword;
import com.visualmod.module.modules.render.ItemPhysics;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemModelManager.class)
public abstract class CustomSwordMixin {

    // 1.21.11: update() больше не перегружен — updateForLivingEntity / updateForNonLivingEntity /
    // clearAndUpdate сводятся в один update(ItemRenderState, ItemStack, ItemDisplayContext,
    // World, HeldItemContext, seed), поэтому достаточно одного дескриптора. Хвост аргументов
    // (LivingEntity + boolean leftHanded) заменён на HeldItemContext и нам не нужен.
    @ModifyVariable(
            method = "update(Lnet/minecraft/client/render/item/ItemRenderState;Lnet/minecraft/item/ItemStack;"
                    + "Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/world/World;"
                    + "Lnet/minecraft/util/HeldItemContext;I)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private ItemStack visuals$modifyItemModel(ItemStack stack) {
        ItemPhysics.noteStack(stack);
        return CustomShield.remap(CustomTotem.remap(CustomCrossbow.remap(CustomSword.remap(stack))));
    }
}

package com.visualmod.mixin;

import com.visualmod.module.modules.render.ViewModelChanger;
import com.visualmod.viewmodel.ViewModelStore;
import com.visualmod.weapon.SwordInspect;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HeldItemRenderer.class, priority = 900)
public class ViewModelTransformMixin {

    @Inject(
            method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
            at = @At("HEAD"),
            require = 0
    )
    private void visuals$applyProfile(LivingEntity entity, ItemStack stack, ItemDisplayContext mode,
                                      MatrixStack matrices,
                                      OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        try {
            if (mode != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                    && mode != ItemDisplayContext.FIRST_PERSON_LEFT_HAND) return;
            boolean leftHanded = mode.isLeftHand();
            if (leftHanded && !ViewModelChanger.offHand) return;
            if (!ViewModelChanger.active()) return;

            ViewModelStore.Profile p = ViewModelStore.forStack(stack);
            if (p == null) return;

            int i = leftHanded ? -1 : 1;

            matrices.translate(p.x * i, p.y, p.z);

            float px = (float) (p.pivotX * i), py = (float) p.pivotY, pz = (float) p.pivotZ;
            matrices.translate(px, py, pz);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) p.pitch));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) (p.yaw * i)));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) (p.roll * i)));
            float s = (float) Math.max(0.15, Math.min(4.0, p.scale));
            matrices.scale(s, s, s);
            matrices.translate(-px, -py, -pz);
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("ViewModelTransform", t);
        }
    }

    @Inject(
            method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
            at = @At("HEAD"),
            require = 0
    )
    private void visuals$applyInspect(LivingEntity entity, ItemStack stack, ItemDisplayContext mode,
                                      MatrixStack matrices,
                                      OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        try {
            if (mode != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                    && mode != ItemDisplayContext.FIRST_PERSON_LEFT_HAND) return;
            SwordInspect.apply(matrices, mode.isLeftHand() ? -1 : 1);
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("SwordInspect.apply", t);
        }
    }
}

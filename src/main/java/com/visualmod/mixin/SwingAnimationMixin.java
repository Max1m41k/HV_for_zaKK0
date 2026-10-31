package com.visualmod.mixin;

import net.minecraft.client.MinecraftClient;
import com.visualmod.VisualsMod;
import com.visualmod.module.modules.player.SwingAnimation;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import com.visualmod.module.modules.player.SwingMath;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class SwingAnimationMixin {

    private static boolean swingActive() {
        if (VisualsMod.moduleManager == null) return false;
        SwingAnimation mod = VisualsMod.moduleManager.getModule(SwingAnimation.class);
        return mod != null && mod.isEnabled();
    }

    @Inject(
            method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
            at = @At("HEAD"),
            require = 0
    )
    private void visuals$modifyItemTransforms(
            LivingEntity entity,
            ItemStack stack,
            ItemDisplayContext renderMode,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            int light,
            CallbackInfo ci
    ) {
        if (!swingActive()) return;

        if (renderMode == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND ||
                renderMode == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {

            boolean leftHanded = renderMode.isLeftHand();
            int i = leftHanded ? -1 : 1;

            boolean mainHand = entity == null || (leftHanded == (entity.getMainArm() == Arm.LEFT));
            if (mainHand) {
                matrices.translate(0.1F * i, -0.1F, -0.1F);

                matrices.multiply(new Quaternionf().rotationX((float) Math.toRadians(SwingAnimation.angleDeg)));
                matrices.multiply(new Quaternionf().rotationY((float) Math.toRadians(SwingAnimation.tiltDeg * i)));

                matrices.translate(-0.1F * i, 0.1F, 0.1F);
            }
        }
    }

    @Inject(method = "applySwingOffset", at = @At("HEAD"), cancellable = true)
    private void visuals$customSwing(MatrixStack matrices, Arm arm, float swingProgress, CallbackInfo ci) {
        if (VisualsMod.moduleManager == null) return;
        SwingAnimation mod = VisualsMod.moduleManager.getModule(SwingAnimation.class);
        if (mod == null || !mod.replacesVanillaSwing()) return;
        net.minecraft.client.MinecraftClient mcl = net.minecraft.client.MinecraftClient.getInstance();
        if (mcl.player != null && arm != mcl.player.getMainArm()) return;

        int i = arm == Arm.RIGHT ? 1 : -1;
        float[] a = SwingMath.params(SwingAnimation.mode, swingProgress, (float) SwingAnimation.strength);

        matrices.translate(a[3] * i, a[4], a[5]);
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(i * (45f + a[0])));
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(i * a[1]));
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(a[2]));
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(i * -45f));
        ci.cancel();
    }
}

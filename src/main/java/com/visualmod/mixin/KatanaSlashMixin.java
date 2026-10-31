package com.visualmod.mixin;

import com.visualmod.module.modules.render.CustomSword;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HeldItemRenderer.class, priority = 900)
public abstract class KatanaSlashMixin {

    private static boolean armed = true;
    private static int attackCounter = 0;

    @Inject(method = "applySwingOffset(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/Arm;F)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void visuals$katana(MatrixStack matrices, Arm arm, float p, CallbackInfo ci) {
        if (!CustomSword.slashActive()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || arm != mc.player.getMainArm()) return;
        if (p <= 0f) { armed = true; return; }

        if (armed) {
            armed = false;
            attackCounter++;
        }

        boolean isVertical = (attackCounter % 5 == 0);
        int i = arm == Arm.RIGHT ? 1 : -1;
        float g = MathHelper.sin(MathHelper.sqrt(p) * (float) Math.PI);
        float f = MathHelper.sin(p * p * (float) Math.PI);

        if (!isVertical) {
            float sweep = -50f * MathHelper.sin(p * (float) Math.PI);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(i * (25f + sweep)));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(i * g * -18f));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(g * -12f));
        } else {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(i * 15f));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(i * g * -5f));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(g * -115f));
        }
        ci.cancel();
    }
}

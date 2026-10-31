package com.visualmod.mixin;

import com.visualmod.cosmetics.CosmeticRenderer;
import com.visualmod.hand.HandEffects;
import com.visualmod.util.Diag;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HeldItemRenderer.class, priority = 950)
public class HandEffectsMixin {

    private static boolean pendingRoot = false;

    @Inject(
            method = "renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FF"
                    + "Lnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;F"
                    + "Lnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
            at = @At("HEAD"), require = 0)
    private void visuals$handRoot(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                  float swingProgress, ItemStack stack, float equipProgress,
                                  MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                  CallbackInfo ci) {
        boolean mainHand = hand == Hand.MAIN_HAND;
        pendingRoot = false;
        if (!mainHand || player == null) return;
        Arm arm = player.getMainArm();
        HandEffects.captureRoot(matrices, arm == Arm.RIGHT ? 1 : -1);
        HandEffects.armModelCapture(true);
        pendingRoot = true;
    }

    /**
     * Кадр руки берём после ванильного вызова, а не на HEAD: на HEAD ещё не отработали
     * ViewModelTransform (900), SwordInspect и BladeMorph (800) — их матрицы и уводили оверлей
     * за край экрана. INVOKE-AFTER гарантированно видит уже все повороты модели.
     */
    @Inject(
            method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;"
                    + "Lnet/minecraft/item/ItemDisplayContext;"
                    + "Lnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/ItemRenderState;render("
                            + "Lnet/minecraft/client/util/math/MatrixStack;"
                            + "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;III)V",
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void visuals$handItem(LivingEntity entity, ItemStack stack, ItemDisplayContext mode,
                                  MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                  CallbackInfo ci) {
        if (!pendingRoot) return;
        if (mode != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND && mode != ItemDisplayContext.FIRST_PERSON_LEFT_HAND) return;
        pendingRoot = false;
        HandEffects.captureItem(matrices);
    }

    @Inject(
            method = "renderItem(F"
                    + "Lnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;"
                    + "Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At("TAIL"),
            require = 0
    )
    private void visuals$handPassEnd(float ping, MatrixStack matrices,
                                     OrderedRenderCommandQueue queue,
                                     ClientPlayerEntity player, int light, CallbackInfo ci) {
        if (queue == null) return;
        // Кадр модели основной руки собран, окно захвата закрываем: иначе в него попадут
        // слои следующей рамки (иконки, чужие руки) и подменят клинок.
        HandEffects.armModelCapture(false);
        HandEffects.renderHandPass(matrices, queue);
        try {
            CosmeticRenderer.renderFirstPersonHands(matrices, queue);
        } catch (Throwable t) {
            Diag.once("HandEffects.gloves", t);
        }
    }
}

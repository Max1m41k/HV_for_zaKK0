package com.visualmod.mixin;

import com.visualmod.module.modules.render.ItemPhysics;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemStackEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.random.Random;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemPhysicsRenderMixin {

    // 1.21.11: предмет тоже уходит через очередь; важны только матрицы, остальное не объявляем.
    @Inject(method = "renderStack", at = @At("HEAD"), require = 0)
    private static void visuals$layFlat(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                        ItemStackEntityRenderState state, Random random, CallbackInfo ci) {
        if (!ItemPhysics.layFlat || !ItemPhysics.active() || ItemPhysics.lastBlock) return;
        matrices.translate(0.0, -0.19, 0.0);
        matrices.multiply(new Quaternionf().rotationX((float) (-Math.PI / 2)));
    }
}

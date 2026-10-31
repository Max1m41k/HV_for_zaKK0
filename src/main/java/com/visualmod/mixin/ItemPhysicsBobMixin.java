package com.visualmod.mixin;

import com.visualmod.module.modules.render.ItemPhysics;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemPhysicsBobMixin {

    // 1.21.11: MathHelper.sin принимает double, а «render» перегружен четырьмя методами —
    // привязка к конкретному дескриптору, иначе Mixin не сможет выбрать call-site.
    @Redirect(
            method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;"
                    + "Lnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;"
                    + "Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;sin(D)F"),
            require = 0
    )
    private float visuals$noBob(double value) {
        return (ItemPhysics.stopBob && ItemPhysics.active()) ? 0.0f : MathHelper.sin(value);
    }
}

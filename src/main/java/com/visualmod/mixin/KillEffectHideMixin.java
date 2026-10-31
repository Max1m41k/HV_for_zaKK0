package com.visualmod.mixin;

import com.visualmod.module.modules.render.KillEffect;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderManager.class)
public abstract class KillEffectHideMixin {

    @Inject(method = "shouldRender(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Frustum;DDD)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void visuals$hideCorpse(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (KillEffect.hides(entity)) cir.setReturnValue(false);
        else if (com.visualmod.module.modules.player.RespawnFx.hidesSelf(entity)) cir.setReturnValue(false);
    }
}

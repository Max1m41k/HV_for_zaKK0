package com.visualmod.mixin;

import com.visualmod.cosmetics.ModelMorph;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
public class PlayerEntityModelMorphMixin {

    @Inject(
            method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V",
            at = @At("TAIL")
    )
    private void visuals$modelMorph(PlayerEntityRenderState state, CallbackInfo ci) {
        try {
            ModelMorph.apply(state, (PlayerEntityModel) (Object) this);
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("PlayerEntityModelMorphMixin", t);
        }
    }
}

package com.visualmod.mixin;

import net.minecraft.client.MinecraftClient;
import com.visualmod.VisualsMod;
import com.visualmod.module.modules.movement.GhostTrail;
import com.visualmod.module.modules.render.NoRender;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public class AbstractClientPlayerEntityMixin {

    @Inject(method = "getFovMultiplier", at = @At("HEAD"), cancellable = true)
    private void onGetFovMultiplier(CallbackInfoReturnable<Float> cir) {
        if (VisualsMod.moduleManager != null) {
            NoRender mod = VisualsMod.moduleManager.getModule(NoRender.class);
            if (mod != null && mod.isEnabled() && NoRender.noFovChanges) {
                cir.setReturnValue(Float.valueOf(1.0f));
            }
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onPlayerTick(CallbackInfo ci) {
        AbstractClientPlayerEntity player = (AbstractClientPlayerEntity) (Object) this;
        if (net.minecraft.client.MinecraftClient.getInstance().player == player) {
            if (VisualsMod.moduleManager != null) {
                GhostTrail ghostModule = VisualsMod.moduleManager.getModule(GhostTrail.class);
                if (ghostModule != null && ghostModule.isEnabled()) {
                    GhostTrail.recordPlayerPosition(player);
                }
            }
        }
    }
}

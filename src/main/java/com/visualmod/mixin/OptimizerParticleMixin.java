package com.visualmod.mixin;

import com.visualmod.module.modules.player.Optimizer;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleManager.class)
public abstract class OptimizerParticleMixin {

    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void opt$cullFar(Particle particle, CallbackInfo ci) {
        if (particle == null || !Optimizer.active()) return;
        var c = particle.getBoundingBox().getCenter();
        if (Optimizer.shouldCullParticle(c.x, c.y, c.z)) ci.cancel();
    }
}

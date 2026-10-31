package com.visualmod.mixin;

import com.visualmod.module.modules.render.AutoReduce;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Срез по шагу счётчика: часть частиц не долетает до списка отрисовки, пока модуль бережёт FPS. */
@Mixin(ParticleManager.class)
public abstract class AutoReduceParticleMixin {

    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void hv$throttle(Particle particle, CallbackInfo ci) {
        if (particle != null && AutoReduce.shouldSkipParticle()) ci.cancel();
    }
}

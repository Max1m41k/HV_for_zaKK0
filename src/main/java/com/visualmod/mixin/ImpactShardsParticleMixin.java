package com.visualmod.mixin;

import com.visualmod.module.modules.render.ImpactShards;
import com.visualmod.util.Diag;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleManager.class)
public abstract class ImpactShardsParticleMixin {

    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void visuals$shards(ParticleEffect effect, double x, double y, double z,
                                double vx, double vy, double vz, CallbackInfoReturnable<Particle> cir) {
        try {
            if (ImpactShards.cancelVanillaCrit(effect, x, y, z)) {
                cir.setReturnValue(null);
            }
        } catch (Throwable t) {
            Diag.once("ImpactShardsParticleMixin", t);
        }
    }
}

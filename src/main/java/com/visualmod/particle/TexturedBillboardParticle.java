package com.visualmod.particle;

import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.random.Random;

public class TexturedBillboardParticle extends BillboardParticle {

    protected TexturedBillboardParticle(ClientWorld world, double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ, Sprite sprite) {
        super(world, x, y, z, velocityX, velocityY, velocityZ, sprite);
        this.gravityStrength = 0.03f;
        this.scale = 0.5f + this.random.nextFloat() * 0.4f;
        this.maxAge = 16 + this.random.nextInt(12);
        this.collidesWithWorld = false;
    }

    @Override
    protected RenderType getRenderType() {
        return RenderType.PARTICLE_ATLAS_TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        this.alpha = 1f - ((float) this.age / (float) this.maxAge);
    }

    public static class Factory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider spriteProvider;

        public Factory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientWorld world,
                                      double x, double y, double z,
                                      double velocityX, double velocityY, double velocityZ,
                                      Random random) {
            return new TexturedBillboardParticle(world, x, y, z, velocityX, velocityY, velocityZ,
                    spriteProvider.getSprite(random));
        }
    }
}

package com.visualmod.particle;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModParticles {
    private ModParticles() {}

    public static final SimpleParticleType HEART = register("heart");
    public static final SimpleParticleType FIREFLY = register("firefly");
    public static final SimpleParticleType POINT = register("point");
    public static final SimpleParticleType SNOWFLAKE = register("snowflake");
    public static final SimpleParticleType STAR = register("star");
    public static final SimpleParticleType LINE = register("line");
    public static final SimpleParticleType CROWN = register("crown");
    public static final SimpleParticleType BLOOM = register("bloom");
    public static final SimpleParticleType DOLLAR = register("dollar");
    public static final SimpleParticleType RHOMBUS = register("rhombus");
    public static final SimpleParticleType LIGHTNING = register("lightning");
    public static final SimpleParticleType SPARK = register("spark");

    private static SimpleParticleType register(String name) {
        return Registry.register(Registries.PARTICLE_TYPE, Identifier.of("visualmod", name), FabricParticleTypes.simple());
    }

    public static void registerFactories() {
        net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry registry =
                net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance();

        registry.register(HEART, TexturedBillboardParticle.Factory::new);
        registry.register(FIREFLY, TexturedBillboardParticle.Factory::new);
        registry.register(POINT, TexturedBillboardParticle.Factory::new);
        registry.register(SNOWFLAKE, TexturedBillboardParticle.Factory::new);
        registry.register(STAR, TexturedBillboardParticle.Factory::new);
        registry.register(LINE, TexturedBillboardParticle.Factory::new);
        registry.register(CROWN, TexturedBillboardParticle.Factory::new);
        registry.register(BLOOM, TexturedBillboardParticle.Factory::new);
        registry.register(DOLLAR, TexturedBillboardParticle.Factory::new);
        registry.register(RHOMBUS, TexturedBillboardParticle.Factory::new);
        registry.register(LIGHTNING, TexturedBillboardParticle.Factory::new);
        registry.register(SPARK, TexturedBillboardParticle.Factory::new);
    }

    public static void init() {
    }
}

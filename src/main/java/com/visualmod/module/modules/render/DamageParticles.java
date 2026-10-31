package com.visualmod.module.modules.render;

import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.particle.CustomParticleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;

import java.util.Arrays;
import java.util.List;

public class DamageParticles extends Module implements Configurable {

    public enum Style {
        DAMAGE_INDICATOR, CRIT, DUST, ANGRY,
        HEART, FIREFLY, POINT, SNOWFLAKE, STAR, LINE,
        CROWN, BLOOM, DOLLAR, RHOMBUS, LIGHTNING, SPARK,
        HW
    }

    public static Style style = Style.SPARK;
    private int color = 0xFFFF3B3B;
    private double particleCount = 8;

    private final List<Setting> settings = List.of(
            new ModeSetting(
                    "Style",
                    Arrays.stream(Style.values()).map(Enum::name).toArray(String[]::new),
                    () -> style.ordinal(),
                    i -> style = Style.values()[i]
            ),
            new ColorSetting("Color", () -> color, c -> color = c),
            new SliderSetting("Particles", 4, 20, 2, () -> particleCount, v -> particleCount = v)
    );

    public DamageParticles() {
        super("DamageParticles", "Particles at target's legs when you hit them", Category.RENDER);
    }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }

    public static void onHit(DamageParticles mod, PlayerEntity targetPlayer) {
        if (mod == null || !mod.isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        int count = (int) mod.particleCount;
        double legY = targetPlayer.getY() + targetPlayer.getHeight() * 0.15;

        int rgbColor = mod.color & 0xFFFFFF;
        DustParticleEffect dustEffect = new DustParticleEffect(rgbColor, 1.0f);

        for (int i = 0; i < count; i++) {
            double x = targetPlayer.getX() + (Math.random() - 0.5) * 0.6;
            double y = legY + (Math.random() - 0.5) * 0.15;
            double z = targetPlayer.getZ() + (Math.random() - 0.5) * 0.6;

            double vx = (Math.random() - 0.5) * 0.15;
            double vy = 0.05 + Math.random() * 0.1;
            double vz = (Math.random() - 0.5) * 0.15;

            switch (style) {
                case DAMAGE_INDICATOR -> mc.world.addParticleClient(ParticleTypes.DAMAGE_INDICATOR, x, y, z, vx, vy, vz);
                case CRIT -> mc.world.addParticleClient(ParticleTypes.CRIT, x, y, z, vx, vy, vz);

                case DUST -> mc.world.addParticleClient(dustEffect, x, y, z, vx, vy, vz);

                case ANGRY -> mc.world.addParticleClient(ParticleTypes.ANGRY_VILLAGER, x, y, z, 0, 0.05, 0);

                case HEART -> CustomParticleManager.spawn("heart", x, y, z, vx, vy, vz);
                case FIREFLY -> CustomParticleManager.spawn("firefly", x, y, z, vx, vy, vz);
                case POINT -> CustomParticleManager.spawn("point", x, y, z, vx, vy, vz);
                case SNOWFLAKE -> CustomParticleManager.spawn("snowflake", x, y, z, vx, vy, vz);
                case STAR -> CustomParticleManager.spawn("star", x, y, z, vx, vy, vz);
                case LINE -> CustomParticleManager.spawn("line", x, y, z, vx, vy, vz);
                case CROWN -> CustomParticleManager.spawn("crown", x, y, z, vx, vy, vz);
                case BLOOM -> CustomParticleManager.spawn("bloom", x, y, z, vx, vy, vz);
                case DOLLAR -> CustomParticleManager.spawn("dollar", x, y, z, vx, vy, vz);
                case RHOMBUS -> CustomParticleManager.spawn("rhombus", x, y, z, vx, vy, vz);
                case LIGHTNING -> CustomParticleManager.spawn("lightning", x, y, z, vx, vy, vz);
                case SPARK -> CustomParticleManager.spawn("spark", x, y, z, vx, vy, vz);
                case HW -> CustomParticleManager.spawn("hw", x, y, z, vx, vy, vz, 0.8f);
            }
        }
    }
}

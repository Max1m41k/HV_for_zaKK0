package com.visualmod.module.modules.render;

import com.visualmod.util.McCompat;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.particle.CustomParticleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

public class Atmosphere extends Module implements Configurable {

    private static final String[] SPRITES = {
            "star", "snowflake", "firefly", "bloom", "spark", "heart", "crown", "lightning", "rhombus", "point", "hw"
    };

    public static int sprite = 0;
    public static double amount = 12;
    public static double fadeSeconds = 4;
    public static double size = 0.55;
    public static int color = 0xBFD8FF;

    private double spawnBudget;
    private Vec3d lastPos;

    private final List<Setting> settings = List.of(
            new ModeSetting("Sprite", SPRITES, () -> sprite, i -> sprite = i),
            new SliderSetting("Amount", 1, 40, 1, () -> amount, v -> amount = v, " /s"),
            new SliderSetting("Fade", 0.5, 12, 0.5, () -> fadeSeconds, v -> fadeSeconds = v, " s"),
            new SliderSetting("Size", 0.2, 1.5, 0.05, () -> size, v -> size = v),
            new ColorSetting("Color", () -> color, c -> color = c)
    );

    public Atmosphere() {
        super("Atmosphere", "Our sprites drift slowly across the sky above you", Category.RENDER);
    }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (mc.world.getRegistryKey() != World.OVERWORLD) return;

        Vec3d pos = McCompat.pos(mc.player);
        // Телепорт/смена мира: старые спрайты остались висеть в прошлом месте.
        if (lastPos == null || lastPos.distanceTo(pos) > 64) CustomParticleManager.clear();
        lastPos = pos;

        spawnBudget += amount / 20.0;
        int life = (int) (fadeSeconds * 20);
        while (spawnBudget >= 1) {
            spawnBudget--;
            if (CustomParticleManager.count() > 900) return;
            spawnAbove(mc, pos, life);
        }
    }

    private void spawnAbove(MinecraftClient mc, Vec3d origin, int life) {
        double angle = Math.random() * Math.PI * 2;
        double radius = 8 + Math.random() * 22;
        double x = origin.x + Math.cos(angle) * radius;
        double z = origin.z + Math.sin(angle) * radius;
        double y = origin.y + 6 + Math.random() * 24;
        // Под крышей/в пещере спрайты висели бы среди блоков — берём только открытое небо.
        if (mc.world.getLightLevel(BlockPos.ofFloored(x, y, z)) < 12) return;

        float s = (float) (size * (0.7 + Math.random() * 0.6));
        double vx = (Math.random() - 0.5) * 0.008;
        double vy = 0.004 + Math.random() * 0.008;
        double vz = (Math.random() - 0.5) * 0.008;

        CustomParticleManager.spawn(SPRITES[sprite], x, y, z, vx, vy, vz,
                s, color, 0.0004f, life);
    }

    @Override
    public void onDisable() {
        spawnBudget = 0;
        lastPos = null;
        CustomParticleManager.clear();
    }
}

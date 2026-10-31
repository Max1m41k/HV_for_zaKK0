package com.visualmod.module.modules.player;

import com.visualmod.util.McCompat;
import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

import java.util.ArrayList;
import java.util.List;

public class Optimizer extends Module implements Configurable {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static boolean backgroundFps = true;
    public static long backgroundFpsLimit = 15;
    public static boolean smartCulling = true;
    public static boolean reduceParticles = true;
    public static boolean reduceEntityRender = true;
    public static int entityRenderDistance = 48;
    public static boolean optimizeChunkUpdates = true;

    public Optimizer() {
        super("Optimizer", "Умная оптимизация рендера и ресурсов процессора", Category.PLAYER);
    }

    public static boolean active() {
        if (VisualsMod.moduleManager == null) return false;
        Optimizer mod = VisualsMod.moduleManager.getModule(Optimizer.class);
        return mod != null && mod.isEnabled();
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> list = new ArrayList<>();
        list.add(new ModeSetting("Smart Culling", new String[]{"OFF", "ON"}, () -> smartCulling ? 1 : 0, i -> smartCulling = (i == 1)));
        list.add(new ModeSetting("Reduce Particles", new String[]{"OFF", "ON"}, () -> reduceParticles ? 1 : 0, i -> reduceParticles = (i == 1)));
        list.add(new ModeSetting("Reduce Entity Render", new String[]{"OFF", "ON"}, () -> reduceEntityRender ? 1 : 0, i -> reduceEntityRender = (i == 1)));
        list.add(new SliderSetting("Entity Distance", 16, 128, 4, () -> entityRenderDistance, v -> entityRenderDistance = (int) v, "blocks"));
        list.add(new ModeSetting("Optimize Chunks", new String[]{"OFF", "ON"}, () -> optimizeChunkUpdates ? 1 : 0, i -> optimizeChunkUpdates = (i == 1)));
        list.add(new ModeSetting("Background FPS", new String[]{"OFF", "ON"}, () -> backgroundFps ? 1 : 0, i -> backgroundFps = (i == 1)));
        list.add(new SliderSetting("Background Limit", 5, 60, 5, () -> backgroundFpsLimit, v -> backgroundFpsLimit = (long) v, "FPS"));
        return list;
    }

    public static boolean shouldCull(Entity entity) {
        if (!active() || !smartCulling || mc.player == null) return false;
        if (entity == mc.player) return false;

        double distSq = mc.player.squaredDistanceTo(entity);
        if (distSq > entityRenderDistance * entityRenderDistance) {
            return true;
        }

        try {
            net.minecraft.util.math.Vec3d playerLook = mc.player.getRotationVector();
            net.minecraft.util.math.Vec3d toEntity = McCompat.pos(entity).subtract(McCompat.pos(mc.player)).normalize();
            double dot = playerLook.dotProduct(toEntity);
            if (dot < -0.1 && distSq > 6.0) {
                return true;
            }
        } catch (Exception ignored) {}

        return false;
    }

    public static boolean shouldCullParticle(double x, double y, double z) {
        if (!active() || !reduceParticles || mc.player == null) return false;

        double distSq = mc.player.squaredDistanceTo(x, y, z);
        if (distSq > 16.0 * 16.0) {
            return true;
        }
        return false;
    }

    public static boolean shouldSkipEntityRender(Entity entity) {
        if (!active() || !reduceEntityRender || mc.player == null) return false;
        if (entity == mc.player) return false;

        double distSq = mc.player.squaredDistanceTo(entity);
        if (distSq > entityRenderDistance * entityRenderDistance) {
            return true;
        }

        if (distSq > 25.0) {
            String entityType = entity.getType().toString();
            if (entityType.contains("item_frame") ||
                entityType.contains("armor_stand") ||
                entityType.contains("painting")) {
                return true;
            }
        }

        return false;
    }
}

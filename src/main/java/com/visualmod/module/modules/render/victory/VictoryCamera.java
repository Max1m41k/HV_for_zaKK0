package com.visualmod.module.modules.render.victory;

import com.visualmod.util.McCompat;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

public final class VictoryCamera {

    public static volatile boolean active = false;

    public static volatile double x, y, z;
    public static volatile float yaw, pitch;

    private VictoryCamera() {}

    public static void release() { active = false; }

    public static void orbit(Entity around, double distance, double height, double angleDeg) {
        if (around == null || distance <= 0.05) { release(); return; }
        Vec3d p = McCompat.pos(around).add(0, around.getHeight() * 0.6, 0);
        double a = Math.toRadians(angleDeg);
        x = p.x - Math.sin(a) * distance;
        z = p.z + Math.cos(a) * distance;
        y = p.y + height;
        yaw = (float) (angleDeg + 180.0);
        pitch = (float) Math.toDegrees(Math.atan2(height, distance));
        active = true;
    }

    public static void fromEyes(Entity around) {
        if (around == null) { release(); return; }
        double eye = around.getY() + around.getHeight() * 0.92;
        float lookYaw = around.getYaw();
        float lookPitch = around.getPitch();
        double rad = Math.toRadians(lookYaw);
        x = around.getX() - Math.sin(rad) * 0.25;
        z = around.getZ() + Math.cos(rad) * 0.25;
        y = eye;
        yaw = lookYaw;
        pitch = lookPitch;
        active = true;
    }

    public static void behind(Entity who, double distance, double height) {
        if (who == null) { release(); return; }
        double rad = Math.toRadians(who.getYaw());
        x = who.getX() + Math.sin(rad) * Math.max(0.2, distance);
        z = who.getZ() - Math.cos(rad) * Math.max(0.2, distance);
        y = who.getY() + who.getHeight() * 0.6 + height;
        yaw = who.getYaw();
        pitch = who.getPitch();
        active = true;
    }
}

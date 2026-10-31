package com.visualmod.module.modules.player;

/**
 * Камера сцены возрождения: тот же приём, что и VictoryCamera (принудительная позиция
 * поверх ванильной), но со своим флагом — килл-синематик и респавн не делят одну камеру.
 */
public final class RespawnCamera {

    public static volatile boolean active = false;

    public static volatile double x, y, z;
    public static volatile float yaw, pitch;

    private RespawnCamera() {}

    public static void release() { active = false; }

    public static void orbit(double cx, double cy, double cz, double distance, double height, double angleDeg) {
        double a = Math.toRadians(angleDeg);
        x = cx - Math.sin(a) * distance;
        z = cz + Math.cos(a) * distance;
        y = cy + height;
        yaw = (float) (angleDeg + 180.0);
        pitch = (float) Math.toDegrees(Math.atan2(height, distance));
        active = true;
    }
}

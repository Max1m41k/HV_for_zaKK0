package com.visualmod.record;

public final class ReplayCamera {

    public static volatile boolean active = false;
    public static volatile double x, y, z;
    public static volatile float yaw, pitch;

    private ReplayCamera() {}

    public static void release() { active = false; }

    public static void set(double px, double py, double pz, float pyaw, float ppitch) {
        x = px; y = py; z = pz; yaw = pyaw; pitch = ppitch;
        active = true;
    }
}

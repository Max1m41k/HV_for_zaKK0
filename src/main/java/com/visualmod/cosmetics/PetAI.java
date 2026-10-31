package com.visualmod.cosmetics;

import com.visualmod.module.modules.cosmetics.CosmeticsManager;
import com.visualmod.util.KillTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Поведение питомца: свободный полёт вокруг хозяина, отставание при быстром беге,
 * выпад в сторону цели при ударе, победная спираль на килле и испуг, когда бьют нас.
 */
public final class PetAI {

    public static final class Pose {
        public float x, y, z;
        public float yaw;
        public float flap;
        public String mode = "ORBIT";
    }

    private static final Pose OUT = new Pose();

    private static float cx, cy = 24f, cz;
    private static float lastYaw;
    private static double lastX, lastY, lastZ;
    private static boolean primed;
    private static double lastSec = -1.0;
    private static double speed;
    private static float aimAngle;
    private static long diveUntil;
    private static boolean diveReturn;
    private static long celebrateUntil;
    private static long scareUntil;
    private static float lastHealth = -1f;
    private static int phaseSeed;
    private static double animClock;

    private PetAI() {}

    static {
        KillTracker.register((victim, x, y, z, height) -> onKill());
    }

    /** Удар: запоминаем направление на цель в системе «вперёд от игрока». */
    public static void onAttack(Entity target) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || target == null || !enabled()) return;
        double dx = target.getX() - mc.player.getX();
        double dz = target.getZ() - mc.player.getZ();
        if (Math.hypot(dx, dz) < 0.2) return;
        double yaw = Math.toRadians(mc.player.getYaw());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        aimAngle = (float) Math.atan2(fx * dz - fz * dx, fx * dx + fz * dz);
        diveUntil = System.currentTimeMillis() + 620L;
        diveReturn = false;
        phaseSeed = (phaseSeed + 1) & 7;
    }

    private static void onKill() {
        if (!enabled()) return;
        celebrateUntil = System.currentTimeMillis() + 1500L;
    }

    public static boolean enabled() {
        return CosmeticsManager.petAI && CosmeticsManager.equippedPet >= 0;
    }

    public static Pose pose(double sec, int petIdx) {
        tick(sec, petIdx);
        return OUT;
    }

    /** Часы для анимаций самой модели: при взмахе крылья бегают быстрее. */
    public static double animTime() {
        return animClock;
    }

    private static void tick(double sec, int petIdx) {
        double dt = lastSec < 0 ? 0.05 : Math.min(0.06, Math.max(0.004, sec - lastSec));
        lastSec = sec;
        long now = System.currentTimeMillis();

        if (!enabled()) {
            double a = sec * 0.55;
            OUT.x = (float) (Math.cos(a) * 13.0);
            OUT.y = (float) (24.0 + Math.sin(sec * 1.8) * 1.6);
            OUT.z = (float) (Math.sin(a) * 13.0);
            OUT.yaw = (float) (-a - Math.PI / 2);
            OUT.flap = 0.35f;
            OUT.mode = "ORBIT";
            primed = false;
            animClock += dt;
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity player = mc.player;
        if (player != null) {
            if (lastHealth >= 0 && player.getHealth() < lastHealth - 0.5f) scareUntil = now + 900L;
            lastHealth = player.getHealth();
            double dx = player.getX() - lastX, dy = player.getY() - lastY, dz = player.getZ() - lastZ;
            double inst = Math.sqrt(dx * dx + dy * dy + dz * dz) / dt;
            speed = speed * 0.82 + Math.min(24.0, inst) * 0.18;
            lastX = player.getX(); lastY = player.getY(); lastZ = player.getZ();
        }

        double radius = 13.0 + (petIdx % 3) * 2.5;
        double height = 24.0 + (petIdx % 2) * 2.0;
        double ang = sec * (0.55 + 0.05 * (petIdx % 4)) + petIdx * 1.1;

        boolean diving = now < diveUntil;
        boolean celebrating = now < celebrateUntil;
        boolean scared = now < scareUntil;
        boolean riding = player != null && speed > 5.5 && !diving && !celebrating;

        double targetX, targetY, targetZ;
        float yaw;
        float flap;
        String mode;

        if (diving) {
            float push = (float) Math.sin(Math.min(1f, (diveUntil - now) / 620f) * Math.PI);
            double r = 15.0 + 15.0 * push;
            targetX = -Math.sin(aimAngle) * r;
            targetZ = -Math.cos(aimAngle) * r;
            targetY = 20.0 + 8.0 * push;
            diveReturn = push < 0.35f;
            flap = 1f;
            mode = "DIVE";
            yaw = diveReturn ? (float) (aimAngle + Math.PI) : aimAngle;
        } else if (celebrating) {
            double k = (celebrateUntil - now) / 1500.0;
            double ca = sec * 6.2 + phaseSeed;
            double cr = 7.0 + 6.0 * (1.0 - k);
            targetX = Math.cos(ca) * cr;
            targetZ = Math.sin(ca) * cr;
            targetY = height + 3.0 + 6.0 * Math.sin(sec * 7.5);
            flap = 1f;
            mode = "WIN";
            yaw = (float) (Math.PI - ca);
        } else if (scared) {
            targetX = noise(now / 90.0) * 1.4;
            targetZ = -9.0 + noise(now / 70.0 + 3.0) * 1.4;
            targetY = height + 7.0 + Math.sin(sec * 9.0) * 0.8;
            flap = 0.85f;
            mode = "HIDE";
            yaw = (float) Math.atan2(targetX, targetZ);
        } else if (riding) {
            targetX = noise(sec * 0.9) * 3.0;
            targetZ = 12.0 + Math.min(10.0, speed) * 0.8;
            targetY = height - 2.0 + Math.sin(sec * 3.2) * 1.2;
            flap = 0.7f;
            mode = "TRAIL";
            yaw = 0f;
        } else {
            targetX = Math.cos(ang) * radius;
            targetZ = Math.sin(ang) * radius;
            targetY = height + Math.sin(sec * 1.8 + petIdx) * 1.6 + noise(sec * 0.6) * 1.2;
            flap = 0.35f;
            mode = "ORBIT";
            yaw = (float) (-ang - Math.PI / 2);
        }

        float k = (float) Math.min(1.0, dt * (diving ? 16f : celebrating ? 12f : 5.5f));
        if (!primed) {
            cx = (float) targetX; cy = (float) targetY; cz = (float) targetZ;
            lastYaw = yaw;
            primed = true;
        }
        cx += ((float) targetX - cx) * k;
        cy += ((float) targetY - cy) * k;
        cz += ((float) targetZ - cz) * k;

        float ky = (float) Math.min(1.0, dt * (diving ? 20f : 9f));
        lastYaw = lerpAngle(lastYaw, yaw, ky);

        OUT.x = cx; OUT.y = cy; OUT.z = cz;
        OUT.yaw = lastYaw;
        OUT.flap = flap;
        OUT.mode = mode;
        animClock += dt * (0.6 + 1.8 * flap);
    }

    private static float lerpAngle(float from, float to, float k) {
        float d = (to - from) % (2f * (float) Math.PI);
        if (d > (float) Math.PI) d -= 2f * (float) Math.PI;
        if (d < -(float) Math.PI) d += 2f * (float) Math.PI;
        return from + d * k;
    }

    /** Непрерывный псевдослучайный дрейф: не зависит от FPS и не дёргается между кадрами. */
    private static double noise(double t) {
        return Math.sin(t * 1.7) * 0.6 + Math.sin(t * 3.9 + 1.3) * 0.3 + Math.sin(t * 7.7 + 2.1) * 0.1;
    }
}

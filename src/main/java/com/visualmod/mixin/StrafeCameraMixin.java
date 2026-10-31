package com.visualmod.mixin;

import com.visualmod.VisualsMod;
import com.visualmod.module.modules.movement.Strafe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
public abstract class StrafeCameraMixin {

    @Shadow
    private org.joml.Quaternionf rotation;

    @ModifyArgs(
            method = "update",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V")
    )
    private void visuals$strafeTilt(Args args) {
        Strafe mod = activeStrafe();
        if (mod == null) return;

        double[] v = strafeVelocity();
        double strafeVel = v[0], forwardVel = v[1], speed = v[2];
        if (speed < 0.05) return;

        float yaw = args.get(0);
        float pitch = args.get(1);
        double sec = System.currentTimeMillis() / 1000.0;

        switch (Strafe.style) {
            case "BOB" -> {
                float bob = (float) Math.sin(sec * 8 * Strafe.speed) * 0.8f * Strafe.intensity * (float) Math.min(1, speed);
                pitch += bob;
            }
            case "SPIN" -> {
                yaw += (float) (strafeVel * 2 * Strafe.intensity);
            }
            default -> {
                if (forwardVel < -0.1) {
                    pitch += (float) (forwardVel * 3 * Strafe.intensity);
                }
            }
        }

        args.set(0, yaw);
        args.set(1, pitch);
    }

    @Inject(method = "setRotation", at = @At("TAIL"))
    private void visuals$strafeRoll(float yaw, float pitch, CallbackInfo ci) {
        Strafe mod = activeStrafe();
        if (mod == null) return;
        double[] v = strafeVelocity();
        double strafeVel = v[0], speed = v[2];
        if (speed < 0.05) return;

        double rollDeg;
        if ("SPIN".equals(Strafe.style)) {
            rollDeg = Math.max(-12.0, Math.min(12.0, strafeVel * 8 * Strafe.intensity));
        } else if ("BOB".equals(Strafe.style)) {
            return;
        } else {
            rollDeg = Math.max(-18.0, Math.min(18.0, -strafeVel * 12 * Strafe.intensity));
        }
        if (Math.abs(rollDeg) < 0.05) return;
        this.rotation.rotateAxis((float) Math.toRadians(rollDeg), 0.0F, 0.0F, 1.0F);
    }

    private static Strafe activeStrafe() {
        if (VisualsMod.moduleManager == null) return null;
        Strafe mod = VisualsMod.moduleManager.getModule(Strafe.class);
        if (mod == null || !mod.isEnabled()) return null;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return null;
        return mod;
    }

    private static double[] strafeVelocity() {
        AbstractClientPlayerEntity p = MinecraftClient.getInstance().player;
        double yawRad = Math.toRadians(p.bodyYaw);
        double sinY = Math.sin(yawRad), cosY = Math.cos(yawRad);
        double velX = p.getVelocity().x;
        double velZ = p.getVelocity().z;
        double strafeVel = velX * cosY + velZ * sinY;
        double forwardVel = -velX * sinY + velZ * cosY;
        return new double[]{strafeVel, forwardVel, Math.sqrt(velX * velX + velZ * velZ)};
    }
}

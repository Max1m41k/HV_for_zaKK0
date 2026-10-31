package com.visualmod.mixin;

import com.visualmod.module.modules.render.FreeLook;
import com.visualmod.module.modules.render.victory.VictoryCamera;
import com.visualmod.module.modules.player.RespawnCamera;
import com.visualmod.record.ReplayCamera;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class VictoryCameraMixin {

    @Shadow protected abstract void setPos(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    // 1.21.11: Camera.update принимает World, а не BlockView.
    @Inject(method = "update", at = @At("TAIL"), require = 0)
    private void visuals$direct(net.minecraft.world.World area, Entity focusedEntity, boolean thirdPerson,
                                boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (VictoryCamera.active) {
            if (FreeLook.isActive()) return;
            setRotation(VictoryCamera.yaw, VictoryCamera.pitch);
            setPos(VictoryCamera.x, VictoryCamera.y, VictoryCamera.z);
            return;
        }
        if (ReplayCamera.active) {
            setRotation(ReplayCamera.yaw, ReplayCamera.pitch);
            setPos(ReplayCamera.x, ReplayCamera.y, ReplayCamera.z);
            return;
        }
        if (RespawnCamera.active) {
            if (FreeLook.isActive()) return;
            setRotation(RespawnCamera.yaw, RespawnCamera.pitch);
            setPos(RespawnCamera.x, RespawnCamera.y, RespawnCamera.z);
        }
    }
}

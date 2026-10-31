package com.visualmod.mixin;

import com.visualmod.module.modules.player.TotemAnimation;
import com.visualmod.screen.HolyWorldScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onGameJoin", at = @At("TAIL"))
    private void visuals$sendAnarchyCommand(GameJoinS2CPacket packet, CallbackInfo ci) {
        if (HolyWorldScreen.commandToSend != null) {
            String command = HolyWorldScreen.commandToSend;
            HolyWorldScreen.commandToSend = null;

            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    MinecraftClient.getInstance().execute(() -> {
                        if (MinecraftClient.getInstance().player != null) {
                            MinecraftClient.getInstance().player.networkHandler.sendChatCommand(command.replace("/", ""));
                        }
                    });
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }

    @Inject(method = "onEntityStatus", at = @At("TAIL"))
    private void visuals$totemPop(EntityStatusS2CPacket packet, CallbackInfo ci) {
        if (packet.getStatus() != 35) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        Entity entity = packet.getEntity(mc.world);
        if (entity instanceof PlayerEntity player) TotemAnimation.trigger(player);
    }

    @Inject(method = "onEntityStatus", at = @At("HEAD"))
    private void visuals$shieldFracture(EntityStatusS2CPacket packet, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        com.visualmod.util.CombatPulse.onStatus(mc, packet.getEntity(mc.world), packet.getStatus());
        com.visualmod.module.modules.combat.ClashSparks.onStatus(mc, packet.getEntity(mc.world), packet.getStatus());
    }

    @Inject(method = "onEntityAnimation", at = @At("HEAD"))
    private void visuals$vulnerabilityPulse(EntityAnimationS2CPacket packet, CallbackInfo ci) {
        // Взмах любой руки — начало кулдауна атаки противника; остальные анимации игнорируем.
        if (packet.getAnimationId() != EntityAnimationS2CPacket.SWING_MAIN_HAND
                && packet.getAnimationId() != EntityAnimationS2CPacket.SWING_OFF_HAND) return;
        com.visualmod.util.CombatPulse.onSwing(MinecraftClient.getInstance(), packet.getEntityId());
    }

    /**
     * Радиус взрыва сервер клиенту не сообщает (explosionPower не синхронизируется), поэтому
     * пакет взрыва — единственный источник настоящего числа. Кладём его в очередь и учитываем
     * в клиентском потоке: здесь мир трогать нельзя.
     */
    @Inject(method = "onExplosion", at = @At("HEAD"))
    private void visuals$tntBlast(ExplosionS2CPacket packet, CallbackInfo ci) {
        com.visualmod.util.TntWatch.onExplosion(packet.center(), packet.radius());
    }
}

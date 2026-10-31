package com.visualmod.mixin;

import com.visualmod.util.PacketStats;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Хвост пакетов считается на io-потоке netty — чип «Packets» берёт из него только атомарный счётчик. */
@Mixin(ClientConnection.class)
public abstract class PacketArrivalMixin {

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V",
            at = @At("HEAD"), require = 0)
    private void hv$inbound(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        PacketStats.recordInbound();
    }
}

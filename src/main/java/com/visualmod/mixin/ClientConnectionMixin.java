package com.visualmod.mixin;

import com.visualmod.module.modules.player.NetworkOptimizer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.WriteBufferWaterMark;
import net.minecraft.network.ClientConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public class ClientConnectionMixin {

    @Shadow private Channel channel;

    @Inject(method = "channelActive", at = @At("HEAD"))
    private void onChannelActive(io.netty.channel.ChannelHandlerContext context, CallbackInfo ci) {
        if (!NetworkOptimizer.isActive()) return;

        if (NetworkOptimizer.tcpNoDelay) {
            try {
                context.channel().config().setOption(ChannelOption.TCP_NODELAY, true);

                context.channel().config().setOption(ChannelOption.IP_TOS, 0x10);

                context.channel().config().setWriteBufferWaterMark(new WriteBufferWaterMark(32768, 65536));

                context.channel().config().setOption(ChannelOption.SO_RCVBUF, 65536);
                context.channel().config().setOption(ChannelOption.SO_SNDBUF, 65536);
            } catch (Exception e) {
            }
        }
    }
}

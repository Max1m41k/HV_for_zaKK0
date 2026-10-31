package com.visualmod.mixin;

import com.visualmod.util.ConnectScreenAccess;
import io.netty.channel.ChannelFuture;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.network.ClientConnection;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin implements ConnectScreenAccess {

    @Shadow volatile ClientConnection connection;
    @Shadow ChannelFuture future;
    @Shadow volatile boolean connectingCancelled;

    @Override
    public void visuals$abort() {
        synchronized (this) {
            this.connectingCancelled = true;
            if (this.future != null) {
                this.future.cancel(true);
                this.future = null;
            }
            if (this.connection != null) {
                this.connection.disconnect(Text.literal("Aborted"));
            }
        }
    }
}

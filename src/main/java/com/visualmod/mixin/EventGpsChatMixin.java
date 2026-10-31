package com.visualmod.mixin;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public class EventGpsChatMixin {

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"))
    private void visuals$eventGps(Text message, MessageSignatureData signature, MessageIndicator indicator, CallbackInfo ci) {
        try {
            // ChatTools может вшить в строку метку времени и счётчик повторов — парсерам нужен чистый текст.
            String line = com.visualmod.util.ChatLog.canonical(message.getString());
            com.visualmod.util.EventGPS.onChatLine(line);
            com.visualmod.util.PvpSafe.onChatLine(line);
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("EventGpsChatMixin", t);
        }
    }
}

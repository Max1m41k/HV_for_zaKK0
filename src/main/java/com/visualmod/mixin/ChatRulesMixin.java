package com.visualmod.mixin;

import com.visualmod.module.modules.hud.ChatRules;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public class ChatRulesMixin {

    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"), argsOnly = true)
    private Text visuals$stamp(Text message) {
        try {
            return ChatRules.stamp(message);
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("ChatRulesMixin.stamp", t);
            return message;
        }
    }

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"), cancellable = true)
    private void visuals$antiSpam(Text message, MessageSignatureData signature, MessageIndicator indicator, CallbackInfo ci) {
        try {
            if (ChatRules.isSpam(message.getString())) ci.cancel();
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("ChatRulesMixin.antiSpam", t);
        }
    }
}

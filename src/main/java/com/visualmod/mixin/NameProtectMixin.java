package com.visualmod.mixin;

import com.visualmod.util.NameProtectUtil;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatHud.class)
public class NameProtectMixin {
    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Text onAddMessage(Text original) {
        return com.visualmod.util.NametagUtil.decorateLine(
                NameProtectUtil.apply(original), com.visualmod.module.modules.cosmetics.NametagStyle.CHAT);
    }
}

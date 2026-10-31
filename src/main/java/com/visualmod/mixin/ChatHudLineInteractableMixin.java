package com.visualmod.mixin;

import com.visualmod.util.ChatLineFx;
import net.minecraft.client.font.Alignment;
import net.minecraft.client.font.DrawnTextConsumer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Бэкенд чата, который рисуется, когда чат открыт (HoverEvent-ы работают через него). */
@Mixin(targets = "net.minecraft.client.gui.hud.ChatHud$Interactable")
public abstract class ChatHudLineInteractableMixin {

    @Shadow
    @Final
    private DrawContext context;

    @Redirect(
            method = "text(IFLnet/minecraft/text/OrderedText;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/font/DrawnTextConsumer;text"
                            + "(Lnet/minecraft/client/font/Alignment;IILnet/minecraft/client/font/"
                            + "DrawnTextConsumer$Transformation;Lnet/minecraft/text/OrderedText;)V"
            ))
    private void visuals$chatLine(DrawnTextConsumer consumer, Alignment align, int x, int y,
                                  DrawnTextConsumer.Transformation pose, OrderedText text) {
        ChatLineFx.draw(context, consumer, align, x, y, pose, text);
    }
}

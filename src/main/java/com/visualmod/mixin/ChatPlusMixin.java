package com.visualmod.mixin;

import com.visualmod.module.modules.util.ChatPlus;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public abstract class ChatPlusMixin {

    @ModifyConstant(
            method = {
                    "addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V",
                    "addVisibleMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V"
            },
            constant = @Constant(intValue = 100),
            require = 0
    )
    private int visuals$historyLimit(int limit) {
        return ChatPlus.historyLimit();
    }

    // 1.21.11: отступ чата зашит в лямбду позы, поэтому сдвигаем матрицу сразу после pushMatrix.
    @Inject(
            method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V",
            at = @At(value = "INVOKE",
                    target = "Lorg/joml/Matrix3x2fStack;pushMatrix()Lorg/joml/Matrix3x2fStack;",
                    shift = At.Shift.AFTER),
            require = 0
    )
    private void visuals$chatLeftMargin(DrawContext ctx, TextRenderer textRenderer, int x, int y, int ticks,
                                        boolean focused, boolean scrolled, CallbackInfo ci) {
        float gutter = ChatPlus.gutterChat();
        if (gutter > 0f) ctx.getMatrices().translate(gutter, 0f);
    }
}

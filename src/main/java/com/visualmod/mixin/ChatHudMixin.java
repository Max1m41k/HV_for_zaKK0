package com.visualmod.mixin;

import com.visualmod.module.modules.util.ChatPlus;
import com.visualmod.module.modules.util.ChatTools;
import com.visualmod.util.ChatLineFx;
import com.visualmod.util.ChatLog;
import com.visualmod.util.HWRulesAuto;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

    @Shadow
    @Final
    private List<ChatHudLine.Visible> visibleMessages;

    @Shadow
    private int scrolledLines;

    @Invoker("getChatScale")
    abstract double visuals$chatScale();

    /**
     * Метка времени, счётчик повторов и кликабельные координаты вшиваются в текст до того,
     * как ChatHud разрежет сообщение на строки: переносы и ширина остаются ванильными.
     */
    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"), argsOnly = true)
    private Text visuals$chatTools(Text message) {
        if (!ChatTools.active()) return message;
        return ChatLog.decorate(message);
    }

    @Inject(
            method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V",
            at = @At("HEAD"))
    private void visuals$beginFrame(DrawContext ctx, TextRenderer textRenderer, int currentTick,
                                    int mouseX, int mouseY, boolean constantOpacity, boolean focused,
                                    CallbackInfo ci) {
        ChatLineFx.beginFrame(this.visibleMessages, this.scrolledLines, currentTick,
                visuals$chatScale(), textRenderer);
        ChatPlus.beginFrame();
        HWRulesAuto.beginFrame();
    }

    @Inject(
            method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V",
            at = @At("TAIL"))
    private void visuals$endFrame(DrawContext ctx, TextRenderer textRenderer, int currentTick,
                                  int mouseX, int mouseY, boolean constantOpacity, boolean focused,
                                  CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.textRenderer == null) return;
        ChatPlus.flush(ctx, mc.textRenderer, visuals$chatScale());
        if (!(mc.currentScreen instanceof ChatScreen)) return;
        HWRulesAuto.renderTooltip(ctx, mc.textRenderer, mouseX, mouseY,
                mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
    }
}

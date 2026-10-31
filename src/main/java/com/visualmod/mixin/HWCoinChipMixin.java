package com.visualmod.mixin;

import com.visualmod.util.Diag;
import com.visualmod.util.HWCoin;
import com.visualmod.util.HudStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HandledScreen.class, priority = 900)
public abstract class HWCoinChipMixin {

    @Shadow
    protected Slot focusedSlot;

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("TAIL"), require = 0)
    private void visuals$chip(DrawContext ctx, int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
        try {
            Slot slot = focusedSlot;
            if (slot == null || !slot.hasStack()) return;

            Text label = HWCoin.labelFor((HandledScreen<?>) (Object) this, slot.getStack());
            if (label == null) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            int pad = 8, h = 17;
            int w = mc.textRenderer.getWidth(label) + pad * 2;
            int x = (mc.getWindow().getScaledWidth() - w) / 2;
            int y = mc.getWindow().getScaledHeight() - h - 4;

            HudStyle.fillRounded(ctx, x, y, w, h, 6f, 0xE00A0C14);
            HudStyle.fillRounded(ctx, x, y + h - 2f, w, 2f, 1f, HudStyle.ACCENT);
            ctx.drawTextWithShadow(mc.textRenderer, label, x + pad, y + 5, HudStyle.TEXT);
        } catch (Throwable t) {
            Diag.once("HWCoin.chip", t);
        }
    }
}

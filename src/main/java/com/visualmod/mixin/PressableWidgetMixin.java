package com.visualmod.mixin;

import com.visualmod.screen.StyledButton;
import com.visualmod.util.ButtonStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PressableWidget.class)
public abstract class PressableWidgetMixin {

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void visuals$styledButton(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof ButtonWidget btn)) return;
        if (self instanceof StyledButton || ButtonStyle.shouldStyle(MinecraftClient.getInstance().currentScreen)) {
            ButtonStyle.render(btn, ctx);
            ci.cancel();
        }
    }
}

package com.visualmod.mixin;

import com.visualmod.module.modules.hud.DeathCard;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DeathScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DeathScreen.class)
public class DeathScreenCardMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void visuals$deathCard(DrawContext ctx, int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
        try {
            DeathCard.drawOnDeathScreen(ctx);
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("DeathScreenCardMixin", t);
        }
    }
}

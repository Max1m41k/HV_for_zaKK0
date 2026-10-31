package com.visualmod.mixin;

import com.visualmod.VisualsMod;
import com.visualmod.module.modules.render.CustomCrosshair;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class CrosshairMixin {

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void visuals$customCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (VisualsMod.moduleManager == null) return;
        CustomCrosshair mod = VisualsMod.moduleManager.getModule(CustomCrosshair.class);
        if (mod == null || !mod.isEnabled()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options == null) return;

        if (!mc.options.getPerspective().isFirstPerson()) {
            ci.cancel();
            return;
        }
        if (mc.getDebugHud().shouldShowDebugHud()) return;
        if (mc.interactionManager != null && mc.interactionManager.getCurrentGameMode() == GameMode.SPECTATOR) return;

        ci.cancel();
        mod.draw(context, context.getScaledWindowWidth() / 2, context.getScaledWindowHeight() / 2);
    }
}

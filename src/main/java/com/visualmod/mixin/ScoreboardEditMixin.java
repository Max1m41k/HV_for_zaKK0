package com.visualmod.mixin;

import com.visualmod.VisualsMod;
import com.visualmod.module.modules.hud.HudPosition;
import com.visualmod.module.modules.hud.ScoreboardEdit;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class ScoreboardEditMixin {

    private static final int ANCHOR_X_OFFSET = -140;
    private static final int ANCHOR_Y = 40;

    private static final String TARGET =
            "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V";

    @Inject(method = TARGET, at = @At("HEAD"))
    private void visuals$begin(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!active()) return;
        int[] d = delta();
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(d[0], d[1]);
    }

    @Inject(method = TARGET, at = @At("RETURN"))
    private void visuals$end(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!active()) return;
        context.getMatrices().popMatrix();
    }

    private static boolean active() {
        if (VisualsMod.moduleManager == null) return false;
        ScoreboardEdit mod = VisualsMod.moduleManager.getModule(ScoreboardEdit.class);
        return mod != null && mod.isEnabled();
    }

    private static int[] delta() {
        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int[] g = HudPosition.glide("Scoreboard", sw, sh);
        int resolvedX = g[0];
        int resolvedY = g[1];
        int anchorX = sw + ANCHOR_X_OFFSET;
        int anchorY = ANCHOR_Y;
        return new int[]{resolvedX - anchorX, resolvedY - anchorY};
    }
}

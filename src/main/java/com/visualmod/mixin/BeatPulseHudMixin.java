package com.visualmod.mixin;

import com.visualmod.util.BeatPulse;
import com.visualmod.util.Diag;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.joml.Matrix3x2fStack;

@Mixin(InGameHud.class)
public class BeatPulseHudMixin {

    private boolean visuals$breathed;

    /**
     * Дыхание кладём в корневую позицию матриц HUD: глубина стека не меняется,
     * поэтому ни один чужой push/pop не съезжает. InGameHud.render в 1.21.4 — это
     * просто LayeredDrawer.render, ранних выходов у него нет, так что TAIL guaranteed.
     */
    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("HEAD"), require = 0)
    private void visuals$breath(DrawContext ctx, RenderTickCounter tickCounter, CallbackInfo ci) {
        visuals$breathed = false;
        try {
            BeatPulse.update();
            float s = BeatPulse.hudScale();
            if (s <= 1.0004f) return;
            Matrix3x2fStack ms = ctx.getMatrices();
            float cx = ctx.getScaledWindowWidth() / 2f;
            float cy = ctx.getScaledWindowHeight() / 2f;
            ms.translate(cx, cy);
            ms.scale(s, s);
            ms.translate(-cx, -cy);
            visuals$breathed = true;
        } catch (Throwable t) {
            Diag.once("BeatPulse.hud", t);
        }
    }

    /** Экран и всё, что рисуется после HUD, обязано остаться в обычных координатах. */
    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("TAIL"), require = 0)
    private void visuals$unbreath(DrawContext ctx, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!visuals$breathed) return;
        visuals$breathed = false;
        ctx.getMatrices().identity();
    }
}

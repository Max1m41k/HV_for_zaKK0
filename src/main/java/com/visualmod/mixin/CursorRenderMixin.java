package com.visualmod.mixin;

import com.visualmod.module.modules.render.CustomCursor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** renderWithTooltip вызывается один раз за кадр — для верхнего экрана и уже после тултипов. */
@Mixin(Screen.class)
public abstract class CursorRenderMixin {

    @Inject(method = "renderWithTooltip", at = @At("TAIL"))
    private void visuals$drawCursor(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        CustomCursor.drawOnScreen(ctx, mouseX, mouseY);
    }
}

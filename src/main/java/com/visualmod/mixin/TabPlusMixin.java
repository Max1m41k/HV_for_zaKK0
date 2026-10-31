package com.visualmod.mixin;

import com.visualmod.util.TabPlus;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Tab+ — встроенная фича, без модуля: подменяет штатные DrawContext.fill(...) фона TAB-списка
 * скруглёнными. Арность хэндлера = приёмники целевого метода + сам вызов, иначе краш старта.
 */
@Mixin(PlayerListHud.class)
public abstract class TabPlusMixin {

    @Redirect(
            method = "render(Lnet/minecraft/client/gui/DrawContext;ILnet/minecraft/scoreboard/Scoreboard;"
                    + "Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"),
            require = 0
    )
    private void visuals$tabFill(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        try {
            TabPlus.fill(ctx, x1, y1, x2, y2, color);
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("TabPlus", t);
        }
    }
}

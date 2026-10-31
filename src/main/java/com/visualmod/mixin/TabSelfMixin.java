package com.visualmod.mixin;

import com.visualmod.util.Diag;
import com.visualmod.util.TabSelf;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Встроенная зелёная подсветка своего ряда в TAB-листе. Плашку надо класть под текст, поэтому
 * перехватываем рисование скина: getSkinTextures вызывается за три байткода до draw и говорит,
 * какой это ряд. Арность хэндлеров = приёмники целевых методов + сам вызов.
 */
@Mixin(PlayerListHud.class)
public abstract class TabSelfMixin {

    @Redirect(
            method = "render(Lnet/minecraft/client/gui/DrawContext;ILnet/minecraft/scoreboard/Scoreboard;"
                    + "Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/network/PlayerListEntry;getSkinTextures()"
                            + "Lnet/minecraft/entity/player/SkinTextures;"),
            require = 0
    )
    private SkinTextures visuals$markRow(PlayerListEntry entry) {
        try {
            TabSelf.mark(entry);
        } catch (Throwable t) {
            Diag.once("TabSelf.mark", t);
        }
        return entry.getSkinTextures();
    }

    @Redirect(
            method = "render(Lnet/minecraft/client/gui/DrawContext;ILnet/minecraft/scoreboard/Scoreboard;"
                    + "Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/PlayerSkinDrawer;draw"
                            + "(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/util/Identifier;IIIZZI)V"),
            require = 0
    )
    private void visuals$plate(DrawContext ctx, Identifier texture, int x, int y, int size,
                               boolean showHat, boolean flip, int color) {
        try {
            TabSelf.plate(ctx, x, y);
        } catch (Throwable t) {
            Diag.once("TabSelf.plate", t);
        }
        PlayerSkinDrawer.draw(ctx, texture, x, y, size, showHat, flip, color);
    }
}
